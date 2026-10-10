package com.ios25pan.launcher.data.window

import android.util.Log
import com.ios25pan.launcher.domain.WindowRect
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** `ActivityOptions` 的 WINDOWING_MODE_FREEFORM。`am start --windowingMode 5` 里的 5 就是这个。 */
const val WINDOWING_MODE_FREEFORM = 5

/**
 * 自由窗口的"遥控器"。
 *
 * 分工必须说清楚，不然很容易写出根本不可能实现的代码：
 *  - **窗口不是我们画的**。Android 里每个 App 跑在自己的进程、自己的 Window 上，
 *    由 SystemUI / WM Shell 负责绘制、层叠、拖动手柄。系统没有 iframe 的等价物让 A 应用
 *    把 B 应用的界面渲染成自己的一个 View（这是防点击劫持 / UI redressing 的刻意设计）。
 *  - Android 13+ 的 Activity Embedding 是唯一的官方口子，但要求**被嵌入的 App 主动声明信任宿主**，
 *    普通 App 不会声明，所以这条路对我们等于不存在。
 *  - 所以我们只**发指令**：`am start --windowingMode 5`，系统负责画。
 *
 * 为什么必须借 shell 身份（Shizuku）而不是 Kotlin 直接调：
 *  `ActivityOptions.setLaunchWindowingMode()` 是隐藏 API，第三方进程被限制，反射也会被拦；
 *  `setLaunchBounds()` 虽然是公开的，但它只能设边界、改不了 windowing mode。
 *  可用的实测路径是 `am start --windowingMode 5`。
 *
 * 顺带要开两个全局开关（需要 WRITE_SECURE_SETTINGS，Shizuku 可授权）：
 *  `enable_freeform_support` 和 `force_resizable_activities`；
 *  后者是"强制所有 Activity 可调整大小"，不开的话一半 App 会拒绝进窗口。
 *
 * ⚠️ 厂商可以覆盖多窗口行为（AOSP 文档原话："device manufacturers can override these
 * multi-window behaviors"）。三星 One UI 有自己的多窗口栈，所以这里所有操作都返回成败，
 * 失败时上层一律降级成整屏启动，绝不留在中间态。
 */
interface FreeformController {

    val shellState: StateFlow<ShellState>

    /** 弹出 Shizuku 的授权对话框；没装 Shizuku（服务没在跑）时返回 false。 */
    fun requestPermission(): Boolean

    /** 打开两个全局开关。返回是否成功；已开过就直接返回 true。 */
    suspend fun ensureEnvironment(): Boolean

    /** 以自由窗口启动一个组件。rect 为空时由系统决定初始大小。 */
    suspend fun launch(component: String, rect: WindowRect?): Boolean

    /** 移动 / 缩放一个已经开着的窗口。 */
    suspend fun moveResize(component: String, rect: WindowRect): Boolean

    /** 关闭窗口（force-stop 会结束该包的所有进程，等于关掉它的全部窗口）。 */
    suspend fun close(component: String): Boolean

    suspend fun closeAll(components: List<String>)

    /** 给 UI 看的诊断信息：两个全局开关的值 + 一次 `am` 的可用情况。 */
    suspend fun diagnostics(): String
}

