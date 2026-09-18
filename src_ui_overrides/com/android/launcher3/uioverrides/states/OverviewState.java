/*
 * Copyright (C) 2017 The Android Open Source Project
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
// withoutQuickstep variant: removed RecentsView, TaskView, LayoutUtils, BaseDepthController deps.
// Kept factory methods (newModalTaskState, newSwitchState, newBackgroundState, newSplitSelectState)
// because core LauncherState.java:154-165 calls them.
package com.android.launcher3.uioverrides.states;

import static com.android.app.animation.Interpolators.DECELERATE_2;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_OVERVIEW;

import android.content.Context;
import android.graphics.Rect;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimColors;

import app.lawnchair.preferences.PreferenceManager;
import app.lawnchair.theme.color.tokens.ColorTokens;

/**
 * Definition for overview state — withoutQuickstep variant (no Recents UI).
 */
public class OverviewState extends LauncherState {

    private static final int OVERVIEW_SLIDE_IN_DURATION = 380;
    private static final int OVERVIEW_POP_IN_DURATION = 250;
    private static final int OVERVIEW_EXIT_DURATION = 250;

    protected static final Rect sTempRect = new Rect();

    private static final int STATE_FLAGS = FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED
            | FLAG_DISABLE_RESTORE | FLAG_RECENTS_VIEW_VISIBLE | FLAG_WORKSPACE_INACCESSIBLE
            | FLAG_CLOSE_POPUPS;

    public OverviewState(int id) {
        this(id, STATE_FLAGS);
    }

    protected OverviewState(int id, int stateFlags) {
        this(id, LAUNCHER_STATE_OVERVIEW, stateFlags);
    }

    protected OverviewState(int id, int logContainer, int stateFlags) {
        super(id, logContainer, stateFlags);
    }

    @Override
    public int getTransitionDuration(ActivityContext context, boolean isToState) {
        if (isToState) {
            return DisplayController.getNavigationMode(context.asContext()).hasGestures
                    ? OVERVIEW_SLIDE_IN_DURATION
                    : OVERVIEW_POP_IN_DURATION;
        } else {
            return OVERVIEW_EXIT_DURATION;
        }
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        // No RecentsView in withoutQuickstep — return identity transform.
        return new ScaleAndTranslation(1f, 0, 0);
    }

    @Override
    public float[] getOverviewScaleAndOffset(Launcher launcher) {
        return new float[]{NO_SCALE, NO_OFFSET};
    }

    @Override
    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        return new PageAlphaProvider(DECELERATE_2) {
            @Override
            public float getPageAlpha(int pageIndex) {
                return 0;
            }
        };
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        if (PreferenceManager.getInstance(launcher).getRecentsActionClearAll().get()) {
            return OVERVIEW_ACTIONS;
        }
        int elements = CLEAR_ALL_BUTTON | OVERVIEW_ACTIONS | ADD_DESK_BUTTON;
        DeviceProfile dp = launcher.getDeviceProfile();
        boolean showFloatingSearch;
        if (dp.getDeviceProperties().isPhone()) {
            showFloatingSearch = !dp.getDeviceProperties().isLandscape();
        } else {
            showFloatingSearch = !dp.isTaskbarPresent || isTaskbarStashed(launcher);
        }
        if (showFloatingSearch) {
            elements |= FLOATING_SEARCH_BAR;
        }
        if (launcher.isSplitSelectionActive()) {
            elements &= ~CLEAR_ALL_BUTTON & ~ADD_DESK_BUTTON;
        }
        return elements;
    }

    @Override
    public int getFloatingSearchBarRestingMarginBottom(Launcher launcher) {
        return areElementsVisible(launcher, FLOATING_SEARCH_BAR) ? 0
                : super.getFloatingSearchBarRestingMarginBottom(launcher);
    }

    @Override
    public boolean shouldFloatingSearchBarUsePillWhenUnfocused(Launcher launcher) {
        DeviceProfile dp = launcher.getDeviceProfile();
        return dp.getDeviceProperties().isPhone() && !dp.getDeviceProperties().isLandscape();
    }

    @Override
    public boolean isTaskbarAlignedWithHotseat(Launcher launcher) {
        return false;
    }

    @Override
    public ScrimColors getWorkspaceScrimColor(Launcher launcher) {
        return new ScrimColors(
                /* backgroundColor */ Themes.getAttrColor(launcher,
                        ColorTokens.OverviewScrimOverBlur.resolveColor(launcher)),
                /* foregroundColor */ ColorUtils.compositeColors(
                        Themes.getAttrColor(launcher, R.attr.overviewScrimForegroundPrimary),
                        Themes.getAttrColor(launcher, R.attr.overviewScrimForegroundSecondary)));
    }

    @Override
    public boolean displayOverviewTasksAsGrid(DeviceProfile deviceProfile) {
        return deviceProfile.getDeviceProperties().isTablet();
    }

    @Override
    public boolean disallowTaskbarGlobalDrag() {
        return true;
    }

    @Override
    public boolean allowTaskbarInitialSplitSelection() {
        return true;
    }

    @Override
    public String getDescription(Launcher launcher) {
        return launcher.getString(R.string.accessibility_recent_apps);
    }

    @Override
    public int getTitle() {
        return R.string.accessibility_recent_apps;
    }

    /** Stub — LayoutUtils.getDefaultSwipeHeight not available in withoutQuickstep. */
    public static float getDefaultSwipeHeight(Launcher launcher) {
        return 0f;
    }

    @Override
    protected float getDepthUnchecked(Context context) {
        return 1f;
    }

    @Override
    public void onBackInvoked(Launcher launcher) {
        // No RecentsView — fall back to default launcher back behavior.
        super.onBackInvoked(launcher);
    }

    // Factory methods required by core LauncherState.java:154-165
    public static OverviewState newBackgroundState(int id) {
        return new BackgroundAppState(id);
    }

    public static OverviewState newSwitchState(int id) {
        return new QuickSwitchState(id);
    }

    public static OverviewState newModalTaskState(int id) {
        return new OverviewModalTaskState(id);
    }

    public static OverviewState newSplitSelectState(int id) {
        return new SplitScreenSelectState(id);
    }
}
