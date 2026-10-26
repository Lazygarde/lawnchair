package app.lawnchair.drawerslots

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotHeightTableTest {

    @Test
    fun recordReportsChanges() {
        val table = SlotHeightTable()
        assertTrue(table.record("inline_0", 220))
        assertFalse(table.record("inline_0", 220))
        assertTrue(table.record("inline_0", 687))
    }

    @Test
    fun slotsKeepIndependentHeights() {
        val table = SlotHeightTable()
        table.record("inline_0", 220)
        table.record("inline_1", 687)
        assertEquals(220, table.estimate("inline_0", 0, 100))
        assertEquals(687, table.estimate("inline_1", 0, 100))
    }

    @Test
    fun estimateFallsBackToViewThenDefault() {
        val table = SlotHeightTable()
        assertEquals(300, table.estimate("inline_2", 300, 100))
        assertEquals(100, table.estimate("inline_2", 0, 100))
        table.record("inline_2", 150)
        assertEquals(150, table.estimate("inline_2", 300, 100))
    }
}
