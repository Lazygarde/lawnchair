package app.lawnchair.allapps

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import kotlin.math.ceil
import kotlin.math.max

/** Draws the (already prepared) content of a view into [canvas]. */
fun interface ContentDrawer {
    fun draw(canvas: Canvas)
}

/**
 * Uniform blur applied to the apps list from the very top of the app drawer, through the search
 * bar, down to a strip below the search pill, with a soft gradient transition at the bottom edge of
 * the strip so the blur starts smoothly without a hard cut line.
 *
 * The list is expected to be allowed to draw [clipExtraPx] above the top of the search pill (see
 * `FloatingHeaderView.setRecyclerViewTopClipExtra`); the real upper limit is [drawerTop].
 *
 * Needs Android 12+ (RenderEffect) and a hardware canvas; otherwise the content is drawn normally.
 */
class SearchBarBlurOverlay(
    private val clipExtraPx: Int,
    private val pillHeightPx: Int,
    private val belowHeightPx: Int,
    private val fadeHeightPx: Int,
    private val blurRadiusPx: Float,
    private val drawerTop: (ViewGroup) -> Int,
    private val isActive: () -> Boolean = { true },
) {

    private var contentNode: RenderNode? = null
    private var blurNode: RenderNode? = null

    private val maskOutPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val maskInPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val addPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private var cachedFadeTop = Float.NaN
    private var cachedFadeBottom = Float.NaN

    fun draw(view: ViewGroup, canvas: Canvas, drawer: ContentDrawer) {
        if (!isSupported || !canvas.isHardwareAccelerated || view.width == 0 || view.height == 0 || !isActive()) {
            drawer.draw(canvas)
            return
        }

        val clipTop = view.clipBounds?.top ?: run {
            drawer.draw(canvas)
            return
        }
        val pillTop = clipTop + clipExtraPx
        val bandTop = max(drawerTop(view), clipTop)
        val bandBottom = pillTop + pillHeightPx + belowHeightPx

        if (bandBottom <= bandTop || !hasContentIn(view, bandTop, bandBottom)) {
            canvas.save()
            canvas.clipRect(0f, bandTop.toFloat(), view.width.toFloat(), maxOf(view.height, bandBottom).toFloat())
            drawer.draw(canvas)
            canvas.restore()
            return
        }

        drawBlurred(view, canvas, drawer, bandTop, bandBottom)
    }

    private fun hasContentIn(view: ViewGroup, top: Int, bottom: Int): Boolean {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val childTop = child.top + child.translationY.toInt()
            val childBottom = child.bottom + child.translationY.toInt()
            if (child.visibility == View.VISIBLE && childBottom > top && childTop < bottom) {
                return true
            }
        }
        return false
    }

    private fun drawBlurred(
        view: ViewGroup,
        canvas: Canvas,
        drawer: ContentDrawer,
        bandTop: Int,
        bandBottom: Int,
    ) {
        val width = view.width
        val height = view.height
        val widthF = width.toFloat()
        val contentBottom = maxOf(height, bandBottom)

        val content = contentNode ?: RenderNode("SearchBarBlurContent").also { contentNode = it }
        // Offset the recording canvas by -bandTop so negative Y coordinates (above the RecyclerView's
        // top = 0, i.e. behind and above the search bar) map to positive Y inside the RenderNode.
        val recordHeight = contentBottom - bandTop
        content.setPosition(0, 0, width, recordHeight)
        content.clipToBounds = false
        val recording = content.beginRecording(width, recordHeight)
        try {
            recording.translate(0f, -bandTop.toFloat())
            drawer.draw(recording)
        } finally {
            content.endRecording()
        }

        val blur = blurNode ?: RenderNode("SearchBarBlur").apply {
            setRenderEffect(RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP))
        }.also { blurNode = it }

        val pad = ceil(blurRadiusPx * 2f).toInt()
        val blurHeight = (bandBottom - bandTop) + pad * 2
        blur.setPosition(0, 0, width, blurHeight)
        val blurRecording = blur.beginRecording(width, blurHeight)
        try {
            blurRecording.translate(0f, pad.toFloat())
            blurRecording.drawRenderNode(content)
        } finally {
            blur.endRecording()
        }

        val fadeTop = max(bandTop, bandBottom - fadeHeightPx)
        val fadeTopF = fadeTop.toFloat()
        val bandTopF = bandTop.toFloat()
        val bandBottomF = bandBottom.toFloat()

        // 1. Sharp content strictly below the cross-fade zone.
        canvas.save()
        canvas.clipRect(0f, bandBottomF, widthF, contentBottom.toFloat())
        canvas.translate(0f, bandTopF)
        canvas.drawRenderNode(content)
        canvas.restore()

        // 2. Solid full-strength blur from the very top of the drawer down to fadeTop.
        if (fadeTop > bandTop) {
            canvas.save()
            canvas.clipRect(0f, bandTopF, widthF, fadeTopF)
            canvas.translate(0f, (bandTop - pad).toFloat())
            canvas.drawRenderNode(blur)
            canvas.restore()
        }

        // 3. Soft transition (fadeTop..bandBottom): cross-fade sharp content out and blurred
        //    content in inside an isolated offscreen layer so DST_OUT never punches through to
        //    the window/wallpaper buffer when the RecyclerView has no offscreen layer of its own.
        if (bandBottom > fadeTop) {
            updateFadeShaders(fadeTopF, bandBottomF)

            val bandSave = canvas.saveLayer(0f, fadeTopF, widthF, bandBottomF, null)
            canvas.save()
            canvas.translate(0f, bandTopF)
            canvas.drawRenderNode(content)
            canvas.restore()

            // Fade out the sharp content inside [fadeTop..bandBottom] towards fadeTop.
            canvas.drawRect(0f, fadeTopF, widthF, bandBottomF, maskOutPaint)

            // Draw the blurred content masked in from full at fadeTop to 0 at bandBottom,
            // composited with ADD so `(1 - t) + t = 1.0` without SRC_OVER's 0.75 alpha dip.
            val layerSave = canvas.saveLayer(0f, fadeTopF, widthF, bandBottomF, addPaint)
            canvas.save()
            canvas.translate(0f, (bandTop - pad).toFloat())
            canvas.drawRenderNode(blur)
            canvas.restore()
            canvas.drawRect(0f, fadeTopF, widthF, bandBottomF, maskInPaint)
            canvas.restoreToCount(layerSave)

            canvas.restoreToCount(bandSave)
        }
    }

    private fun updateFadeShaders(fadeTop: Float, fadeBottom: Float) {
        if (cachedFadeTop == fadeTop && cachedFadeBottom == fadeBottom) return
        cachedFadeTop = fadeTop
        cachedFadeBottom = fadeBottom

        val shader = LinearGradient(
            0f,
            fadeTop,
            0f,
            fadeBottom,
            Color.BLACK,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        maskOutPaint.shader = shader
        maskInPaint.shader = shader
    }

    companion object {
        /** Blur needs RenderEffect, which is Android 12+. */
        val isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
