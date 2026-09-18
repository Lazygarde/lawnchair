# Kế hoạch: tách `:launcher-ui` khỏi non-SDK API (variant `withoutQuickstep`)

## Mục tiêu

Artifact `:launcher-ui` mà host app phụ thuộc vào **không chứa code gọi non-SDK/hidden API**.

Hệ quả:

- Hết chuỗi crash `NoSuchMethodError` / `NoSuchFieldError` / `NoClassDefFoundError` từ alpha05 tới nay.
- Hết cảnh báo Play Console *"Your app uses API bypass SDKs that are affected by the latest updates to Android runtime (ART)"*.
- Không mất tính năng thực tế: Recents/gesture nav **vốn đã không hoạt động** trong app này (xem *Bối cảnh*).

## Bối cảnh — tại sao không có đường nào khác

Nguyên nhân gốc của chuỗi crash là commit `c4a8a7c82c` gỡ `Unseal.unseal()` khỏi
`src/com/android/launcher3/MainProcessInitializer.java`. Lệnh đó gọi
`VMRuntime.setHiddenApiExemptions()` để tắt hidden-API enforcement cho cả process.

Khi còn nó, `ActivityTaskManager.getService()` link được, gọi binder rồi bị từ chối quyền →
ném `SecurityException` (là `Exception`) → các khối `catch` sẵn có bắt được. Khi gỡ đi, cùng
dòng code đó ném `NoSuchMethodError` — là `Error`, **không phải** `Exception` — nên thoát ra
ngoài và giết Activity.

Ví dụ cụ thể, `systemUI/shared/src/com/android/systemui/shared/system/TaskStackChangeListeners.java:176`:

```java
} catch (NoClassDefFoundError | ExceptionInInitializerError | Exception e) {
```

`NoSuchMethodError` → `IncompatibleClassChangeError` → `LinkageError` → `Error`. Không nằm trong
danh sách ⇒ crash.

**Khôi phục exemption không phải lựa chọn.** Cảnh báo của Google nói rõ đây là vấn đề runtime:
ART nay là Mainline module, cập nhật qua Play độc lập với bản Android, và mọi kỹ thuật bypass
(`setHiddenApiExemptions`, `Unsafe` + `MethodHandles.Lookup.IMPL_LOOKUP`, meta-reflection) đều dựa
vào cấu trúc nội bộ ART mà Google đang siết. Trên Android 16 — đúng nền tảng đang test — bypass
không còn tác dụng. Tự viết lại thay vì dùng thư viện cũng không đổi gì: scanner nhắm vào kỹ thuật,
và runtime vẫn hỏng.

**Whack-a-mole cũng không phải lựa chọn.** Số file import non-SDK/internal API:

| API | Số file |
|---|---|
| `com.android.internal.*` | 404 |
| `android.app.WindowConfiguration` | 114 |
| `android.app.ActivityTaskManager` | 67 |
| `android.view.IWindowManager` | 31 |
| `android.view.WindowManagerGlobal` | 22 |
| `android.app.IActivityTaskManager` | 19 |

Tổng **851 file**. Bọc `catch (Throwable)` từng call site chỉ bịt được đúng chỗ đó.

## Nguyên tắc thi hành

**Ưu tiên CHUYỂN file, không VIẾT LẠI.** Phần lớn class nằm trong source set quickstep thật ra
không phụ thuộc quickstep — chúng ở đó vì lý do lịch sử. Đo thực tế:

| File | Dòng | Import quickstep/non-SDK | Xử lý |
|---|---|---|---|
| `LauncherConcurrencyModule.kt` | 81 | 0 | chuyển sang `src/` |
| `LauncherIconProviderImpl.kt` | 137 | 0 | chuyển sang `src/` |
| `ChoreographerFrameRateTracker.kt` | 36 | 0 | chuyển sang `src/` |
| `InstantAppResolverImpl.java` | 74 | 0 | chuyển sang `src/` |
| `LauncherRestoreEventLoggerImpl.kt` | 162 | 0 | chuyển sang `src/` |
| `PluginManagerWrapperImpl.java` | 126 | 0 | chuyển sang `src/` |
| `HintState.java` | 80 | 0 | chuyển sang `src/` |
| `StatsLogCompatManager.kt` | 807 | 2 (`SysUiStatsLog`, `InteractionJankMonitorWrapper`) | chuyển + bọc 1 chỗ |

