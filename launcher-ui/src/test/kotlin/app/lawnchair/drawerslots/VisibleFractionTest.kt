package app.lawnchair.drawerslots

import app.lawnchair.drawerslots.DrawerSlotVisibilityTracker.Companion.visibleFraction
import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleFractionTest {

    @Test
    fun fullyVisibleWithoutCover() {
        assertEquals(1f, visibleFraction(100, 300, 200, Int.MAX_VALUE), 0.001f)
    }

    @Test
    fun coverCutsTheBottom() {
        // View [100, 300], footer starts at 200: half is hidden.
        assertEquals(0.5f, visibleFraction(100, 300, 200, 200), 0.001f)
    }

    @Test
    fun viewEntirelyUnderTheFooterIsNotVisible() {
        // The MREC case: visible rect [127, 2280] of a 687px ad that sits under a footer at 2047.
        assertEquals(0f, visibleFraction(2100, 2280, 687, 2047), 0.001f)
    }

    @Test
    fun clippedByScreenTooCountsOnlyTheVisiblePart() {
        assertEquals(0.25f, visibleFraction(0, 100, 400, Int.MAX_VALUE), 0.001f)
    }

    @Test
    fun zeroHeightIsNotVisible() {
        assertEquals(0f, visibleFraction(0, 0, 0, 100), 0.001f)
    }

    @Test
    fun imeTopFromInsets() {
        assertEquals(1700, DrawerSlotVisibilityTracker.imeTopOf(2280, 580))
        assertEquals(Int.MAX_VALUE, DrawerSlotVisibilityTracker.imeTopOf(2280, 0))
        assertEquals(Int.MAX_VALUE, DrawerSlotVisibilityTracker.imeTopOf(0, 580))
    }

    @Test
    fun coverIsTheHighestOfFooterAndKeyboard() {
        assertEquals(1700, DrawerSlotVisibilityTracker.combineCover(2047, 1700))
        assertEquals(2047, DrawerSlotVisibilityTracker.combineCover(2047, Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, DrawerSlotVisibilityTracker.combineCover(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun slotBehindADockedKeyboardIsNotVisible() {
        // A 687px slot at [1500, 2187] with the keyboard top at 1700: only 200px (29%) shows.
        val cover = DrawerSlotVisibilityTracker.combineCover(Int.MAX_VALUE, 1700)
        assertEquals(0.29f, visibleFraction(1500, 2187, 687, cover), 0.01f)
        // Floating keyboard: no inset, fully visible.
        val floating = DrawerSlotVisibilityTracker.combineCover(Int.MAX_VALUE, DrawerSlotVisibilityTracker.imeTopOf(2280, 0))
        assertEquals(1f, visibleFraction(1500, 2187, 687, floating), 0.001f)
    }
}
