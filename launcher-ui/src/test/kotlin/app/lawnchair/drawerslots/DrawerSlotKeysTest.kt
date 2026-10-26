package app.lawnchair.drawerslots

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DrawerSlotKeysTest {

    @Test
    fun inlineKeysAreSeparatePerList() {
        assertNotEquals(DrawerSlot.Inline(0).key, DrawerSlot.Inline(0, inWorkTab = true).key)
    }

    @Test
    fun searchKeysDependOnQueryButHeightKeysDoNot() {
        val a = DrawerSlot.Search("ab", 0)
        val b = DrawerSlot.Search("abc", 0)
        assertNotEquals(a.key, b.key)
        assertEquals(a.heightKey, b.heightKey)
        assertNotEquals(a.heightKey, DrawerSlot.Search("ab", 1).heightKey)
    }

    @Test
    fun nonSearchHeightKeyIsTheKey() {
        assertEquals(DrawerSlot.Inline(2).key, DrawerSlot.Inline(2).heightKey)
        assertEquals("header", DrawerSlot.Header.heightKey)
    }
}
