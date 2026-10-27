package app.lawnchair.drawerslots

/**
 * Pure logic for placing inline slots in the app grid, kept free of Android types so it can be
 * unit tested.
 */
object InlineSlotPlanner {

    /**
     * Insert slot number [slotIndex] before the adapter item at [beforeIndex] (original indices).
     * [atTop] is true for the slot at position 0, which is the first item of the list: fast
     * scroller sections that start at [beforeIndex] stay on the slot instead of moving behind it,
     * and, if it is also the very first adapter item ([isFirstItem]), it gets no top spacing (the list padding already separates it from the header).
     */
    data class Insertion(val beforeIndex: Int, val slotIndex: Int, val atTop: Boolean = false) {
        /** The slot is the very first adapter item (nothing, e.g. an edu card, before it): it then gets no top spacing. */
        val isFirstItem: Boolean get() = atTop && beforeIndex == 0
    }

    /** Number of app rows for [iconCount] icon/folder items. */
    fun rowCount(iconCount: Int, appsPerRow: Int): Int = if (appsPerRow <= 0 || iconCount <= 0) 0 else (iconCount + appsPerRow - 1) / appsPerRow

    /** Positions as the provider returned them, sanitized: at least 0, distinct, ascending. */
    fun sanitize(rowPositions: List<Int>): List<Int> = rowPositions.filter { it >= 0 }.distinct().sorted()

    /**
     * @param iconAdapterIndices adapter indices of the eligible icon/folder items, in order
     *        (excluding anything in the private space section)
     * @param appsPerRow icons per row
     * @param rowPositions rows after which a slot goes (see the provider's inlineRowPositions);
     *        0 means before the first row
     * @return insertions in ascending order. A slot goes at a row boundary, before the first icon
     *         of the row following the requested row; positions at or past the end are skipped.
     *         [Insertion.slotIndex] is the index in the sanitized position list (stable keys).
     */
    fun plan(iconAdapterIndices: IntArray, appsPerRow: Int, rowPositions: List<Int>): List<Insertion> {
        if (appsPerRow <= 0) return emptyList()
        val result = ArrayList<Insertion>()
        sanitize(rowPositions).forEachIndexed { slotIndex, rows ->
            val iconIndex = rows.toLong() * appsPerRow
            if (iconIndex < iconAdapterIndices.size) {
                result.add(Insertion(iconAdapterIndices[iconIndex.toInt()], slotIndex, atTop = rows == 0))
            }
        }
        return result
    }

    /** New position of an item/section that was at [position] after inserting before each of [insertionIndices]. */
    fun shiftedPosition(position: Int, insertionIndices: List<Int>): Int = position + insertionIndices.count { it <= position }

    /**
     * [shiftedPosition] for fast scroller sections, given the planned [insertions]: a section that
     * starts where the top slot (position 0) goes keeps pointing at the slot, so jumping to the
     * first section shows the slot too.
     */
    fun shiftedSectionPosition(position: Int, insertions: List<Insertion>): Int = position + insertions.count { it.beforeIndex < position || (it.beforeIndex == position && !it.atTop) }
}
