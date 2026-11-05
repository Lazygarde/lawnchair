# Drawer Folders (MVP) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Folders inside the paged app drawer: drop an app on another to make one, tap to open a named panel, Google / Microsoft / OEM apps grouped by default.

**Architecture:** Folders are stored in the existing Room tables behind `FolderService` (`Folders`, `FolderItems`), which the A-Z drawer already uses. Their place in the grid is a `folder:<id>` token in the existing `drawerOrder` pref. A pure function, `DrawerOrder.arrange`, turns the saved tokens, the installed apps and the folders into the cells of the grid. The grid gets a folder icon (3×3 preview) and a new floating panel, `DrawerFolderView`. Launcher3's `Folder` / `FolderIcon` are **not** used: they write the home screen's `favorites` table through `ModelWriter`.

**Tech Stack:** Launcher3 views (`PagedView`, `CellLayout`, `DragController`, `AbstractFloatingView`, `PreviewBackground`), Kotlin, Room (`FolderService`), DataStore (`PreferenceManager2`), JUnit in `launcher-ui/src/test`.

**Spec:** this document. Decisions taken with the host app (Holy Quran) on 2026-11-05:

| Question | Decision |
|---|---|
| Scope | MVP. Not built: summed notification badge, panel pages with dots, the ⊕ "add apps" button, reorder inside a folder, dragging a folder to the home screen, dimming the home screen behind a home folder |
| Create | Long press an app, hold it over the middle of another app (or a folder) for 0.4 s: a ring shows; drop to make the folder (or add to it). A new folder opens its panel at once with the title field focused, empty, hint "Folder name" |
| Panel | As `.ovis/paste-20261105-01402*.jpg` / `-014201*.jpg` in holysheet: a big centered editable title on the blur *above* the panel; a rounded panel of fixed height below it, 3 columns, scrolling vertically when long (One UI pages it: Full). The ⊕ is not built. Tap an app launches it. Back or a tap outside closes it |
| Out of a folder | Long press an app in the panel: the panel closes and the app is dragged in the drawer like any other. A folder left with one app dissolves into that app |
| Default folders | Google (incl. Chrome, not Play Store), Microsoft, and the phone maker's own apps (Samsung, Xiaomi, OPPO). Core apps (phone, messages, camera, gallery, clock, ...) stay out. A group needs ≥ 2 apps |
| Who gets them | Once per install. New installs; and on update only users who never rearranged the drawer (`drawerOrder` empty) and have no folder yet |
| Storage | Reuse `FolderService` (Room). This replaces the "JSON pref" sketched in the analysis: the tables, rename and delete already exist |

## Global Constraints

- Only in paged mode (`LawnchairAlphabeticalAppsList.isPagedMode()`). The A-Z drawer and its folders are unchanged.
- This replaces the paged-drawer plan's rule "Drawer folders are ignored in paged mode".
- A folder token is exactly `folder:<id>`, `id` = `FolderInfoEntity.id`. App keys stay `ComponentKey.toString()`.
- `FolderEntry.hide == true` folders are ignored in paged mode; their apps show as plain apps. `FolderEntry.rank` decides only which folder wins an app listed in two; the grid position comes from `drawerOrder`.
- Hidden apps (`hiddenApps`) stay hidden, inside folders too (they are filtered before `arrange`).
- Nothing here writes `favorites`. No `ModelWriter`, no `FolderInfo` reaching `Workspace`.
- The panel is an `AbstractFloatingView` in `DragLayer`, type `TYPE_OPTIONS_POPUP_DIALOG`. It is in `TYPE_ALL` (back, state change close it) and in `TYPE_TOUCH_CONTROLLER_NO_INTERCEPT` (a swipe on it does not close the drawer), and no code looks it up by type with a cast. Never a `Dialog` or an activity.
- Changes under `src/` (upstream Launcher3) carry a `// LC:` comment.
- Unit tests: `./gradlew :launcher-ui:testLawnWithoutQuickstepDebugUnitTest --tests '<pattern>'`.
- Work on branch `feat/drawer-folders` from `origin/16-dev`. Next published lib: **`16.0.0-alpha45`** (alpha42/43 are burnt).
- Local testing in holysheet: `lawnchair = "16.0.0-local"` + `./gradlew :launcher-ui:publishToMavenLocal`. Never commit `-local`.
- Back up the S24 launcher DB before any device test (recipe in holysheet `HANDOFF.md`, "Watch out").

