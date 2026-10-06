package app.lawnchair.overlay

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Interface that a host application implements to provide a custom in-process
 * minus-one screen (left overlay panel) for Lawnchair.
 */
interface LawnchairOverlayProvider {

    /**
     * Creates and returns the custom view to be displayed in the overlay container.
     *
     * @param context Context of the Launcher Activity.
     * @param container Parent container holding the overlay view.
     */
    fun createOverlayView(context: Context, container: ViewGroup): View

    /**
     * Called when the overlay is attached to the Launcher activity.
     */
    fun onAttachedToLauncher(activity: Activity) {}

    /**
     * Called when the overlay is detached or destroyed.
     */
    fun onDetachedFromLauncher() {}

    /**
     * Called as the user scrolls between the Home workspace and the overlay.
     *
     * @param progress A float from 0.0f (fully closed / on workspace) to 1.0f (fully open).
     */
    fun onOverlayScroll(progress: Float) {}

    /**
     * Called when the overlay becomes fully visible (progress == 1.0f).
     */
    fun onOverlayOpened() {}

    /**
     * Called when the overlay becomes completely hidden (progress == 0.0f).
     */
    fun onOverlayClosed() {}
}
