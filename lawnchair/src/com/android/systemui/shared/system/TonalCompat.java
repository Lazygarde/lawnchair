/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.systemui.shared.system;

import android.app.WallpaperColors;
import android.content.Context;

import com.android.internal.colorextraction.ColorExtractor.GradientColors;
import com.android.internal.colorextraction.types.Tonal;
import com.android.launcher3.Utilities;

public class TonalCompat {

    private final Object mTonal;

    public TonalCompat(Context context) {
        Object tonal = null;
        if (Utilities.ATLEAST_S) {
            try {
                tonal = new Tonal(context);
            } catch (Throwable ignored) {
                // LC-Ignored: Tonal is a non-SDK internal class
            }
        }
        mTonal = tonal;
    }

    public ExtractionInfo extractDarkColors(WallpaperColors colors) {
        if (mTonal != null) {
            try {
                GradientColors darkColors = new GradientColors();
                ((Tonal) mTonal).extractInto(colors, new GradientColors(), darkColors, new GradientColors());

                ExtractionInfo result = new ExtractionInfo();
                result.mainColor = darkColors.getMainColor();
                result.secondaryColor = darkColors.getSecondaryColor();
                result.supportsDarkText = darkColors.supportsDarkText();
                if (colors != null && Utilities.ATLEAST_S) {
                    result.supportsDarkTheme =
                        (colors.getColorHints() & WallpaperColors.HINT_SUPPORTS_DARK_THEME) != 0;
                }
                return result;
            } catch (Throwable ignored) {
                // LC-Ignored: non-SDK internal API
            }
        }
        return new ExtractionInfo();
    }

    public static class ExtractionInfo {
        public int mainColor;
        public int secondaryColor;
        public boolean supportsDarkText;
        public boolean supportsDarkTheme;
    }
}