## Review Focus

1. **App uninstalled from a 2-app folder.** The folder becomes the other app, in the folder's cell, not on the last page. Test in Task 1 (`singleMemberFolder_showsAppAtFolderSlot`).
2. **Pull the second-to-last app out.** Folder row deleted and the survivor takes the token's slot in the saved order. Test in Task 1 (`dissolve_replacesToken`), wiring in Task 6.
3. **Same app in two folders** (old A-Z folders, or a race). Shown once, in the first folder by rank. Test in Task 1.
4. **Default grouping swallowing core apps** on a Pixel (`com.google.android.dialer`, `...apps.messaging`) or an S24 (`com.samsung.android.dialer`, `com.sec.android.app.camera`). Test in Task 2.
5. **A rebind (install, Room emission) during a drag.** Deferred by the existing `pendingBind`; the drag must not lose its icon. Manual check in Tasks 5 and 6.

---

### Task 1: `DrawerOrder.arrange` — folders in the order

**Files:**
- Modify: `lawnchair/src/app/lawnchair/allapps/paged/DrawerOrder.kt`
- Test: `launcher-ui/src/test/kotlin/app/lawnchair/allapps/paged/DrawerOrderTest.kt`

**Interfaces:**
- Produces:
  - `DrawerOrder.FOLDER_PREFIX = "folder:"`, `fun folderToken(id: Int): String`, `fun folderIdOf(token: String): Int?`
  - `data class FolderKeys(val id: Int, val keys: List<String>)` (in folder order; list given in rank order)
  - `data class Arranged(val tokens: List<String>, val members: Map<Int, List<String>>)`
  - `fun arrange(saved: List<String>, installed: List<Entry>, folders: List<FolderKeys>): Arranged`
  - `fun dissolve(saved: List<String>, folderId: Int, survivor: String): List<String>`

- [ ] **Step 1: Branch.** `git fetch origin && git switch -c feat/drawer-folders origin/16-dev`

- [ ] **Step 2: Write the failing tests** (`e(key, time)` is the existing helper; `f(id, vararg keys)` = `DrawerOrder.FolderKeys(id, keys.toList())`)

```kotlin
@Test fun folderAtSavedSlot() {
    val r = DrawerOrder.arrange(listOf("a", "folder:1", "d"), listOf(e("a", 1), e("b", 2), e("c", 3), e("d", 4)), listOf(f(1, "b", "c")))
    assertEquals(listOf("a", "folder:1", "d"), r.tokens)
    assertEquals(mapOf(1 to listOf("b", "c")), r.members)
}
@Test fun unsavedFolder_sitsAtEarliestMember() =
    assertEquals(listOf("a", "folder:1", "c"),
        DrawerOrder.arrange(emptyList(), listOf(e("a", 1), e("b", 2), e("c", 3), e("d", 4)), listOf(f(1, "d", "b"))).tokens)
@Test fun singleMemberFolder_showsAppAtFolderSlot() =
    assertEquals(listOf("c", "a"),
        DrawerOrder.arrange(listOf("folder:1", "a"), listOf(e("a", 1), e("c", 2)), listOf(f(1, "b", "c"))).tokens)
@Test fun emptyFolder_dropped() =
    assertEquals(listOf("a"), DrawerOrder.arrange(listOf("folder:1", "a"), listOf(e("a", 1)), listOf(f(1, "x", "y"))).tokens)
@Test fun appInTwoFolders_firstWins() {
    val r = DrawerOrder.arrange(emptyList(), listOf(e("a", 1), e("b", 2), e("c", 3), e("d", 4)), listOf(f(1, "a", "b"), f(2, "b", "c", "d")))
    assertEquals(mapOf(1 to listOf("a", "b"), 2 to listOf("c", "d")), r.members)
}
@Test fun newInstall_afterFolders_appendsAtEnd() =
    assertEquals(listOf("folder:1", "new"),
        DrawerOrder.arrange(listOf("folder:1"), listOf(e("a", 1), e("b", 2), e("new", 0)), listOf(f(1, "a", "b"))).tokens)
@Test fun unknownFolderToken_dropped() =
    assertEquals(listOf("a"), DrawerOrder.arrange(listOf("folder:9", "a"), listOf(e("a", 1)), emptyList()).tokens)
@Test fun dissolve_replacesToken() =
    assertEquals(listOf("a", "b", "d"), DrawerOrder.dissolve(listOf("a", "folder:1", "d"), 1, "b"))
```

