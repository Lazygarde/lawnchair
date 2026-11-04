package app.lawnchair.hotseat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dock has as many cells as icons, filling its width, and each icon sits in the middle of its
 * cell (One UI): one in the middle, two at a quarter in from each side, five at the edges.
 * Width 1000, no border unless a test says so.
 */
class DockCenteringTest {

    private fun centers(cols: Int, border: Int = 0) =
        (0 until cols).map { DockCentering.center(it, cols, 1000, border) }

    @Test
    fun one_inMiddle() = assertEquals(listOf(500f), centers(1))

    @Test
    fun two_aQuarterInFromEachSide() = assertEquals(listOf(250f, 750f), centers(2))

    @Test
    fun three_fartherOut() {
        val c = centers(3)
        assertEquals(1000f / 6, c[0], 0.01f)
        assertEquals(500f, c[1], 0.01f)
        assertEquals(5000f / 6, c[2], 0.01f)
    }

    @Test
    fun five_atTheEdges() = assertEquals(listOf(100f, 300f, 500f, 700f, 900f), centers(5))

    // Matches CellLayout's cells: cell width (width - borders) / cols, a border between cells.
    @Test
    fun border_betweenCells() = assertEquals(listOf(245f, 755f), centers(2, border = 20))
}
