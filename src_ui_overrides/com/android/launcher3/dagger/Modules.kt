/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.dagger

// withoutQuickstep variant of Modules.kt — no non-SDK/hidden API dependencies.

import com.android.launcher3.backuprestore.LauncherRestoreEventLogger
import com.android.launcher3.icons.LauncherIconProvider
import com.android.launcher3.icons.LauncherIconProviderImpl
import com.android.launcher3.logging.StatsLogManager.StatsLogManagerFactory
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.InstantAppResolver
import com.android.launcher3.util.PluginManagerWrapper
import com.android.launcher3.util.window.RefreshRateTracker
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory
import com.android.quickstep.InstantAppResolverImpl
import com.android.quickstep.LauncherRestoreEventLoggerImpl
import com.android.quickstep.logging.StatsLogCompatManager.StatsLogCompatManagerFactory
import com.android.quickstep.util.ChoreographerFrameRateTracker
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapperImpl
import dagger.Binds
import dagger.Module
import dagger.Provides

import app.lawnchair.factory.LawnchairWidgetHolder
import app.lawnchair.util.LawnchairWindowManagerProxy

private object Modules

@Module
abstract class WindowManagerProxyModule {
    @Binds abstract fun bindWindowManagerProxy(proxy: LawnchairWindowManagerProxy): WindowManagerProxy
}

@Module
abstract class ApiWrapperModule {
    @Binds
    abstract fun bindStatsLogManagerFactory(
        impl: StatsLogCompatManagerFactory
    ): StatsLogManagerFactory

    /** withoutQuickstep: bind core ApiWrapper directly — no SystemApiWrapper (touches hidden API). */
    @Binds abstract fun bindApiWrapper(apiWrapper: ApiWrapper): ApiWrapper

    @Binds
    abstract fun bindIconProvider(iconProviderImpl: LauncherIconProviderImpl): LauncherIconProvider

    @Binds abstract fun bindInstantAppResolver(impl: InstantAppResolverImpl): InstantAppResolver

    @Binds
    abstract fun bindRestoreEventLogger(
        impl: LauncherRestoreEventLoggerImpl
    ): LauncherRestoreEventLogger
}

@Module
abstract class WidgetModule {

    @Binds
    abstract fun bindWidgetHolderFactory(factor: LawnchairWidgetHolder.Factory): WidgetHolderFactory
}

@Module
abstract class PluginManagerWrapperModule {
    @Binds
    abstract fun bindPluginManagerWrapper(impl: PluginManagerWrapperImpl): PluginManagerWrapper
}

@Module
object StaticObjectModule {

    /** withoutQuickstep: no GestureExclusionManager (quickstep-only) */
    /** withoutQuickstep: no ActivityManagerWrapper (non-SDK) */

    @Provides
    @JvmStatic
    fun provideRefreshRateTracker(): RefreshRateTracker = ChoreographerFrameRateTracker
}
