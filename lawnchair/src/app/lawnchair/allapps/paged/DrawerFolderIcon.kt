package app.lawnchair.allapps.paged

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import app.lawnchair.util.resolveFolderPreviewColor
import com.android.launcher3.BaseActivity
import com.android.launcher3.BubbleTextView
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.views.ActivityContext

/**
 * A folder of the paged drawer: up to nine of its apps in a 3×3 grid on the home folders' colour,
 * and its title. Tagged with a [FolderInfo] that never reaches the model (see [DrawerCell.Folder]).
 */
class DrawerFolderIcon(context: Context, attrs: AttributeSet?) : BubbleTextView(context, attrs) {

    fun bind(folder: DrawerCell.Folder) {
        tag = FolderInfo().apply {
            id = folder.id
            title = folder.title
            folder.apps.forEach { add(it) }
        }
        text = folder.title
        contentDescription = folder.title
        val size = ActivityContext.lookupContext<BaseActivity>(context).deviceProfile.allAppsProfile.iconSizePx
        setIcon(BitmapInfo.fromBitmap(preview(folder, size)).newIcon(context))
    }

    // ponytail: a rounded square, not the user's icon shape; use the shape path if they differ.
    private fun preview(folder: DrawerCell.Folder, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = resolveFolderPreviewColor(context)
        val radius = size * CORNER
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)

        val inset = size * INSET
        val gap = size * GAP
        val cell = (size - 2 * inset - 2 * gap) / 3
        val dst = Rect()
        folder.apps.take(9).forEachIndexed { i, app ->
            val left = inset + (i % 3) * (cell + gap)
            val top = inset + (i / 3) * (cell + gap)
            dst.set(left.toInt(), top.toInt(), (left + cell).toInt(), (top + cell).toInt())
            // App icons are HARDWARE bitmaps, which a software canvas cannot draw: copy them.
            // ponytail: up to 9 small copies per folder per rebuild; cache by key if it shows.
            val icon = app.bitmap.icon
            val src = if (icon.config == Bitmap.Config.HARDWARE) icon.copy(Bitmap.Config.ARGB_8888, false) else icon
            canvas.drawBitmap(src, null, dst, paint)
        }
        return bitmap
    }

    private companion object {
        const val CORNER = 0.3f
        const val INSET = 0.14f
        const val GAP = 0.04f
    }
}
