# App Drawer Slots Guide

This guide explains how host applications can put their own views into Lawnchair's app drawer when embedding the `:launcher-ui` library: **ads** (native, banner, MREC) or **regular content** (a "continue reading" card, a promo, a prayer-time widget, a shortcut row).

Everything lives in the package `app.lawnchair.drawerslots`. The feature is off by default and is a complete no-op (no extra adapter items, padding or header height) until you enable it and register a provider.

---

## 1. Overview

A **slot** is a place in the drawer where a view of yours can appear:

| Slot type | Where it is | Notes |
|---|---|---|
| `HEADER` | Right under the drawer search bar, inside the header that collapses when the list scrolls | Hidden while a search is active |
| `FOOTER` | Pinned to the bottom of the drawer, above the navigation bar | Hidden while searching, while the keyboard is open and on the Work tab |
| `INLINE` | A full-width row between the rows of the app grid | Inserted at row boundaries you choose |
| `SEARCH` | A full-width item inside search results | Inserted after a result group, after a debounce |

Each slot has a **kind**, which you declare per slot (`LawnchairDrawerSlotProvider.kindOf`):

| Kind | Meaning | Policies |
|---|---|---|
| `AD` (default) | Advertising | Keyboard/D-pad focus can rest on the slot but never enters its content and never clicks it; hidden on the Work tab; never the search quick-launch / Enter target |
| `CONTENT` | Your own UI | Normal focus inside the view; Work-tab visibility is your choice (`showOnWorkTab`); still never the quick-launch / Enter target |

`AD` is the default on purpose: it is the conservative policy, so a slot you forgot to classify cannot be opened by the Enter key.

### What the library does for you

