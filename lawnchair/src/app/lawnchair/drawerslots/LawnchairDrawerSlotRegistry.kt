package app.lawnchair.drawerslots

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.android.launcher3.R
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Emitted by [LawnchairDrawerSlotRegistry.changes]. A null [type] means every slot changed. */
data class DrawerSlotChange(val type: DrawerSlot.Type?)

/**
 * Global registry for host applications to supply views (ads or regular content) to the app drawer.
 * Slots are only used when `config_enable_drawer_slots` is true.
 *
 * There is an optional global provider plus optional per-type providers. A slot is served by the
 * per-type provider if one is set, else by the global provider.
 */
object LawnchairDrawerSlotRegistry {

    @Volatile
    private var globalProvider: LawnchairDrawerSlotProvider? = null

    // Immutable snapshot, replaced on every change.
    @Volatile
    private var typedProviders: Map<DrawerSlot.Type, LawnchairDrawerSlotProvider> = emptyMap()

    // Guards read-modify-write of the provider maps; reads use volatile snapshots.
    private val lock = Any()

    // Backs registerView/registerFactory/setComposeContent; installed as a per-type provider.
    private val helperProvider = SimpleProvider()

    private val _changes = MutableSharedFlow<DrawerSlotChange>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Drawer components collect this to refresh when slots change. */
    val changes: SharedFlow<DrawerSlotChange> = _changes.asSharedFlow()

    val hasProvider: Boolean
        get() = globalProvider != null || typedProviders.isNotEmpty()

    /**
     * Last measured real height per slot (by [DrawerSlot.heightKey]) for the whole process, so it
     * survives launcher recreation and deferred placeholders are sized right from the first frame.
     */
    internal val knownHeights = SlotHeightTable()

    /** Master switch from the host's resources. */
    fun isEnabled(context: Context): Boolean = context.resources.getBoolean(R.bool.config_enable_drawer_slots)

    /** The provider that serves slots of [type] (per-type first, then global), or null. */
    fun resolveProvider(type: DrawerSlot.Type): LawnchairDrawerSlotProvider? = typedProviders[type] ?: globalProvider

    /** The resolved provider, only if slots are enabled and it wants slots of [type]. */
    internal fun providerFor(context: Context, type: DrawerSlot.Type): LawnchairDrawerSlotProvider? {
        if (!isEnabled(context)) return null
        val provider = resolveProvider(type) ?: return null
        return if (provider.isSlotEnabled(type)) provider else null
    }

    /** Every registered provider, each once (a provider registered for several types is listed once). */
    internal fun distinctProviders(): List<LawnchairDrawerSlotProvider> {
        val result = ArrayList<LawnchairDrawerSlotProvider>()
        (typedProviders.values + listOfNotNull(globalProvider)).forEach { p ->
            if (result.none { it === p }) result.add(p)
        }
        return result
    }

    /** Register the global provider, used for every slot type without its own provider. */
    fun registerProvider(provider: LawnchairDrawerSlotProvider) {
        synchronized(lock) { globalProvider = provider }
        notifySlotsChanged(null)
    }

    /** Register a provider for a single slot type, overriding the global provider for it. */
    fun registerProvider(type: DrawerSlot.Type, provider: LawnchairDrawerSlotProvider) {
        synchronized(lock) { typedProviders = typedProviders + (type to provider) }
        notifySlotsChanged(type)
    }

    /**
     * Register a layout as the view of the given slot types (default: header and footer). Installs
     * an internal per-type provider for those types only; other providers are untouched.
     *
     * [kind] defaults to [DrawerSlotKind.CONTENT] because these helpers are mostly used for the
     * host's own UI; pass [DrawerSlotKind.AD] for ads so they get the ad policies.
     * [headerMode] only matters when [DrawerSlot.Type.HEADER] is among [types].
     */
    fun registerView(
        @LayoutRes layoutResId: Int,
        vararg types: DrawerSlot.Type = arrayOf(DrawerSlot.Type.HEADER, DrawerSlot.Type.FOOTER),
        kind: DrawerSlotKind = DrawerSlotKind.CONTENT,
        showOnWorkTab: Boolean = false,
        headerMode: DrawerHeaderMode = DrawerHeaderMode.QUICK_RETURN,
        onViewBound: ((DrawerSlot, View) -> Unit)? = null,
    ) {
        // parent.context is the themed context (LawnchairDrawerSlotTheme), so Material widgets inflate.
        registerFactory(*types, kind = kind, showOnWorkTab = showOnWorkTab, headerMode = headerMode) { _, parent, slot, _ ->
            LayoutInflater.from(parent.context).inflate(layoutResId, parent, false).also { onViewBound?.invoke(slot, it) }
        }
    }