- [ ] **Step 3: Run, expect FAIL** — `./gradlew :launcher-ui:testLawnWithoutQuickstepDebugUnitTest --tests '*DrawerOrderTest*'` → unresolved reference `arrange`.

- [ ] **Step 4: Implement `arrange`.** Keep `merge` and `move` as they are (`getAppComparator` still uses `merge`). Algorithm, "first occurrence wins":

```
present  = installed keys
claimed  = {}; for folder in folders (given order): m = folder.keys.distinct().filter { it in present && it !in claimed }; claimed += m
live     = folders with m.size >= 2      single = folders with m.size == 1
seq      = saved entries that are a present app or a live/single folder token (distinct), then
           installed apps not in saved, by install time (the same tail `merge` builds)
walk seq, emit each item at most once:
  app claimed by a live folder  -> emit that folder's token
  token of a live folder        -> emit it
  token of a single folder      -> emit its one member
  any other app                 -> emit it
```
`members` holds only live folders. `dissolve` replaces the first `folder:<id>` with `survivor` and drops any other occurrence of `survivor`.

- [ ] **Step 5: Run, expect PASS** (the same command; the old `DrawerOrderTest` cases still pass).

- [ ] **Step 6: Commit** — `git add -A lawnchair/src/app/lawnchair/allapps/paged/DrawerOrder.kt launcher-ui/src/test docs/drawer-folders-plan.md && git commit -m "Drawer order: folder tokens (arrange, dissolve)"`

---

### Task 2: `DefaultDrawerFolders.group`

**Files:**
- Create: `lawnchair/src/app/lawnchair/allapps/paged/DefaultDrawerFolders.kt`
- Test: `launcher-ui/src/test/kotlin/app/lawnchair/allapps/paged/DefaultDrawerFoldersTest.kt`

**Interfaces:**
- Produces: `object DefaultDrawerFolders { data class Group(val title: String, val keys: List<String>); fun group(keys: List<String>, manufacturer: String): List<Group> }`. `keys` in drawer order; package = `key.substringBefore('/')`; `manufacturer` compared lowercase.

Rules (titles are brand names, not translated; output order Google, Microsoft, maker; a group with < 2 keys is dropped):

| Title | Package matches | Manufacturer |
|---|---|---|
| `Google` | starts with `com.google.`, or is `com.android.chrome` | any |
| `Microsoft` | starts with `com.microsoft.`, or is `com.linkedin.android` | any |
| `Samsung` | starts with `com.samsung.` or `com.sec.` | `samsung` |
| `Xiaomi` | starts with `com.miui.` or `com.xiaomi.` | `xiaomi` |
| `OPPO` | starts with `com.coloros.` or `com.heytap.` | `oppo` |

