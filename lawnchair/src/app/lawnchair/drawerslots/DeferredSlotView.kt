package app.lawnchair.drawerslots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Placeholder for a slot whose provider asked to [LawnchairDrawerSlotProvider.deferUntilVisible].
 * It reserves [estimatedHeightPx] and creates the real view only when [materialize] is called (the
 * visibility tracker does that the first time the placeholder is at least 50% on screen).
 */
internal class DeferredSlotView(
    context: Context,
    estimatedHeightPx: Int,
    private val create: () -> View?,
    private val onHeightKnown: (Int) -> Unit,
) : FrameLayout(context) {

    var materialized = false
        private set

    init {
        minimumHeight = estimatedHeightPx
    }

    fun materialize() {
        if (materialized) return
        materialized = true
        val view = create()
        if (view == null) {
            // Nothing to show: take no space.
            minimumHeight = 0
            visibility = GONE
            return
        }
        if (view.layoutParams == null) {
            view.layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        addView(view)
        requestLayout()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (materialized && childCount > 0 && bottom - top > 0) onHeightKnown(bottom - top)
    }
}
