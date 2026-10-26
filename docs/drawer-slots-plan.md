> Host-facing usage: see [drawer-slots-guide.md](drawer-slots-guide.md). This file is the design/implementation plan.

# Kế hoạch: Slot nội dung trong App Drawer (ads hoặc nội dung thường)

Mục tiêu: cho phép host app gắn **view của mình** vào app drawer của `:launcher-ui` — quảng cáo hoặc nội dung thường
(promo card, shortcut "đọc tiếp", widget giờ cầu nguyện...) — qua ba vị trí:

- **A1 / Inline** — hàng xen trong lưới app (full-span).
- **A2 / Header, Footer** — slot cố định: Header (dưới thanh search) và Footer (dính đáy drawer).
- **A3 / Search** — item trong kết quả search (ví dụ "Sponsored" hoặc gợi ý của host).

Cách cung cấp **B3**: provider trả về `View` (host tự load/render, vd. bằng SDK ads), kèm các helper
`registerView(layoutRes)` / `setComposeContent(...)` giống `LawnchairOverlayRegistry`.

Mỗi slot có một **kind** (`DrawerSlotKind.AD` hoặc `CONTENT`) do provider khai báo qua `kindOf(slot)`; drawer áp dụng
chính sách khác nhau theo kind (xem mục "Kind và chính sách").

---

## 0. Hạ tầng chung (B3) — làm trước, cả 3 slot dùng chung

**Package:** `lawnchair/src/app/lawnchair/drawerslots/`

| File | Vai trò |
|---|---|
| `DrawerSlot.kt` | Gồm `Header`, `Footer`, `Inline(index)`, `Search(query, index)`. Mỗi slot có `key` ổn định để cache view và để provider biết slot nào. |
| `LawnchairDrawerSlotProvider.kt` | Interface host implement. Mọi method đều có default, host chỉ override slot nào mình dùng. |
| `LawnchairDrawerSlotRegistry.kt` | `registerProvider`, `registerView(layoutRes)`, `setComposeContent(slot)`, `notifySlotsChanged(slot?)`, `clear()`. Bên trong dùng `SharedFlow` (không phải `StateFlow`, vì `StateFlow` gộp các thay đổi liên tiếp và làm mất thay đổi theo từng slot type) để drawer tự cập nhật. |
| `DrawerSlotViewCache.kt` | Mỗi slot giữ đúng **1 view**. Khi RecyclerView bind lại thì view được gỡ khỏi cha cũ rồi gắn vào cha mới, **không gọi `createView` lại**, nên ví dụ native ad của AdMob không bị load lại. Cache được xoá khi gọi `notifySlotsChanged` hoặc khi launcher destroy. |
| `DrawerSlotVisibilityTracker.kt` | Gọi `onSlotVisible` / `onSlotHidden` chỉ khi drawer **đã mở hẳn** (state `ALL_APPS`) **và** slot hiện ≥50% trên màn hình. Lắng nghe scroll của RecyclerView, state launcher và việc đổi tab. |
| `DrawerSlotKind.kt` | `AD` hoặc `CONTENT`, xem bên dưới. |
| `DrawerSlotEnv.kt` | Thông tin truyền cho host để làm view hợp giao diện drawer: `themedContext`, màu nền/chữ, dark mode, blur, và `availableWidthPx` (bề rộng view thực sự nhận được). Thư viện tự lo lề ngang và khoảng cách dọc (xem mục Spacing), nên env **không** còn `startPaddingPx`/`endPaddingPx` để tránh inset hai lần. |

**API dự kiến:**

```kotlin
interface LawnchairDrawerSlotProvider {
    fun isSlotEnabled(type: DrawerSlot.Type): Boolean = false
    fun kindOf(slot: DrawerSlot): DrawerSlotKind = DrawerSlotKind.AD       // mặc định bảo thủ
    fun showOnWorkTab(slot: DrawerSlot): Boolean = false                   // chỉ áp dụng cho CONTENT
    fun inlineRowPositions(appRowCount: Int): List<Int> = emptyList()      // A1
    fun createView(context: Context, parent: ViewGroup, slot: DrawerSlot, env: DrawerSlotEnv): View?
    suspend fun searchItemCount(query: String): Int = 0                      // A3
    fun onDrawerOpened() {}
    fun onDrawerClosed() {}
    fun onSlotVisible(slot: DrawerSlot) {}
    fun onSlotHidden(slot: DrawerSlot) {}
    fun onDestroy() {}
}
```

