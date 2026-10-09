package com.ios25pan.launcher.domain

/** 桌面元素所在区域：页面网格 / Dock / 文件夹内部。 */
enum class Zone { PAGE, DOCK, FOLDER }

enum class ItemType { APP, BOOKMARK, FOLDER, WIDGET, DIVIDER }

/**
 * 一个桌面元素（对应 Room 的一行）。
 * 字段与网页端布局一一对应：row/col/rowSpan/colSpan 即网格坐标，rotation 预留给自由摆放，
 * action 描述点击后做什么（见 [ItemAction]）。
 */
data class DesktopItem(
    val id: String,
    val zone: Zone,
    val page: Int,
    val parentId: String?,
    val type: ItemType,
    val title: String,
    val action: String,
    val iconRes: String?,
    val row: Int,
    val col: Int,
    val rowSpan: Int,
    val colSpan: Int,
    val rotation: Float,
    val appWidgetId: Int?,
    val order: Int,
    /** 解析后的组件名（ComponentName.flattenToString），用于加载真实图标。 */
    val component: String? = null,
)

/** 打包到网格里的一个格子位置。 */
data class Slot(val item: DesktopItem, val row: Int, val col: Int, val rowSpan: Int, val colSpan: Int)

/** 一个物理页面。group 对应布局数据里的原始页（同一原始页的内容尽量放在一起）。 */
data class DesktopPage(val index: Int, val group: Int, val slots: List<Slot>)

data class Desktop(
    val pages: List<DesktopPage>,
    val dock: List<DesktopItem>,
    val folders: Map<String, List<DesktopItem>>,
    /** 文件夹 id -> 显示名（文件夹名本身存在目录项上，这里单独带出来给 UI 用）。 */
    val folderTitles: Map<String, String> = emptyMap(),
    /** 这一次打包用的网格规格（平板横屏下比手机竖屏列数更多），UI 按它摆放，不用自己猜。 */
    val grid: GridSpec.Grid = GridSpec.Grid.DEFAULT,
) {
    companion object {
        val EMPTY = Desktop(emptyList(), emptyList(), emptyMap(), emptyMap())
    }
}

/** 系统里可启动的一个 Activity（启动器图标）。 */
data class LaunchableApp(val component: String, val label: String, val packageName: String)

/** 某一时刻的应用快照：全部可启动应用 + 每个 Intent 角色解析到的组件（可能为 null）。 */
data class AppSnapshot(val apps: List<LaunchableApp>, val roles: Map<String, String?>)

data class WidgetProvider(val flat: String, val label: String)

/** 桌面元素的动作，由 DesktopItem.action 字符串解析而来。 */
sealed interface ItemAction {
    data class Role(val role: String) : ItemAction
    data class Component(val flat: String) : ItemAction
    data class Url(val url: String) : ItemAction
    data class FolderRef(val id: String) : ItemAction
    data class Widget(val provider: String) : ItemAction
    data object Clock : ItemAction
    data object None : ItemAction

    companion object {
        fun parse(raw: String): ItemAction = when {
            raw.startsWith("role:") -> Role(raw.removePrefix("role:"))
            raw.startsWith("app:") -> Component(raw.removePrefix("app:"))
            raw.startsWith("url:") -> Url(raw.removePrefix("url:"))
            raw.startsWith("folder:") -> FolderRef(raw.removePrefix("folder:"))
            raw.startsWith("appwidget:") -> Widget(raw.removePrefix("appwidget:"))
            raw == "builtin:clock" -> Clock
            else -> None
        }
    }
}
