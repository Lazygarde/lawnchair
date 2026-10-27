package app.lawnchair.drawerslots

import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.android.systemui.plugins.AllAppsRow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BooleanSupplier
import java.util.function.IntSupplier
import kotlinx.coroutines.launch

/**
 * Per-launcher glue for drawer slots (ads or regular host content). Owns the view cache and visibility tracker, builds the
 * [DrawerSlotEnv], and refreshes header/footer slots when the registry reports changes.
 * Inline and search slots obtain their views through [obtainView] as well.
 *
 * Everything here is a no-op when `config_enable_drawer_slots` is false.
 */
class DrawerSlotController(private val launcher: LawnchairLauncher) {

    internal val context: Context get() = launcher

    /**
     * The launcher with the host's `LawnchairDrawerSlotTheme` overlay (empty by default). Used for
     * inflating layouts; the plain launcher activity is still what providers receive as `context`.
     */
    val themedContext: Context by lazy { ContextThemeWrapper(launcher, R.style.LawnchairDrawerSlotTheme) }

    // Never attached anywhere; only gives providers a themed parent for LayoutInflater and LayoutParams.
    private val slotParent by lazy { FrameLayout(themedContext) }

    val cache = DrawerSlotViewCache().also { cache ->
        cache.onRemoved = { slot, provider ->
            tracker.untrack(slot)
            provider.onSlotRemoved(slot)
        }
    }

    // Header/footer changes that were scheduled but not applied yet; deferred slots wait for 0.
    private var headerChangesInFlight = 0

    /** False while a header or footer height change is pending, i.e. the list may still shift. */
    internal fun isLayoutSettled(): Boolean = headerChangesInFlight == 0 && pendingHeaderChange == null && footer?.isChangePending != true

    internal fun headerChangeScheduled() {
        headerChangesInFlight++
    }

    internal fun headerChangeDone() {
        if (headerChangesInFlight > 0) headerChangesInFlight--
    }

    // Bottom inset of the docked keyboard (0 when hidden or floating), set by the drawer.
    @Volatile
    private var imeInsetBottom = 0

    /** Called on window inset changes; slots behind a docked keyboard are not visible. */
    fun setImeInsetBottom(px: Int) {
        if (imeInsetBottom == px) return
        imeInsetBottom = px
        tracker.reevaluate()
    }

    /** True while search results (not the A-Z list) are on screen; set by the drawer. */
    @Volatile
    var searchActive = false
    val tracker = DrawerSlotVisibilityTracker(
        rootView = { launcher.window?.decorView },
        coverTop = { footer?.coverTop() ?: Int.MAX_VALUE },
        imeTop = {
            DrawerSlotVisibilityTracker.imeTopOf(launcher.window?.decorView?.height ?: 0, imeInsetBottom)
        },
        layoutSettled = { isLayoutSettled() },
        coverBottom = { headerCoverBottom?.asInt ?: Int.MIN_VALUE },
    )

    /** Set by the drawer; invoked (main thread) when a header slot view appears or disappears. */
    var headerPresenceListener: Runnable? = null

    // Lists that insert inline slots; re-run when INLINE slots change.
    private val inlineListeners = CopyOnWriteArrayList<Runnable>()

    fun addInlineListener(listener: Runnable) {
        inlineListeners.add(listener)
    }

    fun removeInlineListener(listener: Runnable) {
        inlineListeners.remove(listener)
    }

    private var headerRow: HostSlotHeaderRow? = null
    private var footer: HostSlotFooter? = null
    private var drawerOpen = false

    // Providers that were sent onDrawerOpened (and so must get onDrawerClosed), and every provider
    // this controller engaged (created views for or opened); the latter all get onDestroy.
    private val openedProviders = ArrayList<LawnchairDrawerSlotProvider>()
    private val engagedProviders = ArrayList<LawnchairDrawerSlotProvider>()

    // Drawer is "open" only while the launcher state is ALL_APPS *and* the activity is started
    // (launching an app from the drawer keeps the state ALL_APPS but the drawer is gone).
    private var stateIsAllApps = false
    private var hostStarted = true

    /**
     * Set by the drawer: whether the active list is scrolled away from the top. Header changes
     * that would reset (scroll to top) the list are deferred while the drawer is open and this
     * returns true, and applied when the drawer closes.
     */
    var isListScrolled: BooleanSupplier? = null
        private set

