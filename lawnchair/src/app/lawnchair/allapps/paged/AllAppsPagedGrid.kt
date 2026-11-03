package app.lawnchair.allapps.paged

import android.content.Context
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
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.pageindicators.PageIndicatorDots
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.views.ActivityContext
import kotlinx.coroutines.launch

/**
 * The paged app drawer (`config_drawer_paged`): one [CellLayout] per page, `columns × rows` apps
 * each, filled row-major in the order of [bind].
 *
 * It is also the source and target of a drag that starts on one of its icons: the icons make way
 * as the dragged one moves, the edge of the screen turns the page, and a drop saves the order.
 */
class AllAppsPagedGrid(context: Context, attrs: AttributeSet?) :
    PagedView<PageIndicatorDots>(context, attrs), DragSource, DropTarget {

    private val activityContext: ActivityContext = ActivityContext.lookupContext<BaseActivity>(context)
    private val launcher: Launcher get() = Launcher.getLauncher(context)
    private var apps: List<AppInfo> = emptyList()
    private val icons = HashMap<String, BubbleTextView>()

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

    fun setPageIndicator(indicator: PageIndicatorDots) {
        mPageIndicator = indicator
        indicator.setMarkersCount(childCount)
    }

    fun bind(apps: List<AppInfo>) {
        this.apps = apps
        // A rebind mid-drag (an install, say) would lose the dragged icon; the drop rebinds anyway.
        if (dragKey == null) rebuild()
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

    // ponytail: rebuilds every icon on each bind; reuse views if a large drawer shows jank.
    private fun rebuild() {
        val keepPage = nextPage
        removeAllViews()
        icons.clear()
        val pages = PagedGridMath.pageCount(apps.size, perPage)
        repeat(pages) { addView(newPage(), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        apps.forEachIndexed { i, app ->
            val icon = newIcon(this, app)
            icons[keyOf(app)] = icon
            place(icon, i)
        }
        mPageIndicator?.setMarkersCount(pages)
        mPageIndicator?.visibility = if (pages > 1) VISIBLE else INVISIBLE
        setEnableOverscroll(pages > 1)
        setCurrentPage(keepPage.coerceAtMost(pages - 1))
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
        icon.setLongPressTimeoutFactor(1f)
        icon.setOnClickListener(activityContext.itemOnClickListener)
        icon.setOnLongClickListener(activityContext.allAppsItemLongClickListener)
        return icon
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
        dragOrder = apps.map(::keyOf)
        targetIndex = dragOrder!!.indexOf(dragKey)
    }

    override fun onDragOver(dragObject: DropTarget.DragObject) {
        val order = dragOrder ?: return
        val page = getPageAt(nextPage) as? CellLayout ?: return
        val xy = floatArrayOf(dragObject.x.toFloat(), dragObject.y.toFloat())
        launcher.dragLayer.mapCoordInSelfToDescendant(page, xy)

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
        if (!wasHere) rebuild()
    }

    private fun save(order: List<String>) {
        val prefs = PreferenceManager2.getInstance(context)
        launcher.lifecycleScope.launch { prefs.drawerOrder.set(order.joinToString("\n")) }
    }

    private fun keyOf(info: ItemInfo): String = ComponentKey(info.targetComponent, info.user).toString()

    companion object {
        private const val REORDER_DELAY_MS = 150L
        private const val PAGE_FLIP_DELAY_MS = 500L
        private const val REORDER_ANIMATION_MS = 230
        private const val EDGE_FACTOR = 0.45f
    }
}