- **Placement**: header and footer containers, inline rows at the row positions you return, search items after the first result group.
- **Spacing**: slots line up with the app grid edges (search slots with the result cards) and get consistent vertical gaps. You return a `MATCH_PARENT`-width view and add no outer margins.
- **Caching and re-parenting**: each slot keeps exactly one view. When the list rebinds, the view is moved to the new holder; `createView` is not called again, so an ad is not reloaded.
- **Theming**: your view is inflated with a themed context so Material/AppCompat widgets work (the launcher's own theme is not Material).
- **Footer clip**: while the footer is shown, list content is clipped at its top edge so nothing is drawn underneath it.
- **Visibility tracking**: `onSlotVisible` / `onSlotHidden` while the drawer is fully open and the slot is at least 50% visible, excluding the part covered by the footer or a docked keyboard.
- **D-pad**: arrow-key navigation passes through slots and keeps the grid column.
- **Safety**: a slot is never counted as an app for accessibility counts, never the IME focused child and never launched by Enter.

---

## 2. Host App Configuration

### Step 1: Add Dependency

Same repository and coordinates as the other guides (see `docs/publishing.md`):

```kotlin
dependencies {
    implementation("com.github.lazygarde.lawnchair:launcher-ui:<version>")
}
```

Add your ad SDK separately if you serve ads, for example the Google Mobile Ads SDK (`com.google.android.gms:play-services-ads`).

### Step 2: Enable the feature

In your host app's `res/values/config.xml` (or via `resValue`):

```xml
<resources>
    <!-- Master switch. Default is false. -->
    <bool name="config_enable_drawer_slots">true</bool>

    <!-- Minimum search query length before searchItemCount() is asked. Default 2. -->
    <integer name="config_drawer_slot_search_min_query">2</integer>
</resources>
```

Even when enabled, nothing is shown until a provider is registered and its `isSlotEnabled(type)` returns true.

### Step 3: Theme for your layouts (only if you inflate Material/AppCompat widgets)

The launcher activity theme is not `Theme.MaterialComponents`, so inflating a `MaterialButton` with it throws *"The style on this component requires your app theme to be Theme.MaterialComponents"*. The library wraps the launcher in an overlay style named `LawnchairDrawerSlotTheme` (empty by default). Override it with the same name:

```xml
<style name="LawnchairDrawerSlotTheme" parent="Theme.Material3.DayNight.NoActionBar" />
```

Inflate with `LayoutInflater.from(parent.context)` (what `registerView` does) or `env.themedContext`. The `context` argument of `createView` is still the launcher **Activity**, so ad SDK loaders that need an Activity get one; just do not inflate Material layouts with it.

### Step 4: Spacing (optional)

The library owns slot spacing. Defaults, overridable by declaring a dimen with the same name:

| Dimen | Default | Applies to |
|---|---|---|
| `drawer_slot_vertical_spacing_inline` | `8dp` | Gap above **and** below an inline slot |
| `drawer_slot_vertical_spacing_search` | `12dp` | Gap above **and** below a search slot |
| `drawer_slot_footer_top_spacing` | `4dp` | Gap between the list and the footer view |

Side insets are not configurable. Header, footer and inline slots align with the app grid; search slots align with the search result cards.

### Step 5: Register from your Application class

```kotlin
class MyApp : LawnchairApp() {
    override fun onCreate() {
        super.onCreate()
        // Register slots here (see below)
    }
}
```

---

## 3. Quick Start: regular content with the helpers

For non-ad UI the helpers are enough. They install an internal per-type provider and default to `kind = CONTENT`.

### Compose

```kotlin
import app.lawnchair.drawerslots.DrawerSlot
import app.lawnchair.drawerslots.LawnchairDrawerSlotRegistry

LawnchairDrawerSlotRegistry.setComposeContent(DrawerSlot.Type.HEADER) { slot ->
    ContinueReadingCard(
        onOpen = { ReaderActivity.start(context = ..., resume = true) },
    )
}
```

### XML layout

```kotlin
LawnchairDrawerSlotRegistry.registerView(
    R.layout.drawer_continue_reading,
    DrawerSlot.Type.HEADER,
    showOnWorkTab = false,
) { slot, view ->
    view.findViewById<View>(R.id.btn_open).setOnClickListener { ReaderActivity.start(view.context) }
}
```

### View factory

```kotlin
LawnchairDrawerSlotRegistry.registerFactory(DrawerSlot.Type.FOOTER) { context, parent, slot, env ->
    PrayerTimeBar(env.themedContext)
}
```

All three accept `kind` (pass `DrawerSlotKind.AD` for ads) and `showOnWorkTab`. Calling a helper again for another type adds to the same internal provider. The helpers do not support `deferUntilVisible`, `inlineRowPositions` or `searchItemCount`; implement a provider for those.

---

## 4. Full Provider Example

A provider that serves a native ad in the header, an adaptive banner in the footer, three inline slots (native ad, fixed-size MREC with deferred creation, a content card) and search slots.

> Real apps must **not** hard-code ad unit IDs like the placeholders below. Load them from build configuration, and use the SDK's official test unit IDs while developing.

```kotlin
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.lawnchair.drawerslots.DrawerSlot
import app.lawnchair.drawerslots.DrawerSlotEnv
import app.lawnchair.drawerslots.DrawerSlotKind
import app.lawnchair.drawerslots.LawnchairDrawerSlotProvider
import app.lawnchair.drawerslots.LawnchairDrawerSlotRegistry

private const val HEADER_NATIVE_UNIT = "AD_UNIT_ID_HEADER_NATIVE" // placeholder
private const val FOOTER_BANNER_UNIT = "AD_UNIT_ID_FOOTER_BANNER" // placeholder
private const val INLINE_NATIVE_UNIT = "AD_UNIT_ID_INLINE_NATIVE" // placeholder
private const val INLINE_MREC_UNIT = "AD_UNIT_ID_INLINE_MREC"     // placeholder
private const val SEARCH_NATIVE_UNIT = "AD_UNIT_ID_SEARCH_NATIVE" // placeholder

class MyDrawerSlots(private val appContext: Context) : LawnchairDrawerSlotProvider {

    // Loaded ads / views by slot key, so they can be destroyed in onSlotRemoved / onDestroy.
    private val adViews = HashMap<String, Any>()

    // Preloaded native ads; see "Preload strategy" below.
    private var preloadedHeaderAd: NativeAd? = null

    override fun isSlotEnabled(type: DrawerSlot.Type) = true

    override fun kindOf(slot: DrawerSlot): DrawerSlotKind = when {
        slot is DrawerSlot.Inline && slot.index == 2 -> DrawerSlotKind.CONTENT // the promo card
        else -> DrawerSlotKind.AD
    }

    // Content slots may show in the Work tab's list; ads never do.
    override fun showOnWorkTab(slot: DrawerSlot) = false

    // After row 3, 8 and 14 of app rows. Positions below 1 are ignored, positions at or past the
    // last row are skipped, slots are keyed by their index in this list.
    override fun inlineRowPositions(appRowCount: Int): List<Int> = listOf(3, 8, 14)

    // Create the MREC and the search ads only when they are really on screen (see section 8).
    override fun deferUntilVisible(slot: DrawerSlot): Boolean = when (slot) {
        is DrawerSlot.Inline -> slot.index == 1
        is DrawerSlot.Search -> true
        else -> false
    }

    // Exact height of a 300x250 MREC (250dp), so the placeholder has the right size from frame one.
    override fun estimatedHeightPx(slot: DrawerSlot, env: DrawerSlotEnv): Int = when {
        slot is DrawerSlot.Inline && slot.index == 1 -> (250 * appContext.resources.displayMetrics.density).toInt()
        else -> 0
    }

    override fun createView(
        context: Context,       // the launcher Activity: pass it to ad loaders that need one
        parent: ViewGroup,      // themed, detached: use parent.context to inflate, do not attach to it
        slot: DrawerSlot,
        env: DrawerSlotEnv,
    ): View? = when (slot) {
        DrawerSlot.Header -> nativeAdView(context, parent, slot, HEADER_NATIVE_UNIT)
        DrawerSlot.Footer -> bannerView(context, slot)
        is DrawerSlot.Inline -> when (slot.index) {
            0 -> nativeAdView(context, parent, slot, INLINE_NATIVE_UNIT)
            1 -> mrecView(context, slot)
            else -> promoCard(parent)
        }
        is DrawerSlot.Search -> nativeAdView(context, parent, slot, SEARCH_NATIVE_UNIT)
    }

    // Search: called off the hot path (query stable ~300 ms, >= config min length, ~1.5 s timeout).
    // Return how many items to show; organic results are never delayed by this.
    override suspend fun searchItemCount(query: String): Int = if (adLoader.hasFill(query)) 1 else 0

    override fun onSlotVisible(slot: DrawerSlot) {
        // A visibility signal, NOT a billable impression. Let the SDK decide what counts.
    }

    override fun onSlotHidden(slot: DrawerSlot) = Unit

    // The slot's cached view was dropped (query changed, notifySlotsChanged, search closed, destroy).
    override fun onSlotRemoved(slot: DrawerSlot) {
        adViews.remove(slot.key)?.let(::destroyAd)
    }

    override fun onDrawerOpened() = preloadAds()
    override fun onDrawerClosed() = Unit

    // Also called on launcher recreation (e.g. dark mode). The provider stays registered and
    // createView may be called again afterwards, so release views but keep the provider usable.
    override fun onDestroy() {
        adViews.values.forEach(::destroyAd)
        adViews.clear()
    }

    private fun preloadAds() {
        loadNativeAd(HEADER_NATIVE_UNIT) { ad ->
            preloadedHeaderAd = ad
            // Tell the drawer the header changed: it drops the cached view and calls createView again.
            LawnchairDrawerSlotRegistry.notifySlotsChanged(DrawerSlot.Type.HEADER)
        }
    }

    // --- view builders (sketches; adapt to your ad SDK) ---

    private fun nativeAdView(context: Context, parent: ViewGroup, slot: DrawerSlot, unit: String): View {
        // Return a cheap container immediately and fill it when the ad arrives; do not block here.
        val container = FrameLayout(parent.context)
        loadNativeAd(unit) { ad ->
            val view = NativeAdTemplate.inflate(parent.context, container, ad) // a NativeAdView layout
            container.addView(view)
            adViews[slot.key] = ad
        }
        return container
    }

    private fun bannerView(context: Context, slot: DrawerSlot): View {
        val banner = AdView(context).apply {                 // AdView needs an Activity context
            adUnitId = FOOTER_BANNER_UNIT
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp()))
            loadAd(AdRequest.Builder().build())
        }
        adViews[slot.key] = banner
        return banner
    }

    private fun mrecView(context: Context, slot: DrawerSlot): View {
        val mrec = AdView(context).apply {
            adUnitId = INLINE_MREC_UNIT
            setAdSize(AdSize.MEDIUM_RECTANGLE)
            loadAd(AdRequest.Builder().build())
        }
        adViews[slot.key] = mrec
        return mrec
    }

    private fun promoCard(parent: ViewGroup): View =
        android.view.LayoutInflater.from(parent.context).inflate(R.layout.drawer_promo_card, parent, false)
}
```

Register it once (usually in `Application.onCreate`):

```kotlin
LawnchairDrawerSlotRegistry.registerProvider(MyDrawerSlots(this))
```

### Preload strategy

`createView` for an `INLINE` slot runs synchronously while the app list is being built, so it must return fast. Return a lightweight container and fill it asynchronously (as `nativeAdView` does), or preload ads ahead of time (for example in `onDrawerOpened`, or when the launcher starts) and hand a ready ad to `createView`. When an ad becomes available, call `LawnchairDrawerSlotRegistry.notifySlotsChanged(type)` so the drawer drops the cached view and calls `createView` again.

### Releasing resources

Destroy ad objects in `onSlotRemoved(slot)` (search slots are evicted whenever the query changes, so this is how stale-query ads are released) and in `onDestroy()`. Compose views registered through `setComposeContent` are disposed by the library when removed.

---

## 5. Per-Type Providers

You can register a global provider plus one provider per slot type. A slot is served by the per-type provider if there is one, otherwise by the global provider; `isSlotEnabled` is asked of the resolved provider.

```kotlin
LawnchairDrawerSlotRegistry.registerProvider(MyDrawerSlots(this))                       // everything
LawnchairDrawerSlotRegistry.registerProvider(DrawerSlot.Type.HEADER, MyPromoProvider()) // overrides HEADER only
LawnchairDrawerSlotRegistry.clear(DrawerSlot.Type.HEADER)                                // back to the global one
LawnchairDrawerSlotRegistry.clear()                                                      // remove everything
```

The helpers (`registerView`, `registerFactory`, `setComposeContent`) install an internal per-type provider for the types you pass; they never replace your global provider.

Callback routing: `onDrawerOpened`, `onDrawerClosed` and `onDestroy` go to every distinct registered provider exactly once (a provider registered for several types receives each once). `onSlotVisible`, `onSlotHidden` and `onSlotRemoved` go to the provider that created that slot. If you register or clear a provider while the drawer is open, removed providers receive `onDrawerClosed` and new ones `onDrawerOpened`.

---

## 6. Callbacks Reference

All callbacks run on the main thread.

| Callback | When |
|---|---|
| `onDrawerOpened()` | The launcher state `ALL_APPS` was committed and the activity is started. An aborted pull-down does not fire it. |
| `onDrawerClosed()` | The drawer left that state: closed, an app was launched from it, or the launcher stopped. Always delivered before `onDestroy` if the drawer was open. |
| `onSlotVisible(slot)` | The slot is at least 50% visible while the drawer is open. The part covered by the footer or a docked keyboard does not count. **A visibility signal, not a billable impression**: it can fire repeatedly (scrolling, tab changes, reopening). For deferred slots it fires after `createView`. |
| `onSlotHidden(slot)` | The slot stopped being visible (scrolled away, drawer closed, covered, view removed). |
| `onSlotRemoved(slot)` | The slot's cached view was dropped: `notifySlotsChanged`, a new search query, search closed, or launcher destroyed. Release its resources. |
| `onDestroy()` | The launcher instance is being destroyed, **including on recreation**. The provider stays registered and `createView` may be called again by the next instance. |

---

## 7. Rules and Contracts

### `createView`
- It runs on the main thread. For `INLINE` it runs synchronously during list builds (and again on any rebuild that finds no cached view), so it must return fast. Do the slow work asynchronously.
- Return a view that is happy with a `MATCH_PARENT` width. Height may be fixed or wrap content. The library adds the side insets and vertical spacing itself, so do not add outer margins.
- Return `null` to show nothing: the slot takes no space (an inline or search item is simply not inserted).
- Do not attach the view to `parent`; it is a themed, detached parent for `LayoutInflater` and layout params only. The library attaches and re-parents the view.
- The view is created once per slot and reused; do not rely on being called per bind. To get a new view (for example a new ad), call `notifySlotsChanged(type)`.
- `DrawerSlotEnv.availableWidthPx` is the width your view gets. `themedContext`, colors, `isDarkMode` and `hasBlur` describe the drawer's look.

### Kinds
`kindOf` defaults to `AD` for providers and `CONTENT` for the helpers. The kind is read when a view is created, so changing it takes effect after `notifySlotsChanged(type)`.

### Work tab
- `AD` slots never show in the Work tab (header hidden there, no inline items in the Work list, footer hidden).
- `CONTENT` header and inline slots show there only if `showOnWorkTab(slot)` is true. Inline slots in the Work list are separate instances from those in the main list.
- The footer is hidden on the Work tab for both kinds, because the work profile's pause bar uses the same bottom edge.

### Header and footer
- The header is hidden while a search is active (the whole header is hidden by the drawer). It collapses with the list on scroll. If you register your provider late, the header row still picks it up.
- The footer is hidden while searching, while the keyboard is open, and on the Work tab. List content is clipped at its top.
- Changes to the header height (for example an ad that loads late) are deferred while the drawer is open and the list is scrolled, so the list does not jump to the top; they apply when the drawer closes.

### Inline placement
- `inlineRowPositions(appRowCount)` returns "after app row N" positions. `appRowCount` counts icon and folder rows before any slot is inserted.
- Values below 1 are ignored, duplicates are removed, positions at or past the last row are skipped. Slot indices follow the sorted list.
- Slots go only at row boundaries, never in search results, and never inside the private space section. Both the automatic category mode and the folder drawer mode are supported.
- A slot whose `createView` returns `null` is not inserted.

### Search placement
- Organic results are emitted immediately and unchanged. In parallel, after the query has been stable for 300 ms and is at least `config_drawer_slot_search_min_query` characters, `searchItemCount(query)` is called with a 1.5 s timeout. If it returns more than 0 and the query is still current, the results are emitted again with the slots inserted.
- Slot 0 goes right after the app results; the next slots go after the following result groups. Two slots are never stacked without a visible result between them; if there is no room, the slot is dropped.
- There are no search slots in the zero-state (empty query) search.
- Search slots are never the quick-launch item, so Enter opens the first app, not an ad.
- Views of previous queries are evicted when the query changes (`onSlotRemoved`).

### Focus and accessibility
- `AD` slots are keyboard focus stops (so D-pad navigation does not get stuck behind a tall slot) but are never clickable and their content never takes focus. TalkBack can still read them.
- `CONTENT` slots take focus normally inside the view.

---

## 8. Ad Policy and Impressions

- **Impressions are counted by your SDK, on render.** Some SDKs (AdMob banners and MRECs) count an impression as soon as the ad renders, whether or not it is visible. A tall slot partly under the footer or keyboard would be counted while mostly hidden. Overlapping or hidden ads violate AdMob policy.
- **Use `deferUntilVisible`** for inline and search ads. The library reserves the slot with a placeholder and calls `createView` only when the layout is settled and the slot has been at least 50% visible on two consecutive frames (with the footer and keyboard excluded). `onSlotVisible` is then delivered after `createView`.
- **Give a correct `estimatedHeightPx`** for fixed-size ads (a 300x250 MREC is 250dp). A wrong estimate makes the placeholder look mostly visible while the real view would be mostly covered, and the view is created too early. The real height is remembered for the process after the first measurement; without any estimate one app row is used. If the real view is taller than the estimate, the content below moves once.
- The lightweight alternative that needs no estimate: return a small container from `createView` and only load or attach the real ad in the first `onSlotVisible` for that slot.
- `deferUntilVisible` applies to `INLINE` and `SEARCH` only; header and footer ignore it.
- **Spacing guidance**: keep at least 3 app rows between inline ads, do not put an inline ad right under a header ad (make the first row apps), and be careful combining an MREC with a footer banner on small screens.
- **Labeling**: ads must carry a clear "Ad" / "Sponsored" label. Check the AdMob and Google Play placement policies for launchers before release.
- A visibility callback is not a billing event. Always follow your ad SDK's own impression rules.

---

## 9. Testing Tips

- **Use test ad units** from your SDK while developing; never click your own production ads.
- **Debug-only provider**: register a test provider only in debug builds, for example from a `ContentProvider` declared in the debug manifest (its `onCreate` runs before `Application.onCreate` in the host process), or behind `BuildConfig.DEBUG` in your `Application`.
- **Enable logging**: `adb shell setprop log.tag.DrawerSlots DEBUG`, then `adb logcat -s DrawerSlots`. When search slots are planned it logs the result-kind list and the chosen insertion indices.
- **Try these cases**: 4, 5 and 6 columns, rotation, RTL, dark mode (recreation with the drawer open), the Work tab, the private space, search with and without app results, a docked and a floating keyboard, D-pad and TalkBack, and the Android 16 back gesture.
- **Check impressions** in your ad SDK's validator against what you see on screen: nothing should be counted while covered.

---

## 10. API Reference Summary

```kotlin
enum class DrawerSlotKind { AD, CONTENT }

sealed class DrawerSlot {
    enum class Type { HEADER, FOOTER, INLINE, SEARCH }
    abstract val type: Type
    abstract val key: String                      // stable
    object Header : DrawerSlot()
    object Footer : DrawerSlot()
    data class Inline(val index: Int, val inWorkTab: Boolean = false) : DrawerSlot()
    data class Search(val query: String, val index: Int) : DrawerSlot()
}

data class DrawerSlotEnv(
    val themedContext: Context,
    val backgroundColor: Int,
    val textColor: Int,
    val isDarkMode: Boolean,
    val hasBlur: Boolean,
    val availableWidthPx: Int,
)

interface LawnchairDrawerSlotProvider {
    fun isSlotEnabled(type: DrawerSlot.Type): Boolean = false
    fun kindOf(slot: DrawerSlot): DrawerSlotKind = DrawerSlotKind.AD
    fun showOnWorkTab(slot: DrawerSlot): Boolean = false
    fun deferUntilVisible(slot: DrawerSlot): Boolean = false            // INLINE / SEARCH only
    fun estimatedHeightPx(slot: DrawerSlot, env: DrawerSlotEnv): Int = 0 // 0 = unknown
    fun inlineRowPositions(appRowCount: Int): List<Int> = emptyList()
    fun createView(context: Context, parent: ViewGroup, slot: DrawerSlot, env: DrawerSlotEnv): View?
    fun onSlotRemoved(slot: DrawerSlot) {}
    suspend fun searchItemCount(query: String): Int = 0
    fun onDrawerOpened() {}
    fun onDrawerClosed() {}
    fun onSlotVisible(slot: DrawerSlot) {}
    fun onSlotHidden(slot: DrawerSlot) {}
    fun onDestroy() {}
}

object LawnchairDrawerSlotRegistry {
    fun registerProvider(provider: LawnchairDrawerSlotProvider)
    fun registerProvider(type: DrawerSlot.Type, provider: LawnchairDrawerSlotProvider)
    fun registerView(@LayoutRes layoutResId: Int, vararg types: DrawerSlot.Type = HEADER + FOOTER,
        kind: DrawerSlotKind = CONTENT, showOnWorkTab: Boolean = false, onViewBound: ((DrawerSlot, View) -> Unit)? = null)
    fun registerFactory(vararg types: DrawerSlot.Type, kind: DrawerSlotKind = CONTENT,
        showOnWorkTab: Boolean = false, factory: (Context, ViewGroup, DrawerSlot, DrawerSlotEnv) -> View?)
    fun setComposeContent(vararg types: DrawerSlot.Type, kind: DrawerSlotKind = CONTENT,
        showOnWorkTab: Boolean = false, content: @Composable (DrawerSlot) -> Unit)
    fun notifySlotsChanged(type: DrawerSlot.Type? = null)   // any thread; drops cached views, re-creates
    fun clear()
    fun clear(type: DrawerSlot.Type)
    fun isEnabled(context: Context): Boolean
    fun resolveProvider(type: DrawerSlot.Type): LawnchairDrawerSlotProvider?
    val hasProvider: Boolean
    val changes: SharedFlow<DrawerSlotChange>
}
```

Resources: `config_enable_drawer_slots` (bool, default `false`), `config_drawer_slot_search_min_query` (integer, default `2`), style `LawnchairDrawerSlotTheme` (default empty), dimens `drawer_slot_vertical_spacing_inline` (`8dp`), `drawer_slot_vertical_spacing_search` (`12dp`), `drawer_slot_footer_top_spacing` (`4dp`).

For the design background and the reasoning behind these rules, see `docs/drawer-slots-plan.md`.
