package app.lawnchair.drawerslots

import org.junit.Assert.assertEquals
import org.junit.Test

class SlotFocusMathTest {

    @Test
    fun mapsXToColumn() {
        // 4 columns of 100px
        assertEquals(0, SlotFocusMath.columnForX(10, 400, 4, false))
        assertEquals(2, SlotFocusMath.columnForX(250, 400, 4, false))
        assertEquals(3, SlotFocusMath.columnForX(399, 400, 4, false))
    }

    @Test
    fun clampsOutOfRangeX() {
        assertEquals(0, SlotFocusMath.columnForX(-50, 400, 4, false))
        assertEquals(3, SlotFocusMath.columnForX(900, 400, 4, false))
    }

    @Test
    fun mirrorsInRtl() {
        assertEquals(3, SlotFocusMath.columnForX(10, 400, 4, true))
        assertEquals(1, SlotFocusMath.columnForX(250, 400, 4, true))
    }

    @Test
    fun unknownWhenNoSize() {
        assertEquals(-1, SlotFocusMath.columnForX(10, 0, 4, false))
        assertEquals(-1, SlotFocusMath.columnForX(10, 400, 0, false))
    }
}

class SlotFocusTargetTest {

    // Adapter: [icon r0c0, icon r0c1, icon r0c2, SLOT, icon r1c0, icon r1c1, icon r1c2, icon r1c3(short?)]
    private val isIcon = booleanArrayOf(true, true, true, false, true, true, true, true)
    private val rowIndex = intArrayOf(0, 0, 0, 0, 1, 1, 1, 1)
    private val rowAppIndex = intArrayOf(0, 1, 2, 0, 0, 1, 2, 3)

    @Test
    fun downKeepsTheColumn() {
        assertEquals(6, SlotFocusMath.targetPosition(isIcon, rowIndex, rowAppIndex, 3, true, 2))
        assertEquals(4, SlotFocusMath.targetPosition(isIcon, rowIndex, rowAppIndex, 3, true, 0))
    }

    @Test
    fun upKeepsTheColumn() {
        assertEquals(2, SlotFocusMath.targetPosition(isIcon, rowIndex, rowAppIndex, 3, false, 2))
        assertEquals(0, SlotFocusMath.targetPosition(isIcon, rowIndex, rowAppIndex, 3, false, 0))
    }

    @Test
    fun shortRowUsesTheLastItemBeforeTheColumn() {
        // Row above has only columns 0..2; asking for column 3 gives column 2.
        assertEquals(2, SlotFocusMath.targetPosition(isIcon, rowIndex, rowAppIndex, 3, false, 3))
    }

    @Test
    fun noIconNextToTheSlotMeansDefaultBehaviour() {
        val icons = booleanArrayOf(false, false, true)
        assertEquals(-1, SlotFocusMath.targetPosition(icons, intArrayOf(0, 0, 1), intArrayOf(0, 0, 0), 0, true, 0))
        assertEquals(-1, SlotFocusMath.targetPosition(icons, intArrayOf(0, 0, 1), intArrayOf(0, 0, 0), 1, false, 0))
        assertEquals(-1, SlotFocusMath.targetPosition(isIcon, rowIndex, rowAppIndex, 3, true, -1))
    }

    @Test
    fun topSlotHasNothingAboveAndFindsFirstRowBelow() {
        // [SLOT, r0c0, r0c1, r0c2, r1c0]
        val icon = booleanArrayOf(false, true, true, true, true)
        val row = intArrayOf(0, 0, 0, 0, 1)
        val app = intArrayOf(0, 0, 1, 2, 0)
        assertEquals(-1, SlotFocusMath.targetPosition(icon, row, app, 0, false, 1))
        assertEquals(2, SlotFocusMath.targetPosition(icon, row, app, 0, true, 1))
    }
}
