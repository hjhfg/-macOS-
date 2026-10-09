package com.ios25pan.launcher.domain

import java.text.Collator
import java.util.Locale
import javax.inject.Inject

/** 手机竖屏网格：4 列 × 6 行。 */
const val GRID_COLS = 4
const val GRID_ROWS = 6

/**
 * 把"布局数据 + 应用快照 + 隐藏列表"合成为可渲染的桌面：
 *  1. 解析每个元素的真实组件（Intent 角色 -> 已安装应用）
 *  2. 过滤掉用户删除的元素
 *  3. 把系统里装了、但布局里没有的应用按名称追加到末尾（真正 Launcher 的行为）
 *  4. 按原始页分组打包成网格页
 */
class BuildDesktopUseCase @Inject constructor() {

    operator fun invoke(
        items: List<DesktopItem>,
        snapshot: AppSnapshot,
        hidden: Set<String>,
        cols: Int = GRID_COLS,
        rows: Int = GRID_ROWS,
    ): Desktop {
        val resolved = items.map { resolve(it, snapshot) }

        // 所有已解析组件（包括被隐藏的），避免被隐藏的系统应用又以"其它应用"的身份冒出来
        val used = resolved.mapNotNullTo(HashSet()) { it.component }

        val visible = resolved.filter { it.action !in hidden }
        val dock = visible.filter { it.zone == Zone.DOCK }.sortedBy { it.order }
        val folderItems = visible.filter { it.zone == Zone.PAGE && it.type == ItemType.FOLDER }
        val folders = visible.filter { it.zone == Zone.FOLDER }
            .groupBy { it.parentId.orEmpty() }
            .mapValues { (_, v) -> v.sortedBy { it.order } }
        val folderTitles = folderItems.associate { it.id to it.title }

        val pageItems = visible.filter { it.zone == Zone.PAGE }
        val groups = pageItems.groupBy { it.page }.toSortedMap()

        val pages = ArrayList<DesktopPage>()
        for ((group, list) in groups) {
            pages += pack(group, list.sortedBy { it.order }, cols, rows)
        }

        val extraGroup = (groups.keys.maxOrNull() ?: -1) + 1
        val collator = Collator.getInstance(Locale.CHINA)
        val extras = snapshot.apps
            .filter { it.component !in used && "app:${it.component}" !in hidden }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .map {
                DesktopItem(
                    id = "app:${it.component}", zone = Zone.PAGE, page = extraGroup,
                    parentId = null, type = ItemType.APP, title = it.label,
                    action = "app:${it.component}", iconRes = null,
                    row = 0, col = 0, rowSpan = 1, colSpan = 1, rotation = 0f,
                    appWidgetId = null, order = 0, component = it.component,
                )
            }
        pages += pack(extraGroup, extras, cols, rows)

        return Desktop(
            pages = pages.mapIndexed { i, p -> p.copy(index = i) },
            dock = dock,
            folders = folders,
            folderTitles = folderTitles,
        )
    }

    private fun resolve(item: DesktopItem, snapshot: AppSnapshot): DesktopItem =
        when (val a = ItemAction.parse(item.action)) {
            is ItemAction.Role -> item.copy(component = snapshot.roles[a.role])
            is ItemAction.Component -> item.copy(component = a.flat)
            else -> item
        }

    private fun pack(group: Int, list: List<DesktopItem>, cols: Int, rows: Int): List<DesktopPage> {
        if (list.isEmpty()) return emptyList()
        val byId = list.associateBy { it.id }
        // 只有小组件保留自身跨度；普通图标固定 1x1
        val cells = list.map {
            val widget = it.type == ItemType.WIDGET
            LayoutEngine.Cell(it.id, if (widget) it.rowSpan else 1, if (widget) it.colSpan else 1)
        }
        val placements = LayoutEngine.pack(cells, cols, rows)
        val pageCount = (placements.maxOfOrNull { it.page } ?: -1) + 1
        return (0 until pageCount).map { p ->
            DesktopPage(
                index = -1,
                group = group,
                slots = placements.filter { it.page == p }.map { pl ->
                    Slot(byId.getValue(pl.id), pl.row, pl.col, pl.rowSpan, pl.colSpan)
                },
            )
        }
    }
}
