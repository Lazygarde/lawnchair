package app.lawnchair.util

import android.view.View
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Samsung's own background blur (`android.view.SemBlurInfo`, One UI), for devices where the
 * Android 12 cross-window blur is off: a Galaxy S24 reports `mBlurEnabled=false` and ignores
 * `Window.setBackgroundBlurRadius`, while One UI blurs through this. `BLUR_MODE_WINDOW` blurs
 * what is behind the view, as it draws. Absent anywhere else, where [isAvailable] is false.
 */
object SamsungBlur {

    private const val BLUR_MODE_WINDOW = 0

    private class Api(
        val builder: Constructor<*>,
        val setRadius: Method,
        val setBackgroundColor: Method,
        val build: Method,
        val setBlurInfo: Method,
    )

    private val api: Api? by lazy {
        runCatching {
            val info = Class.forName("android.view.SemBlurInfo")
            val builder = Class.forName("android.view.SemBlurInfo\$Builder")
            Api(
                builder.getConstructor(Int::class.javaPrimitiveType),
                builder.getMethod("setRadius", Int::class.javaPrimitiveType),
                builder.getMethod("setBackgroundColor", Int::class.javaPrimitiveType),
                builder.getMethod("build"),
                View::class.java.getMethod("semSetBlurInfo", info),
            )
        }.getOrNull()
    }

    val isAvailable: Boolean get() = api != null

    /** Blurs what is behind [view] by [radius] px, tinted [color]; 0 takes the blur off. */
    fun apply(view: View, radius: Int, color: Int) {
        val api = api ?: return
        runCatching {
            if (radius <= 0) {
                api.setBlurInfo.invoke(view, null)
                return
            }
            val builder = api.builder.newInstance(BLUR_MODE_WINDOW)
            api.setRadius.invoke(builder, radius)
            api.setBackgroundColor.invoke(builder, color)
            api.setBlurInfo.invoke(view, api.build.invoke(builder))
        }
    }
}
