package com.ios25pan.launcher.data.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.ios25pan.launcher.domain.WidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppWidgetHost 封装。小组件的 RemoteViews 必须走 View 体系，
 * 所以这里返回的是 AppWidgetHostView，由 Compose 侧用 AndroidView 桥接。
 *
 * 缓存：同一个 appWidgetId 只创建一次 HostView，翻页/重组时直接复用，避免反复 inflate RemoteViews。
 * 可视区域懒加载：HorizontalPager 只组合当前页及相邻页，离屏的 AndroidView 会被 dispose。
 */
@Singleton
class WidgetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val host = AppWidgetHost(context, HOST_ID)
    private val manager = AppWidgetManager.getInstance(context)
    private val views = HashMap<Int, AppWidgetHostView>()

    fun startListening() = host.startListening()
    fun stopListening() = host.stopListening()

    fun providers(): List<WidgetProvider> =
        manager.installedProviders.map { info ->
            WidgetProvider(info.provider.flattenToString(), info.loadLabel(context.packageManager))
        }.sortedBy { it.label }

    fun allocate(): Int = host.allocateAppWidgetId()

    /** 有些设备允许直接绑定（已授予 BIND_APPWIDGET 或白名单），否则需要弹系统确认。 */
    fun tryBind(appWidgetId: Int, provider: ComponentName): Boolean =
        manager.bindAppWidgetIdIfAllowed(appWidgetId, provider)

    fun bindIntent(appWidgetId: Int, provider: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)

    /** 该 id 是否已经真正绑定到某个 provider（绑定页关闭后用它对账）。 */
    fun isBound(appWidgetId: Int): Boolean = manager.getAppWidgetInfo(appWidgetId) != null

    /** 返回缓存的 HostView；若 provider 信息缺失返回 null。 */
    fun hostView(activityContext: Context, appWidgetId: Int): AppWidgetHostView? {
        views[appWidgetId]?.let { return it }
        val info: AppWidgetProviderInfo = manager.getAppWidgetInfo(appWidgetId) ?: return null
        return host.createView(activityContext, appWidgetId, info).also { views[appWidgetId] = it }
    }

    fun release(appWidgetId: Int) {
        views.remove(appWidgetId)
        host.deleteAppWidgetId(appWidgetId)
    }

    private companion object {
        const val HOST_ID = 0x1025
    }
}
