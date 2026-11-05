package app.lawnchair.allapps.paged

import kotlin.math.hypot

/** Pagination of the paged drawer: row-major cells, `cols × rows` apps per page. */
object PagedGridMath {

    /** At most this many rows, as One UI: a taller screen spaces them out instead. */
    const val MAX_ROWS = 6

    /** Full rows that fit; never a half row, never fewer than one, never more than [MAX_ROWS]. */
    fun rows(availableHeightPx: Int, cellHeightPx: Int): Int =
        if (cellHeightPx <= 0) 1 else (availableHeightPx / cellHeightPx).coerceIn(1, MAX_ROWS)

    /** At least one page, so an empty drawer still has somewhere to drop. */
    fun pageCount(items: Int, perPage: Int): Int = maxOf(1, (items + perPage - 1) / perPage)

    fun pageOf(index: Int, perPage: Int): Int = index / perPage

    /** `(x, y)` of [index] within its page. */
    fun cellOf(index: Int, cols: Int, perPage: Int): Pair<Int, Int> {
        val inPage = index % perPage
        return (inPage % cols) to (inPage / cols)
    }

    /** Whether ([x], [y]) is within [radius] of an icon's centre ([cx], [cy]): a drop makes a folder. */
    fun isInCore(x: Float, y: Float, cx: Float, cy: Float, radius: Float): Boolean =
        hypot(x - cx, y - cy) <= radius
}