Never grouped (core apps). Samsung list matches One UI's own default drawer (`.ovis/paste-20261105-014017*.jpg` in holysheet: Phone, Contacts, Camera, Gallery, Clock, Calendar, Calculator, Members, Store, Notes, Gaming Hub, Tips outside; My Files, Browser, Samsung Messages inside). Google list from the Pixel emulator:
`com.google.android.dialer`, `com.google.android.apps.messaging`, `com.google.android.contacts`, `com.google.android.deskclock`, `com.google.android.calendar`, `com.google.android.GoogleCamera`, `com.google.android.documentsui`, `com.samsung.android.dialer`, `com.samsung.android.app.contacts`, `com.samsung.android.calendar`, `com.sec.android.app.camera`, `com.sec.android.gallery3d`, `com.sec.android.app.clockpackage`, `com.sec.android.app.popupcalculator`, `com.samsung.android.voc`, `com.sec.android.app.samsungapps`, `com.samsung.android.app.notes`, `com.samsung.android.game.gamehome`, `com.samsung.android.app.tips`, `com.miui.gallery`, `com.coloros.gallery3d`, `com.coloros.filemanager`.

- [ ] **Step 1: Write the failing tests** (`k(pkg) = "$pkg/.Main#UserHandle{0}"`)

```kotlin
@Test fun google_includesChrome_notPlayStore() = assertEquals(
    listOf(DefaultDrawerFolders.Group("Google", listOf(k("com.google.android.gm"), k("com.android.chrome")))),
    DefaultDrawerFolders.group(listOf(k("com.google.android.gm"), k("com.android.chrome"), k("com.android.vending")), "Google"))
@Test fun coreAppsStayOut_pixel() = assertEquals(emptyList<DefaultDrawerFolders.Group>(),
    DefaultDrawerFolders.group(listOf(k("com.google.android.dialer"), k("com.google.android.apps.messaging"), k("com.google.android.gm")), "Google"))
@Test fun samsung_onlyOnSamsung_coreOut() {
    val keys = listOf(k("com.sec.android.app.myfiles"), k("com.samsung.android.messaging"), k("com.sec.android.app.camera"), k("com.samsung.android.voc"))
    assertEquals(listOf(DefaultDrawerFolders.Group("Samsung", keys.take(2))), DefaultDrawerFolders.group(keys, "samsung"))
    assertEquals(emptyList<DefaultDrawerFolders.Group>(), DefaultDrawerFolders.group(keys, "Google"))
}
@Test fun microsoft() = assertEquals(listOf("Microsoft"),
    DefaultDrawerFolders.group(listOf(k("com.microsoft.office.outlook"), k("com.linkedin.android")), "samsung").map { it.title })
@Test fun singleMatch_noFolder() = assertEquals(emptyList<DefaultDrawerFolders.Group>(),
    DefaultDrawerFolders.group(listOf(k("com.microsoft.office.outlook")), "samsung"))
@Test fun manufacturerIsCaseInsensitive() = assertEquals(listOf("Xiaomi"),
    DefaultDrawerFolders.group(listOf(k("com.miui.notes"), k("com.xiaomi.scanner")), "XIAOMI").map { it.title })
```

- [ ] **Step 2: Run, expect FAIL** — `--tests '*DefaultDrawerFoldersTest*'` → unresolved reference.
- [ ] **Step 3: Implement** with the table above as data (a list of `(title, prefixes, manufacturer?)` and one `CORE` set). `// ponytail: prefix lists checked on an S24 and a Pixel emulator only; Xiaomi/OPPO core lists are best guesses, extend from a real device.`
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `git commit -m "Drawer: default folder groups (Google, Microsoft, maker)"`

---

### Task 3: Cells, folder icon, default grouping on first run

The grid shows folders; a tap does nothing yet. No drag changes.

