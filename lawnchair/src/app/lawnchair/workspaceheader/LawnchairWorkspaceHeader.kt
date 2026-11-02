package app.lawnchair.workspaceheader

import android.content.Context
import android.view.View
import android.view.ViewGroup
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.InvariantDeviceProfile

/**
 * Lets the host app fill the pinned header of the first home screen with its own view.
 *
 * Lawnchair already pins a view in the top row of page 0 (the Smartspace, see `enable_smartspace`).
 * A host that registers a [WorkspaceHeaderProvider] replaces that view with its own, and may make
 * it taller than one row. The header is an ordinary view, not an app widget, so nothing is bound
 * through `AppWidgetManager` and nothing depends on the launcher being allowed to bind widgets.
 *
 * Registering is enough to turn the header on: `enable_smartspace` and the Smartspace mode are
 * ignored while a provider is set. With no provider, everything is as before.
 */
interface WorkspaceHeaderProvider {

    /**
     * How many grid rows the header takes, from the top of page 0. Clamped to 1..`numRows`.
     * Read from the model's background thread as well as the UI thread: cheap, no side effects.
     */
    fun rowSpan(context: Context): Int

    /**
     * Builds the header. Called once per launcher; the workspace keeps the view across binds.
     * It is laid out as a full-width cell of [rowSpan] rows, so the view should be
     * `MATCH_PARENT` high and wide.
     *
     * [context] is the launcher activity.
     */
    fun createView(context: Context, parent: ViewGroup): View

    /**
     * Whether a horizontal drag that starts on the header may scroll the workspace to the next
     * page. False for a header that scrolls horizontally itself (a pager), which the Smartspace is.
     */
    fun consumesHorizontalDrags(): Boolean = false
}

object LawnchairWorkspaceHeader {

    @Volatile
    private var provider: WorkspaceHeaderProvider? = null

    /** Replace the pinned header of page 0 with the host's. Call from `Application.onCreate`. */
    fun registerProvider(provider: WorkspaceHeaderProvider) {
        this.provider = provider
    }

    /** Go back to the Smartspace header (or none, per `enable_smartspace`). */
    fun clear() {
        provider = null
    }

    /** The host's header, or null when the Smartspace one (if any) is in use. */
    @JvmStatic
    fun hostProvider(): WorkspaceHeaderProvider? = provider

    /**
     * Rows of page 0, from the top, that the pinned header owns: the host's row span, else the
     * Smartspace mode's span (1 for most modes) when it is enabled, else 0. Every place that used to ask `enableSmartspace` and
     * reserve one row asks this and reserves that many.
     */
    @JvmStatic
    fun reservedRows(context: Context): Int {
        provider?.let {
            val numRows = InvariantDeviceProfile.INSTANCE.get(context).numRows
            return it.rowSpan(context).coerceIn(1, numRows.coerceAtLeast(1))
        }
        val prefs = PreferenceManager2.INSTANCE.get(context)
        if (!prefs.enableSmartspace.firstCached()) return 0
        val mode = prefs.smartspaceMode.firstCached()
        // The mode the workspace falls back to when this one is not available takes one row.
        val span = if (mode.isAvailable(context)) mode.spanY else 1
        val numRows = InvariantDeviceProfile.INSTANCE.get(context).numRows
        return span.coerceIn(1, numRows.coerceAtLeast(1))
    }

    @JvmStatic
    fun isEnabled(context: Context): Boolean = reservedRows(context) > 0
}
