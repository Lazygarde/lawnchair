package app.lawnchair.allapps.paged

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import com.android.launcher3.Alarm
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions

/**
 * Shown over the drawer's search bar while an icon of the paged drawer is dragged. Holding the
 * icon on it leaves the drawer for the home screen with the drag still going, where the drop
 * target bar then reads Cancel (the item comes from the drawer, so it cannot be removed).
 */
class AddToHomeBar(context: Context) : TextView(context), DropTarget, DragController.DragListener {

    private val launcher: Launcher get() = Launcher.getLauncher(context)
    private val handOffAlarm = Alarm(Looper.getMainLooper())
    private val pill = GradientDrawable().apply { setColor(IDLE) }

    init {
        text = context.getString(R.string.paged_drawer_add_to_home)
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        background = pill
        visibility = GONE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pill.cornerRadius = h / 2f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        launcher.dragController.addDragListener(this)
        launcher.dragController.addDropTarget(this)
    }

    override fun onDetachedFromWindow() {
        launcher.dragController.removeDragListener(this)
        launcher.dragController.removeDropTarget(this)
        super.onDetachedFromWindow()
    }

    override fun onDragStart(dragObject: DropTarget.DragObject, options: DragOptions) {
        if (options.fromPagedDrawer) visibility = VISIBLE
    }

    override fun onDragEnd() {
        handOffAlarm.cancelAlarm()
        pill.setColor(IDLE)
        visibility = GONE
    }

    override fun isDropEnabled(): Boolean = visibility == VISIBLE && launcher.isInState(LauncherState.ALL_APPS)

    override fun onDragEnter(dragObject: DropTarget.DragObject) {
        pill.setColor(HOVER)
        handOffAlarm.setOnAlarmListener {
            visibility = GONE
            launcher.stateManager.goToState(LauncherState.SPRING_LOADED)
        }
        handOffAlarm.setAlarm(HAND_OFF_DELAY_MS)
    }

    override fun onDragExit(dragObject: DropTarget.DragObject) {
        handOffAlarm.cancelAlarm()
        pill.setColor(IDLE)
    }

    override fun onDragOver(dragObject: DropTarget.DragObject) {}

    // Released before the hand-off: nothing happens, the drawer puts the icon back.
    override fun acceptDrop(dragObject: DropTarget.DragObject): Boolean = false

    override fun onDrop(dragObject: DropTarget.DragObject, options: DragOptions) {}

    override fun prepareAccessibilityDrop() {}

    override fun getHitRectRelativeToDragLayer(outRect: Rect) {
        launcher.dragLayer.getDescendantRectRelativeToSelf(this, outRect)
    }

    companion object {
        private const val HAND_OFF_DELAY_MS = 400L
        private const val IDLE = 0xCC202124.toInt()
        private const val HOVER = 0xFF1A73E8.toInt()
    }
}
