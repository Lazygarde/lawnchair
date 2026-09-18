/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.dagger;

import app.lawnchair.icons.ThemeManagerModule;
import dagger.Component;

/**
 * Root component for Dagger injection — withoutQuickstep variant.
 * Extends core LauncherBaseAppComponent instead of QuickstepBaseAppComponent
 * to avoid any dependency on hidden/non-SDK API.
 */
@LauncherAppSingleton
@Component(
    modules = {
        LauncherAppModule.class,
        ThemeManagerModule.class
    }
)
public interface LauncherAppComponent extends LauncherBaseAppComponent {
    /** Builder for withoutQuickstep LauncherAppComponent. */
    @Component.Builder
    interface Builder extends LauncherBaseAppComponent.Builder {
        LauncherAppComponent build();
    }
}
