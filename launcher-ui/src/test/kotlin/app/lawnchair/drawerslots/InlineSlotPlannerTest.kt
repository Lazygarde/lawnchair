package app.lawnchair.drawerslots

import app.lawnchair.drawerslots.InlineSlotPlanner.Insertion
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineSlotPlannerTest {

    // Adapter: 10 icons at indices 0..9 (4 per row -> rows of 4, 4, 2).
    private val icons = IntArray(10) { it }

    @Test
    fun rowCountRoundsUp() {
        assertEquals(3, InlineSlotPlanner.rowCount(10, 4))
        assertEquals(0, InlineSlotPlanner.rowCount(0, 4))
        assertEquals(0, InlineSlotPlanner.rowCount(5, 0))
    }

    @Test
    fun insertsAtRowBoundaries() {
        val result = InlineSlotPlanner.plan(icons, 4, listOf(1, 2))
        assertEquals(listOf(Insertion(4, 0), Insertion(8, 1)), result)
    }

    @Test
    fun skipsPositionsPastTheEndAndInvalidOnes() {
        // 3 rows exist; after row 3 there is nothing left to insert before. 0 and negatives are dropped.
        val result = InlineSlotPlanner.plan(icons, 4, listOf(0, -2, 3, 5, 2, 2))
        assertEquals(listOf(Insertion(8, 0)), result)
    }

    @Test
    fun usesAdapterIndicesNotIconOrdinals() {
        // A divider or edu card occupies adapter index 0, icons start at 1.
        val shifted = IntArray(6) { it + 1 }
        val result = InlineSlotPlanner.plan(shifted, 3, listOf(1))
        assertEquals(listOf(Insertion(4, 0)), result)
    }

    @Test
    fun listShorterThanPositionInsertsNothing() {
        assertEquals(emptyList<Insertion>(), InlineSlotPlanner.plan(IntArray(3) { it }, 4, listOf(1)))
        assertEquals(emptyList<Insertion>(), InlineSlotPlanner.plan(IntArray(0), 4, listOf(1)))
        assertEquals(emptyList<Insertion>(), InlineSlotPlanner.plan(icons, 0, listOf(1)))
    }

    @Test
    fun sectionsShiftByInsertionsAtOrBeforeThem() {
        val insertions = listOf(4, 8)
        assertEquals(0, InlineSlotPlanner.shiftedPosition(0, insertions))
        assertEquals(3, InlineSlotPlanner.shiftedPosition(3, insertions))
        // A section starting exactly at an insertion point moves behind the slot.
        assertEquals(5, InlineSlotPlanner.shiftedPosition(4, insertions))
        assertEquals(7, InlineSlotPlanner.shiftedPosition(6, insertions))
        assertEquals(10, InlineSlotPlanner.shiftedPosition(8, insertions))
        // Bottom item (after all icons).
        assertEquals(12, InlineSlotPlanner.shiftedPosition(10, insertions))
    }

    @Test
    fun rowIndexStaysAlignedAfterSlotLikeADivider() {
        // Simulates the rowIndex loop: a slot resets numAppsInSection like a divider.
        val perRow = 4
        val types = MutableList(10) { "icon" }
        InlineSlotPlanner.plan(icons, perRow, listOf(1)).reversed().forEach { types.add(it.beforeIndex, "slot") }
        var inSection = 0
        var row = -1
        val rows = types.map { type ->
            if (type == "slot") {
                inSection = 0
                -1
            } else {
                if (inSection % perRow == 0) row++
                inSection++
                row
            }
        }
        assertEquals(listOf(0, 0, 0, 0, -1, 1, 1, 1, 1, 2, 2), rows)
    }
}
