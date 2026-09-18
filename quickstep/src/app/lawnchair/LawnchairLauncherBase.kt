/*
 * Copyright 2022, Lawnchair
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

package app.lawnchair

import android.content.Intent
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.views.LawnchairFloatingSurfaceView
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.GestureNavContract
import com.android.launcher3.uioverrides.QuickstepLauncher

/**
 * withQuickstep variant: LawnchairLauncher extends QuickstepLauncher to get
 * gesture navigation / recents integration.
 */
open class LawnchairLauncherBase : QuickstepLauncher() {

    open fun clearStuckBlurOnResumeIfHome() {
        depthController?.clearStuckBlurOnResumeIfHome()
    }

    override fun handleGestureContract(intent: Intent) {
        val prefs = PreferenceManager.getInstance(this)
        if (!LawnchairApp.isRecentsEnabled && prefs.enableGnc.get()) {
            val gnc = GestureNavContract.fromIntent(intent)
            if (gnc != null) {
                AbstractFloatingView.closeOpenViews(
                    this,
                    false,
                    AbstractFloatingView.TYPE_ICON_SURFACE,
                )
                LawnchairFloatingSurfaceView.show(this as LawnchairLauncher, gnc)
            }
        }
    }
}