- Cấu hình: `kindOf` mặc định là `AD` vì chính sách AD là phía an toàn (không focus, không tính accessibility,
  không bao giờ là quick-launch), nên host quên khai báo cũng không bao giờ mở nhầm ads bằng phím Enter. Kind được đọc
  **lúc view được tạo** nên đổi được sau `notifySlotsChanged(type)`.
- Nếu `createView` trả về `null` thì slot co về 0 / bị bỏ khỏi danh sách, không để lại khoảng trống.
- Helper Compose: dùng `ComposeView` với `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`, vì view
  được cache lâu hơn ViewHolder. Lifecycle owner dùng lại cách đã làm cho overlay (commit `9efa1819d4`).
- **Theme cho ad view:** `context` truyền vào `createView` vẫn là Activity (SDK ads cần), nhưng theme của launcher không
  phải AppCompat/Material nên inflate `MaterialButton` sẽ crash. Thư viện khai báo
  `<style name="LawnchairDrawerSlotTheme"/>` (rỗng) trong `host_config.xml`; host override cùng tên (vd. parent
  `Theme.Material3.DayNight.NoActionBar`). Thư viện bọc launcher bằng style này thành `DrawerSlotEnv.themedContext`, và
  `parent` truyền vào `createView` được tạo từ context đó, nên `LayoutInflater.from(parent.context)` đã đúng theme.
  `registerView`/`setComposeContent` tự dùng context này.
- Sự kiện drawer mở/đóng lấy từ `onStateSetEnd` (state `ALL_APPS` được commit) và `onStart`/`onStop` của
  `lawnchair/src/app/lawnchair/LawnchairLauncher.kt`.
- `launcher-ui/src/main/res/values/host_config.xml` thêm `config_enable_drawer_slots` (mặc định `false`). Đây là công
  tắc tổng; từng slot do `isSlotEnabled` quyết định.
- `launcher-ui/consumer-rules.pro`: keep package `app.lawnchair.drawerslots.**` để R8 không xoá hay đổi tên API công khai.

### Nhiều provider

Registry giữ một provider **toàn cục** và các provider **theo type** (tuỳ chọn):
`registerProvider(provider)` và `registerProvider(type, provider)`. Provider xử lý một slot = provider theo type nếu có,
ngược lại provider toàn cục; `isSlotEnabled` được hỏi provider đã resolve. `onDrawerOpened/Closed/onDestroy` gửi đến
từng provider khác nhau đúng một lần; `onSlotVisible/Hidden` gửi đến provider đã tạo slot đó. `clear()` xoá hết,
`clear(type)` xoá provider theo type. Các helper `registerView`/`registerFactory`/`setComposeContent` cài một provider
nội bộ **theo type** (không đè provider toàn cục), nhận `kind` (mặc định `CONTENT` vì thường dùng cho UI của host;
truyền `AD` cho ads) và `showOnWorkTab`.

### Kind và chính sách

| | `AD` | `CONTENT` |
|---|---|---|
| Focus bàn phím/D-pad | chặn (`FOCUS_BLOCK_DESCENDANTS`), TalkBack vẫn đọc được | bình thường |
| Tab work | luôn ẩn | theo `showOnWorkTab(slot)` (mặc định ẩn) |
| Inline (Phase 2) | không tính accessibility, không thành focused child của IME/Enter | tính và focus như item thường |
| Search (Phase 3) | không bao giờ là quick-launch, không tính accessibility | không bao giờ là quick-launch; còn lại như item thường |
| Footer | ẩn khi search / IME mở / tab work | ẩn khi search / IME mở / tab work |
| Header | ẩn khi search (cả header bị `GONE`) | ẩn khi search |

