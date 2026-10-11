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

package com.android.systemui.animation.back

import android.view.animation.Interpolator
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import com.android.app.animation.Interpolators

/**
 * Enhanced [OnBackAnimationCallback] with interpolated progress.
 *
 * Simplifies back gesture handling by emitting processed events through `compat` functions.
 * Customize progress interpolation with an optional [Interpolator].
 *
 * @param progressInterpolator [Interpolator] for progress, defaults to
 *   [Interpolators.BACK_GESTURE].
 */
abstract class FlingOnBackAnimationCallback(
    val progressInterpolator: Interpolator = Interpolators.BACK_GESTURE
) : OnBackAnimationCallback {

    private var lastBackEvent: BackEvent? = null

    abstract fun onBackStartedCompat(backEvent: BackEvent)

    abstract fun onBackProgressedCompat(backEvent: BackEvent)

    abstract fun onBackInvokedCompat()

    abstract fun onBackCancelledCompat()

    final override fun onBackStarted(backEvent: BackEvent) {
        reset()
        onBackStartedCompat(backEvent)
    }

    final override fun onBackProgressed(backEvent: BackEvent) {
        val interpolatedProgress = progressInterpolator.getInterpolation(backEvent.progress)
        val event = BackEvent(
            backEvent.touchX,
            backEvent.touchY,
            interpolatedProgress,
            backEvent.swipeEdge,
        )
        lastBackEvent = event
        onBackProgressedCompat(event)
    }

    final override fun onBackInvoked() {
        onBackInvokedCompat()
        reset()
    }

    final override fun onBackCancelled() {
        onBackCancelledCompat()
        reset()
    }

    private fun reset() {
        lastBackEvent = null
    }
}

