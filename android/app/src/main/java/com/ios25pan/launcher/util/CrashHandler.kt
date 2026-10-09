package com.ios25pan.launcher.util

import android.app.Application
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志收集。
 *
 * 启动器是 HOME 应用，一崩就回不了桌面、也看不到系统弹窗，光靠 logcat 很难排查。
 * 这里把崩溃栈写到**应用专属的外部存储**（`/sdcard/Android/data/<包名>/files/crash.log`），
 * 用任意文件管理器就能打开，不需要 root、不需要 adb。
 * 同时保留系统默认处理器，让 logcat 里照样有完整堆栈。
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val FILE_NAME = "crash.log"
    private const val TAG = "LauncherCrash"
    private const val MAX_CHARS = 24_000

    private lateinit var app: Application
    private val systemHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    fun install(application: Application) {
        app = application
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    /** 日志文件的绝对路径，给用户看。 */
    fun logPath(): String = File(dir(), FILE_NAME).absolutePath

    /** 上次崩溃的堆栈；没有则返回 null。 */
    fun lastCrash(): String? = try {
        File(dir(), FILE_NAME).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    fun clear() {
        runCatching { File(dir(), FILE_NAME).delete() }
    }

    private fun dir(): File =
        app.getExternalFilesDir(null) ?: app.filesDir

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val header = buildString {
            appendLine("=== 崩溃时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())} ===")
            appendLine("线程：${thread.name}")
        }
        runCatching {
            File(dir(), FILE_NAME).writeText((header + stack).take(MAX_CHARS))
        }
        Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)
        // 交还给系统，保证 logcat 有完整堆栈、系统也能弹"应用已停止"
        systemHandler?.uncaughtException(thread, throwable)
    }
}
