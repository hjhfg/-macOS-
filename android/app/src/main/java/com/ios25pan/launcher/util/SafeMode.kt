package com.ios25pan.launcher.util

import android.app.Application
import android.content.Context

/**
 * 崩溃后的自动降级开关。
 *
 * 启动器和普通应用不同：它一崩，用户就回不了桌面，连"看看日志"的机会都没有。
 * 所以这里用 SharedPreferences（同步读写，不需要协程）记一个开关：
 * 一旦崩溃堆栈指向毛玻璃/图形层，下次启动就自动关掉模糊，先把桌面保住。
 *
 * 只用 SharedPreferences 而不用 DataStore，是因为这个值要在组合期同步读取，
 * DataStore 是异步的，首帧拿不到。
 */
object SafeMode {

    private const val PREFS = "launcher_safe_mode"
    private const val KEY_NO_BLUR = "no_blur"

    private const val HAZE_PACKAGE = "dev.chrisbanes.haze"
    private const val RENDER_EFFECT = "RenderEffect"
    private const val GRAPHICS = "android.graphics"

    private lateinit var app: Application

    fun install(application: Application) {
        app = application
    }

    /** 是否已关闭模糊（降级模式）。 */
    fun blurDisabled(): Boolean = prefs().getBoolean(KEY_NO_BLUR, false)

    fun setBlurDisabled(disabled: Boolean) {
        prefs().edit().putBoolean(KEY_NO_BLUR, disabled).apply()
    }

    /**
     * 根据崩溃堆栈判断要不要降级。
     *
     * 注意这里刻意只认毛玻璃/图形层的崩溃：崩溃原因有很多，
     * 不能因为一次无关的异常就把效果永久关掉。
     */
    fun shouldDisableBlur(stackTrace: String): Boolean =
        stackTrace.contains(HAZE_PACKAGE) ||
            stackTrace.contains(RENDER_EFFECT) ||
            stackTrace.contains("$GRAPHICS.")

    private fun prefs() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
