package app.lawnchair.allapps.paged

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

import com.android.launcher3.pageindicators.PageIndicator
import kotlin.math.abs

/**
 * The paged drawer's dots: one dot per page, all the same size, the page in view lit (One UI
 * style), the light passing between two dots as the pages scroll.
 */
class DrawerPageDots(context: Context, attrs: AttributeSet?) : View(context, attrs), PageIndicator {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // White over the blurred wallpaper, as the labels: the theme's dot colour (dark on a light
        // wallpaper) all but vanished there.
        color = Color.WHITE
    }
    // Small dots, spaced wide, as One UI draws them.
    private val radius = DOT_DP / 2 * resources.displayMetrics.density
    private val gap = GAP_DP * resources.displayMetrics.density
    private var count = 0
    private var position = 0f

    override fun setScroll(currentScroll: Int, totalScroll: Int) {
        if (count <= 1 || totalScroll <= 0) return
        val scroll = if (layoutDirection == LAYOUT_DIRECTION_RTL) totalScroll - currentScroll else currentScroll
        position = scroll.toFloat() * (count - 1) / totalScroll
        invalidate()
    }

    override fun setActiveMarker(activePage: Int) {
        position = activePage.toFloat()
        invalidate()
    }

    override fun setMarkersCount(numMarkers: Int) {
        count = numMarkers
        position = position.coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())
        requestLayout()
    }

    override fun setPaintColor(color: Int) {
        paint.color = color
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (count * 2 * radius + (count - 1).coerceAtLeast(0) * gap).toInt()
        setMeasuredDimension(
            resolveSize(width + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize((2 * radius).toInt() + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (count <= 1) return
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val start = (width - count * 2 * radius - (count - 1) * gap) / 2 + radius
        val y = height / 2f
        val baseAlpha = paint.alpha
        for (i in 0 until count) {
            // Lit by how close the scroll is to this page: full on it, dim a page away.
            val lit = (1 - abs(position - i)).coerceIn(0f, 1f)
            paint.alpha = (baseAlpha * (DIM + (1 - DIM) * lit)).toInt()
            val slot = if (rtl) count - 1 - i else i
            canvas.drawCircle(start + slot * (2 * radius + gap), y, radius, paint)
        }
        paint.alpha = baseAlpha
    }

    private companion object {
        const val DIM = 0.4f
        const val DOT_DP = 5f
        const val GAP_DP = 14f
    }
}
