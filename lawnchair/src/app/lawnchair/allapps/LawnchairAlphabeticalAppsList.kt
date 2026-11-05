package app.lawnchair.allapps

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.lawnchair.allapps.paged.DefaultDrawerFolders
import app.lawnchair.allapps.paged.DrawerCell
import app.lawnchair.allapps.paged.DrawerOrder
import app.lawnchair.data.folder.service.FolderService
import android.os.Build
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
import com.android.launcher3.R
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

    // Paged drawer (config_drawer_paged): the saved order and the apps as last laid out.
    private val pagedEnabled = supportsHostSlots && context.resources.getBoolean(R.bool.config_drawer_paged)
    private var savedOrder: List<String> = emptyList()
    private var pagedApps: List<AppInfo> = emptyList()
    private var pagedCells: List<DrawerCell> = emptyList()

    // The paged drawer's folders (null until Room answers), and whether the saved order is in.
    private var drawerFolders: List<FolderEntry>? = null
    private var orderLoaded = false
    private var defaultFoldersChecked = false

    /** Called with the cells each time the paged list is rebuilt. */
    var onPagedAppsChanged: ((List<DrawerCell>) -> Unit)? = null
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
        if (pagedEnabled) {
            try {
                prefs2.drawerOrder.onEach(launchIn = context.launcher.lifecycleScope) {
                    savedOrder = it.split('\n').filter(String::isNotEmpty)
                    orderLoaded = true
                    onAppsUpdated()
                }
                FolderService.INSTANCE.get(context).getFoldersFlow()
                    .onEach {
                        drawerFolders = it
                        onAppsUpdated()
                    }
                    .launchIn(context.launcher.lifecycleScope)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to initialize drawer order", t)
            }
        }
        observeFolders()
    }

    /**
     * True when the personal list is shown as pages: the host turned it on and every app belongs
     * to this user (no work profile, no private space). Never for search results or the work list.
     */
    fun isPagedMode(): Boolean =
        pagedEnabled && !isWorkAppsList && appsStore.apps.all { it.user == Process.myUserHandle() }

    /** The cells the paged grid shows: ordered, filtered apps and folders. */
    fun pagedCells(): List<DrawerCell> = pagedCells

    override fun getAppComparator(): Comparator<AppInfo> {
        if (!isPagedMode()) return super.getAppComparator()
        val order = DrawerOrder.merge(savedOrder, appsStore.apps.map(::drawerEntry))
        val index = order.withIndex().associate { (i, key) -> key to i }
        return compareBy { index[it.toComponentKey().toString()] ?: Int.MAX_VALUE }
    }

    private fun drawerEntry(app: AppInfo) =
        DrawerOrder.Entry(app.toComponentKey().toString(), app.firstInstallTime, app.title?.toString().orEmpty())

    // Hidden folders are the A-Z drawer's business: in pages their apps show on their own.
    private fun buildPagedCells(apps: List<AppInfo>): List<DrawerCell> {
        val byKey = apps.associateBy { it.toComponentKey().toString() }
        val folders = drawerFolders.orEmpty().filterNot { it.hide }
        val titles = folders.associate { it.id to it.title }
        val arranged = DrawerOrder.arrange(
            savedOrder,
            apps.map(::drawerEntry),
            folders.map { DrawerOrder.FolderKeys(it.id, it.itemComponentKeys) },
        )
        return arranged.tokens.mapNotNull { token ->
            val id = DrawerOrder.folderIdOf(token)
            if (id == null) {
                byKey[token]?.let(DrawerCell::App)
            } else {
                DrawerCell.Folder(id, titles[id].orEmpty(), arranged.members.getValue(id).mapNotNull(byKey::get))
            }
        }
    }

    /**
     * Once per install, when apps, folders and order are all in: the default folders. On an update
     * only for a user who never arranged the drawer and has no folder, so nothing they set moves.
     */
    private fun maybeAddDefaultFolders() {
        val folders = drawerFolders ?: return
        if (defaultFoldersChecked || !orderLoaded || pagedApps.isEmpty()) return
        defaultFoldersChecked = true
        val untouched = savedOrder.isEmpty() && folders.isEmpty()
        val keys = pagedApps.map { it.toComponentKey().toString() }
        context.launcher.lifecycleScope.launch {
            if (prefs2.drawerDefaultFoldersDone.get().first()) return@launch
            prefs2.drawerDefaultFoldersDone.set(true)
            if (!untouched) return@launch
            val service = FolderService.INSTANCE.get(context)
            DefaultDrawerFolders.group(keys, Build.MANUFACTURER).forEach { group ->
                val id = service.saveFolderInfo(group.title).toInt()
                service.updateFolderWithItems(id, group.title, group.keys)
            }
        }
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

        // Paged drawer: a flat list in the comparator's order, no sections, no folders.
        if (isPagedMode()) {
            appList.filterNotNull().forEach { mAdapterItems.add(AdapterItem.asApp(it)) }
            return position + appList.count { it != null }
        }

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
        if (isPagedMode()) {
            fastScrollerSections.clear()
            pagedApps = items.mapNotNull { it.itemInfo as? AppInfo }
            pagedCells = buildPagedCells(pagedApps)
            onPagedAppsChanged?.invoke(pagedCells)
            maybeAddDefaultFolders()
            return
        }
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
