package app.lawnchair

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
import app.lawnchair.smartspace.SmartspaceAppWidgetProvider
import app.lawnchair.workspaceheader.LawnchairWorkspaceHeader
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
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.GridOccupancy
import com.android.launcher3.util.IntArray as LauncherIntArray
import com.android.launcher3.widget.LauncherWidgetHolder

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
        ensureTopWidget(
            context = context,
            dataModel = dataModel,
            modelWriter = taskController.getModelWriter(),
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

                    // On Screen 0, top row(s) are reserved for Smartspace if enabled
                    val headerRows = LawnchairWorkspaceHeader.reservedRows(context)
                    if (headerRows > 0 && targetScreenId == WorkspaceLayoutManager.FIRST_SCREEN_ID) {
                        grid.markCells(0, 0, idp.numColumns, headerRows.coerceAtMost(maxOf(1, idp.numRows - 1)), true)
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

        fun ensureTopWidget(
            context: Context,
            dataModel: BgDataModel,
            modelWriter: ModelWriter,
            taskController: ModelTaskController? = null,
        ) {
            val hostPackageName = context.packageName
            synchronized(dataModel) {
                // The pinned header owns the top rows, either the Smartspace or the host's own view,
                // which is not an app widget: binding widgets under it would only be dropped again.
                if (LawnchairWorkspaceHeader.isEnabled(context)) return

                val targetScreenId = WorkspaceLayoutManager.FIRST_SCREEN_ID
                val screen0Items = dataModel.itemsIdMap.filter {
                    it.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                        it.screenId == targetScreenId
                }

                val idp = InvariantDeviceProfile.INSTANCE.get(context)
                val user = Process.myUserHandle()
                val spanX = idp.numColumns

                // 1. Find Smartspace widget on Screen 0 if it exists
                var smartspaceWidget = screen0Items.find { item ->
                    item is LauncherAppWidgetInfo &&
                        (item.providerName == SmartspaceAppWidgetProvider.componentName ||
                            item.providerName.className == SmartspaceAppWidgetProvider::class.java.name)
                } as? LauncherAppWidgetInfo

                // 2. Find host app widget (Prayer widget) on Screen 0 if it exists
                var prayerWidget = screen0Items.find { item ->
                    item is LauncherAppWidgetInfo &&
                        item.targetPackage == hostPackageName &&
                        item.providerName.className != SmartspaceAppWidgetProvider::class.java.name
                } as? LauncherAppWidgetInfo

                // 3. Ensure Smartspace widget is positioned at (0, 0, spanX, 1)
                if (smartspaceWidget != null) {
                    if (smartspaceWidget.cellX != 0 || smartspaceWidget.cellY != 0) {
                        Log.d(TAG, "Moving smartspace widget " + smartspaceWidget.id + " to (0, 0)")
                        smartspaceWidget.cellX = 0
                        smartspaceWidget.cellY = 0
                        modelWriter.moveItemInDatabase(
                            smartspaceWidget,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP,
                            targetScreenId,
                            0,
                            0,
                        )
                        taskController?.bindUpdatedWorkspaceItems(listOf(smartspaceWidget))
                    }
                } else {
                    Log.d(TAG, "Auto-adding smartspace widget at (0, 0, " + spanX + ", 1)")
                    val newSmartspaceWidget = LauncherAppWidgetInfo().apply {
                        providerName = SmartspaceAppWidgetProvider.componentName
                        appWidgetId = LauncherAppWidgetInfo.NO_ID
                        restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID or
                            LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY or
                            LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG
                        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                        container = LauncherSettings.Favorites.CONTAINER_DESKTOP
                        screenId = targetScreenId
                        cellX = 0
                        cellY = 0
                        this.spanX = spanX
                        this.spanY = 1
                        this.user = user
                    }
                    modelWriter.addItemToDatabase(
                        newSmartspaceWidget,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP,
                        targetScreenId,
                        0,
                        0,
                    )
                    taskController?.scheduleCallbackTask { callbacks ->
                        callbacks.bindItemsAdded(listOf(newSmartspaceWidget))
                    }
                    smartspaceWidget = newSmartspaceWidget
                }

                // 4. Ensure Prayer widget is positioned at (0, 1, spanX, 2)
                val prayerSpanY = 2
                val prayerTargetY = 1
                if (prayerWidget != null) {
                    if (prayerWidget.cellX != 0 || prayerWidget.cellY != prayerTargetY) {
                        Log.d(TAG, "Moving prayer widget " + prayerWidget.id + " to (0, " + prayerTargetY + ")")
                        prayerWidget.cellX = 0
                        prayerWidget.cellY = prayerTargetY
                        modelWriter.moveItemInDatabase(
                            prayerWidget,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP,
                            targetScreenId,
                            0,
                            prayerTargetY,
                        )
                        taskController?.bindUpdatedWorkspaceItems(listOf(prayerWidget))
                    }
                } else {
                    // Prayer widget not present on Screen 0, resolve provider and auto-bind
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val hostProviders = appWidgetManager.getInstalledProvidersForPackage(hostPackageName, user)
                    val prayerProvider = hostProviders.find {
                        it.provider.className == "com.alquran.holy.audio.tafsir.widget.glance.PrayerOverviewWidgetReceiver"
                    } ?: hostProviders.find {
                        it.provider.className.contains("PrayerOverviewWidgetReceiver")
                    } ?: hostProviders.firstOrNull {
                        it.provider.className != SmartspaceAppWidgetProvider::class.java.name
                    }

                    if (prayerProvider != null) {
                        Log.d(TAG, "Found prayer provider: " + prayerProvider.provider + ", allocating appWidgetId...")
                        val widgetHolder = LauncherWidgetHolder.newInstance(context)
                        try {
                            val appWidgetId = widgetHolder.allocateAppWidgetId()
                            val bound = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, prayerProvider.provider)
                            if (bound) {
                                Log.d(TAG, "Successfully bound appWidgetId: " + appWidgetId + " for " + prayerProvider.provider)
                                val newPrayerWidget = LauncherAppWidgetInfo(appWidgetId, prayerProvider.provider).apply {
                                    this.spanX = spanX
                                    this.spanY = prayerSpanY
                                    this.cellX = 0
                                    this.cellY = prayerTargetY
                                    this.screenId = targetScreenId
                                    this.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
                                    this.user = user
                                    this.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED
                                }
                                modelWriter.addItemToDatabase(
                                    newPrayerWidget,
                                    LauncherSettings.Favorites.CONTAINER_DESKTOP,
                                    targetScreenId,
                                    0,
                                    prayerTargetY,
                                )
                                taskController?.scheduleCallbackTask { callbacks ->
                                    callbacks.bindItemsAdded(listOf(newPrayerWidget))
                                }

                                // Send update broadcast to ensure Glance renders content immediately
                                val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                    component = prayerProvider.provider
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                                }
                                context.sendBroadcast(updateIntent)
                            } else {
                                Log.w(TAG, "Failed to bind appWidgetId " + appWidgetId + " for " + prayerProvider.provider)
                                widgetHolder.deleteAppWidgetId(appWidgetId)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error auto-adding prayer widget", e)
                        } finally {
                            widgetHolder.destroy()
                        }
                    } else {
                        Log.w(TAG, "No prayer provider found for package: " + hostPackageName)
                    }
                }
            }
        }
    }
}
