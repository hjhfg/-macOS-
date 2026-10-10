package com.ios25pan.launcher.domain

/**
 * 纯函数的网格打包器：按顺序把元素塞进 cols × rowsPerPage 的页面里，放不下就开新页。
 *
 * 网页端是 15 列的大画布；手机竖屏只有 4 列，因此不能直接用原坐标，
 * 而是保持元素顺序、按各自的 rowSpan/colSpan 重新排版（首次适配 + 后续屏幕旋转都走这里）。
 */
object LayoutEngine {

    data class Cell(val id: String, val rowSpan: Int, val colSpan: Int)

    data class Placement(val id: String, val page: Int, val row: Int, val col: Int, val rowSpan: Int, val colSpan: Int)

    fun pack(cells: List<Cell>, cols: Int, rowsPerPage: Int): List<Placement> {
        require(cols > 0 && rowsPerPage > 0) { "cols/rowsPerPage must be positive" }
        val pages = ArrayList<Array<BooleanArray>>()
        val out = ArrayList<Placement>(cells.size)

        for (cell in cells) {
            val cs = cell.colSpan.coerceIn(1, cols)
            val rs = cell.rowSpan.coerceIn(1, rowsPerPage)
            var placed = false
            var page = 0
            while (!placed) {
                if (page == pages.size) pages.add(Array(rowsPerPage) { BooleanArray(cols) })
                val grid = pages[page]
                search@ for (r in 0..rowsPerPage - rs) {
                    for (c in 0..cols - cs) {
                        if (fits(grid, r, c, rs, cs)) {
                            mark(grid, r, c, rs, cs)
                            out += Placement(cell.id, page, r, c, rs, cs)
                            placed = true
                            break@search
                        }
                    }
                }
                if (!placed) page++
            }
        }
        return out
    }

    private fun fits(grid: Array<BooleanArray>, r: Int, c: Int, rs: Int, cs: Int): Boolean {
        for (i in r until r + rs) for (j in c until c + cs) if (grid[i][j]) return false
        return true
    }

    private fun mark(grid: Array<BooleanArray>, r: Int, c: Int, rs: Int, cs: Int) {
        for (i in r until r + rs) for (j in c until c + cs) grid[i][j] = true
    }
}
