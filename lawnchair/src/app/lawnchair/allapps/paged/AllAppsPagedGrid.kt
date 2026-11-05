package app.lawnchair.allapps.paged

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.Alarm
import com.android.launcher3.BaseActivity
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.PagedView
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.folder.PreviewBackground
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import kotlinx.coroutines.launch

/**
 * The paged app drawer (`config_drawer_paged`): one [CellLayout] per page, `columns × rows` cells
 * each (an app or a folder), filled row-major in the order of [bind].
 *
 * It is also the source and target of a drag that starts on one of its icons: the icons make way
 * as the dragged one moves, the edge of the screen turns the page, and a drop saves the order.
 * An app held over the middle of another icon for a moment shows a ring; dropped there, it makes
 * a folder with that app, or joins that folder.
 */
class AllAppsPagedGrid(context: Context, attrs: AttributeSet?) :
    PagedView<DrawerPageDots>(context, attrs), DragSource, DropTarget {

    private val activityContext: ActivityContext = ActivityContext.lookupContext<BaseActivity>(context)
    private val launcher: Launcher get() = Launcher.getLauncher(context)
    private var cells: List<DrawerCell> = emptyList()
    private val icons = HashMap<String, BubbleTextView>()

    // The keys in the order on screen, and whether a bind came in mid-drag.
    private var shownKeys: List<String> = emptyList()
    private var shownCells: List<DrawerCell> = emptyList()
    private var pendingBind = false

    val columns: Int get() = activityContext.deviceProfile.numShownAllAppsColumns
    var rows: Int = 1
        private set
    val perPage: Int get() = columns * rows

    // Drag state: the order on screen while dragging, the dragged key and the cell it is over.
    private var dragOrder: List<String>? = null
    private var dragKey: String? = null
    private var targetIndex = -1
    private val reorderAlarm = Alarm(Looper.getMainLooper())
    private val pageFlipAlarm = Alarm(Looper.getMainLooper())
    private var pageFlipDir = 0

    // Folder creation: the icon the dragged app is held over, and whether the hold was long enough.
    private var mergeKey: String? = null
    private var mergeReady = false
    private var mergeRing: PreviewBackground? = null
    private val mergeAlarm = Alarm(Looper.getMainLooper())

    init {
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES)
    }

    fun setPageIndicator(indicator: DrawerPageDots) {
        mPageIndicator = indicator
        indicator.setMarkersCount(childCount)
    }

    fun bind(cells: List<DrawerCell>) {
        this.cells = cells
        // A rebind mid-drag (an install, say) would lose the dragged icon; it runs after the drop.
        if (dragKey != null) {
            pendingBind = true
            return
        }
        // Same cells in the same order (the drop already arranged them, or an unrelated pref
        // changed): nothing to rebuild; icon changes reach the icons through AllAppsStore. A
        // folder compares by its title and apps too, so a rename or an added app redraws it.
        if (cells == shownCells && icons.size == cells.size) return
        rebuild()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val newRows = PagedGridMath.rows(h - paddingTop - paddingBottom, activityContext.deviceProfile.allAppsProfile.cellHeightPx)
        if (newRows != rows) {
            rows = newRows
            // Not inside layout: adding pages from onSizeChanged would request layout mid-pass.
            post { if (dragKey == null) rebuild() }
        }
    }

    // The dots follow the scroll, as in FolderPagedView: given only the settled page, a jump of
    // more than one page (back to the first as the drawer closes) left them on the old page.
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (mMaxScroll > 0) mPageIndicator?.setScroll(l, mMaxScroll)
    }

    // ponytail: rebuilds every icon on each bind; reuse views if a large drawer shows jank.
    private fun rebuild() {
        val keepPage = nextPage
        val store = launcher.appsView.appsStore
        forEachPage { store.unregisterIconContainer(it.shortcutsAndWidgets) }
        removeAllViews()
        icons.clear()
        val pages = PagedGridMath.pageCount(cells.size, perPage)
        repeat(pages) { addView(newPage(), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        cells.forEachIndexed { i, cell ->
            val icon = when (cell) {
                is DrawerCell.App -> newIcon(this, cell.app)
                is DrawerCell.Folder -> newFolderIcon(this, cell)
            }
            icons[cell.key] = icon
            place(icon, i)
        }
        shownKeys = cells.map { it.key }
        shownCells = cells
        // Notification dots and install progress reach the icons this way, as for the A-Z list.
        forEachPage { store.registerIconContainer(it.shortcutsAndWidgets) }
        mPageIndicator?.setMarkersCount(pages)
        mPageIndicator?.visibility = if (pages > 1) VISIBLE else INVISIBLE
        setEnableOverscroll(pages > 1)
        setCurrentPage(keepPage.coerceAtMost(pages - 1))
    }

    private inline fun forEachPage(action: (CellLayout) -> Unit) {
        for (i in 0 until childCount) (getChildAt(i) as? CellLayout)?.let(action)
    }

    private fun place(icon: View, index: Int) {
        val (x, y) = PagedGridMath.cellOf(index, columns, perPage)
        val page = getChildAt(PagedGridMath.pageOf(index, perPage)) as CellLayout
        val info = icon.tag as ItemInfo
        page.addViewToCellLayout(icon, -1, info.viewId, CellLayoutLayoutParams(x, y, 1, 1), true)
    }

    private fun newPage(): CellLayout {
        val page = LayoutInflater.from(context).inflate(R.layout.folder_page, this, false) as CellLayout
        val padding = activityContext.deviceProfile.allAppsPadding
        page.setPadding(padding.left, 0, padding.right, 0)
        page.setGridSize(columns, rows)
        page.setInvertIfRtl(true)
        page.shortcutsAndWidgets.isMotionEventSplittingEnabled = false
        return page
    }

    private fun newIcon(parent: ViewGroup, app: AppInfo): BubbleTextView {
        val layout = if (activityContext.deviceProfile.inv.enableTwoLinesInAllApps) R.layout.all_apps_icon_twoline else R.layout.all_apps_icon
        val icon = LayoutInflater.from(context).inflate(layout, parent, false) as BubbleTextView
        icon.applyFromApplicationInfo(app)
        styleIcon(icon)
        icon.setOnClickListener(activityContext.itemOnClickListener)
        icon.setOnLongClickListener(activityContext.allAppsItemLongClickListener)
        return icon
    }

    private fun newFolderIcon(parent: ViewGroup, folder: DrawerCell.Folder): DrawerFolderIcon {
        val icon = LayoutInflater.from(context).inflate(R.layout.drawer_folder_icon, parent, false) as DrawerFolderIcon
        icon.bind(folder)
        styleIcon(icon)
        icon.setOnClickListener { DrawerFolderView.show(launcher, this, folder, editTitle = false) }
        // Drags within the drawer, as an app does; it never leaves it (see AddToHomeBar).
        icon.setOnLongClickListener(activityContext.allAppsItemLongClickListener)
        return icon
    }

    // Over the blurred wallpaper, white labels with the home screen's shadow (One UI); the
    // drawer's own font and size stay.
    private fun styleIcon(icon: BubbleTextView) {
        icon.setTextColor(Color.WHITE)
        icon.setShadowLayer(LABEL_SHADOW_RADIUS, 0f, 0f, Themes.getAttrColor(context, R.attr.workspaceShadowColor))
        icon.setLongPressTimeoutFactor(1f)
    }

    // ---- Drag and drop ----

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        launcher.dragController.addDropTarget(this)
    }

    override fun onDetachedFromWindow() {
        launcher.dragController.removeDropTarget(this)
        super.onDetachedFromWindow()
    }

    override fun isDropEnabled(): Boolean = isShown && launcher.isInState(LauncherState.ALL_APPS)

    override fun acceptDrop(dragObject: DropTarget.DragObject): Boolean = dragObject.dragSource === this

    override fun prepareAccessibilityDrop() {}

    override fun getHitRectRelativeToDragLayer(outRect: Rect) {
        launcher.dragLayer.getDescendantRectRelativeToSelf(this, outRect)
    }

    override fun onDragEnter(dragObject: DropTarget.DragObject) {
        if (dragObject.dragSource !== this || dragKey != null) return
        dragKey = keyOf(dragObject.dragInfo)
        dragOrder = cells.map { it.key }
        targetIndex = dragOrder!!.indexOf(dragKey)
    }

    override fun onDragOver(dragObject: DropTarget.DragObject) {
        val order = dragOrder ?: return
        val page = getPageAt(nextPage) as? CellLayout ?: return
        // DragController already maps x / y into this view (scroll included); make them page-local.
        val xy = floatArrayOf(dragObject.x - page.left.toFloat(), dragObject.y - page.top.toFloat())

        val cell = IntArray(2)
        page.pointToCellExact(xy[0].toInt(), xy[1].toInt(), cell)
        if (isLayoutRtl) cell[0] = columns - 1 - cell[0]
        // A gap past the last app means "last".
        val index = (nextPage * perPage + cell[1] * columns + cell[0]).coerceAtMost(order.size - 1)
        if (trackMerge(dragObject, page, order)) return
        if (index != targetIndex) {
            targetIndex = index
            reorderAlarm.setOnAlarmListener { applyDragOrder(DrawerOrder.move(order, dragKey!!, index)) }
            reorderAlarm.setAlarm(REORDER_DELAY_MS)
        }

        val edge = page.cellWidth * EDGE_FACTOR
        val towardsStart = xy[0] < edge
        val towardsEnd = xy[0] > page.width - edge
        val dir = when {
            (if (isLayoutRtl) towardsEnd else towardsStart) && nextPage > 0 -> -1
            (if (isLayoutRtl) towardsStart else towardsEnd) && nextPage < pageCount - 1 -> 1
            else -> 0
        }
        if (dir != pageFlipDir) {
            pageFlipDir = dir
            pageFlipAlarm.cancelAlarm()
            if (dir != 0) {
                pageFlipAlarm.setOnAlarmListener {
                    snapToPage(nextPage + pageFlipDir)
                    pageFlipDir = 0 // stay at the edge to keep turning
                }
                pageFlipAlarm.setAlarm(PAGE_FLIP_DELAY_MS)
            }
        }
    }

    /**
     * Whether the dragged app is held over the middle of another icon: then the icons stay put,
     * and after [MERGE_DELAY_MS] a ring shows that a drop makes a folder. Uses the dragged icon's
     * visual centre, as the home screen does.
     */
    private fun trackMerge(dragObject: DropTarget.DragObject, page: CellLayout, order: List<String>): Boolean {
        val center = dragObject.getVisualCenter(null)
        val x = center[0] - page.left
        val y = center[1] - page.top
        val cell = IntArray(2)
        page.pointToCellExact(x.toInt(), y.toInt(), cell)
        val (cellX, cellY) = cell[0] to cell[1]
        if (isLayoutRtl) cell[0] = columns - 1 - cell[0]
        val key = order.getOrNull(nextPage * perPage + cell[1] * columns + cell[0])
        val icon = key?.let(icons::get)
        val inCore = dragObject.dragInfo is AppInfo && key != dragKey && icon != null &&
            icon.parent?.parent === page && run {
                val bounds = Rect().also(icon::getIconBounds)
                val c = floatArrayOf(bounds.exactCenterX(), bounds.exactCenterY())
                Utilities.getDescendantCoordRelativeToAncestor(icon, page, c, false)
                PagedGridMath.isInCore(x, y, c[0], c[1], icon.iconSize * MERGE_RADIUS)
            }
        if (!inCore) {
            if (mergeKey != null) clearMerge()
            return false
        }
        reorderAlarm.cancelAlarm()
        if (key != mergeKey) {
            clearMerge()
            mergeKey = key
            mergeAlarm.setOnAlarmListener {
                mergeReady = true
                mergeRing = PreviewBackground(context).apply {
                    setup(context, activityContext, null, icon!!.measuredWidth, icon.paddingTop)
                    isClipping = false
                    animateToAccept(page, cellX, cellY)
                }
            }
            mergeAlarm.setAlarm(MERGE_DELAY_MS)
        }
        return true
    }

    // Leaving an icon's middle: no ring, and the icons make way again from the cell now under it.
    private fun clearMerge() {
        mergeAlarm.cancelAlarm()
        mergeRing?.animateToRest()
        mergeRing = null
        mergeKey = null
        mergeReady = false
        targetIndex = -1
    }

    override fun onDragExit(dragObject: DropTarget.DragObject) {
        // DragController exits the target before it drops there: the merge must outlive that.
        if (!dragObject.dragComplete) clearMerge()
        reorderAlarm.cancelAlarm()
        pageFlipAlarm.cancelAlarm()
        pageFlipDir = 0
    }

    /** Moves the icons to [order]: an animation within a page, a jump between pages. */
    private fun applyDragOrder(order: List<String>) {
        dragOrder = order
        shownKeys = order
        val byKey = shownCells.associateBy { it.key }
        shownCells = order.mapNotNull(byKey::get)
        order.forEachIndexed { i, key ->
            val icon = icons[key] ?: return@forEachIndexed
            val (x, y) = PagedGridMath.cellOf(i, columns, perPage)
            val page = getChildAt(PagedGridMath.pageOf(i, perPage)) as CellLayout
            val current = icon.parent?.parent as? CellLayout
            if (current === page) {
                page.animateChildToPosition(icon, x, y, REORDER_ANIMATION_MS, 0, true, false)
            } else {
                (icon.parent as? ViewGroup)?.removeView(icon)
                place(icon, i)
            }
        }
    }

    override fun onDrop(dragObject: DropTarget.DragObject, options: DragOptions) {
        reorderAlarm.cancelAlarm()
        val key = dragKey
        val order = dragOrder
        val target = mergeKey.takeIf { mergeReady }
        if (key != null && order != null && target != null) {
            dropInto(dragObject, key, target, order)
        } else if (key != null && order != null) {
            // No cell chosen since the pointer left an icon's middle: the app stays where it is.
            val to = if (targetIndex >= 0) targetIndex else order.indexOf(key)
            val final = DrawerOrder.move(order, key, to)
            applyDragOrder(final)
            save(final)
            val icon = icons[key]
            if (icon != null && dragObject.dragView.hasDrawn()) {
                launcher.dragLayer.animateViewIntoPosition(dragObject.dragView, icon, null)
            } else {
                dragObject.deferDragViewCleanupPostAnimation = false
                icon?.visibility = VISIBLE
            }
        }
    }

    /**
     * The dragged app [key] dropped on [target]: with an app, a new folder of the two in [target]'s
     * cell, open with its title to name it; with a folder, one more app in it. Room and the order
     * pref then rebind the grid.
     */
    private fun dropInto(dragObject: DropTarget.DragObject, key: String, target: String, order: List<String>) {
        val byKey = cells.associateBy { it.key }
        val dragged = (byKey[key] as? DrawerCell.App)?.app ?: return
        val rest = order.filter { it != key }
        // The dragged icon is gone at once rather than back in its cell until the rebind.
        dragObject.deferDragViewCleanupPostAnimation = false
        icons[key]?.let { (it.parent as? ViewGroup)?.removeView(it) }
        clearMerge()
        val service = FolderService.INSTANCE.get(context)
        when (val cell = byKey[target]) {
            is DrawerCell.App -> launcher.lifecycleScope.launch {
                val id = service.saveFolderInfo("").toInt()
                service.updateFolderWithItems(id, "", listOf(target, key))
                save(rest.map { if (it == target) DrawerOrder.folderToken(id) else it })
                DrawerFolderView.show(launcher, this@AllAppsPagedGrid, DrawerCell.Folder(id, "", listOf(cell.app, dragged)), editTitle = true)
            }
            is DrawerCell.Folder -> launcher.lifecycleScope.launch {
                service.updateFolderWithItems(cell.id, cell.title, cell.apps.map { it.toComponentKey().toString() } + key)
                save(rest)
            }
            null -> Unit
        }
    }

    override fun onDropCompleted(target: View?, d: DropTarget.DragObject, success: Boolean) {
        val wasHere = target === this
        clearMerge()
        dragKey = null
        dragOrder = null
        targetIndex = -1
        onDragExit(d)
        // Dropped elsewhere (home screen, Cancel, nowhere): put the drawer back as it was.
        // A bind that came in during the drag runs now.
        if (!wasHere || pendingBind) {
            pendingBind = false
            rebuild()
        }
    }

    private fun save(order: List<String>) {
        val prefs = PreferenceManager2.getInstance(context)
        launcher.lifecycleScope.launch { prefs.drawerOrder.set(order.joinToString("\n")) }
    }

    private fun keyOf(info: ItemInfo): String =
        if (info is FolderInfo) DrawerOrder.folderToken(info.id) else ComponentKey(info.targetComponent, info.user).toString()

    companion object {
        // Long enough to reach the middle of the icon aimed at before it makes way.
        private const val REORDER_DELAY_MS = 300L
        private const val PAGE_FLIP_DELAY_MS = 500L
        private const val REORDER_ANIMATION_MS = 230
        private const val EDGE_FACTOR = 0.45f
        private const val LABEL_SHADOW_RADIUS = 2f
        // Held this long over another icon's middle, within this fraction of its size, the ring
        // shows (Launcher3's home screen uses the same reach).
        private const val MERGE_DELAY_MS = 400L
        private const val MERGE_RADIUS = 0.55f
    }
}