Chỉ những gì thật sự dính quickstep mới phải viết bản thay thế.

## Trở ngại chính: đồ thị Dagger đã bị hợp nhất vào quickstep

`src/com/android/launcher3/dagger/LauncherAppModule.java:29-41` thuộc core `src/`, **luôn được
compile**, nhưng khai báo module list trỏ vào source set quickstep:

```java
@Module(includes = {
        WindowManagerProxyModule.class,    // quickstep/src/.../dagger/Modules.kt
        ApiWrapperModule.class,            // quickstep/src/.../dagger/Modules.kt
        PluginManagerWrapperModule.class,  // quickstep/src/.../dagger/Modules.kt
        StaticObjectModule.class,          // quickstep/src/.../dagger/Modules.kt
        WidgetModule.class,                // quickstep/src/.../dagger/Modules.kt
        AppModule.class,                   // quickstep/dagger/.../AppModule.kt
        PerDisplayModule.class,            // quickstep/src/.../PerDisplayModule.kt
        LauncherConcurrencyModule.class,   // quickstep/src/.../LauncherConcurrencyModule.kt
        ... },
        subcomponents = ActivityContextComponent.class)  // quickstep/dagger/...
```

**9/11 module của core chỉ tồn tại trong source set quickstep** ⇒ core không compile nổi nếu bỏ
quickstep ra. Đây là phần rủi ro nhất và phải làm trước.

---

## Phase 0 — Cầm máu (0.5 ngày)

Độc lập với phần còn lại, làm ngay để có bản alpha chạy được.

1. Commit 22 file đang sửa dở trong working tree (đổi `catch (Exception)` /
   `catch (NoClassDefFoundError | ...)` thành `catch (Throwable)`). Fix đúng crash
   `TaskStackChangeListeners` hiện tại.
2. Gỡ Rikka Refine — SDK bypass hidden API thứ hai còn sót:
   - `gradle/libs.versions.toml:37,135-137`
   - `launcher-ui/build.gradle:18,183`
   - `build.gradle:18`
   - Call site duy nhất: `quickstep/src/com/android/launcher3/uioverrides/QuickstepInteractionHandler.java:90`
     (`Refine.unsafeCast(...)` → bỏ, dùng nhánh fallback sẵn có)
   - `hidden-api/src/main/java/android/app/IActivityTaskManagerHidden.java` → xoá

**Done khi:** app khởi động được; `./gradlew :launcher-ui:assembleLawnWithQuickstepRelease` xanh;
không còn `dev.rikka.tools.refine` trong dependency tree.

**Kiểm tra song song:** vào Play Console xem cảnh báo "API bypass SDK" còn không sau khi gỡ
ChickenHook. Nếu còn, nhiều khả năng thủ phạm là Refine. Cũng nên xác nhận
`com.github.topjohnwu.libsu` (dùng ở 5 file cho tính năng root) có bị tính vào cảnh báo này không.

---

## Phase 1 — Tách tầng DI (3–4 ngày) ⚠️ rủi ro cao nhất

### 1.1 Chuyển file không phụ thuộc quickstep sang `src/`

Theo bảng ở mục *Nguyên tắc thi hành*. Không sửa logic, chỉ đổi vị trí file (giữ nguyên package).

Sau **mỗi** lần chuyển, chạy `./gradlew :launcher-ui:compileLawnWithQuickstepReleaseKotlin` để
chắc variant hiện tại vẫn xanh. Đừng gộp nhiều lần chuyển vào một lần build.

Với `StatsLogCompatManager.kt`: giữ nguyên `SysUiStatsLog` (core `src/` đã dùng sẵn), bọc
`InteractionJankMonitorWrapper` trong `try/catch (Throwable)` vì nó chạm
`com.android.internal.jank`.

### 1.2 Tạo source set `withoutQuickstep` và viết các module twin

Thư mục mới `src_ui_overrides/` + `dagger_ui_overrides/`:

