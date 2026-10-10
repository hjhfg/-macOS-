package com.ios25pan.launcher.data.window

import com.ios25pan.launcher.window.IWindowShell
import java.util.concurrent.TimeUnit

/**
 * 运行在 Shizuku 特权进程里的执行器（UserService）。
 *
 * 与普通的 Android Service 不同：**它不继承 Service**，也不需要（也不能）在 Manifest 里声明 ——
 * Shizuku 是直接用 app_process 加载本 APK 里的这个类并实例化的，
 * 所以它只要实现 AIDL 生成的 Stub（Stub 本身就是 Binder，满足 Shizuku 对 UserService 的要求）。
 *
 * 这里刻意只用纯 Java/JVM 能力（Runtime.exec），不碰任何 Android 框架 API：
 * 官方明确说明 UserService 进程不是一个合法的应用进程，Context/ContentResolver 之类的都不可用。
 */
class WindowShellService : IWindowShell.Stub() {

    override fun destroy() {
        // Shizuku 在版本不匹配或解绑时调用；不退出的话特权进程会一直残留。
        System.exit(0)
    }

    override fun exec(command: String): String = try {
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            // 必须合并：分开读 stdout/stderr，输出量大时任何一个管道写满都会卡住子进程
            .redirectErrorStream(true)
            .start()

        // 读流和等退出分别在两个线程：直接在主线程 `readText()` 会一直阻塞到 EOF，
        // 命令一旦卡住（不产生 EOF）超时判断永远到不了，10s 的超时就形同虚设。
        val output = StringBuilder()
        val reader = Thread {
            runCatching { process.inputStream.bufferedReader().forEachLine { output.append(it).append('\n') } }
        }.apply { isDaemon = true; start() }

        val finished = process.waitFor(COMMAND_TIMEOUT_S, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            reader.join(READER_JOIN_MS)
            "exit=$EXIT_TIMEOUT\n执行超时（${COMMAND_TIMEOUT_S}s）：$command\n$output"
        } else {
            reader.join(READER_JOIN_MS)
            "exit=${process.exitValue()}\n$output"
        }
    } catch (t: Throwable) {
        "exit=$EXIT_EXCEPTION\n${t.javaClass.simpleName}: ${t.message}"
    }

    private companion object {
        const val COMMAND_TIMEOUT_S = 10L
        const val READER_JOIN_MS = 500L
        const val EXIT_TIMEOUT = 124
        const val EXIT_EXCEPTION = 126
    }
}