**Files:**
- Create: `lawnchair/src/app/lawnchair/allapps/paged/DrawerCell.kt`
- Create: `lawnchair/src/app/lawnchair/allapps/paged/DrawerFolderIcon.kt`
- Create: `lawnchair/res/layout/drawer_folder_icon.xml` (copy of `res/layout/all_apps_icon.xml`, root class `app.lawnchair.allapps.paged.DrawerFolderIcon`)
- Modify: `lawnchair/src/app/lawnchair/allapps/LawnchairAlphabeticalAppsList.kt` (pref collect ~l.86, `onAdapterItemsBuilt` ~l.212, `onPagedAppsChanged` l.68, `pagedApps()`)
- Modify: `lawnchair/src/app/lawnchair/allapps/paged/AllAppsPagedGrid.kt` (`bind`, `rebuild`, `newIcon`, `keyOf`, `onDragEnter`)
- Modify: `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java:994-1000`
- Modify: `lawnchair/src/app/lawnchair/preferences2/PreferenceManager2.kt` (next to `drawerOrder`, l.324)

**Interfaces:**
- Consumes: `DrawerOrder.arrange`, `folderToken`, `folderIdOf` (Task 1); `DefaultDrawerFolders.group` (Task 2); `FolderService.INSTANCE.get(context)`: `getFoldersFlow()`, `saveFolderInfo(title): Long`, `updateFolderWithItems(id: Int, title, keys)`.
- Produces:
  - `sealed interface DrawerCell { val key: String; data class App(val app: AppInfo) : DrawerCell; data class Folder(val id: Int, val title: String, val apps: List<AppInfo>) : DrawerCell }` — `App.key` = `ComponentKey.toString()`, `Folder.key` = `folderToken(id)`.
  - `LawnchairAlphabeticalAppsList.pagedCells(): List<DrawerCell>`; `onPagedAppsChanged: ((List<DrawerCell>) -> Unit)?` (replaces `pagedApps()`)
  - `AllAppsPagedGrid.bind(cells: List<DrawerCell>)`
  - `DrawerFolderIcon.bind(folder: DrawerCell.Folder)`: tag = `FolderInfo().apply { id = folder.id; title = folder.title; folder.apps.forEach { add(it) } }`, text = title, icon = 3×3 preview
  - `PreferenceManager2.drawerDefaultFoldersDone` (boolean, key `drawer_default_folders_done`, default false)

- [ ] **Step 1: Live folders in the list.** In paged mode, collect `FolderService.INSTANCE.get(context).getFoldersFlow()` on `context.launcher.lifecycleScope`, same shape as the `drawerOrder` collect: store `folders`, set `foldersLoaded = true`, call `onAppsUpdated()`. Leave `observeFolders()` (A-Z) as is.

- [ ] **Step 2: Build cells in `onAdapterItemsBuilt`.** After `pagedApps` is computed: `arrange(savedOrder, pagedApps → Entry, folders.filterNot { it.hide }.map { FolderKeys(it.id, it.itemComponentKeys) })`, map tokens to `DrawerCell` (apps by key from `pagedApps`; folder apps from `members`), store in `pagedCells`, invoke `onPagedAppsChanged`.

- [ ] **Step 3: Default grouping, once.** At the end of Step 2, when `foldersLoaded`, the order pref has emitted, `appsStore.apps.isNotEmpty()` and an in-memory guard is unset: set the guard; launch on `lifecycleScope`: if `!drawerDefaultFoldersDone.get()` → set it true; then only if `savedOrder.isEmpty() && folders.isEmpty()`, for each `DefaultDrawerFolders.group(pagedApps keys, Build.MANUFACTURER)`: `val id = saveFolderInfo(title).toInt(); updateFolderWithItems(id, title, keys)`. The order pref is not written: an unsaved folder sits at its earliest member (Task 1).

- [ ] **Step 4: Grid binds cells.** `bind(cells)` replaces `bind(apps)`; `rebuild` creates `newIcon(app)` or `newFolderIcon(folder)`; `icons`, `shownKeys`, `dragOrder` use `DrawerCell.key`. Split the white label / shadow / long-press-timeout setup out of `newIcon` so both icon kinds share it. `keyOf(info)` returns `folderToken(info.id)` for a `FolderInfo`. Folder icon click and long click: unset in this task.

