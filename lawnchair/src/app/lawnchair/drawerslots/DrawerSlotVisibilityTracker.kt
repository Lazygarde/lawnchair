package app.lawnchair.drawerslots

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver

/**
 * Reports [LawnchairDrawerSlotProvider.onSlotVisible] / [LawnchairDrawerSlotProvider.onSlotHidden]
 * only while the drawer is fully open and the slot's view is at least [MIN_VISIBLE_FRACTION]
 * on screen. Visibility is re-evaluated before every draw, which covers scrolling, tab changes,
 * header collapse and search, without needing per-source listeners.
 *
 * Main thread only.
 */
class DrawerSlotVisibilityTracker(
    private val rootView: () -> View?,
    /** Global top edge of something drawn over the lists (the footer), or Int.MAX_VALUE. */
    private val coverTop: () -> Int = { Int.MAX_VALUE },
    /** Global top edge of the docked keyboard, or Int.MAX_VALUE (none, or a floating keyboard). */
    private val imeTop: () -> Int = { Int.MAX_VALUE },
    /** False while a header/footer change is being applied; deferred slots wait for it. */
    private val layoutSettled: () -> Boolean = { true },
    /** Global bottom edge of a pinned header drawn over the lists, or Int.MIN_VALUE. */
    private val coverBottom: () -> Int = { Int.MIN_VALUE },
) {

    private class Tracked(
        val slot: DrawerSlot,
        val view: View,
        val provider: LawnchairDrawerSlotProvider,
        var visible: Boolean = false,
        val trigger: DeferTrigger = DeferTrigger(),
    )

    private val tracked = LinkedHashMap<String, Tracked>()
    private val rect = Rect()
    private var drawerOpen = false
    private var observer: ViewTreeObserver? = null

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        evaluate()
        true
    }

    /** Tracks [slot]; callbacks go to [provider], the one that created it. */
    fun track(slot: DrawerSlot, view: View, provider: LawnchairDrawerSlotProvider) {
        val existing = tracked[slot.key]
        if (existing?.view === view) return
        existing?.let { untrack(it.slot) }
        tracked[slot.key] = Tracked(slot, view, provider)
        updateObserver()
    }

    fun untrack(slot: DrawerSlot) {
        val entry = tracked.remove(slot.key) ?: return
        if (entry.visible) entry.provider.onSlotHidden(entry.slot)
        updateObserver()
    }

    fun setDrawerOpen(open: Boolean) {
        if (drawerOpen == open) return
        drawerOpen = open
        if (!open) {
            hideAll()
            tracked.values.forEach { it.trigger.reset() }
        }
        updateObserver()
        if (open) rootView()?.postInvalidateOnAnimation()
    }

    /** Re-checks visibility on the next frame (e.g. after the keyboard moved). */
    fun reevaluate() {
        if (drawerOpen && tracked.isNotEmpty()) rootView()?.postInvalidateOnAnimation()
    }

    fun clear() {
        hideAll()
        tracked.clear()
        updateObserver()
    }

    private fun hideAll() {
        tracked.values.forEach {
            if (it.visible) {
                it.visible = false
                it.provider.onSlotHidden(it.slot)
            }
        }
    }

    private fun evaluate() {
        if (!drawerOpen) return
        var needAnotherPass = false
        // Copy: callbacks may track/untrack.
        tracked.values.toList().forEach { entry ->
            val deferred = (entry.view as? DeferredSlotView)?.takeIf { !it.materialized }
            val nowVisible: Boolean
            if (deferred != null) {
                // The placeholder reserves the slot's full height, so this is the real fraction
                // (footer cover applied). Create the view only on a settled layout, after the
                // condition held on consecutive passes.
                val fraction = visibleFraction(entry.view, entry.slot.type)
                if (entry.trigger.onFrame(fraction, layoutSettled())) deferred.materialize()
                needAnotherPass = needAnotherPass || entry.trigger.isPending
                nowVisible = false
            } else {
                nowVisible = isMostlyVisible(entry.view, entry.slot.type)
            }
            if (nowVisible != entry.visible) {
                entry.visible = nowVisible
                if (nowVisible) entry.provider.onSlotVisible(entry.slot) else entry.provider.onSlotHidden(entry.slot)
            }
        }
        // A trigger needs consecutive passes; make sure another one happens on an idle screen.
        if (needAnotherPass) rootView()?.postInvalidateOnAnimation()
    }

    private fun isMostlyVisible(view: View, type: DrawerSlot.Type): Boolean = visibleFraction(view, type) >= MIN_VISIBLE_FRACTION

    private fun visibleFraction(view: View, type: DrawerSlot.Type): Float {
        if (!view.isShown || view.width <= 0 || view.height <= 0 || view.alpha <= 0f) return 0f
        if (!view.getGlobalVisibleRect(rect)) return 0f
        val cover = combineCover(if (type != DrawerSlot.Type.FOOTER) coverTop() else Int.MAX_VALUE, imeTop())
        // The header slot itself is not covered by the header it lives in.
        val top = if (type == DrawerSlot.Type.HEADER || type == DrawerSlot.Type.FOOTER) Int.MIN_VALUE else coverBottom()
        return visibleFraction(rect.top, rect.bottom, view.height, cover, top)
    }

    private fun updateObserver() {
        val shouldObserve = drawerOpen && tracked.isNotEmpty()
        val current = observer
        if (shouldObserve && current == null) {
            rootView()?.viewTreeObserver?.takeIf { it.isAlive }?.let {
                it.addOnPreDrawListener(preDrawListener)
                observer = it
            }
        } else if (!shouldObserve && current != null) {
            if (current.isAlive) current.removeOnPreDrawListener(preDrawListener)
            observer = null
        }
    }

    companion object {
        private const val MIN_VISIBLE_FRACTION = 0.5f

        /** Effective cover edge: the highest of the footer top and the keyboard top. */
        fun combineCover(footerTop: Int, imeTop: Int): Int = minOf(footerTop, imeTop)

        /**
         * Top edge of a docked keyboard in window coordinates given the IME inset at the bottom
         * of a window [windowHeight] tall; Int.MAX_VALUE when there is no inset (hidden or floating).
         */
        fun imeTopOf(windowHeight: Int, imeInsetBottom: Int): Int = if (imeInsetBottom <= 0 || windowHeight <= 0) Int.MAX_VALUE else windowHeight - imeInsetBottom

        /**
         * Fraction of a view of [viewHeight] that is visible between [top] and [bottom] (its visible
         * rect), ignoring anything at or below [coverTop] and at or above [coverBottom] (a pinned
         * header), which are drawn over it.
         */
        fun visibleFraction(top: Int, bottom: Int, viewHeight: Int, coverTop: Int, coverBottom: Int = Int.MIN_VALUE): Float {
            if (viewHeight <= 0) return 0f
            val visible = (minOf(bottom, coverTop) - maxOf(top, coverBottom)).coerceAtLeast(0)
            return visible.toFloat() / viewHeight
        }
    }
}