Footer luôn ẩn ở tab work kể cả `CONTENT`, vì thanh pause của work profile neo ở cùng mép đáy. Header ẩn khi search là
hành vi sẵn có của `FloatingHeaderView`. Cả hai kind cùng bị ẩn khi IME mở / search ở footer vì chồng lên nhau về hình học.

---

## Phase 1 — A2: Header và Footer

**Header** (đi theo cơ chế sẵn có, ít rủi ro):

- `ActivityAllAppsContainerView.getAdditionalHeaderRows()` hiện trả về list rỗng. Sửa để trả về `HostSlotHeaderRow`
  (implement `AllAppsRow`) khi đã bật cờ.
- Row này **luôn được tạo** khi bật cờ, chiều cao 0 nếu chưa có view. Lý do: `mAdditionalHeaderRows` chỉ được nạp
  một lần lúc khởi tạo container, nên nếu host đăng ký provider muộn thì vẫn hiện được slot.
- Khi view sẵn sàng, gọi `OnHeightUpdatedListener.onHeightUpdated()` để `FloatingHeaderView` tính lại padding.
- Header tự thu gọn khi cuộn (hành vi có sẵn). Cần kiểm tra: ẩn khi đang search và khi thanh search của drawer bị
  ẩn (`isAppDrawerSearchBarHidden`).

**Footer:**

- Thêm `FrameLayout @id/all_apps_host_slot_footer` vào `res/layout/all_apps_content.xml`, căn đáy.
- Trong `setInsets` / `onLayout`: đặt footer phía trên navigation bar, và cộng chiều cao footer vào
  `AdapterHolder.mPadding.bottom` để item cuối và fast scroller không bị che.
- Theo dõi thay đổi chiều cao bằng `OnLayoutChangeListener` để padding luôn khớp.
- Mặc định footer **ẩn khi bàn phím mở, đang search hoặc đang ở tab work** (tab work có view tiện ích dính đáy).
- Header **ẩn ở tab work** với `AD`; với `CONTENT` theo `showOnWorkTab(slot)`. Thay đổi chiều cao/sự hiện diện của header khi drawer đang mở và list đã cuộn
  sẽ được **hoãn đến khi drawer đóng**, vì dựng lại header gọi `reset()` và kéo list về đầu.
- Slot `AD` bị chặn focus bàn phím/D-pad (`FOCUS_BLOCK_DESCENDANTS`), TalkBack vẫn đọc được; `CONTENT` focus bình thường.
- Drawer coi là "đã mở" chỉ khi state `ALL_APPS` được commit **và** activity đang started (mở app từ drawer = đóng).
  `onSlotVisible` là tín hiệu hiển thị, không phải impression tính tiền.

## Phase 2 — A1: Slot xen trong lưới app (Inline) — ĐÃ LÀM

**Điểm móc:**

- `AlphabeticalAppsList.updateAdapterItems()` gọi hook protected `onAdapterItemsBuilt(items, fastScrollerSections)` **trước**
  bước tính `mAccessibilityResultsCount` và `rowIndex` (thêm getter protected `getNumAppsPerRow()`).
- `LawnchairAlphabeticalAppsList` override hook, chèn `HostSlotAdapterItem(DrawerSlot.Inline(i, inWorkTab))`.
  Logic tính vị trí nằm trong `InlineSlotPlanner` (thuần Kotlin, có unit test `InlineSlotPlannerTest`).

**Quy tắc chèn:**

- Số hàng = `ceil(số item icon/folder / appsPerRow)` (đếm trước khi chèn); `provider.inlineRowPositions(appRowCount)`
  trả về các vị trí "sau hàng N" (chỉ nhận N ≥ 1, bỏ trùng, sắp tăng dần). Chèn ở **ranh giới hàng**, trước icon đầu của hàng kế
  tiếp; vị trí ≥ số hàng hiện có thì bỏ qua (danh sách quá ngắn).
- Không chèn: khi đang có kết quả search (list search cũng không bao giờ chèn: `supportsHostSlots = false`), trong vùng
  private space (chỉ xét các icon đứng trước header private space), trong list work trừ khi slot là `CONTENT` **và**
  `showOnWorkTab(slot)`.
