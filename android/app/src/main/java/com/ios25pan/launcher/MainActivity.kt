package com.ios25pan.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.ios25pan.launcher.data.widget.WidgetRepository
import com.ios25pan.launcher.domain.WidgetProvider
import com.ios25pan.launcher.mvi.HomeIntent
import com.ios25pan.launcher.mvi.HomeStore
import com.ios25pan.launcher.ui.HomeScreen
import com.ios25pan.launcher.ui.theme.LauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 因为声明了 android.intent.category.HOME，系统会把它列进"默认主屏幕"选择里 ——
 * 不需要系统签名，也不需要 root，用户手动选一次即可。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var widgets: WidgetRepository

    // 与 Compose 中的 hiltViewModel() 是同一个实例（ViewModelStoreOwner 都是本 Activity）
    private val store: HomeStore by viewModels()

    /** 等待系统绑定确认的小组件：绑定页不返回结果数据，只能回来对账。 */
    private var pendingWidget: Pair<Int, WidgetProvider>? = null

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val (id, provider) = pendingWidget ?: return@registerForActivityResult
        pendingWidget = null
        // AOSP Launcher3 的做法：看这个 id 是否真的拿到了 providerInfo
        store.dispatch(HomeIntent.WidgetBindResult(id, provider, widgets.isBound(id)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LauncherTheme {
                HomeScreen(
                    store = store,
                    onBindWidget = { intent, id, provider ->
                        pendingWidget = id to provider
                        bindWidgetLauncher.launch(intent)
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 开始接收小组件的 RemoteViews 更新
        widgets.startListening()
    }

    override fun onStop() {
        widgets.stopListening()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 作为 HOME，再次点击主页键时回到第一页
        store.dispatch(HomeIntent.PageChanged(0))
    }
}
