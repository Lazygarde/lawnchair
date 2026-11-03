package app.lawnchair.hotseat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dock has as many cells as icons (one more while an app is dragged in), filling the width;
 * each icon is shifted inside its own cell so the row runs edge to edge at the full dock's spacing.
 * Width 1000, at most 5 columns: a full-dock slot is 200 wide, so edge centers are 100 and 900.
 */
class DockCenteringTest {

    private fun offset(left: Int, cols: Int) = DockCentering.offset(
        left = left, cellWidth = 1000 / cols, cols = cols, maxCols = 5, border = 0,
    )

    @Test
    fun fullDock_noShift() = assertEquals(listOf(0f, 0f, 0f, 0f, 0f), (0 until 5).map { offset(it * 200, 5) })

    // 3 cells of 333: centers 166.5 / 500 / 833.5 move to 100 / 500 / 900.
    @Test
    fun three_leftMiddleRight() {
        assertEquals(100f - 1000 / 3 / 2f, offset(0, 3), 1f)
        assertEquals(0f, offset(333, 3), 1f)
        assertEquals(900f - (666 + 1000 / 3 / 2f), offset(666, 3), 1f)
    }

    @Test
    fun two_atBothEdges() {
        assertEquals(100f - 250f, offset(0, 2), 0.01f)
        assertEquals(900f - 750f, offset(500, 2), 0.01f)
    }

    @Test
    fun one_inMiddle() = assertEquals(0f, offset(0, 1), 0.01f)

    // The shift never leaves the icon's own cell, so where it shows is where it drops.
    @Test
    fun staysInsideItsCell() {
        for (cols in 1..5) {
            val cell = 1000 / cols
            for (i in 0 until cols) {
                val center = i * cell + cell / 2f + offset(i * cell, cols)
                assert(center >= i * cell && center <= (i + 1) * cell) { "cols=$cols i=$i center=$center" }
            }
        }
    }

    // RTL mirrors lp.x, so the cell index comes from the position, not the cellX.
    @Test
    fun indexFromPosition() = assertEquals(offset(500, 2), DockCentering.offset(500, 500, 2, 5, 0), 0f)
}
