package app.lawnchair.drawerslots

import app.lawnchair.drawerslots.SearchResultKind.APP
import app.lawnchair.drawerslots.SearchResultKind.DIVIDER
import app.lawnchair.drawerslots.SearchResultKind.HEADER
import app.lawnchair.drawerslots.SearchResultKind.OTHER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSlotPlannerTest {

    @Test
    fun appsThenSpacerHeaderThenWebShape() {
        // The real app-search shape: app icons, TEXT_HEADER spacer, market/web action, no dividers.
        val kinds = listOf(APP, APP, APP, HEADER, OTHER)
        assertEquals(listOf(3), SearchSlotPlanner.insertionIndices(kinds, 1))
    }

    @Test
    fun appsWebSuggestionsAndMoreHeaders() {
        // apps | "From the web" header + suggestions | "More" header + actions
        val kinds = listOf(APP, APP, HEADER, OTHER, OTHER, HEADER, OTHER, OTHER)
        assertEquals(listOf(2, 5), SearchSlotPlanner.insertionIndices(kinds, 2))
    }

    @Test
    fun slotsNeverSitBetweenAHeaderAndItsItems() {
        val kinds = listOf(APP, HEADER, OTHER, HEADER, OTHER)
        val result = SearchSlotPlanner.insertionIndices(kinds, 3)
        // Before headers (1, 3), then the end.
        assertEquals(listOf(1, 3, 5), result)
    }

    @Test
    fun noAppsGoesAfterTheFirstGroup() {
        // header + items, header + items: first group ends before the second header
        val kinds = listOf(HEADER, OTHER, OTHER, HEADER, OTHER)
        assertEquals(listOf(3), SearchSlotPlanner.insertionIndices(kinds, 1))
        // no boundary at all: end
        assertEquals(listOf(2), SearchSlotPlanner.insertionIndices(listOf(OTHER, OTHER), 1))
    }

    @Test
    fun dividerShapes() {
        // apps | divider | web | divider | settings
        val kinds = listOf(APP, APP, DIVIDER, OTHER, DIVIDER, OTHER)
        assertEquals(listOf(3, 5), SearchSlotPlanner.insertionIndices(kinds, 2))
        // no apps: after the first divider
        assertEquals(listOf(2), SearchSlotPlanner.insertionIndices(listOf(OTHER, DIVIDER, OTHER), 1))
    }

    @Test
    fun leadingHeaderBeforeAppsIsNotABoundary() {
        // "Apps" header then apps then the rest
        val kinds = listOf(HEADER, APP, APP, HEADER, OTHER)
        assertEquals(listOf(3), SearchSlotPlanner.insertionIndices(kinds, 1))
    }

    @Test
    fun emptyAndZeroCount() {
        assertEquals(listOf(0), SearchSlotPlanner.insertionIndices(emptyList(), 1))
        assertEquals(emptyList<Int?>(), SearchSlotPlanner.insertionIndices(listOf(APP), 0))
        assertEquals(emptyList<Int?>(), SearchSlotPlanner.insertionIndices(listOf(APP), -1))
    }

    @Test
    fun indicesAreNonDecreasing() {
        val result = SearchSlotPlanner.insertionIndices(listOf(APP, HEADER, OTHER, DIVIDER, OTHER), 6)
        assertEquals(result.filterNotNull().sorted(), result.filterNotNull())
    }

    @Test
    fun adjacentBoundariesDoNotStackSlots() {
        // apps | divider | "From the web" header + items: the divider and header boundaries coincide.
        val kinds = listOf(APP, APP, DIVIDER, HEADER, OTHER, OTHER)
        // slot 0 after the divider (3); slot 1 needs an organic item in between: goes to the end (6).
        assertEquals(listOf<Int?>(3, 6), SearchSlotPlanner.insertionIndices(kinds, 2))
    }

    @Test
    fun slotWithNoOrganicItemLeftIsDropped() {
        // slot 0 ends up at the very end, so there is nothing after it for slot 1.
        val kinds = listOf(APP, APP)
        assertEquals(listOf<Int?>(2, null), SearchSlotPlanner.insertionIndices(kinds, 2))
        assertEquals(listOf<Int?>(2, null, null), SearchSlotPlanner.insertionIndices(kinds, 3))
    }

    @Test
    fun everySlotHasAnOrganicItemBetweenItAndThePreviousOne() {
        val shapes = listOf(
            listOf(APP, APP, DIVIDER, HEADER, OTHER, OTHER),
            listOf(APP, HEADER, DIVIDER, HEADER, OTHER),
            listOf(HEADER, OTHER, DIVIDER, HEADER, OTHER, HEADER, OTHER),
            listOf(APP, DIVIDER, DIVIDER, OTHER),
        )
        for (kinds in shapes) {
            val result = SearchSlotPlanner.insertionIndices(kinds, 4).filterNotNull()
            result.zipWithNext().forEach { (a, b) -> assertTrue("gap in $kinds: $result", b > a) }
        }
    }

    @Test
    fun localSearchShapeForTwoAppsAndWebSuggestions() {
        // What AppsAndShortcuts + WebSuggestions + Actions section builders emit for "ca": two app
        // icons, a spacer (TEXT_HEADER), the "From the web" header with suggestions, a spacer,
        // then actions and a final spacer. The spacer must not count as an item between slots.
        val kinds = listOf(APP, APP, HEADER, HEADER, OTHER, OTHER, OTHER, HEADER, OTHER, OTHER, HEADER)
        assertEquals(listOf<Int?>(2, 7), SearchSlotPlanner.insertionIndices(kinds, 2))
    }

    @Test
    fun singleAppWithShortcutsShape() {
        // "yo": app icon, spacer, app row + 4 shortcuts, spacer, web header + suggestions...
        val kinds = listOf(APP, HEADER, APP, APP, APP, APP, APP, HEADER, HEADER, OTHER, OTHER)
        assertEquals(listOf<Int?>(1, 7), SearchSlotPlanner.insertionIndices(kinds, 2))
    }

    @Test
    fun dividersAndHeadersAloneNeverSeparateSlots() {
        val kinds = listOf(APP, APP, DIVIDER, DIVIDER, HEADER, OTHER, OTHER)
        // slot 0 after the first divider (3); the only visible items left are after the header.
        assertEquals(listOf<Int?>(3, 7), SearchSlotPlanner.insertionIndices(kinds, 2))
    }

    @Test
    fun trailingSpacerOnlyDropsTheSecondSlot() {
        val kinds = listOf(APP, APP, HEADER, HEADER)
        assertEquals(listOf<Int?>(2, null), SearchSlotPlanner.insertionIndices(kinds, 2))
    }
}
