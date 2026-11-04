# Paged App Drawer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An opt-in app drawer that shows apps as horizontally swiped pages of a fixed grid, in install order, which the user can rearrange by dragging inside the drawer.

**Architecture:** A new `PagedView<PageIndicatorDots>` of `CellLayout` pages, modelled on `FolderPagedView`, replaces the personal `AllAppsRecyclerView` when `config_drawer_paged` is true. The order is computed by a pure Kotlin object from a saved key list plus each app's `LauncherActivityInfo.getFirstInstallTime()`. Dragging uses the existing `DragController`; the drawer becomes both `DragSource` and `DropTarget`, and hands the drag to the workspace through an "Add to Home" bar, the way `Folder` hands a drag out.

**Tech Stack:** Launcher3 views (`PagedView`, `CellLayout`, `DragController`, `PageIndicatorDots`), Kotlin, DataStore (`PreferenceManager2`), JUnit in `launcher-ui/src/test`.

**Spec:** this document. Decisions taken with the host app (Holy Quran) on 2026-11-03:

| Question | Decision |
|---|---|
| "Tabs" | Horizontal pages of a fixed grid with page dots (One UI style), not category tabs |
| Order | Oldest install first; a new install goes to the last free cell. Once the user moves an app the whole order is theirs, and later installs still append at the end |
| Drag | Long press then drag rearranges inside the drawer; touching the left or right edge flips the page; dragging to the top hands the drag to the home screen, whose top bar then reads Cancel (One UI behaviour). The drawer has no empty pages, so One UI's "+" page dot is not built: dropping past the last app puts it last |
| Grid | Columns = `DeviceProfile.numShownAllAppsColumns`; rows = as many full rows as fit the drawer height (no half rows); apps per page = columns × rows. No new setting |

## Global Constraints

- Off by default: `<bool name="config_drawer_paged">false</bool>` in `launcher-ui/src/main/res/values/host_config.xml`. With it false the drawer is byte-for-byte today's behaviour.
- Paged mode applies only to the personal list when the user has **no work profile and no private space**. Otherwise the vertical list is used, unchanged.
- Search results keep the vertical `SearchRecyclerView`, unchanged.
- Drawer folders (`drawerList`, `FolderViewModel`) are ignored in paged mode: the grid is a flat list of apps.
- Hidden apps (`PreferenceManager2.hiddenApps`) stay hidden.
- Host slots: `HEADER` keeps working (it lives in the floating header). `INLINE` is not inserted in paged mode. `FOOTER`, when enabled, sits below the page dots.
- Any change to a file under `src/` (upstream Launcher3) is marked with a `// LC:` comment, as elsewhere in this repo.
- Saved order is a list of `ComponentKey.toString()` strings. Keys no longer installed are dropped on the next write, never shown.

## Review Focus

1. **Uninstall and update.** An uninstalled app leaves no hole on its page; an updated app keeps its cell (`firstInstallTime` does not change on update). Test in Task 1.
2. **New install after a custom order.** Goes to the very end, not into a gap and not to the front. Test in Task 1.
3. **Rotation / grid change.** `onIdpChanged` recomputes rows and columns; the order is the same, only the pagination moves. Test in Task 3 (`PagedGridMath`).
4. **Dragging the last app off the last page.** The empty page disappears and the current page is clamped. Test in Task 3 (`pageCount(0, n) == 1`, `pageCount(n, n) == 1`).
5. **Drop outside any cell / drag cancelled.** The app returns to where it was and the saved order is not written. Manual check in Task 4.

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `src/com/android/launcher3/model/data/AppInfo.java` | Modify | Carry `firstInstallTime` (set from `LauncherActivityInfo`, copied by the copy constructor) |
| `src/com/android/launcher3/allapps/AlphabeticalAppsList.java` | Modify | Make the comparator overridable (`protected Comparator<AppInfo> getAppComparator()`) |
| `lawnchair/src/app/lawnchair/allapps/paged/DrawerOrder.kt` | Create | Pure ordering: merge saved keys with installed apps, move one key |
| `lawnchair/src/app/lawnchair/allapps/paged/PagedGridMath.kt` | Create | Pure pagination math |
| `lawnchair/src/app/lawnchair/allapps/paged/AllAppsPagedGrid.kt` | Create | The paged view: binding, drag source, drop target, edge flip |
| `lawnchair/src/app/lawnchair/allapps/paged/AddToHomeBar.kt` | Create | Drop target at the top that hands the drag to the workspace |
| `lawnchair/src/app/lawnchair/allapps/LawnchairAlphabeticalAppsList.kt` | Modify | Use `DrawerOrder` in paged mode, skip sections, folders, inline slots |
| `lawnchair/src/app/lawnchair/preferences2/PreferenceManager2.kt` | Modify | `drawerOrder` preference |
| `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java` | Modify | Swap in `AllAppsPagedGrid`; swipe-to-close; header hooks |
| `src/com/android/launcher3/Workspace.java:561` | Modify | Do not go to `SPRING_LOADED` for a drag that starts in the paged drawer |
| `launcher-ui/src/main/res/values/host_config.xml` | Modify | `config_drawer_paged` |
| `launcher-ui/src/test/kotlin/app/lawnchair/allapps/paged/*Test.kt` | Create | Unit tests |
| `docs/paged-drawer-guide.md` | Create | Host-facing: how to turn it on, what changes |

