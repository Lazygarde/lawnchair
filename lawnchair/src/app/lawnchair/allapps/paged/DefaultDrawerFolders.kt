package app.lawnchair.allapps.paged

/**
 * The folders a paged drawer starts with: the phone maker's own apps, Google and Microsoft, as
 * One UI groups them. Core apps (phone, camera, gallery, ...) stay out, and so does a group of one.
 */
object DefaultDrawerFolders {

    data class Group(val title: String, val keys: List<String>)

    private class Rule(val title: String, val manufacturer: String?, val prefixes: List<String>, val packages: Set<String> = emptySet())

    // In output order: the maker's folder first, as One UI (Samsung, Google, Microsoft).
    private val RULES = listOf(
        Rule("Samsung", "samsung", listOf("com.samsung.", "com.sec.")),
        Rule("Xiaomi", "xiaomi", listOf("com.miui.", "com.xiaomi.")),
        Rule("OPPO", "oppo", listOf("com.coloros.", "com.heytap.")),
        Rule("Google", null, listOf("com.google."), setOf("com.android.chrome")),
        Rule("Microsoft", null, listOf("com.microsoft."), setOf("com.linkedin.android", "com.touchtype.swiftkey")),
    )

    // ponytail: checked on an S24 (as One UI's own drawer) and a Pixel emulator only; the Xiaomi and
    // OPPO entries are best guesses, extend them from a real device.
    private val CORE = setOf(
        "com.google.android.dialer", "com.google.android.apps.messaging", "com.google.android.contacts",
        "com.google.android.deskclock", "com.google.android.calendar", "com.google.android.GoogleCamera",
        "com.google.android.documentsui",
        "com.samsung.android.dialer", "com.samsung.android.app.contacts", "com.samsung.android.calendar",
        "com.sec.android.app.camera", "com.sec.android.gallery3d", "com.sec.android.app.clockpackage",
        "com.sec.android.app.popupcalculator", "com.samsung.android.voc", "com.sec.android.app.samsungapps",
        "com.samsung.android.app.notes", "com.samsung.android.game.gamehome", "com.samsung.android.app.tips",
        "com.miui.gallery", "com.coloros.gallery3d", "com.coloros.filemanager",
    )

    /** [keys] are `ComponentKey.toString()` in drawer order; [manufacturer] is `Build.MANUFACTURER`. */
    fun group(keys: List<String>, manufacturer: String): List<Group> {
        val maker = manufacturer.lowercase()
        val taken = HashSet<String>()
        return RULES.filter { it.manufacturer == null || it.manufacturer == maker }.mapNotNull { rule ->
            val matched = keys.filter { key ->
                val pkg = key.substringBefore('/')
                pkg !in CORE && key !in taken &&
                    (pkg in rule.packages || rule.prefixes.any(pkg::startsWith))
            }
            taken += matched
            if (matched.size >= 2) Group(rule.title, matched) else null
        }
    }
}
