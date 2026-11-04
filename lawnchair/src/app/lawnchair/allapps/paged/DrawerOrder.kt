package app.lawnchair.allapps.paged

/**
 * Order of the paged app drawer. Keys are `ComponentKey.toString()`.
 *
 * The saved list is the user's order once they have moved an app; apps not in it (never moved,
 * or installed since) follow it, oldest install first, so a new install lands in the last cell.
 */
object DrawerOrder {

    data class Entry(val key: String, val installTime: Long, val label: String)

    // 0 means unknown (a promise app still installing): newest, so it goes last.
    private val byInstall = compareBy<Entry> { if (it.installTime == 0L) Long.MAX_VALUE else it.installTime }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        .thenBy { it.key }

    fun merge(saved: List<String>, installed: List<Entry>): List<String> {
        val present = installed.mapTo(HashSet()) { it.key }
        val head = LinkedHashSet<String>().apply { saved.filterTo(this) { it in present } }
        return head.toList() + installed.filter { it.key !in head }.sortedWith(byInstall).map { it.key }
    }

    fun move(order: List<String>, key: String, toIndex: Int): List<String> {
        if (key !in order) return order
        val result = order.toMutableList().apply { remove(key) }
        result.add(toIndex.coerceIn(0, result.size), key)
        return result
    }
}
