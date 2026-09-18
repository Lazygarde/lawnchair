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

import com.android.launcher3.Launcher

/**
 * withoutQuickstep variant: LawnchairLauncher extends core Launcher directly.
 * No quickstep/recents UI — no hidden API dependency.
 * All 14 methods overridden by LawnchairLauncher exist on core Launcher / BaseActivity.
 */
open class LawnchairLauncherBase : Launcher() {
    open fun clearStuckBlurOnResumeIfHome() {
        // No-op in withoutQuickstep (no DepthController)
    }
}