- Slot **không có view** (`createView` trả null) thì **không được chèn** (không để lại khoảng trống). `createView` được gọi
  lúc dựng list (hợp đồng: phải nhanh) và kết quả được cache, nên view được preload.
- `notifySlotsChanged(INLINE)` (hoặc toàn bộ) xoá cache rồi chạy lại các list qua `DrawerSlotController.addInlineListener`.
  Đổi lưới/xoay màn hình: `onIdpChanged` → `onAppsUpdated` đã chạy lại list.
- List main và list work dùng key khác nhau (`Inline.inWorkTab`) vì một view chỉ có một parent và hai RecyclerView cùng tồn tại.
- Cả hai chế độ drawer (phân loại tự động, folder `drawerList`) đều đi qua hook vì chèn sau khi list đã dựng xong.

**Các chỗ đã sửa:**

1. **View type:** `VIEW_TYPE_HOST_SLOT = 1 shl 23` (folder là `1 shl 10`, search dùng `1 shl 10..22`, không trùng). Xử lý trực tiếp trong
   `onCreateViewHolder`/`onBindViewHolder` của `BaseAllAppsAdapter`, không qua `mAdapterProvider`, thông qua `HostSlotBinder`.
   ViewHolder là `FrameLayout` rỗng; bind gỡ view cache khỏi parent cũ rồi gắn vào, **không** tạo lại.
2. **Span:** `GridSpanSizer` đã cho mọi loại không phải icon full-span; không cần sửa.
3. **Tính hàng:** `rowIndex` coi slot như divider (reset `numAppsInSection`).
4. **Fast scroller:** mọi `FastScrollSectionInfo` có `position >= vị trí chèn` được dịch (`InlineSlotPlanner.shiftedPosition`); item
   `VIEW_TYPE_BOTTOM_VIEW_TO_SCROLL_TO` vẫn đứng cuối (chèn luôn ở trước nó).
5. **DiffUtil:** `isSameAs` so theo `slot.key`; `isContentSame` đúng khi cùng kind **và cùng instance view** (để holder bind lại khi
   cache bị xoá và view được tạo lại).
6. **Focus/accessibility:** mọi inline item `isCountedForAccessibility() = false` (không bao giờ là `getFocusedChild`, Enter không mở
   được, không bị đếm là app). `AD`: holder `FOCUS_BLOCK_DESCENDANTS`; `CONTENT`: focus bình thường bên trong. Content
   description do host đặt trên view của mình.
7. **Visibility:** holder attach/detach gọi `DrawerSlotVisibilityTracker` (qua `DrawerSlotController.track`), cùng luật ≥50% + drawer mở
   hẳn; callback đến provider đã tạo slot.
8. **Căn lề:** holder inline nằm trong padding của RecyclerView (= lề lưới) nên view MATCH_PARENT đã thẳng hàng với lưới; xem mục Spacing.
9. **Chiều cao từng slot:** `ScrollableLayoutManager` cache chiều cao theo view type, không dùng được cho slot (cùng type, khác
   chiều cao). Hook `onChildSizeMeasured` cho phép `AppsGridLayoutManager` ghi chiều cao **theo slot key** (`SlotHeightTable`); ước
   lượng dùng chiều cao đã đo, nếu chưa có thì chiều cao view đã đo trước đó, nếu chưa có nữa thì một hàng app. Khi chiều cao
   đổi, scroll cache bị invalidate.
10. **D-pad:** holder của slot `AD` (và `CONTENT` không có view focusable bên trong) là **điểm dừng focus bàn phím** (focusable) nhưng
    **không bao giờ clickable** và không vẽ highlight, nên focus theo D-pad không bị kẹt sau slot cao; Enter không làm gì và slot không bao
    giờ là quick-launch/IME target. Nội dung bên trong slot `AD` không nhận focus (`FOCUS_BLOCK_DESCENDANTS`), TalkBack vẫn đọc được.
    Giữ cột: `AllAppsRecyclerView` nhớ tâm-x của item lưới được focus gần nhất; khi `focusSearch` từ holder lên/xuống, `SlotFocusNavigator` chọn
    item cùng cột ở hàng liền kề (`SlotFocusMath`, hỗ trợ RTL); hàng chưa layout thì `scrollToPosition` rồi focus sau layout kế tiếp.
    (`GridLayoutManager.onFocusSearchFailed` dùng `LayoutParams.mSpanIndex` của view đang focus nên không thể chỉnh qua `getSpanIndex`.)