    fun setListScrolled(supplier: BooleanSupplier?) {
        isListScrolled = supplier
    }

    private var pendingHeaderChange: Runnable? = null

    /** Icons per row of the app grid. */
    internal val appsPerRow: Int get() = launcher.deviceProfile.numShownAllAppsColumns

    val isEnabled: Boolean get() = LawnchairDrawerSlotRegistry.isEnabled(launcher)

    init {
        launcher.lifecycleScope.launch {
            LawnchairDrawerSlotRegistry.changes.collect { change ->
                cache.invalidate(change.type)
                syncOpenProviders()
                if (change.type == null || change.type == DrawerSlot.Type.INLINE) {
                    inlineListeners.forEach { it.run() }
                }
                if (change.type == null || change.type == DrawerSlot.Type.HEADER) headerRow?.refresh()
                if (change.type == null || change.type == DrawerSlot.Type.FOOTER) footer?.refresh()
                // Inline/search consumers hook in here in later phases.
            }
        }
    }

    /** Rows for ActivityAllAppsContainerView#getAdditionalHeaderRows. */
    fun createHeaderRows(): List<AllAppsRow> {
        if (!isEnabled) return emptyList()
        headerRow?.destroy()
        return listOf(HostSlotHeaderRow(this).also { headerRow = it })
    }

    /** Header mode of the provider that created the header view; QUICK_RETURN without one. */
    internal fun headerMode(): DrawerHeaderMode = cache.providerOf(DrawerSlot.Header)?.headerMode() ?: DrawerHeaderMode.QUICK_RETURN

    /** True while the header slot has content on this page and its provider asked for [DrawerHeaderMode.PINNED]. */
    fun isHeaderPinned(): Boolean = headerRow?.isPinned == true

    /**
     * Set by the drawer: global bottom edge of the pinned header (list content above it is
     * clipped), or Int.MIN_VALUE when nothing is pinned.
     */
    var headerCoverBottom: IntSupplier? = null
        private set

    fun setHeaderCoverBottom(supplier: IntSupplier?) {
        headerCoverBottom = supplier
    }

    /** Whether a header slot view exists (and may show on the current page) (the header shell must then stay visible so it can be laid out). */
    fun hasHeaderSlot(): Boolean = headerRow?.hasContent == true

    /** Creates the footer controller for the drawer's footer frame; null when slots are disabled. */
    fun createFooter(frame: FrameLayout, onHeightChanged: Runnable): HostSlotFooter? {
        footer?.destroy()
        footer = null
        if (!isEnabled) return null
        return HostSlotFooter(this, frame, onHeightChanged).also { footer = it }
    }

    fun buildEnv(type: DrawerSlot.Type): DrawerSlotEnv {
        val dp = launcher.deviceProfile
        // Content width of the grid (what app icons span), the width every slot view gets: the
        // library applies the side insets itself (header outdent, footer padding, list padding).
        var width = dp.deviceProperties.availableWidthPx - 2 * dp.allAppsLeftRightMargin -
            dp.allAppsPadding.left - dp.allAppsPadding.right
        if (type == DrawerSlot.Type.SEARCH) {
            // Search results sit inside extra list padding, like the result cards.
            width -= 2 * launcher.resources.getDimensionPixelSize(R.dimen.search_result_recycler_view_padding)
        }
        return DrawerSlotEnv(
            themedContext = themedContext,
            backgroundColor = ColorTokens.ColorBackground.resolveColor(launcher),
            textColor = ColorTokens.TextColorPrimary.resolveColor(launcher),
            isDarkMode = Themes.getAttrBoolean(launcher, R.attr.isMainColorDark),
            hasBlur = launcher.isAllAppsBackgroundBlurEnabled,
            availableWidthPx = width.coerceAtLeast(0),
        )
    }

    /**
     * Vertical space the library puts above and below a slot view (each side). Inline and search
     * slots get a gap so they are never flush with neighbours; header and footer get none here
     * (see [footerTopSpacingPx]). Overridable via host resources.
     */
    internal fun verticalSpacingPx(type: DrawerSlot.Type): Int {
        val res = launcher.resources
        return when (type) {
            DrawerSlot.Type.INLINE -> res.getDimensionPixelSize(R.dimen.drawer_slot_vertical_spacing_inline)
            DrawerSlot.Type.SEARCH -> res.getDimensionPixelSize(R.dimen.drawer_slot_vertical_spacing_search)
            else -> 0
        }
    }

