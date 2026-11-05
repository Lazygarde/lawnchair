package app.lawnchair.allapps.paged

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedGridMathTest {

    @Test
    fun rows_dropsHalfRow() = assertEquals(5, PagedGridMath.rows(availableHeightPx = 1150, cellHeightPx = 200))

    @Test
    fun rows_atLeastOne() = assertEquals(1, PagedGridMath.rows(50, 200))

    @Test
    fun rows_zeroCellHeightIsOne() = assertEquals(1, PagedGridMath.rows(1000, 0))

    // A tall screen (Galaxy Z Fold7 cover) fits seven; One UI stops at six.
    @Test
    fun rows_atMostSix() = assertEquals(6, PagedGridMath.rows(1450, 200))

    @Test
    fun pageCount_emptyIsOnePage() = assertEquals(1, PagedGridMath.pageCount(0, 20))

    @Test
    fun pageCount_exactFit() = assertEquals(1, PagedGridMath.pageCount(20, 20))

    @Test
    fun pageCount_overflow() = assertEquals(2, PagedGridMath.pageCount(21, 20))

    @Test
    fun pageOf_firstOfSecondPage() = assertEquals(1, PagedGridMath.pageOf(20, 20))

    @Test
    fun cellOf_rowMajorOnSecondPage() = assertEquals(1 to 2, PagedGridMath.cellOf(index = 20 + 9, cols = 4, perPage = 20))

    // ---- Folder creation: the pointer over the middle of another icon ----

    @Test
    fun core_inside() = assertTrue(PagedGridMath.isInCore(105f, 100f, 100f, 100f, 10f))

    @Test
    fun core_edgeCounts() = assertTrue(PagedGridMath.isInCore(110f, 100f, 100f, 100f, 10f))

    @Test
    fun core_outsideDiagonal() = assertFalse(PagedGridMath.isInCore(108f, 108f, 100f, 100f, 10f))
}