- [ ] **Step 5: `DrawerFolderIcon`.** `class DrawerFolderIcon(context: Context, attrs: AttributeSet?) : BubbleTextView(context, attrs)`. Preview bitmap, `allAppsProfile.iconSizePx` square: rounded rect (radius 30 % of size, fill `0x40FFFFFF`), then the first 9 apps' `bitmap.icon` in a 3×3 grid, inset 14 %, gap 4 %. Set with `setIcon(BitmapInfo.fromBitmap(preview).newIcon(context))` (`setIcon` is protected, which is why this is a subclass). Its tag is a `FolderInfo` with no component, so `AllAppsStore.updateNotificationDots` skips it (`PackageUserKey.updateFromItemInfo` is false) and `updateProgressBar` never matches it.

- [ ] **Step 6: Container.** `ActivityAllAppsContainerView` l.994-1000: `setOnPagedAppsChanged(cells -> { mPagedGrid.bind(cells); ... })` and `mPagedGrid.bind(list.pagedCells())`. `// LC:` comment.

- [ ] **Step 7: Verify on device.** Holysheet with `16.0.0-local`; S24 (DB backed up), then `pm clear` on the emulator:
  - fresh install → `Google` folder (+ `Samsung`, `Microsoft` on the S24) at the earliest member's cell; Phone, Messages, Camera, Gallery, Play Store outside;
  - an existing S24 install that has a custom drawer order → no folder appears;
  - search still finds Gmail (inside a folder).
- [ ] **Step 8: Commit** — `git commit -m "Paged drawer: folder cells, 3x3 folder icon, default folders once"`

---

### Task 4: The folder panel

**Files:**
- Create: `lawnchair/src/app/lawnchair/allapps/paged/DrawerFolderView.kt`
- Create: `lawnchair/res/layout/drawer_folder_view.xml`
- Modify: `lawnchair/res/values/strings.xml` (`drawer_folder_name_hint` = "Folder name")
- Modify: `AllAppsPagedGrid.kt` (folder icon click)

**Interfaces:**
- Consumes: `DrawerCell.Folder`; `FolderService.renameFolderInfo(id, title)`.
- Produces: `class DrawerFolderView : AbstractFloatingView` with `companion object { fun show(launcher: Launcher, grid: AllAppsPagedGrid, folder: DrawerCell.Folder, editTitle: Boolean): DrawerFolderView }`.

- [ ] **Step 1: Layout** (match the screenshots). Full-size transparent root; a touch outside title and panel closes. Title: `EditText`, centered, 40 sp, bold, white, at 17 % of the screen height, single line, `imeOptions=actionDone`, hint "Folder name" (new string `drawer_folder_name_hint`) in 50 % white, no underline. Panel: top at 37 %, bottom at 92 % of the screen height, side margins 10 % of the width, corner 40 dp, fill `0x66303030`. Inside: `RecyclerView` with `GridLayoutManager(3)`, 36 dp top padding, scrolls when longer. Items: `BubbleTextView` inflated from `all_apps_icon`, `applyFromApplicationInfo`, white label, click = `activityContext.itemOnClickListener` then close.
- [ ] **Step 2: Floating view.** `isOfType(type) = type and TYPE_OPTIONS_POPUP_DIALOG != 0`. Added to `launcher.dragLayer`; fade + scale 0.9→1 in 150 ms. `onControllerInterceptTouchEvent`: `ACTION_DOWN` outside the panel → `close(true)`, return true. `handleClose`: hide the keyboard; if the trimmed title changed, `renameFolderInfo(id, title)` on `launcher.lifecycleScope`; fade out and remove.
- [ ] **Step 3: `editTitle`.** When true, focus the title and show the keyboard (`WindowInsetsControllerCompat.show(ime())`). IME "done" hides the keyboard only.
- [ ] **Step 4: Wire the tap.** Folder icon click → `DrawerFolderView.show(launcher, this, folder, editTitle = false)`.
- [ ] **Step 5: Verify on device.** Tap Google → panel over the blur with its apps; Gmail launches; rename to "G" → the grid label reads "G" after close; back closes the panel and not the drawer; a vertical swipe on the panel does not close the drawer; 20+ apps scroll.
- [ ] **Step 6: Commit** — `git commit -m "Paged drawer: folder panel with editable title"`

