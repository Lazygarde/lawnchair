package app.lawnchair.hotseat

/**
 * Offsets that spread the dock's icons evenly (`config_dock_centered_columns`), so a dock with
 * fewer icons than slots shows no empty slot: the first icon at the left edge, the last at the
 * right edge, the rest evenly between; a single icon in the middle.
 */
object DockCentering {

    /**
     * Translation for each icon, in the order of [lefts] (each icon's laid-out left, `lp.x`).
     * Icons keep their order on screen; ranking by position keeps RTL right, as `lp.x` is mirrored.
     */
    fun offsets(lefts: List<Int>, cols: Int, cellWidth: Int, border: Int): List<Float> {
        val n = lefts.size
        if (n == 0) return emptyList()
        // Room the icons' lefts can range over: from the first slot to the last.
        val span = (cols * cellWidth + (cols - 1) * border - cellWidth).toFloat()
        val rank = lefts.indices.sortedBy { lefts[it] }.withIndex().associate { (r, i) -> i to r }
        return lefts.mapIndexed { i, left ->
            val target = if (n == 1) span / 2 else rank.getValue(i) * span / (n - 1)
            target - left
        }
    }
}
