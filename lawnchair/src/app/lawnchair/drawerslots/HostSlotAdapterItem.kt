package app.lawnchair.drawerslots

import android.view.View
import com.android.launcher3.allapps.BaseAllAppsAdapter

/**
 * Adapter item for an inline [DrawerSlot]. It is never counted for accessibility, so it can
 * never be the IME launch target (`getFocusedChild`) and Enter cannot open it, whatever its kind.
 */
class HostSlotAdapterItem(
    val slot: DrawerSlot,
    val view: View,
    val kind: DrawerSlotKind,
    /** First item of the list (inline position 0): no top spacing. */
    val atTop: Boolean = false,
) : BaseAllAppsAdapter.AdapterItem(BaseAllAppsAdapter.VIEW_TYPE_HOST_SLOT) {

    override fun isCountedForAccessibility(): Boolean = false

    override fun isSameAs(other: BaseAllAppsAdapter.AdapterItem): Boolean = other is HostSlotAdapterItem && other.slot.key == slot.key

    /** Changed when the kind changed or the view was recreated, so the holder rebinds. */
    override fun isContentSame(other: BaseAllAppsAdapter.AdapterItem): Boolean = other is HostSlotAdapterItem && other.kind == kind && other.atTop == atTop && other.view === view
}
