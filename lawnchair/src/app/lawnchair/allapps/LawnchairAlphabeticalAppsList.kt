package app.lawnchair.allapps

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.lawnchair.data.folder.FolderEntry
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.drawerslots.DrawerSlot
import app.lawnchair.drawerslots.DrawerSlotController
import app.lawnchair.drawerslots.DrawerSlotKind
import app.lawnchair.drawerslots.HostSlotAdapterItem
import app.lawnchair.drawerslots.InlineSlotPlanner
import app.lawnchair.drawerslots.LawnchairDrawerSlotRegistry
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.categorizeAppsWithSystemAndGoogle
import app.lawnchair.util.observeOnce
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.PrivateProfileManager
import com.android.launcher3.allapps.WorkProfileManager
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.views.ActivityContext
import com.patrykmichalik.opto.core.onEach
import java.util.function.Predicate

@Suppress("SYNTHETIC_PROPERTY_WITHOUT_JAVA_ORIGIN")
class LawnchairAlphabeticalAppsList<T>(
    private val context: T,
    private val appsStore: AllAppsStore<T>,
    workProfileManager: WorkProfileManager?,
    privateProfileManager: PrivateProfileManager?,
    /** False for the search results list, which must not insert host slots (keys are per list). */
    private val supportsHostSlots: Boolean = true,
) : AlphabeticalAppsList<T>(context, appsStore, workProfileManager, privateProfileManager),
    OnIDPChangeListener,
    DefaultLifecycleObserver
    where T : Context, T : ActivityContext {

    private var hiddenApps: Set<String> = setOf()
    private val prefs2 = PreferenceManager2.getInstance(context)
    private val prefs = PreferenceManager.getInstance(context)

    private val viewModel = FolderViewModel(
        (context as? ComponentActivity)?.application ?: context.launcher.application,
    )
    private val folderList = mutableListOf<FolderEntry>()
    private val filteredList = mutableListOf<AppInfo>()

    // Re-runs the list when INLINE slots change (see DrawerSlotController.addInlineListener).
    private val inlineSlotsChanged = Runnable { onAppsUpdated() }

    init {
        if (supportsHostSlots) DrawerSlotController.get(context)?.addInlineListener(inlineSlotsChanged)
        context.launcher.deviceProfile.inv.addOnChangeListener(this)
        (context as? LifecycleOwner)?.lifecycle?.addObserver(this)
        try {
            prefs2.hiddenApps.onEach(launchIn = context.launcher.lifecycleScope) {
                hiddenApps = it
                onAppsUpdated()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize hidden apps", t)
        }
        observeFolders()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        DrawerSlotController.get(context)?.removeInlineListener(inlineSlotsChanged)
        context.launcher.deviceProfile.inv.removeOnChangeListener(this)
    }

    private fun observeFolders() {
        viewModel.folders.observeOnce(context as LifecycleOwner) { folders ->
            if (folders != null) {
                folderList.clear()
                folderList.addAll(folders)
                updateAdapterItems()
            }
        }
    }

    override fun updateItemFilter(itemFilter: Predicate<ItemInfo>?) {
        mItemFilter = Predicate { info ->
            require(info is AppInfo) { "`info` must be an instance of `AppInfo`." }
            val componentKey = info.toComponentKey().toString()
            (itemFilter?.test(info) != false) && !hiddenApps.contains(componentKey)
        }
        onAppsUpdated()
    }

    override fun addAppsWithSections(appList: List<AppInfo?>?, startPosition: Int): Int {
        if (appList.isNullOrEmpty()) return startPosition
        val drawerListDefault = prefs.drawerList.get()
        filteredList.clear()
        var position = startPosition

        // Show app drawer folders only on main profile, to prevent state complexity
        if (isWorkOrPrivateSpace(appList)) return super.addAppsWithSections(appList, position)

        if (!drawerListDefault) {
            val validApps = appList.mapNotNull { it }
            val finalCategorizedApps = categorizeAppsWithSystemAndGoogle(validApps, context)

            finalCategorizedApps.forEach { (category, apps) ->
                if (apps.size == 1) {
                    mAdapterItems.add(AdapterItem.asApp(apps.first()))
                } else {
                    val folderInfo = FolderInfo().apply {
                        title = category
                        apps.forEach { add(it) }
                    }
                    mAdapterItems.add(AdapterItem.asFolder(folderInfo))
                }
                position++
            }
        } else {
            folderList.forEach { folderEntry ->
                val resolvedApps = folderEntry.itemComponentKeys.mapNotNull { keyString ->
                    val componentKey = ComponentKey.fromString(keyString) ?: return@mapNotNull null
                    appsStore.getApp(componentKey) as? AppInfo
                }

                if (resolvedApps.size > 1) {
                    val folderInfo = FolderInfo().apply {
                        id = folderEntry.id
                        title = folderEntry.title
                        resolvedApps.forEach { add(it) }
                    }
                    mAdapterItems.add(AdapterItem.asFolder(folderInfo))
                    position++

                    if (prefs.folderApps.get()) {
                        filteredList.addAll(resolvedApps)
                    }
                }
            }
            val remainingApps = appList.filterNot { app -> filteredList.contains(app) && prefs.folderApps.get() }
            position = super.addAppsWithSections(remainingApps, position)
        }

        return position
    }

    /**
     * Inserts inline host slots at row boundaries of the A-Z / folder grid. Nothing is inserted
     * for search results, inside the private space section, or in the work list unless the slot is
     * CONTENT and its provider allows the work tab. Slots without a view are not inserted.
     */
    override fun onAdapterItemsBuilt(
        items: MutableList<BaseAllAppsAdapter.AdapterItem>,
        fastScrollerSections: MutableList<FastScrollSectionInfo>,
    ) {
        if (!supportsHostSlots || hasSearchResults()) return
        val perRow = numAppsPerRow
        if (perRow <= 0) return
        val controller = DrawerSlotController.get(context) ?: return
        val provider = LawnchairDrawerSlotRegistry.providerFor(context, DrawerSlot.Type.INLINE) ?: return

        val psHeader = items.indexOfFirst { BaseAllAppsAdapter.isPrivateSpaceHeaderView(it.viewType) }
        val limit = if (psHeader >= 0) psHeader else items.size
        val icons = (0 until limit).filter { BaseAllAppsAdapter.isIconViewType(items[it].viewType) }.toIntArray()
        if (icons.isEmpty()) return

        val positions = provider.inlineRowPositions(InlineSlotPlanner.rowCount(icons.size, perRow))
        val inserted = mutableListOf<Pair<Int, HostSlotAdapterItem>>()
        val planned = mutableListOf<InlineSlotPlanner.Insertion>()
        for (insertion in InlineSlotPlanner.plan(icons, perRow, positions)) {
            val (beforeIndex, slotIndex) = insertion
            val slot = DrawerSlot.Inline(slotIndex, inWorkTab = isWorkAppsList)
            if (isWorkAppsList && !(provider.kindOf(slot) == DrawerSlotKind.CONTENT && provider.showOnWorkTab(slot))) continue
            val view = controller.obtainView(slot) ?: continue
            inserted.add(beforeIndex to HostSlotAdapterItem(slot, view, controller.kindOf(slot), insertion.isFirstItem))
            planned.add(insertion)
        }
        if (inserted.isEmpty()) return

        // Highest index first so earlier indices stay valid.
        inserted.asReversed().forEach { (index, item) -> items.add(index, item) }
        fastScrollerSections.replaceAll { section ->
            FastScrollSectionInfo(section.sectionName, InlineSlotPlanner.shiftedSectionPosition(section.position, planned)).also { it.id = section.id }
        }
    }

    override fun onIdpChanged(modelPropertiesChanged: Boolean) {
        onAppsUpdated()
    }
}
