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

package com.android.launcher3.uioverrides.flags

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceGroup

/**
 * Stub DevOptionsUiHelper for withoutQuickstep variant.
 * Exists only so res/xml/launcher_preferences.xml can inflate without error.
 * No developer options UI is shown in the withoutQuickstep build.
 */
class DevOptionsUiHelper(c: Context, attr: AttributeSet?) : PreferenceGroup(c, attr) {
    // No-op stub — dev options require quickstep internal APIs.
}
