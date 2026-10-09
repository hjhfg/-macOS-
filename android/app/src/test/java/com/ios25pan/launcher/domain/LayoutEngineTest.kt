package com.ios25pan.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEngineTest {

    private fun cell(id: String, rs: Int = 1, cs: Int = 1) = LayoutEngine.Cell(id, rs, cs)

    @Test
    fun `items are placed in order and never overlap`() {
        val cells = (0 until 24).map { cell("i$it") }
        val placements = LayoutEngine.pack(cells, cols = 4, rowsPerPage = 6)

        assertEquals(24, placements.size)
        // 4x6 = 24 个格子，正好放满一页
        assertTrue(placements.all { it.page == 0 })

        val occupied = mutableSetOf<Pair<Int, Int>>()
        placements.forEach { p ->
            for (r in p.row until p.row + p.rowSpan) {
                for (c in p.col until p.col + p.colSpan) {
                    assertTrue("重叠于 ($r,$c)", occupied.add(p.page to r * 100 + c))
                }
            }
        }
        assertEquals(24, occupied.size)
    }

    @Test
    fun `overflow opens a new page`() {
        val cells = (0 until 25).map { cell("i$it") }
        val placements = LayoutEngine.pack(cells, cols = 4, rowsPerPage = 6)

        assertEquals(24, placements.count { it.page == 0 })
        assertEquals(1, placements.count { it.page == 1 })
        assertEquals(0, placements.single { it.page == 1 }.row)
    }

    @Test
    fun `widget spanning cells reserves the whole area`() {
        val placements = LayoutEngine.pack(
            listOf(cell("clock", rs = 2, cs = 2), cell("a"), cell("b")),
            cols = 4,
            rowsPerPage = 6,
        )

        val clock = placements.first { it.id == "clock" }
        assertEquals(0, clock.row)
        assertEquals(0, clock.col)
        // 2x2 占了 (0,1)(0,2)(1,1)(1,2)，后面的图标只能从 col 2 开始
        val a = placements.first { it.id == "a" }
        val b = placements.first { it.id == "b" }
        assertTrue(a.col >= 2 || a.row >= 2)
        assertTrue(a.row to a.col != b.row to b.col)
    }

    @Test
    fun `oversized span is clamped to the grid`() {
        val placements = LayoutEngine.pack(listOf(cell("big", rs = 9, cs = 9)), cols = 4, rowsPerPage = 6)
        val big = placements.single()
        assertEquals(4, big.colSpan)
        assertEquals(6, big.rowSpan)
    }
}