    /** Gap between the end of the list and the top of the footer slot's view. */
    internal fun footerTopSpacingPx(): Int = launcher.resources.getDimensionPixelSize(R.dimen.drawer_slot_footer_top_spacing)

    /** Side padding of the footer frame so its view lines up with the app grid edges. */
    internal fun footerPaddingLeftPx(): Int = launcher.deviceProfile.allAppsPadding.left

    internal fun footerPaddingRightPx(): Int = launcher.deviceProfile.allAppsPadding.right

    /** FloatingHeaderView pads itself by allAppsPadding.left + right on both sides (see its setInsets). */
    private fun headerInsetPx(): Int {
        val dp = launcher.deviceProfile
        return dp.allAppsPadding.left + dp.allAppsPadding.right
    }

    /**
     * How far the header container must extend into the header's padding (as negative physical
     * left margin) so the view reaches the grid's left edge like the other slots.
     */
    internal fun headerOutdentLeftPx(): Int = (headerInsetPx() - launcher.deviceProfile.allAppsPadding.left).coerceAtLeast(0)

    /** Same as [headerOutdentLeftPx] for the physical right side. */
    internal fun headerOutdentRightPx(): Int = (headerInsetPx() - launcher.deviceProfile.allAppsPadding.right).coerceAtLeast(0)

    /**
     * Returns the cached view for [slot], asking the resolved provider to create it on first use.
     * Null when slots are disabled, the provider does not want this slot type, or it returned null.
     * The provider gets a themed, detached parent instead of the container.
     * The returned view may still be attached to a previous parent; callers must [DrawerSlotViewCache.detach] it.
     */
    fun obtainView(slot: DrawerSlot): View? {
        val provider = LawnchairDrawerSlotRegistry.providerFor(launcher, slot.type) ?: return null
        engage(provider)
        return cache.getOrCreate(slot, provider) {
            val create = { provider.createView(launcher, slotParent, slot, buildEnv(slot.type)) }
            val deferrable = slot.type == DrawerSlot.Type.INLINE || slot.type == DrawerSlot.Type.SEARCH
            if (deferrable && provider.deferUntilVisible(slot)) {
                // Reserve the space now, create/attach the real view on first >=50% visibility.
                val fallback = launcher.deviceProfile.getAllAppsProfile().cellHeightPx
                val known = LawnchairDrawerSlotRegistry.knownHeights.known(slot.heightKey)
                val estimate = known
                    ?: provider.estimatedHeightPx(slot, buildEnv(slot.type)).takeIf { it > 0 }
                    ?: fallback
                DeferredSlotView(themedContext, estimate, create) {
                    LawnchairDrawerSlotRegistry.knownHeights.record(slot.heightKey, it)
                }
            } else {
                create()
            }
        }
    }

    /**
     * Drops search slot views whose query is not [keepQuery] (all search slots when null), so the
     * cache does not grow with every query; providers are told via onSlotRemoved.
     */
    fun evictSearchSlots(keepQuery: String?) {
        cache.evict { it is DrawerSlot.Search && it.query != keepQuery }
    }

    /** Kind of [slot] as read when its view was created. */
    fun kindOf(slot: DrawerSlot): DrawerSlotKind = cache.kindOf(slot)

    /** Starts visibility tracking for [slot]'s cached view with the provider that created it. */
    internal fun track(slot: DrawerSlot, view: View) {
        cache.providerOf(slot)?.let { tracker.track(slot, view, it) }
    }

    /**
     * Whether the slot's view may show while the work tab is current: only CONTENT slots whose
     * provider says so; ads never.
     */
    internal fun isAllowedOnWorkTab(slot: DrawerSlot): Boolean {
        if (cache.kindOf(slot) != DrawerSlotKind.CONTENT) return false
        return cache.providerOf(slot)?.showOnWorkTab(slot) == true
    }

    /**
     * Called when the launcher state settles. Only a committed state counts, so an aborted
     * pull-down (which ends back in ALL_APPS) does not cause closed/opened cycles.
     */
    fun onStateSetEnd(state: LauncherState) {
        stateIsAllApps = state == LauncherState.ALL_APPS
        updateDrawerOpen()
        // The list is going away for real (not just an app launch): safe to apply deferred changes.
        if (!stateIsAllApps) {
            pendingHeaderChange?.let {
                pendingHeaderChange = null
                it.run()
            }
        }
    }

