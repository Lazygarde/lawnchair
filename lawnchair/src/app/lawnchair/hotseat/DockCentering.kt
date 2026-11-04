package app.lawnchair.hotseat

/**
 * The spread dock (`config_dock_centered_columns`): as many cells as icons, filling the width, each
 * icon in the middle of its cell, as One UI lays its dock out (one icon in the middle, two a
 * quarter in from each side, a full dock at the edges). The cells are CellLayout's own, so this
 * only says where an icon shows, for the drag over the dock.
 */
object DockCentering {

    /** Center of the icon in visual slot [index] of [cols] cells across [width], [border] apart. */
    fun center(index: Int, cols: Int, width: Int, border: Int): Float {
        val cell = (width - (cols - 1) * border) / cols.toFloat()
        return index * (cell + border) + cell / 2
    }
}
