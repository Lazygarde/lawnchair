package app.lawnchair.drawerslots

/** Pure helpers for keeping the grid column when D-pad focus passes through a full-width slot. */
object SlotFocusMath {

    /**
     * Column (0 = first in layout order) at horizontal position [x] inside a row of [width] pixels
     * split into [appsPerRow] columns; mirrored for RTL. Returns -1 if unknown.
     */
    fun columnForX(x: Int, width: Int, appsPerRow: Int, rtl: Boolean): Int {
        if (width <= 0 || appsPerRow <= 0) return -1
        val leftColumn = (x.toLong() * appsPerRow / width).toInt().coerceIn(0, appsPerRow - 1)
        return if (rtl) appsPerRow - 1 - leftColumn else leftColumn
    }

    /**
     * Adapter position to focus when leaving the slot at [holderPos] up or down, keeping [column].
     * Arrays describe the adapter items. Picks the nearest icon row on that side and, in it, the
     * item in [column] (or the last item of a shorter row). Returns -1 if there is no icon row.
     */
    fun targetPosition(
        isIcon: BooleanArray,
        rowIndex: IntArray,
        rowAppIndex: IntArray,
        holderPos: Int,
        down: Boolean,
        column: Int,
    ): Int {
        if (column < 0) return -1
        val step = if (down) 1 else -1
        var p = holderPos + step
        // Only items directly next to the slot count; another slot or a divider ends the search.
        if (p !in isIcon.indices || !isIcon[p]) return -1
        val row = rowIndex[p]
        var best = p
        var bestIndex = -1
        while (p in isIcon.indices && isIcon[p] && rowIndex[p] == row) {
            if (rowAppIndex[p] == column) return p
            if (rowAppIndex[p] < column && rowAppIndex[p] > bestIndex) {
                best = p
                bestIndex = rowAppIndex[p]
            }
            p += step
        }
        // Short row: the last item before [column].
        return best
    }
}
