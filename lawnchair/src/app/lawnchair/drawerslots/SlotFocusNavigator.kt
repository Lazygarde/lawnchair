package app.lawnchair.drawerslots

import android.view.View
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.BaseAllAppsAdapter

/**
 * Keeps the grid column when D-pad focus leaves an inline slot up or down. GridLayoutManager would
 * use the slot's own (first) column, and the focus finder can find nothing when the neighbouring
 * row is off screen behind a tall slot.
 */
object SlotFocusNavigator {

    /**
     * @param lastGridX horizontal centre (RecyclerView coordinates) of the last focused grid item
     *        before the slot took focus, or -1
     * @return the view to focus (or [focused] itself while the target row is scrolled into view),
     *         null to use the default behaviour
     */
    @JvmStatic
    fun resolve(rv: AllAppsRecyclerView, focused: View?, direction: Int, lastGridX: Int): View? {
        if (focused == null || lastGridX < 0) return null
        if (direction != View.FOCUS_UP && direction != View.FOCUS_DOWN) return null
        val itemView = rv.findContainingItemView(focused) ?: return null
        val holder = rv.getChildViewHolder(itemView) ?: return null
        if (holder.itemViewType != BaseAllAppsAdapter.VIEW_TYPE_HOST_SLOT) return null
        val holderPos = holder.bindingAdapterPosition
        if (holderPos == RecyclerView.NO_POSITION) return null
        val layoutManager = rv.layoutManager as? GridLayoutManager ?: return null

        val items = rv.apps.adapterItems
        val perRow = DrawerSlotController.get(rv.context)?.appsPerRow ?: return null
        val contentWidth = rv.width - rv.paddingLeft - rv.paddingRight
        val column = SlotFocusMath.columnForX(
            lastGridX - rv.paddingLeft,
            contentWidth,
            perRow,
            rv.layoutDirection == View.LAYOUT_DIRECTION_RTL,
        )
        val target = SlotFocusMath.targetPosition(
            BooleanArray(items.size) { BaseAllAppsAdapter.isIconViewType(items[it].viewType) },
            IntArray(items.size) { items[it].rowIndex },
            IntArray(items.size) { items[it].rowAppIndex },
            holderPos,
            direction == View.FOCUS_DOWN,
            column,
        )
        if (target < 0) return null

        layoutManager.findViewByPosition(target)?.let { return it }
        // Not laid out: scroll it in, then focus it. Keep focus on the slot meanwhile.
        rv.scrollToPosition(target)
        rv.doOnNextLayout { layoutManager.findViewByPosition(target)?.requestFocus() }
        return focused
    }
}
