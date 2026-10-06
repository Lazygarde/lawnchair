package app.lawnchair.overlay

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes

/**
 * Global registry for host applications to supply custom minus-one / side-panel
 * overlays to Lawnchair.
 */
object LawnchairOverlayRegistry {

    private var activeProvider: LawnchairOverlayProvider? = null

    /**
     * Check if a custom overlay provider is registered.
     */
    val hasProvider: Boolean
        get() = activeProvider != null

    /**
     * Get the currently registered provider.
     */
    fun getProvider(): LawnchairOverlayProvider? = activeProvider

    /**
     * Register a custom [LawnchairOverlayProvider].
     */
    fun registerProvider(provider: LawnchairOverlayProvider) {
        activeProvider = provider
    }

    /**
     * Convenience method to register an overlay via a layout resource and a binder callback.
     *
     * Example:
     * ```
     * LawnchairOverlayRegistry.registerView(R.layout.my_remote_tv_panel) { view ->
     *     view.findViewById<Button>(R.id.btn_power).setOnClickListener { ... }
     * }
     * ```
     */
    fun registerView(
        @LayoutRes layoutResId: Int,
        onViewBound: ((View) -> Unit)? = null,
    ) {
        registerProvider(object : LawnchairOverlayProvider {
            override fun createOverlayView(context: Context, container: ViewGroup): View {
                val view = LayoutInflater.from(context).inflate(layoutResId, container, false)
                onViewBound?.invoke(view)
                return view
            }
        })
    }

    /**
     * Convenience method to register an overlay using a view factory lambda.
     */
    fun registerView(factory: (context: Context, container: ViewGroup) -> View) {
        registerProvider(object : LawnchairOverlayProvider {
            override fun createOverlayView(context: Context, container: ViewGroup): View {
                return factory(context, container)
            }
        })
    }

    /**
     * Convenience method to register a Jetpack Compose overlay.
     */
    fun setComposeContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        registerProvider(object : LawnchairOverlayProvider {
            override fun createOverlayView(context: Context, container: ViewGroup): View {
                return androidx.compose.ui.platform.ComposeView(context).apply {
                    setViewCompositionStrategy(
                        androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
                    )
                    setContent {
                        content()
                    }
                }
            }
        })
    }

    /**
     * Clears the registered provider.
     */
    fun clear() {
        activeProvider = null
    }
}
