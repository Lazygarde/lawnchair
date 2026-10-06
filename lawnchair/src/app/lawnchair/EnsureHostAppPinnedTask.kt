package app.lawnchair

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings
import com.android.launcher3.WorkspaceLayoutManager
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.GridOccupancy
import com.android.launcher3.util.IntArray as LauncherIntArray

/**
 * Task to ensure the host application icon is pinned to page 0 at the bottom-left corner
 * (cellX = 0, cellY = numRows - 1) by default if it is not already present on the home screen.
 */
class EnsureHostAppPinnedTask : LauncherModel.ModelUpdateTask {

    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        val context = taskController.context
        ensurePinned(
            context = context,
            dataModel = dataModel,
            modelWriter = taskController.getModelWriter(),
            iconCache = taskController.iconCache,
            taskController = taskController,
        )
    }

    companion object {
        private const val TAG = "EnsureHostAppPinnedTask"

        fun ensurePinned(
            context: Context,
            dataModel: BgDataModel,
            modelWriter: ModelWriter,
            iconCache: IconCache,
            taskController: ModelTaskController? = null,
        ) {
            val hostPackageName = context.packageName

            synchronized(dataModel) {
                // 1. Check if the host app already exists on the workspace, hotseat, or inside any folder
                val alreadyExists = dataModel.itemsIdMap.any { item ->
                    item.targetPackage == hostPackageName
                }
                if (alreadyExists) {
                    Log.d(TAG, "Host app (" + hostPackageName + ") already present on home, skipping.")
                    return
                }

                Log.d(TAG, "Host app (" + hostPackageName + ") missing from home. Auto-pinning to page 0 bottom-left...")

                // 2. Resolve host app launcher activity
                val user = Process.myUserHandle()
                val launcherApps = context.getSystemService(LauncherApps::class.java)
                val activities = launcherApps?.getActivityList(hostPackageName, user) ?: emptyList()

                val launchIntent = context.packageManager.getLaunchIntentForPackage(hostPackageName)
                val launchComponent = launchIntent?.component

                val activityInfo = if (launchComponent != null) {
                    activities.find { it.componentName == launchComponent } ?: activities.firstOrNull()
                } else {
                    activities.firstOrNull()
                }

                val workspaceItemInfo: WorkspaceItemInfo = if (activityInfo != null) {
                    val appInfo = AppInfo(context, activityInfo, user)
                    appInfo.makeWorkspaceItem(context) ?: WorkspaceItemInfo().apply {
                        intent = AppInfo.makeLaunchIntent(activityInfo)
                        this.user = user
                        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                    }
                } else if (launchIntent != null) {
                    WorkspaceItemInfo().apply {
                        intent = launchIntent
                        this.user = user
                        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                    }
                } else {
                    Log.w(TAG, "Unable to resolve launch intent for host app (" + hostPackageName + ")")
                    return
                }

                // Fill title and icon from cache
                workspaceItemInfo.title = ""
                workspaceItemInfo.bitmap = iconCache.getDefaultIcon(user)
                if (activityInfo != null) {
                    iconCache.getTitleAndIcon(
                        workspaceItemInfo,
                        activityInfo,
                        LauncherSettings.Favorites.DESKTOP_ICON_FLAG,
                    )
                } else {
                    iconCache.getTitleAndIcon(
                        workspaceItemInfo,
                        LauncherSettings.Favorites.DESKTOP_ICON_FLAG,
                    )
                }

                // 3. Target position: Screen 0, bottom-left corner
                val idp = InvariantDeviceProfile.INSTANCE.get(context)
                val targetScreenId = WorkspaceLayoutManager.FIRST_SCREEN_ID
                val targetCellX = 0
                val targetCellY = maxOf(0, idp.numRows - 1)

                // 4. Check if (targetCellX, targetCellY) on Screen 0 is occupied and relocate occupant if needed
                while (true) {
                    val screen0Items = dataModel.itemsIdMap.filter {
                        it.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                            it.screenId == targetScreenId
                    }

                    val occupant = screen0Items.find { item ->
                        targetCellX >= item.cellX && targetCellX < item.cellX + item.spanX &&
                            targetCellY >= item.cellY && targetCellY < item.cellY + item.spanY
                    } ?: break

                    Log.d(TAG, "Cell (" + targetCellX + ", " + targetCellY + ") occupied by " + occupant + ". Relocating occupant...")
                    val grid = GridOccupancy(idp.numColumns, idp.numRows)
                    screen0Items.forEach { grid.markCells(it, true) }
                    // Free occupant's cells
                    grid.markCells(occupant, false)
                    // Reserve host app cell so occupant won't be moved back to the same spot
                    grid.markCells(targetCellX, targetCellY, 1, 1, true)

                    // On Screen 0, row 0 is reserved for Smartspace if enabled
                    val smartspaceEnabled = PreferenceManager2.getInstance(context).enableSmartspace.firstCached()
                    if (smartspaceEnabled && targetScreenId == WorkspaceLayoutManager.FIRST_SCREEN_ID) {
                        grid.markCells(0, 0, idp.numColumns, 1, true)
                    }

                    val vacant = IntArray(2)
                    if (grid.findVacantCell(vacant, occupant.spanX, occupant.spanY)) {
                        modelWriter.moveItemInDatabase(
                            occupant,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP,
                            targetScreenId,
                            vacant[0],
                            vacant[1],
                        )
                        taskController?.bindUpdatedWorkspaceItems(listOf(occupant))
                    } else {
                        // If no space on screen 0, find space on another screen
                        val workspaceScreens = dataModel.itemsIdMap.collectWorkspaceScreens(context)
                        val itemSpaceFinder = WorkspaceItemSpaceFinder(
                            dataModel,
                            idp,
                            LauncherAppState.getInstance(context).model,
                        )
                        val coords = itemSpaceFinder.findSpaceForItem(
                            workspaceScreens,
                            LauncherIntArray(),
                            ArrayList<ItemInfo>(),
                            occupant.spanX,
                            occupant.spanY,
                            context,
                        )
                        modelWriter.moveItemInDatabase(
                            occupant,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP,
                            coords[0],
                            coords[1],
                            coords[2],
                        )
                        taskController?.bindUpdatedWorkspaceItems(listOf(occupant))
                    }
                }

                // 5. Add host app item to database and memory
                modelWriter.addItemToDatabase(
                    workspaceItemInfo,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP,
                    targetScreenId,
                    targetCellX,
                    targetCellY,
                )

                // 6. Schedule UI callback to bind newly added item
                taskController?.scheduleCallbackTask { callbacks ->
                    callbacks.bindItemsAdded(listOf(workspaceItemInfo))
                }

                Log.d(TAG, "Successfully auto-pinned host app (" + hostPackageName + ") to Screen " + targetScreenId + " (" + targetCellX + ", " + targetCellY + ")")
            }
        }
    }
}
