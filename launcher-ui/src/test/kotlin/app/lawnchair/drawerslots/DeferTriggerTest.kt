package app.lawnchair.drawerslots

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferTriggerTest {

    private fun run(trigger: DeferTrigger, frames: List<Pair<Float, Boolean>>) = frames.map { (fraction, settled) -> trigger.onFrame(fraction, settled) }

    @Test
    fun firesOnTheSecondConsecutiveQualifyingFrame() {
        val t = DeferTrigger()
        assertEquals(listOf(false, true, false), run(t, listOf(0.8f to true, 0.8f to true, 0.8f to true)))
        assertTrue(t.fired)
    }

    @Test
    fun unsettledFramesNeverCount() {
        val t = DeferTrigger()
        assertEquals(listOf(false, false, false), run(t, listOf(1f to false, 1f to false, 1f to false)))
        assertFalse(t.fired)
    }

    @Test
    fun aLayoutShiftBreaksTheStreak() {
        // Header height applied between frames: fraction drops, streak restarts.
        val t = DeferTrigger()
        assertEquals(
            listOf(false, false, false, true),
            run(t, listOf(0.75f to true, 0.25f to true, 0.6f to true, 0.6f to true)),
        )
    }

    @Test
    fun belowThresholdDoesNotFire() {
        val t = DeferTrigger()
        assertEquals(listOf(false, false, false), run(t, listOf(0.49f to true, 0.49f to true, 0.49f to true)))
    }

    @Test
    fun pendingWhileStreakRunsAndResetClearsIt() {
        val t = DeferTrigger()
        assertFalse(t.isPending)
        t.onFrame(0.9f, true)
        assertTrue(t.isPending)
        t.reset()
        assertFalse(t.isPending)
        assertFalse(t.onFrame(0.9f, true))
    }

    @Test
    fun placeholderUsesItsFullReservedHeightWithFooterCover() {
        // The MREC case: 687px reserved, only 248px above the footer -> 36%, must not fire.
        val fraction = DrawerSlotVisibilityTracker.visibleFraction(top = 127, bottom = 814, viewHeight = 687, coverTop = 375)
        assertTrue(fraction < 0.5f)
        // With the old one-row placeholder (330px) the same position looked 75% visible.
        val wrong = DrawerSlotVisibilityTracker.visibleFraction(top = 127, bottom = 457, viewHeight = 330, coverTop = 375)
        assertTrue(wrong >= 0.5f)
    }
}
