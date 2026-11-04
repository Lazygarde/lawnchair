# Centered Dock Plan

**Goal:** an opt-in dock that holds 0 to N apps (N = 5 for Holy Quran) with no empty slots: the
apps spread evenly over the dock, the first at the left edge and the last at the right edge (3
apps: left, middle, right); a single app sits in the middle.

Decisions taken with the host on 2026-11-03:

| Question | Decision |
|---|---|
| Layout of 1–4 apps | Spread edge to edge, evenly (changed from "centered as a group" after the first device test) |
| While dragging into the dock | One UI style: icons stay put; an app dragged over the dock opens a gap at once where its x falls between the icons, or at either end, and the icons make way. (The first version slid icons to the N real slots at drag start; on a phone whose dock had gaps that jumped them aside and made dropping hard.) |
| Dock with 0 apps | Its background is hidden; it shows again while dragging so the dock can be dropped on |
| Existing installs with a 4-slot dock | Switched to N slots for everyone: Launcher3's own grid migration (`launcher_6_4_4.db` → `launcher_6_4_5.db`) keeps every app |

## Mechanism

- **Switch:** `<integer name="config_dock_centered_columns">` in `launcher-ui` `host_config.xml`,
  default `0` (off). `N > 0` turns the feature on and fixes the dock at N columns:
  `PreferenceManager.init` sets `pref_hotseatColumns` to N when it differs, before it listens to
  pref changes, so the grid migration runs on the next grid load.
- **One cell per icon:** `Hotseat.updateDockCells` packs the dock's icons into cells `0..n−1`
  (saving any position that moved through `ModelWriter.moveItemInDatabase`) and sets the page's
  grid to `n` columns, re-marking the occupied cells `setGridSize` forgets and re-measuring the
  icons' container. It runs when binding finishes, after apps are removed, and after every drag.
- **Gap for a drop:** while a drag is over the dock, `Workspace.onDragOver` asks
  `Hotseat.onDockDragOver` instead of running Launcher3's timed reorder. The dragged app's x is
  compared with the centers the icons show (`DockCentering.center`): between two icons, or past
  either end, the gap opens there at once (layout only, the icons slide); over an icon's middle the
  gap stays and that icon is the target, so hovering still makes a folder. The drop
  (`Workspace.onDrop` / `onDropExternal`) lands in the gap through `Hotseat.getDockDropCell`.
  A spread dock takes the drag across the whole width, so both screen edges reach it. Leaving the
  dock for a home page takes the gap back (or puts it at the dragged icon's own cell), and the
  `null` layout that comes before every drop is ignored. A full dock falls back to Launcher3's
  reorder. The pack after the drag compares against the saved cell, not the laid out one.
- **Edge to edge:** the hotseat page's translation provider (the hook the bubble bar uses; the
  published `withoutQuickstep` variant has no bubble bar) shifts each icon inside its own cell
  (`DockCentering.offset`) so the first sits at the left edge, the last at the right edge, at the
  full dock's spacing; one icon sits in the middle. The icon never leaves its cell, so the cell a
  drop lands in is the one it shows in. The cell index comes from `lp.x`, so RTL works the same.
- **Empty background:** hidden when the dock has no icon and no drag is going.
- **Guard:** only for a horizontal dock with one row and one page, and no bubble-bar adjustment.

Known corner (`ponytail`): an icon added to the dock by code rather than by a drag (none in Holy Quran) lands in a cell past the packed grid until the next pack.