---

### Task 5: Create a folder by drag

**Files:**
- Modify: `lawnchair/src/app/lawnchair/allapps/paged/PagedGridMath.kt`, test `PagedGridMathTest.kt`
- Modify: `AllAppsPagedGrid.kt` (`onDragOver`, `onDragExit`, `onDrop`, folder long click)
- Modify: `lawnchair/src/app/lawnchair/allapps/paged/AddToHomeBar.kt:42` (`onDragStart`)

**Interfaces:**
- Consumes: `FolderService.saveFolderInfo`, `updateFolderWithItems`; `DrawerFolderView.show` (Task 4); `DrawerOrder.folderToken` (Task 1).
- Produces: `PagedGridMath.isInCore(x: Float, y: Float, cx: Float, cy: Float, radius: Float): Boolean`.

- [ ] **Step 1: Failing tests** in `PagedGridMathTest`:

```kotlin
@Test fun core_inside() = assertTrue(PagedGridMath.isInCore(105f, 100f, 100f, 100f, 10f))
@Test fun core_edgeCounts() = assertTrue(PagedGridMath.isInCore(110f, 100f, 100f, 100f, 10f))
@Test fun core_outsideDiagonal() = assertFalse(PagedGridMath.isInCore(108f, 108f, 100f, 100f, 10f))
```
- [ ] **Step 2: Run, expect FAIL; implement (`hypot ≤ radius`); run, expect PASS.**
- [ ] **Step 3: Merge state in `onDragOver`.** After the cell index is known: target = `dragOrder[index]`. If the dragged item is an `AppInfo`, target ≠ dragged key, and `isInCore(pointer, target icon center, 0.55f * allAppsProfile.iconSizePx)` (center = `getIconBounds` center mapped to page coords with `Utilities.getDescendantCoordRelativeToAncestor`): cancel `reorderAlarm`, set `mergeKey = target`, arm `mergeAlarm` (400 ms) which sets `mergeReady = true` and shows the ring: `PreviewBackground().apply { setup(context, activityContext, null, icon.measuredWidth, icon.paddingTop); isClipping = false }.animateToAccept(page, cellX, cellY)`. Leaving the core or changing target: cancel `mergeAlarm`, `animateToRest()`, clear `mergeKey`/`mergeReady`, then the reorder logic runs as today. `onDragExit` also clears it.
- [ ] **Step 4: Drop.** If `mergeReady`, instead of the reorder:
  - target is an app `T`, dragged `D` → on `launcher.lifecycleScope`: `id = saveFolderInfo("").toInt()`; `updateFolderWithItems(id, "", listOf(T, D))`; `save(order without D, with T replaced by folderToken(id))`; then on main `DrawerFolderView.show(launcher, this, Folder(id, "", [T app, D app]), editTitle = true)`.
  - target is a folder `F` → `updateFolderWithItems(F.id, F.title, F keys + D)`; `save(order without D)`.
  - the drag view animates into the target icon (`animateViewIntoPosition(dragView, targetIcon, null)`); the grid rebuilds from the Room emission (deferred by `pendingBind` if it lands mid-drag).
  - If the two Room emissions flash a 1-app state, wrap `saveFolderInfo` + `updateFolderWithItems` in one `AppDatabase.withTransaction`.
