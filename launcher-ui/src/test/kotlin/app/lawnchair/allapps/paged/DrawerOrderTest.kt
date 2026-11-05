package app.lawnchair.allapps.paged

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerOrderTest {

    private fun e(key: String, time: Long, label: String = key) = DrawerOrder.Entry(key, time, label)

    @Test
    fun emptySaved_sortsByInstallTimeOldestFirst() =
        assertEquals(listOf("b", "a", "c"), DrawerOrder.merge(emptyList(), listOf(e("a", 20), e("b", 10), e("c", 30))))

    @Test
    fun tieOnInstallTime_breaksByLabel() =
        assertEquals(listOf("x", "y"), DrawerOrder.merge(emptyList(), listOf(e("y", 5, "Beta"), e("x", 5, "alpha"))))

    // "new" has the oldest install time yet goes last: it is not in the saved list.
    @Test
    fun savedOrderWins_newInstallAppendsAtEnd() =
        assertEquals(listOf("c", "a", "new"), DrawerOrder.merge(listOf("c", "a"), listOf(e("a", 1), e("c", 2), e("new", 0))))

    @Test
    fun unknownInstallTime_goesLast() =
        assertEquals(listOf("a", "p"), DrawerOrder.merge(emptyList(), listOf(e("p", 0), e("a", 10))))

    @Test
    fun uninstalledKeyIsDropped() =
        assertEquals(listOf("a"), DrawerOrder.merge(listOf("gone", "a"), listOf(e("a", 1))))

    @Test
    fun duplicateSavedKey_appearsOnce() =
        assertEquals(listOf("a", "b"), DrawerOrder.merge(listOf("a", "a", "b"), listOf(e("a", 1), e("b", 2))))

    @Test
    fun move_toFront() = assertEquals(listOf("c", "a", "b"), DrawerOrder.move(listOf("a", "b", "c"), "c", 0))

    @Test
    fun move_clampsPastEnd() = assertEquals(listOf("b", "c", "a"), DrawerOrder.move(listOf("a", "b", "c"), "a", 99))

    @Test
    fun move_unknownKeyIsNoOp() = assertEquals(listOf("a"), DrawerOrder.move(listOf("a"), "z", 0))

    // ---- Folders ----

    private fun f(id: Int, vararg keys: String) = DrawerOrder.FolderKeys(id, keys.toList())

    @Test
    fun folderAtSavedSlot() {
        val r = DrawerOrder.arrange(listOf("a", "folder:1", "d"), listOf(e("a", 1), e("b", 2), e("c", 3), e("d", 4)), listOf(f(1, "b", "c")))
        assertEquals(listOf("a", "folder:1", "d"), r.tokens)
        assertEquals(mapOf(1 to listOf("b", "c")), r.members)
    }

    @Test
    fun unsavedFolder_sitsAtEarliestMember() = assertEquals(
        listOf("a", "folder:1", "c"),
        DrawerOrder.arrange(emptyList(), listOf(e("a", 1), e("b", 2), e("c", 3), e("d", 4)), listOf(f(1, "d", "b"))).tokens,
    )

    @Test
    fun singleMemberFolder_showsAppAtFolderSlot() = assertEquals(
        listOf("c", "a"),
        DrawerOrder.arrange(listOf("folder:1", "a"), listOf(e("a", 1), e("c", 2)), listOf(f(1, "b", "c"))).tokens,
    )

    @Test
    fun emptyFolder_dropped() =
        assertEquals(listOf("a"), DrawerOrder.arrange(listOf("folder:1", "a"), listOf(e("a", 1)), listOf(f(1, "x", "y"))).tokens)

    @Test
    fun appInTwoFolders_firstWins() {
        val r = DrawerOrder.arrange(emptyList(), listOf(e("a", 1), e("b", 2), e("c", 3), e("d", 4)), listOf(f(1, "a", "b"), f(2, "b", "c", "d")))
        assertEquals(mapOf(1 to listOf("a", "b"), 2 to listOf("c", "d")), r.members)
        assertEquals(listOf("folder:1", "folder:2"), r.tokens)
    }

    @Test
    fun newInstall_afterFolders_appendsAtEnd() = assertEquals(
        listOf("folder:1", "new"),
        DrawerOrder.arrange(listOf("folder:1"), listOf(e("a", 1), e("b", 2), e("new", 0)), listOf(f(1, "a", "b"))).tokens,
    )

    @Test
    fun unknownFolderToken_dropped() =
        assertEquals(listOf("a"), DrawerOrder.arrange(listOf("folder:9", "a"), listOf(e("a", 1)), emptyList()).tokens)

    @Test
    fun dissolve_replacesToken() =
        assertEquals(listOf("a", "b", "d"), DrawerOrder.dissolve(listOf("a", "folder:1", "d"), 1, "b"))

    @Test
    fun dissolve_dropsSurvivorElsewhere() =
        assertEquals(listOf("a", "b"), DrawerOrder.dissolve(listOf("a", "folder:1", "b"), 1, "b"))

    @Test
    fun folderToken_roundTrip() {
        assertEquals("folder:7", DrawerOrder.folderToken(7))
        assertEquals(7, DrawerOrder.folderIdOf("folder:7"))
        assertEquals(null, DrawerOrder.folderIdOf("com.x/.A#0"))
    }
}
