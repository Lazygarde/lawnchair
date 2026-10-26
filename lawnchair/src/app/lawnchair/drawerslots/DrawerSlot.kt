package app.lawnchair.drawerslots

/**
 * A place in the app drawer where a host-provided view (an ad or regular content) can be shown.
 *
 * Every slot has a stable [key] which is used to cache the view (see [DrawerSlotViewCache]) and
 * to diff adapter items, so the same slot never recreates its view just because the list changed.
 */
sealed class DrawerSlot {

    /** The kind of slot. [isSlotEnabled][LawnchairDrawerSlotProvider.isSlotEnabled] is asked per type. */
    enum class Type { HEADER, FOOTER, INLINE, SEARCH }

    abstract val type: Type

    /** Stable identifier, unique per slot. */
    abstract val key: String

    /**
     * Key under which the last known height of this slot is remembered. Search slots ignore the
     * query (their size does not depend on it), so a new query reuses the previous height.
     */
    val heightKey: String
        get() = when (this) {
            is Search -> "search_$index"
            else -> key
        }

    /** Fixed slot below the drawer search bar (scrolls and collapses with the header). */
    data object Header : DrawerSlot() {
        override val type get() = Type.HEADER
        override val key get() = "header"
    }

    /** Fixed slot pinned to the bottom of the drawer. */
    data object Footer : DrawerSlot() {
        override val type get() = Type.FOOTER
        override val key get() = "footer"
    }

    /**
     * Full-span row between app rows. [index] is the position in the provider's
     * [inlineRowPositions][LawnchairDrawerSlotProvider.inlineRowPositions] list. [inWorkTab] is true
     * for slots in the work tab's list, which have their own view (a view has one parent).
     */
    data class Inline(val index: Int, val inWorkTab: Boolean = false) : DrawerSlot() {
        override val type get() = Type.INLINE
        override val key get() = if (inWorkTab) "inline_work_$index" else "inline_$index"
    }

    /** Item in search results for [query]. */
    data class Search(val query: String, val index: Int) : DrawerSlot() {
        override val type get() = Type.SEARCH
        override val key get() = "search_${index}_$query"
    }
}
