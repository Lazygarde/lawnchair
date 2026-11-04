# Paged App Drawer Guide

An opt-in app drawer for hosts embedding `:launcher-ui`: apps on horizontally swiped pages of a
fixed grid, in install order, rearranged by dragging inside the drawer. Off by default; with it
off the drawer is the A–Z list as before.

## Turning it on

In the host's resources (or with a `resValue`):

```xml
<bool name="config_drawer_paged">true</bool>
```

## What the user gets

| | |
|---|---|
| Pages | Swipe left / right; dots under the grid show the page. The drawer opens on page 1 unless "remember position" is on. |
| Grid | Columns = the drawer's column count (`numShownAllAppsColumns`). Rows = as many full rows as fit the drawer height, no half rows. |
| Order | Oldest install first; ties (apps preinstalled together) by name. A new install goes to the last cell. |
| Rearranging | Long press, then drag: the icons make way, the left / right edge turns the page after 0.5 s, the drop saves the order. A drop past the last app puts it last. |
| Add to Home | Drag to the "Add to Home screen" bar over the search field and hold 0.4 s: the home screen appears with the drag still going, and its top bar reads **Cancel**. Dropping on Cancel puts nothing on the home screen; dropping on a free cell adds the app as before. Either way the drawer order is unchanged. |
| Search | Unchanged: the vertical result list. |
| Swipe down | Closes the drawer from any page. |

Once the user has moved an app the order is theirs (saved as a list of component keys in
`PreferenceManager2.drawerOrder`); apps installed later still append at the end, and uninstalled
apps simply drop out.

## Where it does not apply

The vertical list is used instead when the user has a **work profile** or a **private space**
(any app of another user), for **search results**, and for the work tab. Drawer folders
(`drawerList`, the folder database) are not shown in paged mode; every app is on the grid.

## Drawer slots

- `HEADER`: unchanged; it sits in the floating header above the pages.
- `INLINE`: not inserted in paged mode (there are no rows between apps to put it in).
- `FOOTER`: sits below the page dots.
- `SEARCH`: unchanged.

## Code

| | |
|---|---|
| `app.lawnchair.allapps.paged.DrawerOrder` | Pure ordering: merge saved keys with installed apps, move one key |
| `app.lawnchair.allapps.paged.PagedGridMath` | Pure pagination |
| `app.lawnchair.allapps.paged.AllAppsPagedGrid` | The pages; drag source and drop target |
| `app.lawnchair.allapps.paged.AddToHomeBar` | The bar that hands a drag to the home screen |
| `LawnchairAlphabeticalAppsList` | `isPagedMode()`, the order, `onPagedAppsChanged` |
| `ActivityAllAppsContainerView` | Puts the grid beside the A–Z list and swaps them |

The plan with the decisions and their reasons is `docs/paged-drawer-plan.md`.