- [ ] **Step 5: Folders drag only inside the drawer.** Folder icon long click → `if (ItemLongClickListener.canStartDrag(launcher)) launcher.workspace.beginDragShared(v, this, DragOptions().apply { fromPagedDrawer = true })`; no popup. `AddToHomeBar.onDragStart`: show only when `options.fromPagedDrawer && dragObject.dragInfo is AppInfo` — a `FolderInfo` must never reach `SPRING_LOADED` / `Workspace.onDropExternal`.
- [ ] **Step 6: Verify on device.** Hold Notes over Wearable 0.4 s → ring → drop → panel opens, keyboard up, empty title; type "Tools", back → folder "Tools" in Wearable's cell. Hold a third app on "Tools" → added. Pass quickly over icons → reorder as before, no ring. Drag a folder → reorders; the "Add to Home" bar does not appear. Install an app mid-drag (adb) → the drag survives.
- [ ] **Step 7: Commit** — `git commit -m "Paged drawer: drop an app on another to make a folder"`

---

### Task 6: Drag an app out of a folder

**Files:**
- Modify: `AllAppsPagedGrid.kt` (new `pullOut`), `DrawerFolderView.kt` (item long click)

**Interfaces:**
- Consumes: `DrawerOrder.dissolve` (Task 1); `FolderService.updateFolderWithItems`, `deleteFolderInfo`.
- Produces: `AllAppsPagedGrid.pullOut(folderId: Int, key: String): BubbleTextView?` — the new icon of the app, laid out, or null when the folder is gone.

Order matters; do it exactly so:

- [ ] **Step 1: `pullOut`, in memory first.** Replace the folder cell in the grid's own `cells` with `[App(key), Folder(rest)]` (the app takes the folder's cell, so its icon is on the current page), or `[App(key), App(survivor)]` when one app is left; `rebuild()` synchronously; return `icons[key]`.
- [ ] **Step 2: Persist.** On `launcher.lifecycleScope`: ≥ 2 left → `updateFolderWithItems(id, title, rest)` and `save(shownKeys)`; 1 left → `deleteFolderInfo(id)` and `save(dissolve(shownKeys-with-token, id, survivor))` (the app stays before it). The later Room / pref emissions produce the same keys, so `bind` does nothing.
- [ ] **Step 3: Long press in the panel.** `DrawerFolderView` sets its own long-click on items (not `allAppsItemLongClickListener`): if `ItemLongClickListener.canStartDrag(launcher)`: register a `DragController.DragListener` whose `onDragStart` calls `close(false)` and removes itself; `val icon = grid.pullOut(id, key) ?: return`; `launcher.workspace.beginDragShared(icon, grid, DragOptions().apply { fromPagedDrawer = true })`.
- [ ] **Step 4: Verify on device.** Pull Gmail out of a 3-app folder → panel closes, Gmail follows the finger, drop on page 2 → stays there, folder has 2. Pull one of the last 2 → folder gone, survivor in the folder's cell. Pull then release at once → the app sits in the folder's old cell. Hold it on "Add to Home" → goes to the home screen (an app, so allowed). Restart the launcher → state kept.
- [ ] **Step 5: Commit** — `git commit -m "Paged drawer: drag an app out of a folder"`

---

### Task 7: Unit suite, publish, host app

- [ ] **Step 1:** `./gradlew :launcher-ui:testLawnWithoutQuickstepDebugUnitTest` → all pass (81 + the new ones).
- [ ] **Step 2:** PR `feat/drawer-folders` → `16-dev`; after merge, `gh workflow run publish_packages.yml --ref 16-dev -f version=16.0.0-alpha45`, then `gh release create lib-v16.0.0-alpha45 --prerelease` with notes.
- [ ] **Step 3:** In holysheet: `lawnchair = "16.0.0-alpha45"`, bump version, `docs/releases/v<version>.md`, `verify-ad-ids.sh debug` and `release`. Ask the user before any prod upload or tag (holysheet `CLAUDE.md`, "Release tags").