---

### Task 1: Install time and order model

**Files:**
- Modify: `src/com/android/launcher3/model/data/AppInfo.java` (constructor at line 98, copy constructor at line 112)
- Create: `lawnchair/src/app/lawnchair/allapps/paged/DrawerOrder.kt`
- Test: `launcher-ui/src/test/kotlin/app/lawnchair/allapps/paged/DrawerOrderTest.kt`

**Interfaces:**
- Produces:
  - `AppInfo.firstInstallTime: long` (public field, `0` when unknown).
  - `object DrawerOrder { data class Entry(val key: String, val installTime: Long, val label: String); fun merge(saved: List<String>, installed: List<Entry>): List<String>; fun move(order: List<String>, key: String, toIndex: Int): List<String> }`
  - `merge` returns every installed key exactly once: first the saved keys that are still installed, in saved order; then the rest sorted by `installTime` ascending (`0`, which a promise app from `AppInfo(PackageInstallInfo)` carries, counts as newest, so it goes last, not first), ties by `label` (case-insensitive), then by `key`.
  - `move` removes `key` and inserts it at `toIndex` clamped to `0..size-1`; returns `order` unchanged if `key` is absent.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun emptySaved_sortsByInstallTimeOldestFirst() =
    assertEquals(listOf("b", "a", "c"), DrawerOrder.merge(emptyList(), listOf(e("a", 20), e("b", 10), e("c", 30))))
@Test fun tieOnInstallTime_breaksByLabel() =
    assertEquals(listOf("x", "y"), DrawerOrder.merge(emptyList(), listOf(e("y", 5, "Beta"), e("x", 5, "alpha"))))
@Test fun savedOrderWins_newInstallAppendsAtEnd() =
    assertEquals(listOf("c", "a", "new"), DrawerOrder.merge(listOf("c", "a"), listOf(e("a", 1), e("c", 2), e("new", 0))))
@Test fun unknownInstallTime_goesLast() =
    assertEquals(listOf("a", "p"), DrawerOrder.merge(emptyList(), listOf(e("p", 0), e("a", 10))))
@Test fun uninstalledKeyIsDropped() =
    assertEquals(listOf("a"), DrawerOrder.merge(listOf("gone", "a"), listOf(e("a", 1))))
@Test fun move_toFront() = assertEquals(listOf("c", "a", "b"), DrawerOrder.move(listOf("a", "b", "c"), "c", 0))
@Test fun move_clampsPastEnd() = assertEquals(listOf("b", "c", "a"), DrawerOrder.move(listOf("a", "b", "c"), "a", 99))
@Test fun move_unknownKeyIsNoOp() = assertEquals(listOf("a"), DrawerOrder.move(listOf("a"), "z", 0))
// e(key, time, label = key) = DrawerOrder.Entry(key, time, label)
```

Note `savedOrderWins_newInstallAppendsAtEnd`: `new` has the oldest install time yet goes last, because it is not in the saved list.

- [ ] **Step 2: Run, expect FAIL** — `./gradlew :launcher-ui:testLawnWithoutQuickstepDebugUnitTest --tests '*DrawerOrderTest*'` → unresolved reference `DrawerOrder`.
- [ ] **Step 3: Implement `DrawerOrder`**, and in `AppInfo` set `firstInstallTime = info.getFirstInstallTime()` in the `LauncherActivityInfo` constructor and copy it in `AppInfo(AppInfo info)` (`// LC:` comments). `AllAppsStore` keeps copies, so the copy constructor is not optional.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `feat(drawer): install time on AppInfo and DrawerOrder`.

