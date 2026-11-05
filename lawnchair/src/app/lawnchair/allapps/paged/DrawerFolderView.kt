package app.lawnchair.allapps.paged

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.lawnchair.data.folder.service.FolderService
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.Themes
import kotlinx.coroutines.launch

/**
 * A folder of the paged drawer, open over the drawer's blur (One UI): its title, big and editable,
 * above a rounded panel of its apps, three a row, scrolling when there are many. Back or a tap
 * outside closes it; a changed title is saved then.
 *
 * Not Launcher3's `Folder`, which writes the home screen's model; this one only renames through
 * [FolderService].
 */
@SuppressLint("ViewConstructor")
class DrawerFolderView private constructor(
    private val launcher: Launcher,
    private val folder: DrawerCell.Folder,
    // The drawer under the panel (its pages and search bar): hidden while open, so only the blur
    // shows behind, as in One UI.
    private val behind: List<View>,
) : AbstractFloatingView(launcher, null) {

    private val title = EditText(launcher)
    private val panel = FrameLayout(launcher)
    private val content = FrameLayout(launcher)

    init {
        val density = resources.displayMetrics.density
        title.apply {
            setText(folder.title)
            setHint(R.string.drawer_folder_name_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SP)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnClickListener { isCursorVisible = true }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard()
                    title.isCursorVisible = false
                }
                false
            }
        }
        panel.background = GradientDrawable().apply {
            setColor(PANEL_COLOR)
            cornerRadius = PANEL_CORNER_DP * density
        }
        // Apps scrolled past the panel's edge are cut by its rounded outline, not a straight line.
        panel.clipToOutline = true
        val grid = RecyclerView(launcher).apply {
            layoutManager = GridLayoutManager(launcher, COLUMNS)
            adapter = AppsAdapter(folder.apps)
            setPadding(0, (PANEL_TOP_PADDING_DP * density).toInt(), 0, (PANEL_TOP_PADDING_DP * density).toInt())
            clipToPadding = false
            overScrollMode = OVER_SCROLL_NEVER
        }
        panel.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // The title and the panel at One UI's places, as fractions of the screen.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        (title.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = (h * TITLE_TOP).toInt()
            leftMargin = (w * PANEL_SIDE).toInt()
            rightMargin = leftMargin
        }
        (panel.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = (h * PANEL_TOP).toInt()
            height = (h * (PANEL_BOTTOM - PANEL_TOP)).toInt()
            leftMargin = (w * PANEL_SIDE).toInt()
            rightMargin = leftMargin
        }
        post { content.requestLayout() }
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN &&
            !launcher.dragLayer.isEventOverView(panel, ev) &&
            !launcher.dragLayer.isEventOverView(title, ev)
        ) {
            close(true)
            return true
        }
        return false
    }

    override fun isOfType(type: Int): Boolean = type and TYPE_OPTIONS_POPUP_DIALOG != 0

    override fun handleClose(animate: Boolean) {
        if (!mIsOpen) return
        mIsOpen = false
        hideKeyboard()
        val newTitle = title.text.toString().trim()
        if (newTitle != folder.title) {
            val service = FolderService.INSTANCE.get(launcher)
            launcher.lifecycleScope.launch { service.renameFolderInfo(folder.id, newTitle) }
        }
        behind.forEach { it.animate().alpha(1f).setDuration(ANIM_MS) }
        if (animate) {
            animate().alpha(0f).scaleX(OPEN_SCALE).scaleY(OPEN_SCALE).setDuration(ANIM_MS)
                .withEndAction { remove() }
        } else {
            remove()
        }
    }

    // Android hands the title's focus to the next text field, the drawer's search, which then
    // opened search mode; it is kept out of reach until this view is gone.
    private fun remove() {
        val search = launcher.appsView.searchUiManager?.editText
        search?.isFocusableInTouchMode = false
        launcher.dragLayer.removeView(this)
        search?.post { search.isFocusableInTouchMode = true }
    }

    private fun open(editTitle: Boolean) {
        mIsOpen = true
        launcher.dragLayer.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        alpha = 0f
        scaleX = OPEN_SCALE
        scaleY = OPEN_SCALE
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ANIM_MS)
        behind.forEach { it.animate().alpha(0f).setDuration(ANIM_MS) }
        if (editTitle) {
            title.requestFocus()
            title.post {
                launcher.getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(title, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun hideKeyboard() {
        launcher.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
    }

    private inner class AppsAdapter(private val apps: List<AppInfo>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = apps.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val icon = LayoutInflater.from(parent.context).inflate(R.layout.all_apps_icon, parent, false) as BubbleTextView
            // A drawer cell's height, as the A-Z list gives its icons; unset, the icon was cut.
            icon.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                launcher.deviceProfile.allAppsProfile.cellHeightPx,
            )
            icon.setTextColor(Color.WHITE)
            icon.setShadowLayer(LABEL_SHADOW_RADIUS, 0f, 0f, Themes.getAttrColor(context, R.attr.workspaceShadowColor))
            icon.setOnClickListener { v ->
                launcher.itemOnClickListener.onClick(v)
                close(false)
            }
            return object : RecyclerView.ViewHolder(icon) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder.itemView as BubbleTextView).applyFromApplicationInfo(apps[position])
        }
    }

    companion object {
        private const val COLUMNS = 3
        private const val TITLE_SP = 40f
        private const val HINT_COLOR = 0x80FFFFFF.toInt()
        private const val PANEL_COLOR = 0x66303030
        private const val PANEL_CORNER_DP = 40f
        private const val PANEL_TOP_PADDING_DP = 36f
        private const val TITLE_TOP = 0.13f
        private const val PANEL_TOP = 0.37f
        private const val PANEL_BOTTOM = 0.92f
        private const val PANEL_SIDE = 0.10f
        private const val OPEN_SCALE = 0.92f
        private const val ANIM_MS = 150L
        private const val LABEL_SHADOW_RADIUS = 2f

        /** Opens [folder] over the drawer; [editTitle] focuses the title, as for a new folder. */
        fun show(launcher: Launcher, grid: AllAppsPagedGrid, folder: DrawerCell.Folder, editTitle: Boolean): DrawerFolderView {
            closeAllOpenViews(launcher)
            val behind = listOfNotNull(
                grid.parent as? View,
                launcher.appsView.findViewById<View>(R.id.search_container_all_apps),
            )
            return DrawerFolderView(launcher, folder, behind).also { it.open(editTitle) }
        }
    }
}