11. **Footer che list:** khi footer đang hiện, nội dung list bị clip tại mép trên của footer (`FloatingHeaderView.setRecyclerViewBottomClip`,
    dùng chung clip rect của header; không footer thì giữ nguyên như cũ), và `DrawerSlotVisibilityTracker` không tính phần bị footer
    che là "visible" (`visibleFraction`). Khi chiều cao footer đổi, item đang focus được cuộn lại để nằm hẳn phía trên footer.
12. **Bàn phím (IME) cũng là cover:** cạnh trên của bàn phím docked (inset `WindowInsets.Type.ime()` từ API 30, dưới 30 dùng
    system inset trừ stable inset; cùng hệ toạ độ với footer) được trừ vào vùng nhìn thấy: cover hiệu dụng = `min(đỉnh footer, đỉnh IME)`
    (`combineCover`/`imeTopOf`, có test). Tracker và `DeferTrigger` đánh giá lại khi inset đổi (hiện/ẩn/đổi cỡ bàn phím). Bàn phím nổi
    (floating) không báo ime inset nên không che gì.

### Spacing (thư viện sở hữu, host chỉ trả view MATCH_PARENT)

Host **không** tự thêm margin ngoài để căn lề; mọi slot do thư viện đặt lề để nhìn nhất quán:

| Slot | Lề ngang | Khoảng cách dọc |
|---|---|---|
| Header | mép lưới app (container header kéo ra vvào padding riêng của `FloatingHeaderView`) | giữ nguyên spacing ngoài của header |
| Footer | mép lưới app (`allAppsPadding` trái/phải làm padding của frame) | `drawer_slot_footer_top_spacing` (4dp ≈ 11px @440dpi) giữa list và view footer |
| Inline | mép lưới app (holder nằm trong padding RecyclerView) | `drawer_slot_vertical_spacing_inline` (8dp ≈ 22px) **trên và dưới** |
| Search | cùng mép với các card kết quả (RecyclerView search có thêm `search_result_recycler_view_padding` 8dp) | `drawer_slot_vertical_spacing_search` (12dp ≈ 33px) **trên và dưới**; khoảng cách giữa các nhóm kết quả khoảng 17dp |

Ba dimen dọc nằm trong `host_config.xml`, host override bằng cùng tên. Khoảng cách dọc là padding của holder, nên chiều cao đo được, bảng
chiều cao slot (scroll estimate) và clip ở footer đều đã bao gồm nó. Placeholder `DeferredSlotView` và `estimatedHeightPx` chỉ là chiều
cao **của view host**; thư viện cộng spacing bên ngoài, và `DeferTrigger`/visibility chỉ tính trên chính view (không tính phần spacing).
`DrawerSlotEnv.availableWidthPx` là bề rộng view nhận được (search trừ thêm padding riêng của list search).

### Hướng dẫn cho host: khoảng cách giữa slot `AD` (chính sách AdMob)

- Quảng cáo **không được bị che/chồng** bởi UI khác (footer, thanh điều hướng, ad khác); ad bị che mà vẫn tính impression vi phạm
  chính sách AdMob. Thư viện clip list tại mép trên footer và không báo `onSlotVisible` cho phần bị footer che, nhưng host vẫn phải
  tự kiểm tra impression theo luật của SDK.
- Không đặt slot `AD` inline sát nhau: giữ **ít nhất 3 hàng app** giữa hai ad inline (`inlineRowPositions`), và không đặt ad inline
  ngay dưới ad ở header (hàng đầu tiên nên là app); tránh ad lớn (MREC) đi cùng ad banner ở footer trên màn hình nhỏ.
