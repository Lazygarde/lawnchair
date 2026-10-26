package app.lawnchair.drawerslots

/** What a search result item is, as far as slot placement is concerned. */
enum class SearchResultKind {
    /** An app or shortcut result. */
    APP,

    /** A text header / spacer that starts a group (slots go before it, never between it and its items). */
    HEADER,

    /** An explicit divider between groups (slots go after it). */
    DIVIDER,
    OTHER,
}

/** Pure logic for placing search slots between result groups; free of Android types for testing. */
object SearchSlotPlanner {

    /**
     * @param kinds for each organic result item, its [SearchResultKind]
     * @param count number of slots
     * @return for each slot k (0 until [count]) the index in the organic list to insert it before
     *         (non-decreasing), or null if the slot is dropped.
     *
     * Group boundaries are the position after every DIVIDER and before every HEADER that is not
     * the first item. Slot 0 goes right after the app results (the end of the first run of APP
     * items; after a divider that directly follows it). With no app results it goes at the first
     * boundary. Slot k goes at the next boundary that has at least one VISIBLE organic item (APP
     * or OTHER; never a divider or a header, which can be invisible spacers) between it and slot
     * k-1's position, else at the end; if not even the end has a visible item after the previous
     * slot, the slot is dropped rather than stacked on the previous one.
     */
    fun insertionIndices(kinds: List<SearchResultKind>, count: Int): List<Int?> {
        val size = kinds.size
        val boundaries = sortedSetOf<Int>()
        kinds.forEachIndexed { index, kind ->
            if (kind == SearchResultKind.DIVIDER) boundaries.add(index + 1)
            if (kind == SearchResultKind.HEADER && index > 0) boundaries.add(index)
        }
        val firstApp = kinds.indexOf(SearchResultKind.APP)
        val slot0 = if (firstApp >= 0) {
            var end = firstApp
            while (end < size && kinds[end] == SearchResultKind.APP) end++
            if (end < size && kinds[end] == SearchResultKind.DIVIDER) end + 1 else end
        } else {
            boundaries.firstOrNull() ?: size
        }.coerceIn(0, size)
        // visibleBefore[i] = number of visible items in [0, i)
        val visibleBefore = IntArray(size + 1)
        kinds.forEachIndexed { i, kind ->
            val visible = kind == SearchResultKind.APP || kind == SearchResultKind.OTHER
            visibleBefore[i + 1] = visibleBefore[i] + if (visible) 1 else 0
        }
        fun hasVisibleBetween(from: Int, to: Int) = visibleBefore[to] - visibleBefore[from] >= 1
        val result = ArrayList<Int?>()
        var previous = -1
        for (k in 0 until count.coerceAtLeast(0)) {
            val index: Int? = if (k == 0) {
                slot0
            } else {
                (boundaries.firstOrNull { it > previous && hasVisibleBetween(previous, it) } ?: size)
                    .takeIf { hasVisibleBetween(previous, it) }
            }
            result.add(index)
            if (index != null) previous = index
        }
        return result
    }
}
