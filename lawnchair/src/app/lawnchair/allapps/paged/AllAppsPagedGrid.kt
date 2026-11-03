package app.lawnchair.allapps.paged

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import com.android.launcher3.BaseActivity
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.PagedView
import com.android.launcher3.R
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.pageindicators.PageIndicatorDots
import com.android.launcher3.views.ActivityContext

/**
 * The paged app drawer (`config_drawer_paged`): one [CellLayout] per page, `columns × rows` apps
 * each, filled row-major in the order of [bind].
 */
class AllAppsPagedGrid(context: Context, attrs: AttributeSet?) : PagedView<PageIndicatorDots>(context, attrs) {

    private val activityContext: ActivityContext = ActivityContext.lookupContext<BaseActivity>(context)
    private var apps: List<AppInfo> = emptyList()

    val columns: Int get() = activityContext.deviceProfile.numShownAllAppsColumns
    var rows: Int = 1
        private set
    val perPage: Int get() = columns * rows

    init {
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES)
    }

    fun setPageIndicator(indicator: PageIndicatorDots) {
        mPageIndicator = indicator
        indicator.setMarkersCount(childCount)
    }

    fun bind(apps: List<AppInfo>) {
        this.apps = apps
        rebuild()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val newRows = PagedGridMath.rows(h - paddingTop - paddingBottom, activityContext.deviceProfile.allAppsProfile.cellHeightPx)
        if (newRows != rows) {
            rows = newRows
            // Not inside layout: adding pages from onSizeChanged would request layout mid-pass.
            post { rebuild() }
        }
    }

    // ponytail: rebuilds every icon on each bind; reuse views if a large drawer shows jank.
    private fun rebuild() {
        val keepPage = nextPage
        removeAllViews()
        val pages = PagedGridMath.pageCount(apps.size, perPage)
        repeat(pages) { addView(newPage(), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        apps.forEachIndexed { i, app ->
            val (x, y) = PagedGridMath.cellOf(i, columns, perPage)
            val page = getChildAt(PagedGridMath.pageOf(i, perPage)) as CellLayout
            page.addViewToCellLayout(newIcon(page, app), -1, app.viewId, CellLayoutLayoutParams(x, y, 1, 1), true)
        }
        mPageIndicator?.setMarkersCount(pages)
        mPageIndicator?.visibility = if (pages > 1) VISIBLE else INVISIBLE
        setEnableOverscroll(pages > 1)
        setCurrentPage(keepPage.coerceAtMost(pages - 1))
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
}
