package app.lawnchair.smartspace

import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import com.android.launcher3.HostPackage

class SmartspaceAppWidgetProvider : AppWidgetProvider() {

    companion object {
        @JvmField val componentName = ComponentName(HostPackage.get(), SmartspaceAppWidgetProvider::class.java.name)
    }
}
