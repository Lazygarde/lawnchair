package app.lawnchair.drawerslots

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Implemented by the host app to put its own views into the app drawer: ads or regular content
 * (promo card, shortcut, widget...). Register it with [LawnchairDrawerSlotRegistry], globally or
 * for a single slot type. Every method has a default; override only what you use.
 *
 * All callbacks run on the main thread. [createView] must return quickly; load anything slow
 * asynchronously and call [LawnchairDrawerSlotRegistry.notifySlotsChanged] when it is ready (or
 * return a view that fills itself in).
 */
interface LawnchairDrawerSlotProvider {

    /** Whether slots of this type should be shown by this provider. Default: nothing is shown. */
    fun isSlotEnabled(type: DrawerSlot.Type): Boolean = false

    /**
     * What [slot] is. The default is [DrawerSlotKind.AD] on purpose: the AD policies (no focus,
     * no accessibility counting, hidden on the work tab, never quick-launched) are the safe ones,
     * so a host that forgets to declare content never has an ad opened by the Enter key. Read
     * when the view is created; it can change after [LawnchairDrawerSlotRegistry.notifySlotsChanged].
     */
    fun kindOf(slot: DrawerSlot): DrawerSlotKind = DrawerSlotKind.AD

    /**
     * Whether a [DrawerSlotKind.CONTENT] slot is shown while the work tab is the current page.
     * Ads are never shown there. The footer is the exception: it is always hidden on the work
     * tab because the work profile's pause bar is anchored to the same bottom edge.
     */
    fun showOnWorkTab(slot: DrawerSlot): Boolean = false

    /**
     * How the header slot behaves while the app list scrolls; read from the provider serving
     * [DrawerSlot.Type.HEADER] when the header is (re)built, so a change takes effect after
     * [LawnchairDrawerSlotRegistry.notifySlotsChanged] or launcher recreation. Default:
     * [DrawerHeaderMode.QUICK_RETURN]. [DrawerHeaderMode.PINNED] only applies while the header has
     * content on the current page (it is hidden on the work tab unless [showOnWorkTab] allows it).
     */
    fun headerMode(): DrawerHeaderMode = DrawerHeaderMode.QUICK_RETURN

    /**
     * Opt-in for [DrawerSlot.Type.INLINE] and [DrawerSlot.Type.SEARCH] slots (ignored for header and
     * footer): when true the library reserves the slot but calls [createView] only the first time
     * the slot is at least 50% on screen while the drawer is open, and then attaches the view.
     * Use it when a view's own logic counts impressions on render (e.g. ad SDK banners), so the
     * count follows what the user can really see rather than what was laid out off screen or
     * clipped.
     *
     * Trade-offs: until then the slot is an empty placeholder whose height is the last known
     * height of that slot (else one app row), so when the real view is larger the content below it
     * moves down once; [onSlotVisible] is delivered after [createView]. The simpler host-side
     * alternative, with no layout estimate, is to return a lightweight container from [createView]
     * (fixed or wrap height) and only load/attach the real ad view in the first [onSlotVisible]
     * for that slot.
     */
    fun deferUntilVisible(slot: DrawerSlot): Boolean = false

    /**
     * Height in pixels the view of a deferred [slot] will have, or 0 if unknown. Used to size the
     * placeholder until the real height has been measured once (it is then remembered for the
     * process). Fixed-size ads (a 300x250 MREC is 250dp) should return their exact height: with
     * a wrong estimate the placeholder can look mostly visible while the real view would be mostly
     * covered, and the view is created too early. Without any estimate one app row is used.
     */
    fun estimatedHeightPx(slot: DrawerSlot, env: DrawerSlotEnv): Int = 0

    /**
     * Item positions (counted in app rows, before any slot is inserted) after which an inline
     * row is placed: 1 puts a slot after the first app row, 2 after the second, and so on; 0 puts
     * it at the very top of the scrolling list, before the first app row (below the floating
     * header; it scrolls with the list). Negative and duplicate values are ignored, and positions
     * at or past the end of the list insert nothing. [appRowCount] is the number of app rows in
     * the list. Used by inline slots. [DrawerSlot.Inline.index] is the index in this list after
     * sorting and removing duplicates.
     */
    fun inlineRowPositions(appRowCount: Int): List<Int> = emptyList()

    /**
     * Creates the view for [slot]. Called once per slot; the view is cached and re-parented, so
     * it must not be attached to [parent] (use it only to resolve layout params). Return null to
     * show nothing for this slot (it takes no space).
     *
     * [context] is the launcher Activity, whose theme is not AppCompat/Material: pass it to ad SDK
     * loaders that need an Activity, but do NOT inflate Material/AppCompat layouts with it (e.g.
     * MaterialButton throws "requires Theme.MaterialComponents"). Inflate with
     * `LayoutInflater.from(parent.context)` or `env.themedContext` instead; both carry the style
     * overlay `LawnchairDrawerSlotTheme`, which the host overrides in its own resources, e.g.
     * `<style name="LawnchairDrawerSlotTheme" parent="Theme.Material3.DayNight.NoActionBar" />`.
     *
     * Threading: this runs on the main thread. For [DrawerSlot.Type.INLINE] it is called
     * synchronously while the app list is being built (and again on every rebuild that finds no
     * cached view), so it must return fast: create the view and start loading asynchronously
     * instead of blocking, and call [LawnchairDrawerSlotRegistry.notifySlotsChanged] when ready.
     */
    fun createView(context: Context, parent: ViewGroup, slot: DrawerSlot, env: DrawerSlotEnv): View?

    /**
     * [slot]'s cached view was dropped (list rebuilt for a new query, [LawnchairDrawerSlotRegistry.notifySlotsChanged],
     * search closed, or launcher destroyed). Release the ad/view resources for it; a later
     * [createView] for the same slot creates a fresh view. Search slots are evicted when the query
     * changes, so their cache does not grow.
     */
    fun onSlotRemoved(slot: DrawerSlot) {}

    /**
     * How many items to show in search results for [query]. Used by search slots. Called off the
     * search hot path: only after the query was stable for ~300 ms, it is at least
     * `config_drawer_slot_search_min_query` characters long, and with a ~1.5 s timeout; organic
     * results are never delayed by it. Items go after the first group of results.
     */
    suspend fun searchItemCount(query: String): Int = 0

    /** The drawer is fully open. */
    fun onDrawerOpened() {}

    /** The drawer left the fully open state (closed, app launched, or launcher stopped). */
    fun onDrawerClosed() {}

    /**
     * [slot] is at least 50% on screen while the drawer is fully open. This is a visibility
     * signal, not a billable impression: it can fire repeatedly (scrolling, tab changes,
     * drawer reopening) and the host must apply its own ad SDK's impression rules. Delivered to
     * the provider that created the slot.
     */
    fun onSlotVisible(slot: DrawerSlot) {}

    /** [slot] stopped being visible (scrolled away, drawer closed, view removed). */
    fun onSlotHidden(slot: DrawerSlot) {}

    /**
     * The launcher instance is being destroyed (also on recreation); release resources.
     * The provider stays registered, so a new launcher instance may call [createView] again
     * afterwards. [onDrawerClosed] is always delivered before this if the drawer was open.
     */
    fun onDestroy() {}
}
