package com.ios25pan.launcher.domain

/**
 * 点击桌面图标时，应用以什么形态起来。
 *
 * 这里刻意**不**尝试在 Compose 里画别人的窗口 —— Android 里每个 App 跑在自己的进程、
 * 自己的 Window 上，由 SystemUI / WM Shell 负责绘制与层叠，第三方应用既拿不到也画不了
 * （没有 iframe 等价物，这是防止点击劫持的刻意设计）。
 *
 * 唯一的官方口子是 Android 13+ 的 Activity Embedding，但要求**被嵌入的 App 主动声明信任宿主**，
 * 普通 App 不会声明，所以对我们等于不存在。
 *
 * 真正能做的是"发指令"：用 Shizuku 拿到的 shell 身份去执行
 * `am start --windowingMode 5`，窗口由系统去画，我们只负责告诉系统在哪里画。
 */
enum class WindowMode { FULLSCREEN, FREEFORM }

/**
 * 窗口矩形，单位是**屏幕像素**（`am` 命令只认像素，不认 dp）。
 *
 * `am task resize` 用的是四个独立参数：`left top right bottom`，
 * 所以 [flatten] 输出空格分隔的形式，与 AOSP `ActivityManagerShellCommand#getBounds()` 一致。
 */
data class WindowRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun flatten(): String = "$left $top $right $bottom"

    fun offsetBy(dx: Int, dy: Int): WindowRect = copy(
        left = left + dx, top = top + dy, right = right + dx, bottom = bottom + dy,
    )

    companion object {
        fun parse(raw: String): WindowRect? {
            val p = raw.trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
            return if (p.size == 4) WindowRect(p[0], p[1], p[2], p[3]) else null
        }
    }
}

/** 一个由本启动器拉起的自由窗口（注册表里的一条）。 */
data class AppWindow(
    /** ComponentName.flattenToString()，例如 com.example/.MainActivity */
    val component: String,
    val label: String,
    val rect: WindowRect,
    val openedAt: Long,
) {
    val packageName: String get() = component.substringBefore('/', component)
}
