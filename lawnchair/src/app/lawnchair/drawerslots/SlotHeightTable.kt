package app.lawnchair.drawerslots

/**
 * Remembers the measured height of each inline slot (by slot key) for scroll estimates. Inline
 * slots share one view type but differ in height, so the per-view-type size cache cannot be used.
 */
class SlotHeightTable {

    private val heights = HashMap<String, Int>()

    /** Records a measured [height]; returns true if it differs from the previous value. */
    @Synchronized
    fun record(key: String, height: Int): Boolean {
        val previous = heights.put(key, height)
        return previous != height
    }

    /**
     * Best known height for [key]: the recorded one, else [viewMeasuredHeight] if the view was
     * measured before, else [fallback] (e.g. one app row) for a slot that was never laid out.
     */
    @Synchronized
    fun estimate(key: String, viewMeasuredHeight: Int, fallback: Int): Int = heights[key] ?: viewMeasuredHeight.takeIf { it > 0 } ?: fallback

    @Synchronized
    fun clear() = heights.clear()

    /** The recorded height for [key], or null. */
    @Synchronized
    fun known(key: String): Int? = heights[key]
}
