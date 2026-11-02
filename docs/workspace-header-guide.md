# Workspace Header Guide

A host app embedding `:launcher-ui` can fill the pinned header of the first home screen with its own
view, in place of the Smartspace and in place of app widgets. The header is an ordinary view: it is
not bound through `AppWidgetManager`, so it does not depend on the launcher being allowed to bind
widgets (some devices refuse that for a launcher that is not the system's own).

Everything lives in `app.lawnchair.workspaceheader`. With no provider registered nothing changes:
the header is the Smartspace when `enable_smartspace` is on, and absent otherwise.

## Register a provider

From `Application.onCreate`, before the launcher loads:

```kotlin
LawnchairWorkspaceHeader.registerProvider(object : WorkspaceHeaderProvider {
    override fun rowSpan(context: Context) = 3

    override fun createView(context: Context, parent: ViewGroup): View =
        LayoutInflater.from(context).inflate(R.layout.my_home_header, parent, false)
})
```

Registering is enough to turn the header on; `enable_smartspace` and the Smartspace mode are ignored
while a provider is set.

| Member | Meaning |
|---|---|
| `rowSpan(context)` | Grid rows the header takes from the top of page 0, full width. Clamped to `1..numRows`. Called from the model's background thread too: cheap, no side effects. |
| `createView(context, parent)` | Builds the view once per launcher; the workspace keeps it across binds. `context` is the launcher activity. The view is laid out as one cell of `rowSpan` rows, so make it `match_parent` by `match_parent`. |
| `consumesHorizontalDrags()` | `false` (default): a horizontal drag that starts on the header pages the workspace. `true` for a header that scrolls sideways itself. |

## What the library reserves

`LawnchairWorkspaceHeader.reservedRows(context)` is the one answer to "how many rows of page 0 are
taken". It is used by:

- `Workspace` — the header's span, and whether page 0 may be stripped when empty;
- `LoaderCursor` — the cells that saved items may not occupy (an item saved inside them is dropped);
- `WorkspaceItemSpaceFinder` — new items never land on page 0;
- `GridSizeMigrationLogic` / `GridSizeMigrationDBController` — grid changes place items below it;
- `WorkspaceData` — page 0 always exists.

The launcher preview does not draw a host header.

## Moving from widgets

If the host used to place the header's content as widgets in `default_workspace_*.xml`, remove them
from those files. On an install that already has them saved in the rows the header now reserves, the
loader drops them, because the header owns those cells.
