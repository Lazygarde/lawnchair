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
}