    /** Register a view factory for the given slot types. See [registerView] for [kind]. */
    fun registerFactory(
        vararg types: DrawerSlot.Type,
        kind: DrawerSlotKind = DrawerSlotKind.CONTENT,
        showOnWorkTab: Boolean = false,
        headerMode: DrawerHeaderMode = DrawerHeaderMode.QUICK_RETURN,
        factory: (Context, ViewGroup, DrawerSlot, DrawerSlotEnv) -> View?,
    ) {
        synchronized(lock) {
            helperProvider.put(types.toList(), HelperEntry(factory, kind, showOnWorkTab, headerMode))
            typedProviders = typedProviders + types.map { it to (helperProvider as LawnchairDrawerSlotProvider) }
        }
        types.forEach { notifySlotsChanged(it) }
    }

    /**
     * Register Jetpack Compose content for the given slot types. The view is cached longer than a
     * ViewHolder, so it is disposed when the launcher's lifecycle is destroyed.
     * See [registerView] for [kind].
     */
    fun setComposeContent(
        vararg types: DrawerSlot.Type,
        kind: DrawerSlotKind = DrawerSlotKind.CONTENT,
        showOnWorkTab: Boolean = false,
        headerMode: DrawerHeaderMode = DrawerHeaderMode.QUICK_RETURN,
        content: @Composable (DrawerSlot) -> Unit,
    ) {
        registerFactory(*types, kind = kind, showOnWorkTab = showOnWorkTab, headerMode = headerMode) { _, _, slot, env ->
            ComposeView(env.themedContext).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { content(slot) }
            }
        }
    }

    /**
     * Tell the drawer that slots of [type] (all types when null) changed. Their cached views are
     * dropped and [LawnchairDrawerSlotProvider.createView] / [LawnchairDrawerSlotProvider.kindOf]
     * are called again. Safe from any thread.
     */
    fun notifySlotsChanged(type: DrawerSlot.Type? = null) {
        _changes.tryEmit(DrawerSlotChange(type))
    }

    /** Remove every provider, including those installed by the view/compose helpers. */
    fun clear() {
        synchronized(lock) {
            globalProvider = null
            typedProviders = emptyMap()
            helperProvider.clear()
        }
        notifySlotsChanged(null)
    }

    /** Remove the per-type provider (and helper factory) of [type]; the global provider remains. */
    fun clear(type: DrawerSlot.Type) {
        synchronized(lock) {
            typedProviders = typedProviders - type
            helperProvider.clear(type)
        }
        notifySlotsChanged(type)
    }

    private class HelperEntry(
        val factory: (Context, ViewGroup, DrawerSlot, DrawerSlotEnv) -> View?,
        val kind: DrawerSlotKind,
        val showOnWorkTab: Boolean,
        val headerMode: DrawerHeaderMode,
    )

    /** Backs the helpers. Copy-on-write snapshot, so reads from any thread are safe. */
    private class SimpleProvider : LawnchairDrawerSlotProvider {
        @Volatile
        private var entries: Map<DrawerSlot.Type, HelperEntry> = emptyMap()

        // Callers hold the registry lock.
        fun put(types: List<DrawerSlot.Type>, entry: HelperEntry) {
            entries = entries + types.map { it to entry }
        }

        fun clear(type: DrawerSlot.Type? = null) {
            entries = if (type == null) emptyMap() else entries - type
        }

        override fun isSlotEnabled(type: DrawerSlot.Type) = entries.containsKey(type)

        override fun kindOf(slot: DrawerSlot) = entries[slot.type]?.kind ?: DrawerSlotKind.CONTENT

        override fun showOnWorkTab(slot: DrawerSlot) = entries[slot.type]?.showOnWorkTab ?: false

        override fun headerMode() = entries[DrawerSlot.Type.HEADER]?.headerMode ?: DrawerHeaderMode.QUICK_RETURN

        override fun createView(context: Context, parent: ViewGroup, slot: DrawerSlot, env: DrawerSlotEnv): View? = entries[slot.type]?.factory?.invoke(context, parent, slot, env)
    }
}
