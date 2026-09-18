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
 *
 * Modifications copyright 2025, Lawnchair
 */

package app.lawnchair

import android.content.Context
import androidx.annotation.Keep
import app.lawnchair.bugreport.LawnchairBugReporter
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.MainProcessInitializer
import com.android.launcher3.Utilities
import com.android.launcher3.icons.mono.ThemedIconDrawable

@Keep
class LawnchairProcessInitializer(context: Context) : MainProcessInitializer() {

    override fun init(context: Context) {
        LawnchairBugReporter.INSTANCE.get(context)
        ThemedIconDrawable.COLORS_LOADER = {
            if (Utilities.isDarkTheme(it)) {
                intArrayOf(
                    ColorTokens.Accent2_800.resolveColor(it),
                    ColorTokens.Accent1_200.resolveColor(it),
                )
            } else {
                intArrayOf(
                    ColorTokens.Accent1_100.resolveColor(it),
                    ColorTokens.Accent1_700.resolveColor(it),
                )
            }
        }
        super.init(context)
    }
}
