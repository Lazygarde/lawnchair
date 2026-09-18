/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import static android.view.CrossWindowBlurListeners.CROSS_WINDOW_BLUR_SUPPORTED;

import android.app.ActivityManager;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

public abstract class BlurUtils {

    private static final String TAG = "BlurUtils";

    // LC-Note: every symbol the real check needs is non-SDK -- the
    // CrossWindowBlurListeners.CROSS_WINDOW_BLUR_SUPPORTED field, ActivityManager.isHighEndGfx()
    // and the whole android.os.SystemProperties class. Without a hidden-API exemption in the
    // process they resolve to NoSuchFieldError / NoSuchMethodError / NoClassDefFoundError, which
    // are Errors rather than Exceptions, so a plain `catch (Exception)` at the call site does not
    // stop them. Probe once behind a Throwable guard and cache; blur is a visual nicety, so
    // falling back to "unsupported" is the right answer when the platform will not tell us.
    private static final boolean SUPPORTS_BLUR = computeSupportsBlursOnWindows();

    /**
     * If this device can render blurs.
     *
     * @return {@code true} when supported.
     */
    public static boolean supportsBlursOnWindows() {
        return SUPPORTS_BLUR;
    }

    private static boolean computeSupportsBlursOnWindows() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false;
        }
        try {
            return CROSS_WINDOW_BLUR_SUPPORTED
                    && ActivityManager.isHighEndGfx()
                    && !SystemProperties.getBoolean("persist.sysui.disableBlur", false);
        } catch (Throwable t) {
            Log.w(TAG, "Cannot determine cross-window blur support, assuming unsupported", t);
            return false;
        }
    }
}
