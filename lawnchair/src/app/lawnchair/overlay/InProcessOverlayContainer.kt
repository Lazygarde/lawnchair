package app.lawnchair.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.launcher3.Insettable

/**
 * Container view holding the host app's custom overlay view.
 * Sits directly behind DragLayer and manages parallax translation, insets, and swipe-to-close gestures.
 */
class InProcessOverlayContainer(
    private val launcher: app.lawnchair.LawnchairLauncher,
    private val manager: InProcessOverlayManager,
    private val provider: LawnchairOverlayProvider,
) : FrameLayout(launcher), Insettable {

    private val overlayContentView: View
    private var lastProgress: Float = 0f

    // Touch handling for dragging to close
    private val touchSlop = ViewConfiguration.get(launcher).scaledTouchSlop
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialProgress = 0f
    private var isDraggingToClose = false
    private var velocityTracker: VelocityTracker? = null

    init {
        setViewTreeLifecycleOwner(launcher)
        setViewTreeSavedStateRegistryOwner(launcher)

        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(Color.TRANSPARENT)
        visibility = GONE

        // Create and add the host app's custom view
        overlayContentView = provider.createOverlayView(launcher, this)
        addView(
            overlayContentView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    override fun setInsets(insets: Rect) {
        // Child ComposeView handles statusBarsPadding / navigationBarsPadding
    }

    /**
     * Called when touch interaction begins to prepare the container for drawing.
     */
    fun onScrollBegin() {
        if (visibility != VISIBLE) {
            visibility = VISIBLE
        }
    }

    /**
     * Updates translation and alpha based on scroll progress [0.0f .. 1.0f].
     */
    fun onScrollProgress(progress: Float) {
        lastProgress = progress
        if (progress <= 0f) {
            if (visibility != GONE) {
                visibility = GONE
            }
            alpha = 0f
            return
        }

        if (visibility != VISIBLE) {
            visibility = VISIBLE
        }

        alpha = progress

        // Subtle parallax slide-in from the left
        val width = measuredWidth.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val parallaxOffset = (1f - progress) * (width * 0.2f)
        translationX = -parallaxOffset
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!manager.isOverlayOpen()) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                initialProgress = manager.currentProgress
                isDraggingToClose = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val dx = ev.rawX - initialTouchX
                val dy = ev.rawY - initialTouchY

                // Intercept if dragging to the left (dx < -touchSlop) and predominantly horizontal
                if (!isDraggingToClose && dx < -touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    isDraggingToClose = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    manager.onScrollInteractionBegin()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingToClose = false
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return isDraggingToClose
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!manager.isOverlayOpen() && !isDraggingToClose) return super.onTouchEvent(ev)

        velocityTracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                initialProgress = manager.currentProgress
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - initialTouchX
                val dy = ev.rawY - initialTouchY

                if (!isDraggingToClose && dx < -touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    isDraggingToClose = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    manager.onScrollInteractionBegin()
                }

                if (isDraggingToClose) {
                    val width = measuredWidth.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                    val newProgress = (initialProgress + dx / width).coerceIn(0f, 1f)
                    manager.applyProgress(newProgress)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDraggingToClose) {
                    isDraggingToClose = false
                    velocityTracker?.computeCurrentVelocity(1000)
                    val xVel = velocityTracker?.xVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null

                    if (xVel < -400f) {
                        manager.hideOverlay(200)
                    } else if (xVel > 400f) {
                        manager.openOverlay()
                    } else {
                        manager.onScrollInteractionEnd()
                    }
                    return true
                }
                velocityTracker?.recycle()
                velocityTracker = null
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDraggingToClose) {
                    isDraggingToClose = false
                    manager.onScrollInteractionEnd()
                }
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return super.onTouchEvent(ev)
    }

    /**
     * Clean up references when the overlay is destroyed.
     */
    fun onDestroy() {
        removeAllViews()
        setViewTreeLifecycleOwner(null)
        setViewTreeSavedStateRegistryOwner(null)
    }
}