| File cần viết | Mẫu | Nội dung |
|---|---|---|
| `LauncherAppComponent.java` | `quickstep/dagger/.../LauncherAppComponent.java` (41 dòng) | `extends LauncherBaseAppComponent` thay vì `QuickstepBaseAppComponent` |
| `ActivityContextComponent.kt` | `quickstep/dagger/.../ActivityContextComponent.kt` (32) | `extends BaseActivityContextComponent` — `QuickstepBaseActivityComponent` chỉ là marker interface rỗng |
| `AppModule.kt` | `quickstep/dagger/.../AppModule.kt` (32) | binds `ModelDelegate` ← bản lite (xem Phase 2) |
| `Modules.kt` | `quickstep/src/.../dagger/Modules.kt` (100) | xem bảng binding bên dưới |
| `PerDisplayModule.kt` | `quickstep/src/.../PerDisplayModule.kt` (275) | copy, bỏ 5 provider quickstep |

**Bảng binding cho `Modules.kt` bản không quickstep:**

| Binding | Bản quickstep | Bản không quickstep |
|---|---|---|
| `WindowManagerProxy` | `LawnchairWindowManagerProxy` | giữ nguyên (kế thừa core `WindowManagerProxy`) |
| `WidgetHolderFactory` | `LawnchairWidgetHolder.Factory` | giữ nguyên (kế thừa core `LauncherWidgetHolder`) |
| `StatsLogManagerFactory` | `StatsLogCompatManagerFactory` | giữ nguyên (đã chuyển ở 1.1) |
| `LauncherIconProvider` | `LauncherIconProviderImpl` | giữ nguyên (đã chuyển) |
| `InstantAppResolver` | `InstantAppResolverImpl` | giữ nguyên (đã chuyển) |
| `LauncherRestoreEventLogger` | `LauncherRestoreEventLoggerImpl` | giữ nguyên (đã chuyển) |
| `PluginManagerWrapper` | `PluginManagerWrapperImpl` | giữ nguyên (đã chuyển) |
| `RefreshRateTracker` | `ChoreographerFrameRateTracker` | giữ nguyên (đã chuyển) |
| `ApiWrapper` | `SystemApiWrapper` (quickstep) | bind core `com.android.launcher3.util.ApiWrapper` |
| `GestureExclusionManager` | `GestureExclusionManager.INSTANCE` | **bỏ** provider |
| `ActivityManagerWrapper` | `ActivityManagerWrapper.getInstance()` | **bỏ** provider (non-SDK) |

**`PerDisplayModule.kt`** — bỏ 5 provider trả về type quickstep:
`provideRecentsAnimationDeviceStateRepo`, `provideTaskAnimationManagerRepo`,
`provideRotationTouchHandlerRepo`, `provideFallbackWindowInterfaceRepo`,
`provideRecentsWindowManagerRepo`. Giữ `provideDisplayContext`, `provideWindowContext`,
`providesDisplayRepositoryFromLib`, `providesDisplaysWithDecorationsRepository*` (từ `:displaylib`
và core).

**Done khi:** `./gradlew :launcher-ui:kspLawnWithoutQuickstepReleaseKotlin` sinh được
`DaggerLauncherAppComponent` không lỗi.

> Vòng lặp phản hồi ở bước này chậm: Dagger/KSP báo lỗi thiếu binding từng cái một. Dự trù phần lớn
> thời gian của Phase 1 nằm ở đây.

---

## Phase 2 — Lớp state và launcher (2 ngày)

### 2.1 `src_ui_overrides/com/android/launcher3/uioverrides/`

| File | Ghi chú |
|---|---|
| `states/AllAppsState.java` | copy từ quickstep (224 dòng); thay `BaseDepthController.DEPTH_60_PERCENT` bằng `0.6f` — đó là dependency quickstep duy nhất |
| `states/OverviewState.java` | rút gọn từ bản quickstep (264 dòng); bỏ `RecentsView`, `TaskView`, `LayoutUtils`, `BaseDepthController`; giữ `newModalTaskState` / `newSwitchState` / `newBackgroundState` / `newSplitSelectState` vì `LauncherState.java:154-165` gọi tới |
| `flags/DevOptionsUiHelper.kt` | stub `Preference` rỗng — chỉ để `res/xml/launcher_preferences.xml:57` inflate được; không phải compile blocker |

`HintState.java` không cần bản riêng (đã chuyển sang `src/` ở Phase 1.1).

### 2.2 Lớp lawnchair

Tạo `LawnchairLauncherBase` ở cả hai source set — đây là điểm tách duy nhất cho superclass:

