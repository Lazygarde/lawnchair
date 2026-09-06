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
