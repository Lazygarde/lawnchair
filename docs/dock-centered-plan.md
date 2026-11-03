# Centered Dock Plan

**Goal:** an opt-in dock that holds 0 to N apps (N = 5 for Holy Quran) with no empty slots: the
apps spread evenly over the dock, the first at the left edge and the last at the right edge (3
apps: left, middle, right); a single app sits in the middle.

Decisions taken with the host on 2026-11-03:

| Question | Decision |
|---|---|
| Layout of 1–4 apps | Spread edge to edge, evenly (changed from "centered as a group" after the first device test) |
| While dragging into the dock | Icons slide to the N real slots, so the free slots show where a drop lands; after the drag they center again |
| Dock with 0 apps | Its background is hidden; it shows again while dragging so the dock can be dropped on |
| Existing installs with a 4-slot dock | Switched to N slots for everyone: Launcher3's own grid migration (`launcher_6_4_4.db` → `launcher_6_4_5.db`) keeps every app |

## Mechanism

- **Switch:** `<integer name="config_dock_centered_columns">` in `launcher-ui` `host_config.xml`,
  default `0` (off). `N > 0` turns the feature on and fixes the dock at N columns:
  `PreferenceManager.init` sets `pref_hotseatColumns` to N when it differs, as `migratePrefs`
  already does for this pref, so the grid migration runs on the next grid load.
- **Spreading, no DB change:** the hotseat page's `ShortcutAndWidgetContainer` translation
  provider (the hook the bubble bar uses; the published `withoutQuickstep` variant has no bubble
  bar) shifts each icon by `target − lp.x`, where the icons sorted by `lp.x` take ranks
  `0..n−1`, `span = contentWidth − cellWidth` and `target = rank × span / (n − 1)`
  (`span / 2` for one icon). Working in `lp.x`
  keeps RTL right, since `setupLp` already mirrors it. The math is a pure object with a unit test.
- **Drag:** `Hotseat` listens to `DragController`; on drag start every icon animates to
  translation 0 (the real slots), on drag end to the centered value. Drops, removals and folder
  merges all land before `onDragEnd`, so the icon count is final there.
- **Empty background:** hidden when the page has no icon and no drag is going; re-applied at the
  end of `setUpBackground()` (which `setInsets` re-runs) and on drag start / end / layout.
- **Guard:** centering only for a horizontal dock with one row and one page, hotseat enabled, and
  no bubble-bar adjustment. Anything else behaves as today.

Known corner (`ponytail`): a drop animates the icon to its real slot, then the recenter slides it.
