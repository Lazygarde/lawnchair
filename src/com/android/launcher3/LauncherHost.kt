/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3

import android.content.Context
import android.content.Intent
import android.view.View
import com.android.launcher3.model.data.ItemInfo

/**
 * Interceptor called before the launcher starts an activity (e.g. when clicking an app icon or shortcut).
 * Allows the hosting app to intercept, cancel, or redirect the action.
 */
fun interface AppLaunchInterceptor {
    /**
     * Called when an activity is about to be started from the launcher.
     *
     * @param context The launcher context.
     * @param intent The intent to be launched.
     * @param item The [ItemInfo] associated with the clicked item, if available.
     * @param view The clicked view, if available.
     * @return `true` if the launch event was consumed/overridden by the host (canceling the launcher's
     * default launch), or `false` to let the launcher proceed normally.
     */
    fun onAppLaunch(
        context: Context,
        intent: Intent,
        item: ItemInfo?,
        view: View?,
    ): Boolean
}

/**
 * Facts about the app that hosts the launcher.
 *
 * These used to come from `BuildConfig.APPLICATION_ID` and `BuildConfig.FLAVOR_channel`, neither
 * of which exists for a library module. Now that the launcher UI is published as a library the
 * hosting app decides them, so they are resolved at runtime instead of baked in at compile time.
 *
 * [LauncherApplication.attachBaseContext] captures the context before any component of the app
 * runs, which is early enough for content providers.
 */
object LauncherHost {

    @Volatile
    private var context: Context? = null

    /** Optional hook for the host app to intercept activity launches (app clicks, shortcuts, etc.). */
    @Volatile
    @JvmStatic
    var appLaunchInterceptor: AppLaunchInterceptor? = null

    @JvmStatic
    fun init(context: Context) {
        this.context = context.applicationContext ?: context
    }

    private fun requireContext(): Context = checkNotNull(context) {
        "LauncherHost was read before LauncherApplication.attachBaseContext(); the hosting app " +
            "must extend LauncherApplication or call LauncherHost.init(context) itself."
    }

    /** The hosting app's package name, e.g. `app.lawnchair`. */
    @JvmStatic
    fun get(): String = requireContext().packageName

    /**
     * The distribution channel the host was built for, from `R.string.config_release_channel`.
     * `play` disables the parts of the UI that a Play Store build is not allowed to ship.
     */
    @JvmStatic
    fun releaseChannel(): String = requireContext().getString(R.string.config_release_channel)
}