    /** Activity stopped (e.g. an app was launched from the drawer): the drawer counts as closed. */
    fun onHostStopped() {
        hostStarted = false
        updateDrawerOpen()
    }

    /**
     * Activity started again. The launcher state can still be the stale ALL_APPS here (a HOME
     * press moves it to NORMAL slightly later), so the state is re-read on the next frame
     * instead of immediately. A committed [onStateSetEnd] in between wins. Returning via BACK to
     * an open drawer has no state change, so the re-read is what reopens it.
     */
    fun onHostStarted(currentState: () -> LauncherState) {
        // A fresh instance starts as started and may already have seen onStateSetEnd(ALL_APPS);
        // only a real stop/start needs the stale-state handling below.
        if (hostStarted) return
        hostStarted = true
        stateIsAllApps = false
        updateDrawerOpen()
        launcher.window?.decorView?.postOnAnimation {
            if (hostStarted && !stateIsAllApps) {
                stateIsAllApps = currentState() == LauncherState.ALL_APPS
                updateDrawerOpen()
            }
        }
    }

    private fun updateDrawerOpen() = setDrawerOpen(stateIsAllApps && hostStarted)

    /**
     * Runs a header height/presence change now, or defers it until the drawer closes when
     * applying it would yank a scrolled list back to the top (the drawer's header rebuild
     * resets scroll). Deferring was chosen over a no-reset rebuild because the header's
     * collapse/translation state is tied to the scroll offset, and recomputing it mid-scroll
     * would visibly jump the header or the list anyway; the view simply appears next open.
     */
    fun runHeaderChange(change: Runnable) {
        val applyAndRun = Runnable {
            headerRow?.applyHeight()
            change.run()
        }
        if (drawerOpen && isListScrolled?.getAsBoolean() == true) {
            pendingHeaderChange = applyAndRun
        } else {
            pendingHeaderChange = null
            applyAndRun.run()
        }
    }

    /**
     * Tells the header row whether the work tab is current. Returns true if its effective
     * visibility changed (the caller must then rebuild the header).
     */
    fun setHeaderOnWorkTab(onWorkTab: Boolean): Boolean = headerRow?.setOnWorkTab(onWorkTab) ?: false

    private fun setDrawerOpen(open: Boolean) {
        if (!isEnabled || drawerOpen == open) return
        drawerOpen = open
        tracker.setDrawerOpen(open)
        if (open) {
            LawnchairDrawerSlotRegistry.distinctProviders().forEach(::openProvider)
        } else {
            openedProviders.toList().forEach(::closeProvider)
        }
    }

    /** While the drawer is open, close providers that were removed and open ones that were added. */
    private fun syncOpenProviders() {
        if (!isEnabled || !drawerOpen) return
        val current = LawnchairDrawerSlotRegistry.distinctProviders()
        openedProviders.toList().filter { p -> current.none { it === p } }.forEach(::closeProvider)
        current.forEach(::openProvider)
    }

    private fun engage(provider: LawnchairDrawerSlotProvider) {
        if (engagedProviders.none { it === provider }) engagedProviders.add(provider)
    }

    private fun openProvider(provider: LawnchairDrawerSlotProvider) {
        if (openedProviders.any { it === provider }) return
        engage(provider)
        openedProviders.add(provider)
        provider.onDrawerOpened()
    }

    private fun closeProvider(provider: LawnchairDrawerSlotProvider) {
        if (openedProviders.removeAll { it === provider }) provider.onDrawerClosed()
    }

    fun onDestroy() {
        pendingHeaderChange = null
        // Hosts must see onDrawerClosed before onDestroy.
        stateIsAllApps = false
        updateDrawerOpen()
        isListScrolled = null
        tracker.clear()
        headerRow?.destroy()
        footer?.destroy()
        headerRow = null
        footer = null
        headerPresenceListener = null
        headerCoverBottom = null
        cache.clear()
        engagedProviders.toList().forEach { it.onDestroy() }
        engagedProviders.clear()
    }

    companion object {
        /** Finds the controller for any context that wraps a [LawnchairLauncher]; null otherwise. */
        @JvmStatic
        fun get(context: Context): DrawerSlotController? {
            var c: Context? = context
            while (c is ContextWrapper) {
                if (c is LawnchairLauncher) return c.drawerSlots
                c = c.baseContext
            }
            return null
        }
    }
}
