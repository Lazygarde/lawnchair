package app.lawnchair.allapps.paged

import com.android.launcher3.model.data.AppInfo

/** One cell of the paged drawer: an app, or a folder of apps ([DrawerOrder.arrange]). */
sealed interface DrawerCell {
    /** The cell's key in `drawerOrder`: the app's `ComponentKey`, or [DrawerOrder.folderToken]. */
    val key: String

    data class App(val app: AppInfo) : DrawerCell {
        override val key: String get() = app.toComponentKey().toString()
    }

    data class Folder(val id: Int, val title: String, val apps: List<AppInfo>) : DrawerCell {
        override val key: String get() = DrawerOrder.folderToken(id)
    }
}
