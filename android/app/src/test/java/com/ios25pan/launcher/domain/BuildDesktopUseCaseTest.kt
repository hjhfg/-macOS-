package com.ios25pan.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildDesktopUseCaseTest {

    private val useCase = BuildDesktopUseCase()

    private val snapshot = AppSnapshot(
        apps = listOf(
            LaunchableApp("com.a/.Main", "微信", "com.a"),
            LaunchableApp("com.b/.Main", "支付宝", "com.b"),
            LaunchableApp("com.c/.Main", "设置", "com.c"),
        ),
        roles = mapOf("settings" to "com.c/.Main", "browser" to null),
    )

    private fun item(
        id: String,
        action: String,
        zone: Zone = Zone.PAGE,
        page: Int = 0,
        type: ItemType = ItemType.APP,
        parentId: String? = null,
        order: Int = 0,
    ) = DesktopItem(
        id = id, zone = zone, page = page, parentId = parentId, type = type,
        title = id, action = action, iconRes = null, row = 0, col = 0,
        rowSpan = 1, colSpan = 1, rotation = 0f, appWidgetId = null, order = order,
    )

    @Test
    fun `role action is resolved to the installed component`() {
        val desktop = useCase(listOf(item("s", "role:settings")), snapshot, emptySet())

        val slot = desktop.pages.single().slots.single()
        assertEquals("com.c/.Main", slot.item.component)
    }

    @Test
    fun `unresolvable role stays on the desktop but has no component`() {
        val desktop = useCase(listOf(item("b", "role:browser")), snapshot, emptySet())

        assertEquals(1, desktop.pages.single().slots.size)
        assertEquals(null, desktop.pages.single().slots.single().item.component)
    }

    @Test
    fun `installed apps missing from the layout are appended`() {
        val desktop = useCase(listOf(item("s", "role:settings")), snapshot, emptySet())

        val components = desktop.pages.flatMap { it.slots }.map { it.item.component }.toSet()
        assertTrue(components.contains("com.a/.Main"))
        assertTrue(components.contains("com.b/.Main"))
        // 已经在布局里的设置（通过角色解析到 com.c）不应重复出现
        assertEquals(1, desktop.pages.flatMap { it.slots }.count { it.item.component == "com.c/.Main" })
    }

    @Test
    fun `hidden actions are dropped and not re-added as extra apps`() {
        val desktop = useCase(
            items = listOf(item("a", "app:com.a/.Main")),
            snapshot = snapshot,
            hidden = setOf("app:com.a/.Main"),
        )

        assertTrue(desktop.pages.flatMap { it.slots }.none { it.item.component == "com.a/.Main" })
    }

    @Test
    fun `dock keeps its own order and folders are grouped`() {
        val desktop = useCase(
            items = listOf(
                item("d2", "role:settings", zone = Zone.DOCK, order = 5),
                item("d1", "app:com.a/.Main", zone = Zone.DOCK, order = 1),
                item("x", "folder:x", type = ItemType.FOLDER),
                item("g1", "app:com.b/.Main", zone = Zone.FOLDER, parentId = "x", order = 2),
                item("g0", "app:com.a/.Main", zone = Zone.FOLDER, parentId = "x", order = 1),
            ),
            snapshot = AppSnapshot(emptyList(), emptyMap()),
            hidden = emptySet(),
        )

        assertEquals(listOf("d1", "d2"), desktop.dock.map { it.id })
        assertEquals(listOf("g0", "g1"), desktop.folders["x"]?.map { it.id })
        // 文件夹名取自目录项本身
        assertEquals("x", desktop.folderTitles["x"])
    }

    @Test
    fun `pages from the same group stay contiguous and are renumbered`() {
        val items = (0 until 30).map { item("i$it", "app:com.x$it/.Main", page = 0) } +
            (0 until 3).map { item("p$it", "app:com.y$it/.Main", page = 1) }

        val desktop = useCase(items, AppSnapshot(emptyList(), emptyMap()), emptySet())

        // 第一组 30 项在 4x6 网格里要占 2 页，第二组接着排
        assertEquals(0, desktop.pages[0].group)
        assertEquals(0, desktop.pages[1].group)
        assertEquals(1, desktop.pages[2].group)
        assertEquals(0, desktop.pages[0].index)
        assertEquals(2, desktop.pages[2].index)
    }
}