### Task 2: Paged-mode list contents

**Files:**
- Modify: `src/com/android/launcher3/allapps/AlphabeticalAppsList.java:119,128,260-261`
- Modify: `lawnchair/src/app/lawnchair/allapps/LawnchairAlphabeticalAppsList.kt`
- Modify: `lawnchair/src/app/lawnchair/preferences2/PreferenceManager2.kt` (beside `hiddenApps`, line 318)
- Modify: `launcher-ui/src/main/res/values/host_config.xml`

**Interfaces:**
- Consumes: `DrawerOrder.merge`, `AppInfo.firstInstallTime` (Task 1).
- Produces:
  - `PreferenceManager2.drawerOrder` — `stringPreferencesKey("drawer_order")`, value is keys joined by `\n`, default `""`. (A string, not a string set: order matters.)
  - `R.bool.config_drawer_paged`.
  - `LawnchairAlphabeticalAppsList.isPagedMode(): Boolean` — `config_drawer_paged && !isWorkOrPrivateSpace(...) && work/private managers report no profile`.
  - `LawnchairAlphabeticalAppsList.pagedApps(): List<AppInfo>` — the ordered, filtered apps the grid binds.
  - `AlphabeticalAppsList.getAppComparator(): Comparator<AppInfo>` (protected; default returns `mAppNameComparator`).

- [ ] **Step 1:** Replace both `sorted(mAppNameComparator)` at lines 260–261 with `sorted(getAppComparator())` (`// LC:`). With the flag off nothing changes; run `./gradlew :launcher-ui:compileLawnWithoutQuickstepDebugKotlin` → succeeds.
- [ ] **Step 2:** In `LawnchairAlphabeticalAppsList`, collect `drawerOrder` like `hiddenApps` (same `onEach` in `init`, then `onAppsUpdated()`). In paged mode override `getAppComparator()` with a comparator by index in `DrawerOrder.merge(savedOrder, installed)`; `addAppsWithSections` adds plain `AdapterItem.asApp` items with no section names and no folders; `onAdapterItemsBuilt` clears `fastScrollerSections` and returns before inline slots.
- [ ] **Step 3:** Build the launcher app (`./gradlew assembleLawnWithQuickstepDebug` or the variant you use), set `config_drawer_paged` true in a local overlay, open the drawer: still the vertical list (the grid arrives in Task 3), now in install order, no A–Z sections, fast scroller hidden.
- [ ] **Step 4: Commit** — `feat(drawer): install-ordered flat list behind config_drawer_paged`.

### Task 3: The paged grid view

**Files:**
- Create: `lawnchair/src/app/lawnchair/allapps/paged/PagedGridMath.kt`
- Create: `lawnchair/src/app/lawnchair/allapps/paged/AllAppsPagedGrid.kt`
- Modify: `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java` (adapter holder setup; `shouldContainerScroll` at line 615; `setListScrolled` at line 345)
- Test: `launcher-ui/src/test/kotlin/app/lawnchair/allapps/paged/PagedGridMathTest.kt`

**Interfaces:**
- Consumes: `pagedApps()` (Task 2).
- Produces:
  - `object PagedGridMath { fun rows(availableHeightPx: Int, cellHeightPx: Int): Int; fun pageCount(items: Int, perPage: Int): Int; fun pageOf(index: Int, perPage: Int): Int; fun cellOf(index: Int, cols: Int, perPage: Int): Pair<Int, Int> }` — `rows` is `max(1, availableHeightPx / cellHeightPx)`; `pageCount` is at least 1; `cellOf` returns `(x, y)` within its page, row-major.
  - `class AllAppsPagedGrid(context: Context, attrs: AttributeSet?) : PagedView<PageIndicatorDots>` with `fun bind(apps: List<AppInfo>)`, `val columns: Int`, `val rows: Int`. Cell height is `DeviceProfile.allAppsCellHeightPx`; columns `numShownAllAppsColumns`.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun rows_dropsHalfRow() = assertEquals(5, PagedGridMath.rows(availableHeightPx = 1150, cellHeightPx = 200))
