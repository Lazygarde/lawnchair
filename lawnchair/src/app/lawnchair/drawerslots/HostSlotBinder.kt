package app.lawnchair.drawerslots

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.allapps.BaseAllAppsAdapter

/** Creates, binds and tracks the ViewHolders of inline slots; called from BaseAllAppsAdapter. */
object HostSlotBinder {

    private class Holder(val frame: FrameLayout) : BaseAllAppsAdapter.ViewHolder(frame) {
        var item: HostSlotAdapterItem? = null
    }

    @JvmStatic
    fun createHolder(context: Context): BaseAllAppsAdapter.ViewHolder {
        val frame = FrameLayout(context)
        frame.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        frame.clipChildren = false
        return Holder(frame)
    }

    /** Attaches the item's cached view to the holder, moving it from its previous parent. */
    @JvmStatic
    fun bind(context: Context, holder: BaseAllAppsAdapter.ViewHolder, item: BaseAllAppsAdapter.AdapterItem) {
        val h = holder as Holder
        val slotItem = item as HostSlotAdapterItem
        h.item = slotItem
        val view = slotItem.view
        if (view.parent !== h.frame) {
            (view.parent as? ViewGroup)?.removeView(view)
            h.frame.removeAllViews()
            if (view.layoutParams == null) {
                view.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            h.frame.addView(view)
        }
        // Ads never take D-pad/keyboard focus; TalkBack can still read them. Content focuses normally.
        h.frame.descendantFocusability = if (slotItem.kind == DrawerSlotKind.AD) {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } else {
            ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        // The holder itself must be focusable when nothing inside it can take focus. Otherwise a
        // D-pad focus search from the row above finds nothing when the next app row is off screen
        // behind a tall slot, and navigation dead-ends. It is not clickable, so Enter does nothing,
        // and it is never counted for accessibility / IME launch. No highlight is drawn over ads.
        // The library owns vertical spacing: no slot is ever flush with its neighbours.
        val spacing = DrawerSlotController.get(context)?.verticalSpacingPx(slotItem.slot.type) ?: 0
        // The top slot sits right under the header/list padding, which already is its gap.
        h.frame.setPadding(0, if (slotItem.atTop) 0 else spacing, 0, spacing)
        val needsFocusStop = slotItem.kind == DrawerSlotKind.AD || !view.hasFocusable()
        h.frame.isFocusable = needsFocusStop
        h.frame.isClickable = false
        h.frame.defaultFocusHighlightEnabled = false
        if (h.frame.isAttachedToWindow) onAttached(context, holder)
    }

    /** Stable slot key of the item bound to [holder], or null. */
    @JvmStatic
    fun keyOf(holder: RecyclerView.ViewHolder): String? = (holder as? Holder)?.item?.slot?.key

    @JvmStatic
    fun onAttached(context: Context, holder: BaseAllAppsAdapter.ViewHolder) {
        val item = (holder as? Holder)?.item ?: return
        DrawerSlotController.get(context)?.track(item.slot, item.view)
    }

    @JvmStatic
    fun onDetached(context: Context, holder: BaseAllAppsAdapter.ViewHolder) {
        val item = (holder as? Holder)?.item ?: return
        DrawerSlotController.get(context)?.tracker?.untrack(item.slot)
    }
}
