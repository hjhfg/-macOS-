package com.ios25pan.launcher.data.window

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.ios25pan.launcher.window.IWindowShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/** Shizuku 的可用性。 */
enum class ShellState {
    /** 没装 Shizuku / Sui，或者它的服务没启动。 */
    NOT_RUNNING,

    /** 服务在跑，但还没把 shell 身份授权给我们。 */
    NOT_GRANTED,

    /** 可以在特权进程里执行命令了。 */
    READY,
}

data class ShellResult(val exitCode: Int, val output: String) {
    val isOk: Boolean get() = exitCode == 0
}

private const val EXIT_NOT_READY = 125
private const val EXIT_BIND_FAILED = 127

/**
 * 拿 shell 身份执行命令的通道。
 *
 * 走的是 Shizuku 官方推荐的 **UserService**，不是已经废弃的 `Shizuku#newProcess`：
 * newProcess 从 13.1.1 起被官方标记废弃（"prepare to remove"，14 起移除），
 * 而且它只能传文本、没有 tty、不可靠。UserService 是把我们自己 APK 里的一个类
 * 丢到 Shizuku 拉起的特权进程（uid 0 / 2000）里跑，能力只受 Linux 权限限制。
 *
 * 顺带一提，这也是为什么不能直接在 Kotlin 里调 `ActivityOptions.setLaunchWindowingMode()`：
 * 那是隐藏 API，第三方进程被限制；而特权进程里没有非 SDK 接口限制，
 * 但最稳的做法仍然是让 `am` 这类系统工具去做，它们本来就是系统的一部分。
 */
@Singleton
class WindowShell @Inject constructor(
    @ApplicationContext private val app: Context,
) {

    private val _state = MutableStateFlow(ShellState.NOT_RUNNING)
    val state: StateFlow<ShellState> = _state.asStateFlow()

    private val lock = Mutex()
    private var shell: IWindowShell? = null

    private val serviceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(app.packageName, WindowShellService::class.java.name))
            // 非守护模式：本进程死了就一起收掉，不在系统里残留一个特权进程
            .daemon(false)
            .processNameSuffix("window")
            // tag 用来区分不同服务，不能省（类名在混淆后会变，这里 minify 虽然关着但保持好习惯）
            .tag("launcher-window-shell")
            // 改了 WindowShellService 的代码就把这个数字 +1，Shizuku 会重建服务
            .version(SERVICE_VERSION)
    }

    private val onBinderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val onBinderDead = Shizuku.OnBinderDeadListener {
        shell = null
        refresh()
    }
    private val onPermissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        // 这个类是 @Singleton，第一次被注入（随便哪条依赖链触发都行）时就把监听器装上，
        // 不需要单独在 Application 里再接一遍线。
        runCatching {
            // Sticky 版本：如果 Binder 已经在了会立刻回调一次，不用等下一次变化
            Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
            Shizuku.addBinderDeadListener(onBinderDead)
            Shizuku.addRequestPermissionResultListener(onPermissionResult)
        }.onFailure { Log.w(TAG, "注册 Shizuku 监听失败", it) }
        refresh()
    }

    /** 弹出 Shizuku 的授权对话框（用户在 Shizuku 里点允许）。 */
    fun requestPermission(): Boolean = runCatching {
        if (!Shizuku.pingBinder()) return false
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refresh()
            return true
        }
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        true
    }.getOrDefault(false)

    private fun refresh() {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            shell = null
            _state.value = ShellState.NOT_RUNNING
            return
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) shell = null
        _state.value = if (granted) ShellState.READY else ShellState.NOT_GRANTED
    }

    /** 在特权进程里执行一条命令。IO 线程调用，返回退出码与合并后的输出。 */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (_state.value != ShellState.READY) refresh()
        if (_state.value != ShellState.READY) {
            return@withContext ShellResult(EXIT_NOT_READY, "Shizuku 未就绪：${_state.value}")
        }

        // 最多两次：特权进程可能刚好被回收，清掉缓存重绑一次通常就好了
        var attempt = 0
        var lastFailure: ShellResult? = null
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val svc = lock.withLock { shell ?: bind().also { shell = it } }
            if (svc == null) {
                shell = null
                lastFailure = ShellResult(EXIT_BIND_FAILED, "绑定 Shizuku 用户服务失败")
                continue
            }
            val raw = try {
                svc.exec(command)
            } catch (t: Throwable) {
                shell = null
                lastFailure = ShellResult(EXIT_BIND_FAILED, "执行失败：${t.message}")
                continue
            }
            return@withContext parse(raw)
        }
        lastFailure ?: ShellResult(EXIT_BIND_FAILED, "执行失败")
    }

    private fun parse(raw: String): ShellResult {
        val first = raw.indexOf('\n')
        if (!raw.startsWith("exit=") || first < 0) return ShellResult(0, raw)
        val code = raw.substring(5, first).trim().toIntOrNull() ?: 0
        return ShellResult(code, raw.substring(first + 1))
    }

    private suspend fun bind(): IWindowShell? {
        val deferred = CompletableDeferred<IWindowShell?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                deferred.complete(service?.let { runCatching { IWindowShell.Stub.asInterface(it) }.getOrNull() })
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                shell = null
                if (deferred.isActive) deferred.complete(null)
            }
        }
        return try {
            Shizuku.bindUserService(serviceArgs, connection)
            withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }
        } catch (t: Throwable) {
            Log.w(TAG, "绑定 Shizuku 用户服务失败", t)
            null
        }
    }

    private companion object {
        const val TAG = "WindowShell"
        const val PERMISSION_REQUEST_CODE = 8001
        const val BIND_TIMEOUT_MS = 8_000L
        const val SERVICE_VERSION = 1
        const val MAX_ATTEMPTS = 2
    }
}
