package app.lawnchair.smartspace

import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import com.android.launcher3.LauncherHost

class SmartspaceAppWidgetProvider : AppWidgetProvider() {

    companion object {
        @JvmField val componentName = ComponentName(LauncherHost.get(), SmartspaceAppWidgetProvider::class.java.name)
    }
}
