package com.ios25pan.launcher.mvi

import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ios25pan.launcher.R
import com.ios25pan.launcher.data.DesktopRepository
import com.ios25pan.launcher.data.apps.AppRepository
import com.ios25pan.launcher.data.prefs.LauncherPrefs
import com.ios25pan.launcher.data.widget.WidgetRepository
import com.ios25pan.launcher.domain.BuildDesktopUseCase
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.domain.ItemType
import com.ios25pan.launcher.domain.LaunchItemUseCase
import com.ios25pan.launcher.domain.LaunchResult
import com.ios25pan.launcher.domain.WidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 桌面的 Store（ViewModel）。
 * 单向数据流：UI -> dispatch(intent) -> reduce 更新 State；需要 IO 的动作在 perform() 里异步完成，
 * 结果再以 intent 的形式回到 reduce。
 */
@HiltViewModel
class HomeStore @Inject constructor(
    private val desktopRepo: DesktopRepository,
    private val prefs: LauncherPrefs,
    private val apps: AppRepository,
    private val widgets: WidgetRepository,
    private val buildDesktop: BuildDesktopUseCase,
    private val launch: LaunchItemUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            // 安装/更新会连发好几条广播，去抖合并成一次；
            // 首次加载走 merge 的 flowOf(Unit)，不受去抖影响。
            val snapshots = merge(flowOf(Unit), apps.packageChanges().debounce(PACKAGE_DEBOUNCE_MS))
                .map { apps.snapshot() }

            combine(desktopRepo.observe(), prefs.hiddenActions, snapshots) { items, hidden, snapshot ->
                buildDesktop(items, snapshot, hidden)
            }
                // PackageManager 查询是阻塞式 IPC，必须放在 IO 而不是 Default
                .flowOn(Dispatchers.IO)
                // 包变化不一定真的改变桌面，别为了同样的结果重组一遍
                .distinctUntilChanged()
                // 上游偶发失败（PackageManager 事务异常等）重试几次，
                // 实在不行就记日志收尾 —— 绝不让异常冒到协程外面把应用搞崩
                .retry(3) { it !is kotlinx.coroutines.CancellationException }
                .catch { Log.e(TAG, "构建桌面失败", it) }
                .collect { dispatch(HomeIntent.Loaded(it)) }
        }
    }

    fun dispatch(intent: HomeIntent) {
        _state.value = reduce(_state.value, intent)
        perform(intent)
    }

    /** 纯状态转换，不做任何 IO。 */
    private fun reduce(s: HomeState, i: HomeIntent): HomeState = when (i) {
        is HomeIntent.Loaded -> s.copy(
            loading = false,
            desktop = i.desktop,
            currentPage = s.currentPage.coerceIn(0, (i.desktop.pages.size - 1).coerceAtLeast(0)),
        )
        is HomeIntent.PageChanged -> s.copy(currentPage = i.index)
        is HomeIntent.LongPress -> s.copy(editing = true)
        HomeIntent.ExitEdit -> s.copy(editing = false)
        is HomeIntent.OpenFolder -> s.copy(openFolderId = i.folderId)
        is HomeIntent.SetControlCenter -> s.copy(controlCenterOpen = i.open)
        is HomeIntent.SetWidgetPicker -> s.copy(
            widgetPickerOpen = i.open,
            providers = if (i.open) s.providers else emptyList(),
        )
        is HomeIntent.ProvidersLoaded -> s.copy(providers = i.providers)
        is HomeIntent.Tap, is HomeIntent.Remove,
        is HomeIntent.PickProvider, is HomeIntent.WidgetBindResult -> s
    }

    /** 副作用：IO、启动 Activity、系统服务调用。 */
    private fun perform(i: HomeIntent) {
        when (i) {
            is HomeIntent.Tap -> onTap(i.item)
            is HomeIntent.Remove -> viewModelScope.launch {
                if (i.item.type == ItemType.WIDGET) i.item.appWidgetId?.let { widgets.release(it) }
                // 小组件可以再次添加，其它元素记录到隐藏列表，防止被自动补齐又冒出来
                if (i.item.type != ItemType.WIDGET) prefs.hide(i.item.action)
                desktopRepo.remove(i.item.id)
            }
            is HomeIntent.SetWidgetPicker -> if (i.open) viewModelScope.launch {
                val list = withContext(Dispatchers.IO) { widgets.providers() }
                dispatch(HomeIntent.ProvidersLoaded(list))
            }
            is HomeIntent.PickProvider -> pickProvider(i.provider)
            is HomeIntent.WidgetBindResult -> viewModelScope.launch {
                if (i.ok) addWidget(i.appWidgetId, i.provider)
                else {
                    widgets.release(i.appWidgetId)
                    _effects.send(HomeEffect.Toast(R.string.widget_bind_denied))
                }
            }
            else -> Unit
        }
    }

    private fun onTap(item: DesktopItem) {
        if (_state.value.editing) return
        viewModelScope.launch {
            when (val r = launch(item)) {
                is LaunchResult.Start -> _effects.send(HomeEffect.Launch(r.intent))
                is LaunchResult.OpenFolder -> dispatch(HomeIntent.OpenFolder(r.folderId))
                LaunchResult.NotInstalled -> _effects.send(HomeEffect.Toast(R.string.app_not_installed))
                LaunchResult.None -> Unit
            }
        }
    }

    private fun pickProvider(provider: WidgetProvider) {
        viewModelScope.launch {
            val id = widgets.allocate()
            val cn = ComponentName.unflattenFromString(provider.flat) ?: return@launch
            if (widgets.tryBind(id, cn)) {
                addWidget(id, provider)
            } else {
                _effects.send(HomeEffect.BindWidget(widgets.bindIntent(id, cn), id, provider))
            }
            dispatch(HomeIntent.SetWidgetPicker(false))
        }
    }

    private suspend fun addWidget(appWidgetId: Int, provider: WidgetProvider) {
        val s = _state.value
        // 放到当前页所属的原始页组里
        val group = s.desktop.pages.getOrNull(s.currentPage)?.group ?: 0
        desktopRepo.addWidget(appWidgetId, provider.flat, group, provider.label)
    }

    // ---- 供 UI 调用的非状态能力 ----

    suspend fun loadIcon(flat: String) = apps.icon(flat)

    /** 同步取内存缓存里的图标（可能为空，为空时 UI 会用占位并等 loadIcon 回来）。 */
    fun cachedIcon(flat: String) = apps.cachedIcon(flat)

    fun widgetView(context: Context, appWidgetId: Int): View? = widgets.hostView(context, appWidgetId)

    private companion object {
        private const val TAG = "HomeStore"

        /** 包变化广播去抖：安装/更新会连发多条，且每次都要全量查 PackageManager。 */
        const val PACKAGE_DEBOUNCE_MS = 400L
    }

}