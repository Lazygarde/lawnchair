package app.lawnchair.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.InsettableFrameLayout
import com.android.systemui.plugins.shared.LauncherOverlayManager
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlay
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayCallbacks

/**
 * Manages the in-process overlay lifecycle, gesture synchronization with Launcher's Workspace,
 * and snap animations.
 */
class InProcessOverlayManager(
    private val launcher: LawnchairLauncher,
    private val provider: LawnchairOverlayProvider,
) : LauncherOverlayManager,
    LauncherOverlay {

    private var overlayContainer: InProcessOverlayContainer? = null
    private var overlayCallbacks: LauncherOverlayCallbacks? = null
    var currentProgress: Float = 0f
        private set
    private var snapAnimator: ValueAnimator? = null
    private var isDragging: Boolean = false

    init {
        setupContainer()
        launcher.setLauncherOverlay(this)
        provider.onAttachedToLauncher(launcher)
    }

    private fun setupContainer() {
        val container = InProcessOverlayContainer(launcher, this, provider)
        overlayContainer = container

        // Add container directly into LauncherRootView behind DragLayer (index 0)
        val rootView = launcher.rootView ?: launcher.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
        if (rootView != null) {
            val lp = InsettableFrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                ignoreInsets = true
            }
            rootView.addView(container, 0, lp)
        }
    }

    fun isOverlayOpen(): Boolean = currentProgress > 0.05f

    override fun setOverlayCallbacks(callbacks: LauncherOverlayCallbacks) {
        overlayCallbacks = callbacks
    }

    override fun onScrollInteractionBegin() {
        snapAnimator?.cancel()
        isDragging = true
        overlayContainer?.onScrollBegin()
    }

    override fun onScrollChange(progress: Float, rtl: Boolean) {
        val boundedProgress = progress.coerceIn(0f, 1f)
        applyProgress(boundedProgress)
    }

    override fun onScrollInteractionEnd() {
        isDragging = false
        // Determine whether to snap open or close based on current progress threshold
        val target = if (currentProgress >= 0.5f) 1f else 0f
        animateTo(target, 250)
    }

    override fun onFlingVelocity(velocity: Float) {
        if (!isDragging) return
        isDragging = false
        val target = if (velocity > 400f) {
            1f
        } else if (velocity < -400f) {
            0f
        } else {
            (if (currentProgress >= 0.5f) 1f else 0f)
        }
        val duration = (200 - (Math.abs(velocity) / 20f)).toLong().coerceIn(100L, 250L)
        animateTo(target, duration)
    }

    override fun onOverlayMotionEvent(ev: MotionEvent, scrollProgress: Float) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> onScrollInteractionBegin()
            MotionEvent.ACTION_MOVE -> onScrollChange(scrollProgress, false)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onScrollInteractionEnd()
        }
    }

    override fun openOverlay() {
        snapAnimator?.cancel()
        animateTo(1f, 250)
    }

    override fun hideOverlay(duration: Int) {
        snapAnimator?.cancel()
        animateTo(0f, duration.toLong().coerceAtLeast(150L))
    }

    internal fun applyProgress(progress: Float) {
        currentProgress = progress
        overlayContainer?.onScrollProgress(progress)
        provider.onOverlayScroll(progress)
        overlayCallbacks?.onOverlayScrollChanged(progress)
    }

    private fun animateTo(targetProgress: Float, durationMs: Long) {
        snapAnimator?.cancel()
        if (Math.abs(currentProgress - targetProgress) < 0.001f) {
            applyProgress(targetProgress)
            notifyStateFinished(targetProgress)
            return
        }

        val animator = ValueAnimator.ofFloat(currentProgress, targetProgress).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                applyProgress(progress)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    notifyStateFinished(targetProgress)
                }
            })
        }
        snapAnimator = animator
        animator.start()
    }

    private fun notifyStateFinished(progress: Float) {
        if (progress >= 0.999f) {
            provider.onOverlayOpened()
        } else if (progress <= 0.001f) {
            provider.onOverlayClosed()
        }
    }

    override fun onActivityDestroyed() {
        snapAnimator?.cancel()
        launcher.setLauncherOverlay(null)
        overlayContainer?.let { container ->
            val parent = container.parent as? ViewGroup
            parent?.removeView(container)
            container.onDestroy()
        }
        overlayContainer = null
        provider.onDetachedFromLauncher()
    }
}
