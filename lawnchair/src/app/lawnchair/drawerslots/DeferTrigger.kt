package app.lawnchair.drawerslots

/**
 * Decides when a deferred slot may create its real view: only once the layout is settled and the
 * slot has been mostly visible for [requiredFrames] consecutive passes, so a header/footer height
 * being applied (which shifts the list) cannot flip the decision.
 */
class DeferTrigger(
    private val requiredFrames: Int = 2,
    private val threshold: Float = 0.5f,
) {
    private var streak = 0
    var fired = false
        private set

    /** Forgets a running streak (e.g. when the drawer closed). */
    fun reset() {
        if (!fired) streak = 0
    }

    /** True while a qualifying streak is running but has not fired yet (more frames are needed). */
    val isPending: Boolean get() = !fired && streak > 0

    /**
     * @param fraction visible fraction of the slot's full reserved height (footer cover applied)
     * @param settled drawer fully open and no header/footer change in flight
     * @return true exactly once, on the pass that completes the streak
     */
    fun onFrame(fraction: Float, settled: Boolean): Boolean {
        if (fired) return false
        if (settled && fraction >= threshold) {
            streak++
            if (streak >= requiredFrames) {
                fired = true
                return true
            }
        } else {
            streak = 0
        }
        return false
    }
}