@Test fun rows_atLeastOne() = assertEquals(1, PagedGridMath.rows(50, 200))
@Test fun pageCount_emptyIsOnePage() = assertEquals(1, PagedGridMath.pageCount(0, 20))
@Test fun pageCount_exactFit() = assertEquals(1, PagedGridMath.pageCount(20, 20))
@Test fun pageCount_overflow() = assertEquals(2, PagedGridMath.pageCount(21, 20))
@Test fun cellOf_rowMajorOnSecondPage() = assertEquals(1 to 2, PagedGridMath.cellOf(index = 20 + 9, cols = 4, perPage = 20))
```

- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement `PagedGridMath`**, then `AllAppsPagedGrid`: one `CellLayout` per page (`CellLayout.FOLDER` type as `FolderPagedView` does), icons are `BubbleTextView` from `R.layout.all_apps_icon` with the drawer's click and long-click listeners, pages rebuilt in `bind`, current page clamped to `pageCount - 1`. Rows come from the height left under the floating header and above the page dots and footer, recomputed in `onSizeChanged` and on IDP change.
- [ ] **Step 4:** In `ActivityAllAppsContainerView`, when `isPagedMode()` add `AllAppsPagedGrid` as a sibling of the personal holder's recycler and set the recycler `GONE` (`// LC:`). Do not replace or null the recycler: the file reads `mRecyclerView` 51 times, some without a null check (line 647). Then and `onAppsUpdated` calls `bind(pagedApps())`. `shouldContainerScroll` returns true for a vertical swipe on the grid (swipe down closes the drawer from any page; horizontal swipes go to the pager). `setListScrolled` returns false in paged mode.
- [ ] **Step 5: Run tests, expect PASS; on a device:** swipe pages, dots follow, tap launches, swipe down closes, search still shows the vertical results, rotate keeps the order.
- [ ] **Step 6: Commit** — `feat(drawer): paged grid view`.

### Task 4: Rearranging inside the drawer

**Files:**
- Modify: `lawnchair/src/app/lawnchair/allapps/paged/AllAppsPagedGrid.kt`
- Modify: `src/com/android/launcher3/Workspace.java:561-563`
- Modify: `src/com/android/launcher3/touch/ItemLongClickListener.java:177` (`onAllAppsItemLongClick`)

**Interfaces:**
- Consumes: `DrawerOrder.move`, `PreferenceManager2.drawerOrder` (Tasks 1–2), `PagedGridMath.cellOf` (Task 3).
- Produces: `AllAppsPagedGrid` implements `DragSource`, `DropTarget`; `DragOptions.fromPagedDrawer: Boolean` (new field, `// LC:`).

- [ ] **Step 1:** In `onAllAppsItemLongClick` (line 177), when the view's parent is an `AllAppsPagedGrid`, keep `beginDragShared` but pass the grid as the `DragSource` and a `DragOptions` with `fromPagedDrawer = true`. Preview and popup (`preDragCondition`) stay as they are.
- [ ] **Step 2:** In `Workspace.onDragStart`, skip `goToState(SPRING_LOADED)` when `options.fromPagedDrawer` (`// LC:`). The drawer stays open.
- [ ] **Step 3:** Register the grid with `DragController.addDropTarget` while the launcher is in `ALL_APPS`, remove it on leaving. `onDragOver` maps the touch to `(page, x, y)` and calls the page's reorder like `FolderPagedView.realTimeReorder`, debounced with an `Alarm` (150 ms, as `Folder` uses). Within 1 cell width of the left / right edge, flip the page after 500 ms (the workspace's `PagedView` scroll delay). `onDrop` writes `DrawerOrder.move(displayed, key, targetIndex)`, where `displayed` is the full merged order the grid shows (every key, from `DrawerOrder.merge`), not the saved pref: the pref is empty until the first move, and `move` on an empty list does nothing. So the first drop persists the whole order. joined by `\n` to `drawerOrder`. Cancelled drag or drop outside the grid: rebind from the unchanged order, no write.
- [ ] **Step 3b:** The full-screen `Workspace` under the drawer is also a drop target and the default `DragScroller`. `DragController.findDropTarget` asks targets last-registered first (`DragController.java:583`), so the grid wins over its own rect, but a drop on the header gap or the page dots would reach `Workspace.onDrop` and add the app behind the open drawer. `Workspace.isDropEnabled()` returns false, and the workspace does not edge-scroll, while in `ALL_APPS` (`// LC:`).
- [ ] **Step 4: On a device:** drop on the page dots and on the search bar gap (nothing added to home, app back in place), drag within a page, drag across pages by the edge, drop on a gap past the last app (goes to the end), cancel by dropping outside, kill the launcher and reopen (order kept), install a new app (last cell).
- [ ] **Step 5: Commit** — `feat(drawer): rearrange apps by drag in the paged drawer`.

