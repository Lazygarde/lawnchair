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
 * limitations under the License.
 */
// withoutQuickstep variant: stub — no RecentsView.
package com.android.launcher3.uioverrides.states;

import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_OVERVIEW;

import com.android.launcher3.Launcher;
import com.android.launcher3.views.ActivityContext;

/**
 * Overview modal task state stub — withoutQuickstep variant.
 */
public class OverviewModalTaskState extends OverviewState {

    private static final int STATE_FLAGS = FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED
            | FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI | FLAG_WORKSPACE_INACCESSIBLE;

    public OverviewModalTaskState(int id) {
        super(id, LAUNCHER_STATE_OVERVIEW, STATE_FLAGS);
    }

    @Override
    public int getTransitionDuration(ActivityContext context, boolean isToState) {
        return isToState ? 200 : 200;
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return OVERVIEW_ACTIONS;
    }

    @Override
    public boolean displayOverviewTasksAsGrid(com.android.launcher3.DeviceProfile deviceProfile) {
        return false;
    }
}
