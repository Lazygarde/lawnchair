package app.lawnchair.ui.util

import com.android.launcher3.LauncherHost

/**
 * Whether the host was built for the Play Store, which forbids some of the things the launcher
 * would otherwise offer (the in-app updater, broad file access).
 */
fun isPlayStoreFlavor(): Boolean = LauncherHost.releaseChannel() == "play"
