package app.lawnchair.drawerslots

import android.content.Context

/**
 * Information about the drawer's look so the host can style its slot view to match.
 * All sizes are in pixels.
 */
data class DrawerSlotEnv(
    /**
     * Context to inflate layouts with. It wraps the launcher activity with the host's
     * `LawnchairDrawerSlotTheme` style overlay, so Material/AppCompat widgets inflate. It is not an
     * Activity; use the `context` passed to createView where an Activity is required (ad SDK loaders).
     */
    val themedContext: Context,
    val backgroundColor: Int,
    val textColor: Int,
    val isDarkMode: Boolean,
    /** Whether the drawer background is blurred (backgrounds should then be translucent). */
    val hasBlur: Boolean,
    /**
     * Width your view gets (return a MATCH_PARENT-width view). The library applies the side
     * insets and vertical spacing itself, so every slot lines up with the app grid (or the search
     * result cards for search slots); do not add your own outer margins for alignment.
     */
    val availableWidthPx: Int,
)
