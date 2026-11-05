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
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragOptions
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

    override fun onDragExit(dragObject: DropTarget.DragObject) {
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
        if (key != null && order != null) {
            val final = DrawerOrder.move(order, key, targetIndex)
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

    override fun onDropCompleted(target: View?, d: DropTarget.DragObject, success: Boolean) {
        val wasHere = target === this
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
        private const val REORDER_DELAY_MS = 150L
        private const val PAGE_FLIP_DELAY_MS = 500L
        private const val REORDER_ANIMATION_MS = 230
        private const val EDGE_FACTOR = 0.45f
        private const val LABEL_SHADOW_RADIUS = 2f
    }
}