- Đặt khoảng đệm quanh ad để tránh click nhầm vào icon app liền kề.

## Phase 3 — A3: Slot trong kết quả search — ĐÃ LÀM

**Luồng (không làm chậm search):**

- `LawnchairSearchAlgorithm.emitWithHostSlots(query, items, callback)` là điểm ra duy nhất của kết quả search; cả
  `LawnchairLocalSearchAlgorithm`, `LawnchairAppSearchAlgorithm` (kể cả `doSearch` gọi callback trực tiếp) và
  `LawnchairASISearchAlgorithm` đều đi qua đây. Kết quả organic được phát **ngay, không đổi**.
- Song song: đợi 300ms; nếu query dài ≥ `config_drawer_slot_search_min_query` (mặc định 2, `host_config.xml`) thì gọi
  `provider.searchItemCount(query)` với timeout 1.5s. Nếu count > 0, query **vẫn là query hiện tại** và đang ở màn hình search
  (`DrawerSlotController.searchActive`, tránh kéo màn hình search quay lại sau khi đã đóng) thì phát lại kết quả có chèn
  `HostSlotAdapterItem(DrawerSlot.Search(query, i))`. Kết quả organic đến sau (Local phát nhiều lần) vẫn giữ slot nhờ count đã biết.
  `cancel()` huỷ job; zero-state (`doZeroStateSearch`) không bao giờ có slot và huỷ job.
- Vị trí (`SearchSlotPlanner`, có test): kết quả thật của Lawnchair thường **không có** `EMPTY_DIVIDER`, nên ranh giới nhóm gồm: sau mỗi
  `EMPTY_DIVIDER` và **trước** mỗi `TEXT_HEADER` (không bao giờ chen giữa header và item của nó). Slot 0 nằm ngay sau khối kết quả app
  (hết chuỗi app/shortcut đầu tiên); không có app thì ở ranh giới đầu tiên. Slot k nằm ở ranh giới kế tiếp, hết ranh giới thì xuống cuối.
- Debounce áp dụng cả khi count của query đã biết (ví dụ gõ `cam`, xoá về `ca`): view slot không bao giờ được tạo trước khi query ổn định
  300ms. Khi slot đã hiện cho query đó thì các lần phát organic sau giữ nguyên slot.

**Hiển thị:**

- Dùng lại `VIEW_TYPE_HOST_SLOT` và `HostSlotBinder` của Phase 2 (xử lý trong `BaseAllAppsAdapter`, nên `LawnchairSearchAdapterProvider` không
  cần view type mới; full-span như inline). View lấy từ `DrawerSlotController.obtainView` (cache theo `Search(query, i)`).
- Slot có background riêng (không phải `SearchAdapterItem` nên `SearchItemDecorator` không vẽ gộp nhóm).
- **Không bao giờ** là quick-launch: slot được chèn sau khi `setFirstItemQuickLaunch` đã chạy trên danh sách `SearchTargetCompat`, và nó
  không phải `SearchResultView`.
- Kind: `AD` holder focusable + chặn descendant; `CONTENT` focus bình thường (như inline).
- **Evict cache:** mỗi lần phát kết quả cho query mới, view của các query cũ bị xoá (`evictSearchSlots`) và provider nhận
  `onSlotRemoved(slot)` để giải phóng ad SDK; đóng search (`onClearSearchResult`) xoá hết. `onSlotRemoved` cũng được gọi khi
  `notifySlotsChanged`/destroy xoá cache.
- List A-Z của search (`supportsHostSlots = false`) không bao giờ có slot inline; item slot search chỉ nằm trong kết quả search.

## Defer đến khi nhìn thấy (`deferUntilVisible`, cho INLINE/SEARCH)

