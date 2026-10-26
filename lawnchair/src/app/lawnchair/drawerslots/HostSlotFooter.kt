package app.lawnchair.drawerslots

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout

/**
 * Footer slot: a fixed [FrameLayout] pinned to the bottom of the drawer (declared in
 * all_apps_content.xml). Takes no space unless a view exists and [update] allows showing it.
 *
 * @param onHeightChanged called when [heightPx] changes, so the drawer can re-pad its lists.
 */
class HostSlotFooter(
    private val controller: DrawerSlotController,
    private val frame: FrameLayout,
    private val onHeightChanged: Runnable,
) {

    /** Current height the footer occupies, 0 when hidden or empty. */
    var heightPx = 0
        private set

    private var slotView: View? = null
    private var allowed = false
    private var bottomMargin = 0
    private var notifyPosted = false

    /** A height/position change was scheduled and not applied yet. */
    val isChangePending: Boolean get() = notifyPosted
    private var lastTop = 0
    private var destroyed = false

    init {
        frame.visibility = View.GONE
        // Ads block D-pad/keyboard focus (TalkBack can still read them); updated per kind in refresh().
        frame.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        frame.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            if (frame.visibility == View.VISIBLE) {
                setHeight(bottom - top)
                if (top != lastTop) {
                    lastTop = top
                    scheduleNotify()
                }
            }
        }
        refresh()
    }

    /** Re-reads the view from the controller/cache. */
    fun refresh() {
        val view = controller.obtainView(DrawerSlot.Footer)
        if (view !== slotView) {
            slotView?.let { controller.tracker.untrack(DrawerSlot.Footer) }
            frame.removeAllViews()
            slotView = view
            if (view != null) {
                controller.cache.detach(view)
                if (view.layoutParams == null) {
                    view.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                frame.addView(view)
                controller.track(DrawerSlot.Footer, view)
            }
        }
        frame.descendantFocusability = if (controller.kindOf(DrawerSlot.Footer) == DrawerSlotKind.AD) {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } else {
            ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        applyVisibility()
    }

    /**
     * @param bottomMarginPx space under the footer (navigation bar, floating search bar)
     * @param allowed false while search is active, the keyboard is open, etc.
     */
    fun update(bottomMarginPx: Int, allowed: Boolean) {
        this.allowed = allowed
        // The footer owns its side insets (grid edges) and a small gap above its view.
        frame.setPadding(controller.footerPaddingLeftPx(), controller.footerTopSpacingPx(), controller.footerPaddingRightPx(), 0)
        val lp = frame.layoutParams as? RelativeLayout.LayoutParams
        if (lp != null && lp.bottomMargin != bottomMarginPx) {
            lp.bottomMargin = bottomMarginPx
            frame.layoutParams = lp
        }
        bottomMargin = bottomMarginPx
        applyVisibility()
    }

    private fun applyVisibility() {
        val show = allowed && slotView != null
        val target = if (show) View.VISIBLE else View.GONE
        if (frame.visibility != target) frame.visibility = target
        if (!show) {
            setHeight(0)
        } else if (frame.height > 0) {
            setHeight(frame.height)
        }
    }

    private fun setHeight(newHeight: Int) {
        if (newHeight == heightPx) return
        heightPx = newHeight
        scheduleNotify()
    }

    /** Top edge in the drawer container's coordinates (valid while shown). */
    val topPx: Int get() = frame.top

    /** Top edge in global coordinates while the footer is shown, else Int.MAX_VALUE (covers nothing). */
    fun coverTop(): Int {
        if (heightPx <= 0 || frame.visibility != View.VISIBLE) return Int.MAX_VALUE
        val r = android.graphics.Rect()
        return if (frame.getGlobalVisibleRect(r)) r.top else Int.MAX_VALUE
    }

    private fun scheduleNotify() {
        // Never change list padding from inside a layout pass; coalesce posts.
        if (!notifyPosted) {
            notifyPosted = true
            frame.post {
                notifyPosted = false
                // The container may have been destroyed or detached before this ran.
                if (!destroyed && frame.isAttachedToWindow) onHeightChanged.run()
            }
        }
    }

    fun destroy() {
        destroyed = true
        controller.tracker.untrack(DrawerSlot.Footer)
        frame.removeAllViews()
        slotView = null
    }
}
