package app.lawnchair.allapps.paged

/** Pagination of the paged drawer: row-major cells, `cols × rows` apps per page. */
object PagedGridMath {

    /** Full rows that fit; never a half row, never fewer than one. */
    fun rows(availableHeightPx: Int, cellHeightPx: Int): Int =
        if (cellHeightPx <= 0) 1 else maxOf(1, availableHeightPx / cellHeightPx)

    /** At least one page, so an empty drawer still has somewhere to drop. */
    fun pageCount(items: Int, perPage: Int): Int = maxOf(1, (items + perPage - 1) / perPage)

    fun pageOf(index: Int, perPage: Int): Int = index / perPage

    /** `(x, y)` of [index] within its page. */
    fun cellOf(index: Int, cols: Int, perPage: Int): Pair<Int, Int> {
        val inPage = index % perPage
        return (inPage % cols) to (inPage / cols)
    }
}
