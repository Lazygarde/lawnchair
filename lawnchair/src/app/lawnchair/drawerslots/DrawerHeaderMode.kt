package app.lawnchair.drawerslots

/** How the header slot behaves when the app list scrolls. See [LawnchairDrawerSlotProvider.headerMode]. */
enum class DrawerHeaderMode {
    /** Scrolls away with the list and slides back on any scroll up (the drawer's usual header behaviour). */
    QUICK_RETURN,

    /**
     * Stays fully expanded right under the search bar. The list scrolls underneath and is clipped at
     * the header's bottom edge. Only applies while the header slot has content on the current page.
     */
    PINNED,
}