```kotlin
// withQuickstep
open class LawnchairLauncherBase : QuickstepLauncher()
// withoutQuickstep
open class LawnchairLauncherBase : Launcher()
```

Điều này an toàn vì **mọi method `LawnchairLauncher` override đều tồn tại trên core `Launcher`** —
đã kiểm tra cả 14 method; `makeDefaultActivityOptions` và `getActivityLaunchOptions` khai báo ở
`ActivityContext.java:521,547` và `BaseActivity.java:518,525`.

Sửa trong `lawnchair/src` (luôn được compile):

| File | Thay đổi |
|---|---|
| `LawnchairLauncher.kt:99` | `: QuickstepLauncher()` → `: LawnchairLauncherBase()` |
| `LawnchairLauncher.kt:132` | `is BackgroundAppState` → `toState == LauncherState.BACKGROUND_APP` (không cần flavor; `BACKGROUND_APP` là hằng của core) |
| `LawnchairApp.kt:209` | `RecentsActivity::class.java.name` → hằng chuỗi `"com.android.quickstep.RecentsActivity"` (hành vi y hệt, hết dependency compile) |
| `PreferenceManager.kt:52-54` | `RecentsModel.INSTANCE.get(context).onThemeChanged()` → facade `RecentsCompat.onThemeChanged(context)`, no-op ở bản không quickstep |

Twin theo flavor (file nhỏ, viết hai bản):

- `LawnchairProcessInitializer.kt` (31 dòng) — `MainProcessInitializer` thay `QuickstepProcessInitializer`
- `ReloadHelper.kt` (64) — bỏ `TISBindHelper` / `TouchInteractionService`, no-op
- `LawnchairModelDelegate.kt` — `ModelDelegate` (core) thay `QuickstepModelDelegate`

Chuyển hẳn sang source set `withQuickstep` (không cần bản không-quickstep):

- `lawnchair/src/app/lawnchair/overview/LawnchairOverviewActionsView.kt`
- `lawnchair/src/app/lawnchair/overview/TaskOverlayFactoryImpl.kt`
- `lawnchair/src/app/lawnchair/util/TaskIconUtils.kt` — **code chết, không có caller nào**
- `lawnchair/src/app/lawnchair/util/RecentHelper.kt` — **code chết, không có caller nào**

---

## Phase 3 — Tài nguyên và manifest (1 ngày)

Chuyển khỏi `lawnchair/res` (luôn được compile) sang `quickstep/res`:

- `layout/overview_actions_container.xml` (tham chiếu `LawnchairOverviewActionsView`)
- `layout-v28/task_desktop.xml`, `layout-v31/task_desktop.xml` (tham chiếu `DesktopTaskView`,
  `TaskThumbnailViewDeprecated`, `IconView`)
- `values/config.xml:43` — dòng `task_overlay_factory_class`

`res/layout/overview_panel.xml` gốc đã là stub `<Space>` sẵn, không cần làm gì.

Manifest: tạo `AndroidManifest-launcher-noqs.xml` cho source set `lawnWithoutQuickstep` — copy
`quickstep/AndroidManifest-launcher.xml` (chỉ khai báo activity HOME, không kèm service/receiver
của quickstep).

---

## Phase 4 — Gradle và publishing (0.5 ngày)

Trong `launcher-ui/build.gradle`:

```groovy
productFlavors {
    lawn { dimension "app" }
    withQuickstep { dimension "recents" }
    withoutQuickstep { dimension "recents" }   // mới
}

sourceSets {
    withoutQuickstep {
        java.srcDirs = ["$rootDir/src_ui_overrides", "$rootDir/dagger_ui_overrides"]
        kotlin.directories.addAll(java.srcDirs.collect { it.path })
    }
    lawnWithoutQuickstep {
        manifest.srcFile "$rootDir/AndroidManifest-launcher-noqs.xml"
    }
}
```

Chỉnh `publishVariant` ở `gradle/publishing.gradle:81` (`singleVariant`) để publish variant
`lawnWithoutQuickstepRelease` cho host app. Cân nhắc publish cả hai variant nếu vẫn muốn giữ bản
standalone có quickstep.

---

## Phase 5 — Dọn non-SDK còn sót (2–4 ngày, tuỳ chọn)

