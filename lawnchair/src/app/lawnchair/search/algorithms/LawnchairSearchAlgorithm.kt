package app.lawnchair.search.algorithms

import android.content.Context
import android.util.Log
import app.lawnchair.LawnchairApp
import app.lawnchair.allapps.views.SearchItemBackground
import app.lawnchair.allapps.views.SearchResultView.Companion.EXTRA_QUICK_LAUNCH
import app.lawnchair.drawerslots.DrawerSlot
import app.lawnchair.drawerslots.DrawerSlotController
import app.lawnchair.drawerslots.HostSlotAdapterItem
import app.lawnchair.drawerslots.LawnchairDrawerSlotRegistry
import app.lawnchair.drawerslots.SearchResultKind
import app.lawnchair.drawerslots.SearchSlotPlanner
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.LawnchairSearchAdapterProvider
import app.lawnchair.search.adapter.START_PAGE
import app.lawnchair.search.adapter.SearchAdapterItem
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetCompat.Companion.RESULT_TYPE_APPLICATION
import app.lawnchair.search.adapter.SearchTargetCompat.Companion.RESULT_TYPE_SHORTCUT
import com.android.app.search.LayoutType.CALCULATOR
import com.android.app.search.LayoutType.EDUCARD
import com.android.app.search.LayoutType.EMPTY_DIVIDER
import com.android.app.search.LayoutType.EMPTY_STATE
import com.android.app.search.LayoutType.HORIZONTAL_MEDIUM_TEXT
import com.android.app.search.LayoutType.ICON_HORIZONTAL_TEXT
import com.android.app.search.LayoutType.ICON_SINGLE_VERTICAL_TEXT
import com.android.app.search.LayoutType.ICON_SLICE
import com.android.app.search.LayoutType.PEOPLE_TILE
import com.android.app.search.LayoutType.PLAY_PLACEHOLDER
import com.android.app.search.LayoutType.SEARCH_SETTINGS
import com.android.app.search.LayoutType.SMALL_ICON_HORIZONTAL_TEXT
import com.android.app.search.LayoutType.TEXT_HEADER
import com.android.app.search.LayoutType.THUMBNAIL
import com.android.app.search.LayoutType.WIDGET_LIVE
import com.android.launcher3.LauncherHost
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.search.SearchAlgorithm
import com.android.launcher3.search.SearchCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class LawnchairSearchAlgorithm(
    protected val context: Context,
) : SearchAlgorithm<BaseAllAppsAdapter.AdapterItem> {

    private val transparentBackground = SearchItemBackground(
        context,
        showBackground = false,
        roundBottom = false,
        roundTop = false,
    )
    private val iconBackground = SearchItemBackground(
        context,
        showBackground = false,
        roundTop = true,
        roundBottom = true,
    )
    private val normalBackground = SearchItemBackground(
        context,
        showBackground = true,
        roundTop = true,
        roundBottom = true,
    )
    private val topBackground = SearchItemBackground(
        context,
        showBackground = true,
        roundTop = true,
        roundBottom = false,
    )
    private val centerBackground = SearchItemBackground(
        context,
        showBackground = true,
        roundTop = false,
        roundBottom = false,
    )
    private val bottomBackground = SearchItemBackground(
        context,
        showBackground = true,
        roundTop = false,
        roundBottom = true,
    )

    protected fun transformSearchResults(results: List<SearchTargetCompat>): List<SearchAdapterItem> {
        val filtered = results
            .asSequence()
            .filter { it.packageName != LauncherHost.get() }
            .filter { LawnchairSearchAdapterProvider.viewTypeMap[it.layoutType] != null }
            .removeDuplicateDividers()
            .toList()

        val appAndShortcutIndices = findAppAndShorcutIndices(filtered)
        val smallIconIndices = findIndices(filtered, SMALL_ICON_HORIZONTAL_TEXT)
        val iconRowIndices = findIndices(filtered, ICON_HORIZONTAL_TEXT)
        val peopleTileIndices = findIndices(filtered, PEOPLE_TILE)
        val suggestionIndices = findIndices(filtered, HORIZONTAL_MEDIUM_TEXT)
        val fileIndices = findIndices(filtered, THUMBNAIL)
        val settingIndices = findIndices(filtered, ICON_SLICE)
        val recentIndices = findIndices(filtered, WIDGET_LIVE)
        val calculator = findIndices(filtered, CALCULATOR)

        return filtered.mapIndexedNotNull { index, target ->
            val isFirst = index == 0 || filtered[index - 1].isDivider
            val isLast = index == filtered.lastIndex || filtered[index + 1].isDivider

            // todo make quick launch work on non-app results
            if (
                (target.isApp && target.layoutType == SMALL_ICON_HORIZONTAL_TEXT) ||
                target.isShortcut
            ) {
                SearchAdapterItem.createAdapterItem(target, getGroupedBackground(index, appAndShortcutIndices))
            } else if (target.layoutType == ICON_SINGLE_VERTICAL_TEXT && target.extras.getBoolean(EXTRA_QUICK_LAUNCH, false)) {
                SearchAdapterItem.createAdapterItem(target, normalBackground)
            } else {
                val background = getBackground(
                    target.layoutType,
                    index,
                    isFirst,
                    isLast,
                    smallIconIndices,
                    iconRowIndices,
                    peopleTileIndices,
                    suggestionIndices,
                    fileIndices,
                    settingIndices,
                    recentIndices,
                    calculator,
                )
                SearchAdapterItem.createAdapterItem(target, background)
            }
        }
    }

    protected fun setFirstItemQuickLaunch(searchTargets: List<SearchTargetCompat>) {
        val hasQuickLaunch = searchTargets.any { it.extras.getBoolean(EXTRA_QUICK_LAUNCH, false) }
        if (!hasQuickLaunch) {
            // check if we have a header or spacer item. if so, we skip as there isn't any relevant
            // action to be applied
            val target = searchTargets.getOrNull(
                searchTargets.indexOfFirst { it.layoutType != TEXT_HEADER },
            )
            target?.extras?.apply {
                putBoolean(EXTRA_QUICK_LAUNCH, true)
            }
        }
    }

    private fun findIndices(filtered: List<SearchTargetCompat>, layoutType: String): List<Int> {
        return filtered.indices.filter {
            filtered[it].layoutType == layoutType && !filtered[it].isApp
        }
    }

    private fun findAppAndShorcutIndices(filtered: List<SearchTargetCompat>): List<Int> {
        val appAndShortcutIndices =
            filtered.indices.filter {
                (filtered[it].isApp || filtered[it].isShortcut) &&
                    (
                        filtered[it].layoutType == ICON_HORIZONTAL_TEXT ||
                            filtered[it].layoutType == SMALL_ICON_HORIZONTAL_TEXT
                        )
            }

        return appAndShortcutIndices
    }

    private fun getBackground(
        layoutType: String,
        index: Int,
        isFirst: Boolean,
        isLast: Boolean,
        smallIconIndices: List<Int>,
        iconRowIndices: List<Int>,
        peopleTileIndices: List<Int>,
        suggestionIndices: List<Int>,
        fileIndices: List<Int>,
        settingIndices: List<Int>,
        recentIndices: List<Int>,
        calculator: List<Int>,
    ): SearchItemBackground = when {
        layoutType == TEXT_HEADER || layoutType == ICON_SINGLE_VERTICAL_TEXT || layoutType == EMPTY_DIVIDER -> iconBackground

        layoutType == SMALL_ICON_HORIZONTAL_TEXT -> getGroupedBackground(index, smallIconIndices)

        layoutType == ICON_HORIZONTAL_TEXT -> getGroupedBackground(index, iconRowIndices)

        layoutType == PEOPLE_TILE -> getGroupedBackground(index, peopleTileIndices)

        layoutType == HORIZONTAL_MEDIUM_TEXT -> getGroupedBackground(index, suggestionIndices)

        layoutType == THUMBNAIL -> getGroupedBackground(index, fileIndices)

        layoutType == ICON_SLICE -> getGroupedBackground(index, settingIndices)

        layoutType == WIDGET_LIVE -> getGroupedBackground(index, recentIndices)

        layoutType == CALCULATOR && calculator.isNotEmpty() -> normalBackground

        layoutType == EMPTY_STATE -> transparentBackground

        layoutType == SEARCH_SETTINGS -> transparentBackground

        //        layoutType == PLAY_PLACEHOLDER -> transparentBackground
//        layoutType == EDUCARD -> transparentBackground
        isFirst && isLast -> normalBackground

        isFirst -> topBackground

        isLast -> bottomBackground

        else -> centerBackground
    }

    private fun getGroupedBackground(index: Int, indices: List<Int>): SearchItemBackground = when {
        indices.size == 1 -> normalBackground
        index == indices.first() -> topBackground
        index == indices.last() -> bottomBackground
        else -> centerBackground
    }

    open fun doZeroStateSearch(callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        // Default implementation is to clear results.
        cancelHostSlots()
        callback.clearSearchResult()
    }

    // region Host search slots

    private val hostSlotScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hostSlotJob: Job? = null
    private var hostSlotJobQuery: String? = null
    private var latestQuery: String? = null
    private var latestOrganic: ArrayList<BaseAllAppsAdapter.AdapterItem> = ArrayList()
    private var latestCallback: SearchCallback<BaseAllAppsAdapter.AdapterItem>? = null

    // Provider's answer for the latest query, so later organic updates keep the slots.
    private var resolvedQuery: String? = null
    private var resolvedCount = 0

    // Query for which slots were emitted (their views exist); organic updates keep them.
    private var shownQuery: String? = null

    /**
     * Single exit point for search results (main thread). Organic [items] are emitted right away
     * and unchanged. In parallel, if a provider wants search slots, it is asked for a count after a
     * debounce and, if still relevant, the results are emitted again with slots inserted after the
     * first group. Never used for zero-state results.
     */
    protected fun emitWithHostSlots(
        query: String,
        items: ArrayList<BaseAllAppsAdapter.AdapterItem>,
        callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>,
    ) {
        latestQuery = query
        latestOrganic = items
        latestCallback = callback
        val controller = DrawerSlotController.get(context)
        val provider = if (controller?.isEnabled == true) {
            LawnchairDrawerSlotRegistry.providerFor(context, DrawerSlot.Type.SEARCH)
        } else {
            null
        }
        if (controller == null || provider == null) {
            callback.onSearchResult(query, items)
            return
        }
        // Drop views of stale queries (provider gets onSlotRemoved).
        controller.evictSearchSlots(query)
        if (shownQuery != null && shownQuery != query) shownQuery = null

        // Slots are on screen for this query already: keep them on organic updates (views exist).
        if (shownQuery == query && resolvedQuery == query && resolvedCount > 0) {
            callback.onSearchResult(query, withHostSlots(query, items, resolvedCount, controller))
            return
        }
        callback.onSearchResult(query, items)

        if (hostSlotJob?.isActive == true && hostSlotJobQuery == query) return // pending, uses latestOrganic
        hostSlotJob?.cancel()
        val minQuery = context.resources.getInteger(R.integer.config_drawer_slot_search_min_query)
        if (query.length < minQuery) return
        hostSlotJobQuery = query
        hostSlotJob = hostSlotScope.launch {
            // Never create slot views (and so ad requests) before the query was stable for the
            // debounce interval, including when the count for this query is already known.
            delay(HOST_SLOT_DEBOUNCE_MS)
            val count = if (resolvedQuery == query) {
                resolvedCount
            } else {
                try {
                    withTimeoutOrNull(HOST_SLOT_TIMEOUT_MS) { provider.searchItemCount(query) } ?: 0
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    0
                }
            }
            if (latestQuery != query) return@launch
            resolvedQuery = query
            resolvedCount = count
            val target = latestCallback
            // Stale or closed search: do not bring the results screen back.
            if (count <= 0 || target == null || !controller.searchActive) return@launch
            shownQuery = query
            target.onSearchResult(query, withHostSlots(query, latestOrganic, count, controller))
        }
    }

    private fun withHostSlots(
        query: String,
        organic: ArrayList<BaseAllAppsAdapter.AdapterItem>,
        count: Int,
        controller: DrawerSlotController,
    ): ArrayList<BaseAllAppsAdapter.AdapterItem> {
        val kinds = organic.map { item ->
            val target = (item as? SearchAdapterItem)?.searchTarget
            when {
                target == null -> SearchResultKind.OTHER
                target.layoutType == EMPTY_DIVIDER -> SearchResultKind.DIVIDER
                target.layoutType == TEXT_HEADER -> SearchResultKind.HEADER
                target.isApp || target.isShortcut -> SearchResultKind.APP
                else -> SearchResultKind.OTHER
            }
        }
        val indices = SearchSlotPlanner.insertionIndices(kinds, count)
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) Log.d(LOG_TAG, "search slots for '$query': kinds=$kinds -> $indices")
        val result = ArrayList(organic)
        // Highest slot first so earlier indices stay valid; equal indices keep slot order.
        for (k in count - 1 downTo 0) {
            val index = indices[k] ?: continue
            val slot = DrawerSlot.Search(query, k)
            val view = controller.obtainView(slot) ?: continue
            result.add(index, HostSlotAdapterItem(slot, view, controller.kindOf(slot)))
        }
        return result
    }

    protected fun cancelHostSlots() {
        hostSlotJob?.cancel()
        hostSlotJob = null
        hostSlotJobQuery = null
    }

    override fun destroy() {
        cancelHostSlots()
        hostSlotScope.cancel()
    }

    // endregion

    companion object {

        private const val LOG_TAG = "DrawerSlots"
        private const val HOST_SLOT_DEBOUNCE_MS = 300L
        private const val HOST_SLOT_TIMEOUT_MS = 1500L

        const val APP_SEARCH = "appSearch"
        const val LOCAL_SEARCH = "localSearch"
        const val ASI_SEARCH = "globalSearch"

        private var ranCompatibilityCheck = false

        fun isASISearchEnabled(context: Context): Boolean {
            if (!Utilities.ATLEAST_S) return false
            if (!LawnchairApp.isRecentsEnabled) return false

            if (!ranCompatibilityCheck) {
                ranCompatibilityCheck = true
                LawnchairASISearchAlgorithm.checkSearchCompatibility(context)
            }
            return true
        }

        fun create(context: Context): LawnchairSearchAlgorithm {
            val prefs = PreferenceManager2.getInstance(context)
            val searchAlgorithm = prefs.searchAlgorithm.firstCached()

            return when {
                searchAlgorithm == ASI_SEARCH && isASISearchEnabled(context) -> LawnchairASISearchAlgorithm(
                    context,
                )

                searchAlgorithm == LOCAL_SEARCH -> LawnchairLocalSearchAlgorithm(context)

                else -> LawnchairAppSearchAlgorithm(context)
            }
        }
    }
}

private fun Sequence<SearchTargetCompat>.removeDuplicateDividers(): Sequence<SearchTargetCompat> {
    var previousWasDivider = true
    return filter { item ->
        val isDivider = item.layoutType == EMPTY_DIVIDER
        val remove = isDivider && previousWasDivider
        previousWasDivider = isDivider
        !remove
    }
}

private val SearchTargetCompat.isApp get() = resultType == RESULT_TYPE_APPLICATION
private val SearchTargetCompat.isShortcut get() = resultType == RESULT_TYPE_SHORTCUT
private val SearchTargetCompat.isDivider get() = layoutType == EMPTY_DIVIDER
