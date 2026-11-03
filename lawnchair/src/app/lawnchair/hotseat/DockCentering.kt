package app.lawnchair.hotseat

/**
 * Offsets that center the dock's icons as a group (`config_dock_centered_columns`), so a dock
 * with fewer icons than slots shows no empty slot.
 */
object DockCentering {

    /**
     * Translation for each icon, in the order of [lefts] (each icon's laid-out left, `lp.x`).
     * Icons keep their order on screen; ranking by position keeps RTL right, as `lp.x` is mirrored.
     */
    fun offsets(lefts: List<Int>, cols: Int, cellWidth: Int, border: Int): List<Float> {
        val n = lefts.size
        if (n == 0) return emptyList()
        val stride = cellWidth + border
        val contentWidth = cols * cellWidth + (cols - 1) * border
        val groupWidth = n * cellWidth + (n - 1) * border
        val start = (contentWidth - groupWidth) / 2f
        val rank = lefts.indices.sortedBy { lefts[it] }.withIndex().associate { (r, i) -> i to r }
        return lefts.mapIndexed { i, left -> start + rank.getValue(i) * stride - left }
    }
}
