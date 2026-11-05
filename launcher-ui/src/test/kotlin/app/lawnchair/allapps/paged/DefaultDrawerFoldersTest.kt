package app.lawnchair.allapps.paged

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultDrawerFoldersTest {

    private fun k(pkg: String) = "$pkg/.Main#UserHandle{0}"
    private val none = emptyList<DefaultDrawerFolders.Group>()

    @Test
    fun google_includesChrome_notPlayStore() = assertEquals(
        listOf(DefaultDrawerFolders.Group("Google", listOf(k("com.google.android.gm"), k("com.android.chrome")))),
        DefaultDrawerFolders.group(listOf(k("com.google.android.gm"), k("com.android.chrome"), k("com.android.vending")), "Google"),
    )

    @Test
    fun coreAppsStayOut_pixel() = assertEquals(
        none,
        DefaultDrawerFolders.group(listOf(k("com.google.android.dialer"), k("com.google.android.apps.messaging"), k("com.google.android.gm")), "Google"),
    )

    @Test
    fun samsung_onlyOnSamsung_coreOut() {
        val keys = listOf(k("com.sec.android.app.myfiles"), k("com.samsung.android.messaging"), k("com.sec.android.app.camera"), k("com.samsung.android.voc"))
        assertEquals(listOf(DefaultDrawerFolders.Group("Samsung", keys.take(2))), DefaultDrawerFolders.group(keys, "samsung"))
        assertEquals(none, DefaultDrawerFolders.group(keys, "Google"))
    }

    @Test
    fun microsoft_includesLinkedIn() = assertEquals(
        listOf("Microsoft"),
        DefaultDrawerFolders.group(listOf(k("com.microsoft.office.outlook"), k("com.linkedin.android")), "samsung").map { it.title },
    )

    @Test
    fun singleMatch_noFolder() = assertEquals(none, DefaultDrawerFolders.group(listOf(k("com.microsoft.office.outlook")), "samsung"))

    @Test
    fun manufacturerIsCaseInsensitive() = assertEquals(
        listOf("Xiaomi"),
        DefaultDrawerFolders.group(listOf(k("com.miui.notes"), k("com.xiaomi.scanner")), "XIAOMI").map { it.title },
    )

    @Test
    fun order_googleMicrosoftMaker() = assertEquals(
        listOf("Google", "Microsoft", "Samsung"),
        DefaultDrawerFolders.group(
            listOf(k("com.samsung.android.oneconnect"), k("com.sec.android.app.shealth"), k("com.microsoft.skydrive"), k("com.microsoft.office.onenote"), k("com.google.android.youtube"), k("com.google.android.gm")),
            "samsung",
        ).map { it.title },
    )
}
