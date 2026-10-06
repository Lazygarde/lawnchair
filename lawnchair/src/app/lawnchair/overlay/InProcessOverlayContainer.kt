package app.lawnchair.overlay

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Container view holding the host app's custom overlay view.
 * Sits directly behind DragLayer and manages parallax translation and insets.
 */
class InProcessOverlayContainer(
    context: Context,
    private val provider: LawnchairOverlayProvider,
) : FrameLayout(context) {

    private val overlayContentView: View
    private var lastProgress: Float = 0f

    init {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(Color.TRANSPARENT)
        visibility = GONE

        // Create and add the host app's custom view
        overlayContentView = provider.createOverlayView(context, this)
        addView(
            overlayContentView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        // Propagate system window insets
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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

    /**
     * Clean up references when the overlay is destroyed.
     */
    fun onDestroy() {
        removeAllViews()
    }
}
