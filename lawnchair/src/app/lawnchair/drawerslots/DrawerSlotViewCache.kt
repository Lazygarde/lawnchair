package app.lawnchair.drawerslots

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.AbstractComposeView

/**
 * Keeps exactly one view per slot key. When a RecyclerView rebinds, the cached view is
 * detached from its old parent and attached to the new one, so the provider is not asked to
 * create (and load) the view again. Also remembers which provider created a slot and the slot's
 * [DrawerSlotKind] as read at creation time.
 */
class DrawerSlotViewCache {

    private class Entry(
        val slot: DrawerSlot,
        val view: View?,
        val provider: LawnchairDrawerSlotProvider,
        val kind: DrawerSlotKind,
    )

    private val entries = HashMap<String, Entry>()

    /** Called after a slot's view was dropped (and disposed). */
    var onRemoved: ((DrawerSlot, LawnchairDrawerSlotProvider) -> Unit)? = null

    /**
     * Returns the view for [slot], creating it with [create] on first use; [provider] and its
     * [LawnchairDrawerSlotProvider.kindOf] are recorded then. A null result is remembered too,
     * until [invalidate] or [clear] is called.
     */
    fun getOrCreate(slot: DrawerSlot, provider: LawnchairDrawerSlotProvider, create: () -> View?): View? {
        entries[slot.key]?.let { return it.view }
        val view = create()
        entries[slot.key] = Entry(slot, view, provider, provider.kindOf(slot))
        return view
    }

    fun peek(slot: DrawerSlot): View? = entries[slot.key]?.view

    /** Kind of [slot] as read when its view was created; [DrawerSlotKind.AD] if unknown. */
    fun kindOf(slot: DrawerSlot): DrawerSlotKind = entries[slot.key]?.kind ?: DrawerSlotKind.AD

    /** The provider that created [slot]'s view, if it is cached. */
    fun providerOf(slot: DrawerSlot): LawnchairDrawerSlotProvider? = entries[slot.key]?.provider

    /** Detaches [view] from its current parent, if any, so it can be added elsewhere. */
    fun detach(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    /** Drops cached views of [type], or of all types when null. */
    fun invalidate(type: DrawerSlot.Type?) = remove { type == null || it.type == type }

    /** Drops every cached slot matching [predicate]. */
    fun evict(predicate: (DrawerSlot) -> Boolean) = remove(predicate)

    fun clear() = invalidate(null)

    private fun remove(predicate: (DrawerSlot) -> Boolean) {
        val removed = ArrayList<Entry>()
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (predicate(entry.slot)) {
                iterator.remove()
                removed.add(entry)
            }
        }
        removed.forEach { entry ->
            entry.view?.let {
                detach(it)
                dispose(it)
            }
            onRemoved?.invoke(entry.slot, entry.provider)
        }
    }

    // Compose views are cached beyond a ViewHolder and only auto-dispose with the activity
    // lifecycle, so dispose explicitly or every refresh leaks one.
    private fun dispose(view: View) {
        when (view) {
            is AbstractComposeView -> view.disposeComposition()
            is DeferredSlotView -> view.getChildAt(0)?.let(::dispose)
        }
    }
}