Sau Phase 4, `:shared` (systemUI) và `:wmshell` vẫn nằm trong dependency của `:launcher-ui` và vẫn
chứa code non-SDK (`TaskStackChangeListeners`, `ActivityManagerWrapper`, `QuickStepContract`...).
Code này **không còn được gọi** nên không gây crash — ART nạp class theo kiểu lazy — nhưng vẫn hiện
diện trong DEX và có thể vẫn bị scanner tính.

Xử lý: tách `:shared` thành phần thuần (`Flags`, `SysUiStatsLog`, `plugins`) và phần đụng non-SDK,
chỉ để variant `withQuickstep` phụ thuộc phần sau. Chỉ làm nếu Phase 6 cho thấy Play Console vẫn
cảnh báo.

---

## Phase 6 — Verify (2 ngày)

1. Build: `./gradlew :launcher-ui:assembleLawnWithoutQuickstepRelease`
2. Kiểm tra DEX của host app không còn tham chiếu non-SDK:
   ```bash
   $ANDROID_HOME/build-tools/<ver>/dexdump -d app-release.apk \
     | grep -E "ActivityTaskManager;->getService|WindowManagerGlobal|IActivityTaskManager"
   # kỳ vọng: không có kết quả
   ```
3. Cài lên máy Android 16 (máy đã tái hiện crash), xác nhận launcher khởi động, không có
   `InflateException` / `NoSuchMethodError` trong logcat.
4. Regression: workspace, all-apps, widget picker, folder, search, đổi theme, xoay màn hình,
   thêm/xoá widget, app predictions.
5. Upload lên internal testing track → kiểm tra cảnh báo "API bypass SDK" trong Play Console.

**Definition of done:** bước 2 không ra kết quả nào, bước 3 sạch logcat, bước 5 hết cảnh báo.

---

## Tổng thời gian

| Phase | Ngày công |
|---|---|
| 0 — Cầm máu | 0.5 |
| 1 — Tầng DI | 3–4 |
| 2 — State + launcher | 2 |
| 3 — Res + manifest | 1 |
| 4 — Gradle | 0.5 |
| 5 — Dọn `:shared` (tuỳ chọn) | 2–4 |
| 6 — Verify | 2 |
| **Tổng** | **9–14 ngày ≈ 2–3 tuần** |

## Rủi ro

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Dagger graph không resolve được, lỗi dây chuyền | Cao | Làm Phase 1 trước tiên; chuyển từng file một, build sau mỗi lần |
| App predictions ngừng hoạt động (`StatsLogCompatManager` không được wire ở bản không quickstep) | Trung bình | Kiểm tra `LOGS_CONSUMER` có nhận event sau Phase 1.1; nếu không, wire `StatsLogManagerFactory` ở `Modules.kt` bản mới |
| Rebase upstream làm lệch source set mới | Trung bình | Bám cấu trúc `src_ui_overrides` của AOSP Launcher3 37.2 thay vì tự đặt layout; ghi chú mọi chỗ lệch |
| Phase 5 phát sinh khi Play vẫn cảnh báo | Trung bình | Chạy Phase 6 bước 5 sớm, ngay sau Phase 4 |
| Host app đang dùng API của `:launcher-ui` mà bản không quickstep không có | Thấp | Rà API surface mà host gọi trước khi đổi `publishVariant` |

## Ghi chú số liệu

Mọi con số trong tài liệu này đến từ đo đạc trên cây mã tại commit `4afd5ead82`:

- 503 class định nghĩa trong source set quickstep; 41 trong số đó nằm ở package dùng chung.
- 55 file luôn-được-compile có tham chiếu tên class quickstep (cận trên, có false positive).
- 68 tham chiếu `LauncherAppComponent` từ nguồn luôn-compile — tất cả đều là method reference
  `LauncherAppComponent::getXxx` với `getXxx` khai báo trên `LauncherBaseAppComponent` của core,
  nên bản twin kế thừa `LauncherBaseAppComponent` là đủ.
- `com.android.launcher3.uioverrides.ApiWrapper` và `com.android.quickstep.views.BlurUtils`:
  **0 tham chiếu** từ nguồn luôn-compile (các lần grep trước bị nhầm với
  `com.android.launcher3.util.ApiWrapper` của core).
