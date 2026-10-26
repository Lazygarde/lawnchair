package app.lawnchair.drawerslots

/**
 * Pure logic for placing inline slots in the app grid, kept free of Android types so it can be
 * unit tested.
 */
object InlineSlotPlanner {

    /** Insert slot number [slotIndex] before the adapter item at [beforeIndex] (original indices). */
    data class Insertion(val beforeIndex: Int, val slotIndex: Int)

    /** Number of app rows for [iconCount] icon/folder items. */
    fun rowCount(iconCount: Int, appsPerRow: Int): Int = if (appsPerRow <= 0 || iconCount <= 0) 0 else (iconCount + appsPerRow - 1) / appsPerRow

    /** Positions as the provider returned them, sanitized: at least 1, distinct, ascending. */
    fun sanitize(rowPositions: List<Int>): List<Int> = rowPositions.filter { it >= 1 }.distinct().sorted()

    /**
     * @param iconAdapterIndices adapter indices of the eligible icon/folder items, in order
     *        (excluding anything in the private space section)
     * @param appsPerRow icons per row
     * @param rowPositions rows after which a slot goes (see the provider's inlineRowPositions)
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
                result.add(Insertion(iconAdapterIndices[iconIndex.toInt()], slotIndex))
            }
        }
        return result
    }

    /** New position of an item/section that was at [position] after inserting before each of [insertionIndices]. */
    fun shiftedPosition(position: Int, insertionIndices: List<Int>): Int = position + insertionIndices.count { it <= position }
}
