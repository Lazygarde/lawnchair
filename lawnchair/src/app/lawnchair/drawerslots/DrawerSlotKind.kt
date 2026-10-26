package app.lawnchair.drawerslots

/**
 * What a slot's view is. The drawer applies different policies per kind; the provider answers it
 * per slot through [LawnchairDrawerSlotProvider.kindOf]. The kind is read when the view is
 * created, so it can change after [LawnchairDrawerSlotRegistry.notifySlotsChanged] recreates it.
 */
enum class DrawerSlotKind {
    /**
     * Advertising. Keyboard/D-pad focus never enters it, it is hidden on the work tab, and (inline
     * and search slots) it is not counted for accessibility, never becomes the IME-focused child
     * and is never the search quick-launch item, so Enter cannot open an ad by accident.
     */
    AD,

    /**
     * Regular host UI (promo card, shortcut, widget). It takes normal focus and accessibility.
     * Work-tab visibility is decided by [LawnchairDrawerSlotProvider.showOnWorkTab]. For inline
     * and search slots it participates in focus and accessibility counting like any item, but is
     * still never the search quick-launch item.
     */
    CONTENT,
}