@Singleton
class ShizukuFreeformController @Inject constructor(
    private val shell: WindowShell,
) : FreeformController {

    override val shellState: StateFlow<ShellState> = shell.state

    override fun requestPermission(): Boolean = shell.requestPermission()

    @Volatile
    private var environmentReady = false

    /** null = 还没试过；true/false = 这个系统支不支持 `am start --activity-bounds`。 */
    @Volatile
    private var boundsSupported: Boolean? = null

    override suspend fun ensureEnvironment(): Boolean {
        if (environmentReady) return true
        if (shell.state.value != ShellState.READY) return false

        val freeform = shell.exec("settings put global $KEY_FREEFORM_SUPPORT 1")
        val resizable = shell.exec("settings put global $KEY_FORCE_RESIZABLE 1")
        environmentReady = freeform.isOk && resizable.isOk
        if (!environmentReady) {
            Log.w(TAG, "自由窗口开关写入失败: ${freeform.output} / ${resizable.output}")
        }
        return environmentReady
    }

    override suspend fun launch(component: String, rect: WindowRect?): Boolean {
        if (!ensureEnvironment()) return false

        // 第一次总是先试带 bounds 的版本，失败（多半是"Unknown option"）就降级成不带，
        // 并把结果记住，后面不再白试一次
        val support = boundsSupported
        val useBounds = rect != null && support != false

        var result = shell.exec(buildStart(component, if (useBounds) rect else null))
        if (useBounds && !acceptable(result)) {
            Log.w(TAG, "带 bounds 的启动失败，降级为不带 bounds：${result.output}")
            boundsSupported = false
            result = shell.exec(buildStart(component, null))
        }
        if (support == null && acceptable(result)) boundsSupported = useBounds

        if (!acceptable(result)) Log.w(TAG, "自由窗口启动失败 [$component]：${result.output}")
        return acceptable(result)
    }

    override suspend fun moveResize(component: String, rect: WindowRect): Boolean {
        if (!ensureEnvironment()) return false

        val taskId = taskIdOf(component)
        if (taskId != null) {
            val r = shell.exec("am task resize $taskId ${rect.flatten()}")
            if (acceptable(r)) return true
            Log.w(TAG, "am task resize 失败（task=$taskId），改用重新投递 bounds：${r.output}")
        }
        // 拿不到 taskId 或 resize 不支持时的兜底：
        // 对已经在自由窗口里的 Activity 再 start 一次，系统会把它前置，
        // 同时把新的 bounds 应用到它所在的任务上。
        return launch(component, rect)
    }

    override suspend fun close(component: String): Boolean {
        if (shell.state.value != ShellState.READY) return false
        val pkg = component.substringBefore('/', component)
        return acceptable(shell.exec("am force-stop $pkg"))
    }

    override suspend fun closeAll(components: List<String>) {
        for (c in components) close(c)
    }

    override suspend fun diagnostics(): String {
        if (shell.state.value != ShellState.READY) return "Shizuku 未就绪（${shell.state.value}）"
        val a = shell.exec("settings get global $KEY_FREEFORM_SUPPORT").output.trim()
        val b = shell.exec("settings get global $KEY_FORCE_RESIZABLE").output.trim()
        return "enable_freeform_support=$a\nforce_resizable_activities=$b\nbounds 参数支持: ${boundsSupported?.let { if (it) "是" else "否（已自动降级）" } ?: "未探测（还没开过窗口）"}"
    }

    // ---- 内部 ----

    private fun buildStart(component: String, rect: WindowRect?): String = buildString {
        append("am start -n ").append(component)
        append(" -a android.intent.action.MAIN")
        append(" -c android.intent.category.LAUNCHER")
        append(" --windowingMode ").append(WINDOWING_MODE_FREEFORM)
        if (rect != null) {
            // 必须加引号：AOSP 的 getBounds()/unflattenFromString 要的是"一个"参数 "left top right bottom"
            append(" --activity-bounds \"").append(rect.flatten()).append('"')
        }
    }

    /**
     * 从 `dumpsys activity activities` 里找某个包的任务 id。
     *
     * 不用 `am stack list`：它打印的是 **RootTaskInfo.taskId**（根任务/栈的 id，通常是 1、2、3 这种），
     * 而 `am task resize` 要的是**活动任务 id**，两者不是一回事，传进去会 resize 到别的地方去。
     *
     * 输出形如：
     * `  * Task{7f1c2a #42 type=standard A=10123:com.example U=0 visible=true ...}`
     * 我们要的是 `#42`。解析失败就返回 null，上层有兜底路径。
     */
    private suspend fun taskIdOf(component: String): Int? {
        val pkg = component.substringBefore('/', component)
        if (pkg.isBlank()) return null
        return runCatching {
            val r = shell.exec("dumpsys activity activities | grep -F \"$pkg\" | head -20")
            if (!r.isOk) return null
            for (line in r.output.lineSequence()) {
                if (!line.contains("Task{")) continue
                val id = TASK_ID.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
                return id
            }
            null
        }.onFailure { Log.w(TAG, "解析任务 id 失败", it) }.getOrNull()
    }

    /** 退出码为 0 且输出里没有明显的错误字样。`am` 的错误是打到 stderr 的（已被合并）。 */
    private fun acceptable(r: ShellResult): Boolean =
        r.isOk && !r.output.contains("error", ignoreCase = true) && !r.output.contains("exception", ignoreCase = true)

    private companion object {
        const val TAG = "FreeformController"
        const val KEY_FREEFORM_SUPPORT = "enable_freeform_support"
        const val KEY_FORCE_RESIZABLE = "force_resizable_activities"

        /** 匹配 Task{xxxxxx #123 里的 #123。 */
        val TASK_ID = Regex("#(\\d+)")
    }
}
