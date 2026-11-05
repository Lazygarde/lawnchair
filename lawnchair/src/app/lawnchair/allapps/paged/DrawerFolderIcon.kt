package app.lawnchair.allapps.paged

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.LruCache
import com.android.launcher3.BaseActivity
import com.android.launcher3.BubbleTextView
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.views.ActivityContext

/**
 * A folder of the paged drawer: up to nine of its apps in a 3×3 grid, in the icons' shape,
 * and its title. Tagged with a [FolderInfo] that never reaches the model (see [DrawerCell.Folder]).
 */
class DrawerFolderIcon(context: Context, attrs: AttributeSet?) : BubbleTextView(context, attrs) {

    /** [drawerApps] are all the drawer's apps: the plate colour is the icon theme's, not the folder's. */
    fun bind(folder: DrawerCell.Folder, drawerApps: List<AppInfo>) {
        tag = FolderInfo().apply {
            id = folder.id
            title = folder.title
            folder.apps.forEach { add(it) }
        }
        text = folder.title
        contentDescription = folder.title
        val size = ActivityContext.lookupContext<BaseActivity>(context).deviceProfile.allAppsProfile.iconSizePx
        val color = plateColor(drawerApps, size)
        // Drawn once per set of icons: copying hardware icons back from the GPU on every rebind
        // made the drawer lag. A new icon (theme change, update) is a new bitmap, so a new key.
        val key = "$size|$color|" + folder.apps.take(9).joinToString(",") { System.identityHashCode(it.bitmap.icon).toString() }
        val preview = previews.get(key) ?: preview(folder, size, color).also { previews.put(key, it) }
        setIcon(BitmapInfo.fromBitmap(preview).newIcon(context))
    }

    // A folder reads as one more icon: the plate of its first app's icon (whatever shape the icon
    // theme draws), filled with the plate colour the apps share, under up to nine of them. The
    // plate is stretched to nearly the whole icon square, a little larger than an app icon's art.
    private fun preview(folder: DrawerCell.Folder, size: Int, color: Int): Bitmap {
        val icons = folder.apps.take(9).map { softwareIcon(it.bitmap.icon, size) }
        val (plate, bounds) = plate(icons.first(), color)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val margin = size * MARGIN
        val area = RectF(margin, margin, size - margin, size - margin)
        canvas.drawBitmap(plate, bounds, area, paint)

        val inset = area.width() * INSET
        val gap = area.width() * GAP
        val cell = (area.width() - 2 * inset - 2 * gap) / 3
        val dst = RectF()
        icons.forEachIndexed { i, icon ->
            val left = area.left + inset + (i % 3) * (cell + gap)
            val top = area.top + inset + (i / 3) * (cell + gap)
            dst.set(left, top, left + cell, top + cell)
            canvas.drawBitmap(icon, null, dst, paint)
        }
        return bitmap
    }

    // App icons are HARDWARE bitmaps, which a software canvas cannot draw or read: copy them.
    private fun softwareIcon(icon: Bitmap, size: Int): Bitmap {
        val soft = if (icon.config == Bitmap.Config.HARDWARE) icon.copy(Bitmap.Config.ARGB_8888, false) else icon
        return if (soft.width == size && soft.height == size) soft else Bitmap.createScaledBitmap(soft, size, size, true)
    }

    // The icon's silhouette in [color], its soft edge kept and its faint shadow dropped, and the
    // bounds of its solid part.
    private fun plate(icon: Bitmap, color: Int): Pair<Bitmap, Rect> {
        val w = icon.width
        val h = icon.height
        val pixels = IntArray(w * h)
        icon.getPixels(pixels, 0, w, 0, 0, w, h)
        val bounds = Rect(w, h, 0, 0)
        val rgb = color and 0x00FFFFFF
        for (i in pixels.indices) {
            val a = Color.alpha(pixels[i])
            if (a >= SOLID) bounds.union(i % w, i / w)
            val edge = ((a - FAINT) * 255 / (SOLID - FAINT)).coerceIn(0, 255)
            pixels[i] = (edge shl 24) or rgb
        }
        if (bounds.isEmpty) bounds.set(0, 0, w, h) else bounds.set(bounds.left, bounds.top, bounds.right + 1, bounds.bottom + 1)
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888) to bounds
    }

    // The plate colour of the icon theme: the colour most of the drawer's first apps have just
    // inside the left and top edges of their plate, where the art seldom reaches; white (One UI's
    // folder) when they share none, each app having its own. One answer for every folder, so a
    // folder's colour does not depend on which apps, or how many, it holds.
    // ponytail: two points on each of SAMPLE_APPS icons; widen it if a theme's art fills the edge.
    private fun plateColor(drawerApps: List<AppInfo>, size: Int): Int {
        val sample = drawerApps.take(SAMPLE_APPS)
        val key = sample.joinToString(",") { System.identityHashCode(it.bitmap.icon).toString() }
        if (key == plateKey) return plateValue
        val samples = sample.map { softwareIcon(it.bitmap.icon, size) }.flatMap { icon ->
            val mid = icon.height / 2
            val x = (0 until icon.width).firstOrNull { Color.alpha(icon.getPixel(it, mid)) >= SOLID }
            val y = (0 until icon.height).firstOrNull { Color.alpha(icon.getPixel(icon.width / 2, it)) >= SOLID }
            listOfNotNull(
                x?.let { icon.getPixel((it + EDGE_IN).coerceAtMost(icon.width - 1), mid) },
                y?.let { icon.getPixel(icon.width / 2, (it + EDGE_IN).coerceAtMost(icon.height - 1)) },
            )
        }.filter { Color.alpha(it) >= SOLID }
        // Group near colours (4 bits a channel); a plate colour only if most samples share it.
        val common = samples.groupBy { it and QUANT }.maxByOrNull { it.value.size }?.value
        plateValue = if (common != null && common.size * 3 >= samples.size * 2) common.first() else Color.WHITE
        plateKey = key
        return plateValue
    }

    private companion object {
        const val SAMPLE_APPS = 16
        const val PREVIEW_CACHE = 24
        val previews = LruCache<String, Bitmap>(PREVIEW_CACHE)
        var plateKey: String? = null
        var plateValue = Color.WHITE
        const val MARGIN = 0.02f
        const val INSET = 0.08f
        const val GAP = 0.03f
        const val FAINT = 110
        const val SOLID = 200
        const val EDGE_IN = 4
        const val QUANT = 0x00F0F0F0
    }
}
