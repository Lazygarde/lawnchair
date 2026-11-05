package app.lawnchair.allapps.paged

/**
 * Order of the paged app drawer. Keys are `ComponentKey.toString()`.
 *
 * The saved list is the user's order once they have moved an app; apps not in it (never moved,
 * or installed since) follow it, oldest install first, so a new install lands in the last cell.
 *
 * A folder sits in the saved list as [folderToken]; its apps are not shown on their own.
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

    const val FOLDER_PREFIX = "folder:"

    fun folderToken(id: Int): String = "$FOLDER_PREFIX$id"

    fun folderIdOf(token: String): Int? =
        if (token.startsWith(FOLDER_PREFIX)) token.substring(FOLDER_PREFIX.length).toIntOrNull() else null

    /** A folder's app keys, in the folder's order. */
    data class FolderKeys(val id: Int, val keys: List<String>)

    /** What the grid shows: app keys and folder tokens, and the apps of each folder shown. */
    data class Arranged(val tokens: List<String>, val members: Map<Int, List<String>>)

    /**
     * The grid's cells. [folders] come in rank order: an app listed in two folders belongs to the
     * first. A folder with two installed apps or more is shown; with one, that app is shown in the
     * folder's place; with none, nothing. A folder not in [saved] (just made, or made by default)
     * sits where its earliest app would.
     */
    fun arrange(saved: List<String>, installed: List<Entry>, folders: List<FolderKeys>): Arranged {
        val present = installed.mapTo(HashSet()) { it.key }
        val claimed = HashSet<String>()
        val members = LinkedHashMap<Int, List<String>>()
        folders.forEach { folder ->
            members[folder.id] = folder.keys.distinct().filter { it in present && claimed.add(it) }
        }
        val live = members.filterValues { it.size >= 2 }
        val owner = HashMap<String, Int>().apply { live.forEach { (id, keys) -> keys.forEach { put(it, id) } } }

        val head = saved.filter { it in present || folderIdOf(it)?.let { id -> members[id]?.isNotEmpty() } == true }
        // The saved head, tokens included, then the apps merge appends (it drops tokens: not installed).
        val seq = LinkedHashSet(head).apply { addAll(merge(head, installed)) }
        // Each cell once: a folder lands at the first of its token and its apps.
        val result = LinkedHashSet<String>()
        for (item in seq) {
            val id = folderIdOf(item)
            when {
                id == null && item in owner -> result.add(folderToken(owner.getValue(item)))
                id == null -> result.add(item)
                id in live -> result.add(item)
                else -> members[id]?.singleOrNull()?.let(result::add)
            }
        }
        return Arranged(result.toList(), live)
    }

    /** [saved] with folder [folderId] replaced by [survivor], its last app. */
    fun dissolve(saved: List<String>, folderId: Int, survivor: String): List<String> {
        val token = folderToken(folderId)
        return saved.filter { it != survivor }.map { if (it == token) survivor else it }
    }

    fun move(order: List<String>, key: String, toIndex: Int): List<String> {
        if (key !in order) return order
        val result = order.toMutableList().apply { remove(key) }
        result.add(toIndex.coerceIn(0, result.size), key)
        return result
    }
}
