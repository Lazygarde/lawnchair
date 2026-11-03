package app.lawnchair.hotseat

/**
 * Spreads the dock's icons edge to edge (`config_dock_centered_columns`). The dock has as many
 * cells as icons, filling its width; each icon moves inside its own cell so the first sits at the
 * left edge and the last at the right edge, at the spacing of a full dock (a single icon in the
 * middle). An icon never leaves its cell, so the cell a drop lands in is the one it shows in.
 */
object DockCentering {

    /**
     * Translation for the icon laid out at [left] (`lp.x`) in a dock of [cols] cells, at most
     * [maxCols]. The cell index comes from the position, so RTL (mirrored `lp.x`) works the same.
     */
    fun offset(left: Int, cellWidth: Int, cols: Int, maxCols: Int, border: Int): Float {
        val stride = cellWidth + border
        val width = cols * cellWidth + (cols - 1) * border
        val index = if (stride > 0) Math.round(left.toFloat() / stride) else 0
        return center(index, cols, maxCols, width, border) - (left + cellWidth / 2f)
    }

    /** Where the icon in visual slot [index] of [cols] shows its center, across [width]. */
    fun center(index: Int, cols: Int, maxCols: Int, width: Int, border: Int): Float {
        // A full dock's cell: the spacing the icons keep at the edges.
        val slot = (width - (maxCols - 1) * border) / maxCols.toFloat()
        return if (cols <= 1) width / 2f else slot / 2 + index * (width - slot) / (cols - 1)
    }
}