Một số SDK tính impression ngay khi render, bất kể bị clip (kể cả bị footer che). `provider.deferUntilVisible(slot) = true` làm thư viện giữ chỗ bằng
placeholder (`DeferredSlotView`) và chỉ gọi `createView` + gắn view khi **layout đã ổn định**: drawer mở hẳn, không còn thay đổi header/footer
đang chờ áp dụng, và slot hiện ≥50% (tính trên **toàn bộ chiều cao đã giữ chỗ**, có trừ phần footer che) trong **2 lần vẽ liên tiếp**
(`DeferTrigger`, có test). Sau đó mới gọi `onSlotVisible`. Chiều cao placeholder: chiều cao thật đã đo gần nhất của slot đó (lưu theo
process ở `LawnchairDrawerSlotRegistry.knownHeights`, sống qua việc recreate launcher), chưa có thì `provider.estimatedHeightPx(slot, env)`
(0 = không biết), cuối cùng là một hàng app. **Host nên trả ước lượng đúng cho ad kích thước cố định** (ví dụ MREC 300x250 = 250dp);
ước lượng sai làm placeholder trông hiện nhiều trong khi view thật lại bị che phần lớn.
Khuyến nghị dùng `deferUntilVisible` + `estimatedHeightPx` cho cả slot `AD` trong search. Đánh đổi: nếu view
thật cao hơn ước lượng thì nội dung bên dưới dịch xuống một lần; chiều cao được nhớ nên các lần sau không nhảy. Cách đơn giản phía host
không cần ước lượng: trả về container nhẹ từ `createView`, chỉ load/gắn AdView thật ở `onSlotVisible` đầu tiên.

## D-pad qua slot (Phase 2 bổ sung)

Holder nhớ cột của item vừa focus trước khi vào slot; rời slot theo cả hai hướng giữ đúng cột: (1) `getFocusedRect` thu thành tia hẹp
tại cột đó cho focus finder, (2) khi finder thất bại (hàng kế tiếp ngoài màn hình) `AppsGridLayoutManager.onFocusSearchFailed` cho
`GridLayoutManager` coi holder đang ở cột đó (`getSpanIndex` override tạm thời).

## Phase 4 — Tài liệu, mẫu tích hợp, kiểm thử

- `docs/drawer-slots-guide.md` viết theo kiểu `custom-overlay-guide.md`: ví dụ AdMob `NativeAdView` cho
  Inline/Search, adaptive banner cho Footer, Compose cho Header, kèm phần preload và đo impression.
- **Unit test:** chèn slot theo hàng, fast-scroller section dịch đúng, rowIndex không lệch, không chèn khi
  search/work, bỏ kết quả slot khi query đã đổi.
- **Ma trận test thủ công:** lưới 4/5/6 cột, xoay màn hình, RTL, dark/light, bật/tắt blur, tab work, private space,
  ẩn thanh search, điều hướng bàn phím, TalkBack, Android 16 back gesture.

## Thứ tự và rủi ro

| Phase | Phụ thuộc | Rủi ro |
|---|---|---|
| 0 Hạ tầng | — | Thấp |
| 1 Header/Footer | 0 | Thấp–TB (padding/insets) |
| 2 Inline | 0 | **Cao nhất** (fast scroller, rowIndex, focus) |
| 3 Search | 0 | TB (async, thứ tự kết quả) |
| 4 Docs/test | 1–3 | Thấp |

Mỗi phase là một commit riêng và có thể phát hành độc lập dưới dạng alpha.

## Các mặc định đã chọn

1. Vị trí inline mặc định: sau hàng 3, sau đó cứ 8 hàng một lần. Host có thể ghi đè qua `inlineRowPositions`.
2. Footer ẩn khi đang search hoặc bàn phím mở.
3. Không hiện slot ở zero-state search; tab work: `AD` không hiện, `CONTENT` theo `showOnWorkTab`.
4. Header nằm trong `FloatingHeaderView` (cuộn và thu gọn theo danh sách), còn footer là view cố định ở đáy drawer.

## Lưu ý chính sách (cho slot `AD`)

- Ads phải có nhãn "Ad" / "Quảng cáo" rõ ràng.
- Ads đặt sát icon app dễ gây click nhầm; giữ khoảng cách/padding hợp lý.
- Cần tự kiểm tra lại chính sách vị trí đặt ads của AdMob/Google Play cho app launcher trước khi phát hành.
