package app.lawnchair.drawerslots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.android.systemui.plugins.AllAppsRow

/**
 * Header slot, hooked into the drawer's [com.android.launcher3.allapps.FloatingHeaderView]
 * through the existing [AllAppsRow] mechanism. It always exists while slots are enabled and has
 * height 0 until a view is available, so a provider registered late still works.
 * It is hidden (height 0) on the work tab unless the slot is CONTENT and its provider allows it,
 * and, like the whole header, while searching.
 */
internal class HostSlotHeaderRow(private val controller: DrawerSlotController) : AllAppsRow {

    private var listener: AllAppsRow.OnHeightUpdatedListener? = null

    /** Natural height of the view including its vertical margins. */
    private var contentHeight = 0

    /** Height the drawer has actually applied (list padding etc.); lags [contentHeight] while deferred. */
    private var appliedHeight = 0
    private var onWorkTab = false
    private var pageAllowed = true
    private val container = SlotContainer(controller.context).also { it.onChildHeight = ::onContentHeight }
    private var slotView: View? = null

    private val effectiveHeight: Int get() = if (pageAllowed) appliedHeight else 0

    /**
     * Whether a view is present and allowed on the current page. The header must be visible (and
     * laid out) for the view to be measured, so this, not the height, decides whether the drawer
     * shows the header shell.
     */
    val hasContent: Boolean get() = slotView != null && pageAllowed

    init {
        refresh()
    }

    override fun setup(parent: ViewGroup): View {
        val c = container
        (c.parent as? ViewGroup)?.removeView(c)
        // Extend into the header's own side padding so the view lines up with the app grid edges.
        c.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, effectiveHeight).apply {
            leftMargin = -controller.headerOutdentLeftPx()
            rightMargin = -controller.headerOutdentRightPx()
        }
        refresh()
        return c
    }

    override fun getExpectedHeight(): Int = effectiveHeight

    override fun setOnHeightUpdatedListener(l: AllAppsRow.OnHeightUpdatedListener?) {
        listener = l
    }

    /** @return true if the effective visibility changed (caller must rebuild the header). */
    fun setOnWorkTab(onWork: Boolean): Boolean {
        onWorkTab = onWork
        return applyPageAllowed()
    }

    private fun computeAllowed() = !onWorkTab || controller.isAllowedOnWorkTab(DrawerSlot.Header)

    private fun applyPageAllowed(): Boolean {
        val allowed = computeAllowed()
        if (pageAllowed == allowed) return false
        pageAllowed = allowed
        slotView?.visibility = if (allowed) View.VISIBLE else View.GONE
        container.layoutParams?.height = effectiveHeight
        container.requestLayout()
        return slotView != null
    }

    /** Re-reads the view from the controller/cache and attaches it. */
    fun refresh() {
        val c = container
        val view = controller.obtainView(DrawerSlot.Header)
        val hadContent = hasContent
        if (view !== slotView) {
            slotView?.let { controller.tracker.untrack(DrawerSlot.Header) }
            c.removeAllViews()
            slotView = view
            if (view != null) {
                controller.cache.detach(view)
                if (view.layoutParams == null) {
                    view.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                c.addView(view)
                controller.track(DrawerSlot.Header, view)
            }
        }
        // Kind and work-tab policy are read from the provider when the view is created.
        c.descendantFocusability = if (controller.kindOf(DrawerSlot.Header) == DrawerSlotKind.AD) {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } else {
            ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        pageAllowed = computeAllowed()
        view?.visibility = if (pageAllowed) View.VISIBLE else View.GONE
        if (view == null) setHeight(0)
        c.requestLayout()
        if (hadContent != hasContent) {
            // The drawer decides whether to show the header shell from this; let it re-evaluate.
            controller.headerChangeScheduled()
            c.post {
                controller.headerChangeDone()
                controller.headerPresenceListener?.run()
            }
        }
    }

    private fun onContentHeight(measured: Int) {
        if (slotView == null) return
        setHeight(measured)
    }

    private fun setHeight(newHeight: Int) {
        if (newHeight == contentHeight) return
        contentHeight = newHeight
        // Defer: this can be triggered from a layout pass, and the header rebuilds itself. The
        // rebuild resets list scroll, so the controller may postpone it while the user is mid-list.
        // The applied height (container params and getExpectedHeight) only changes when the
        // controller runs the change, so header and list padding never disagree.
        controller.headerChangeScheduled()
        container.post {
            controller.headerChangeDone()
            controller.runHeaderChange { listener?.onHeightUpdated() }
        }
    }

    /** Applies the latest natural height. Called by the controller right before a header rebuild. */
    fun applyHeight() {
        appliedHeight = contentHeight
        container.layoutParams?.height = effectiveHeight
        container.requestLayout()
    }

    fun destroy() {
        controller.tracker.untrack(DrawerSlot.Header)
        container.removeAllViews()
        slotView = null
        contentHeight = 0
        listener = null
    }

    /**
     * Measures its child honoring the child's LayoutParams (fixed height, margins) but with an
     * unspecified height for wrap/match, so the child can report its natural height even while
     * this container is still sized 0.
     */
    private class SlotContainer(context: Context) : FrameLayout(context) {
        var onChildHeight: ((Int) -> Unit)? = null

        init {
            // Ads block D-pad/keyboard focus (TalkBack can still read them); updated per kind in refresh().
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == GONE) continue
                val lp = child.layoutParams as MarginLayoutParams
                val childWidth = getChildMeasureSpec(exactWidth, lp.leftMargin + lp.rightMargin, lp.width)
                val childHeight = if (lp.height >= 0) {
                    MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                } else {
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                }
                child.measure(childWidth, childHeight)
            }
            setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec))
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            val child = if (childCount > 0) getChildAt(0) else return
            if (child.visibility == GONE) return
            val lp = child.layoutParams as MarginLayoutParams
            onChildHeight?.invoke(child.measuredHeight + lp.topMargin + lp.bottomMargin)
        }
    }
}
