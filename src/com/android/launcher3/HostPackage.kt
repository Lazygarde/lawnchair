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
 * The package name of the app that hosts the launcher.
 *
 * This used to be read from `BuildConfig.APPLICATION_ID`, which only exists for application
 * modules. Now that the launcher UI is also published as a library the hosting app decides
 * the package name, so it has to be resolved at runtime instead of baked in at compile time.
 *
 * [LauncherApplication.attachBaseContext] captures it before any component of the app runs,
 * which is early enough for content providers.
 */
object HostPackage {

    @Volatile
    private var packageName: String? = null

    @JvmStatic
    fun init(context: Context) {
        packageName = context.applicationContext?.packageName ?: context.packageName
    }

    /** The hosting app's package name, e.g. `app.lawnchair`. */
    @JvmStatic
    fun get(): String = checkNotNull(packageName) {
        "HostPackage was read before LauncherApplication.attachBaseContext(); the hosting app " +
            "must extend LauncherApplication or call HostPackage.init(context) itself."
    }

    /** Convenience for building authorities and actions, e.g. `HostPackage.suffixed(".search")`. */
    @JvmStatic
    fun suffixed(suffix: String): String = get() + suffix
}