### Task 5: Add to Home

**Files:**
- Create: `lawnchair/src/app/lawnchair/allapps/paged/AddToHomeBar.kt`
- Modify: `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java` (show the bar over the search bar during a paged drag)
- Modify: `src/com/android/launcher3/ButtonDropTarget.java` (`isDropEnabled`, line 249)
- Modify: `lawnchair/res/values/strings.xml` (`paged_drawer_add_to_home` = "Add to Home screen")

Reference: One UI. In the drawer, a drag rearranges; dragged to the top it leaves for the home screen. On the home screen the top bar then reads **Cancel**: dropping there puts nothing on the home screen.

Upstream already gives the second half. `DeleteDropTarget.setTextBasedOnDragSource` (line 112) labels the button `android.R.string.cancel` when `canRemove(item)` is false, which it is for an app dragged from the drawer (`AppInfo.id == NO_ID`, and also under deck layout, where applications are never removable). Dropping on Cancel ends the drag with nothing added. So this task builds the hand-off and keeps that path intact; it does not build a Cancel target.

The trap: `drop_target_bar` sits **under** `apps_view` in `res/layout/launcher.xml` (lines 62 and 77), and `DropTargetBar.onDragStart` shows it for every drag. While the drawer is open its Remove / Uninstall / App info buttons are invisible but still hit-tested, so a drop at the top of the drawer would land on one of them.

**Interfaces:**
- Consumes: `DragOptions.fromPagedDrawer` (Task 4).
- Produces: `AddToHomeBar : DropTarget`, drawn over the search bar, visible only during a drag with `fromPagedDrawer` while in `ALL_APPS`.

- [ ] **Step 1:** `ButtonDropTarget.isDropEnabled()` also returns false while the launcher is in `ALL_APPS` (`// LC:`). Check on a device that dropping at the very top of the drawer neither uninstalls nor opens App info.
- [ ] **Step 2:** Show `AddToHomeBar` over the search bar on `onDragStart` of a paged drag, hide it on `onDragEnd` or once the state leaves `ALL_APPS`.
- [ ] **Step 3:** On `onDragEnter`, after 400 ms of hover (an `Alarm`, as `Folder.mOnExitAlarm`), call `launcher.stateManager.goToState(SPRING_LOADED)`. The drag object carries on; the workspace and the now visible `DropTargetBar` take it, so the bar reads Cancel, and a drop on a cell adds the app exactly as a drag from today's drawer does. The drawer's order is not written on either path.
- [ ] **Step 4: On a device:** drag to the top, home screen appears with Cancel at the top; drop on Cancel (home unchanged, app still in its drawer cell, drawer order unchanged); drop on a free cell (shortcut added); drop on a full page (normal workspace behaviour); drag a home screen icon (its top bar still reads Remove, unchanged).
- [ ] **Step 5: Commit** — `feat(drawer): add to Home from the paged drawer`.

### Task 6: Host guide, local test in Holy Quran, PR

**Files:**
- Create: `docs/paged-drawer-guide.md`
- In the host (not committed): `holysheet/gradle/libs.versions.toml:55`, a `res/values` bool

- [ ] **Step 1:** Write `docs/paged-drawer-guide.md`: the flag, the decisions table above, what is not paged (work profile, private space, search), slot behaviour.
- [ ] **Step 2:** `./gradlew publishToMavenLocal` at the lawnchair root (publishes every module as `16.0.0-local`, `gradle.properties:13`; later runs can be `:launcher-ui:publishToMavenLocal` when only launcher-ui changed). In holysheet set `lawnchair = "16.0.0-local"` locally (its `settings.gradle.kts` already lists `mavenLocal()`), add `<bool name="config_drawer_paged">true</bool>`, `./gradlew :app:assembleDebug`, install, repeat the device checks of Tasks 3–5 with the `b_allapp_lc` header banner showing. Revert the version line before any holysheet commit.
- [ ] **Step 3:** Push `feat/paged-drawer`, open a PR against `16-dev`.
