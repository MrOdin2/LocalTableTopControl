# Dynamic Map Builder Concepts

This file records the current design and functional decisions for the Dynamic Map Builder suite.

If you change builder behavior, pane responsibilities, workflow, or shared interaction rules, update this file in the same change.
Future decisions should be added here as well so the builder evolves from one consistent source of truth.

## Core Direction

- The Dynamic Map Builder should feel like one shared editor spread across multiple panes.
- The pane system exists to let the DM arrange the workspace freely, not to isolate builder subsystems from each other.
- Builder side-panes must coordinate with the main builder canvas so actions in one pane immediately affect the others.
- Inter-pane coordination should still respect the project architecture: use shared Dynamic Map builder events on the central `EventBus` instead of direct plugin references.

## Implemented Decisions

### Workspace And Identity

- The builder runs in its own DM workspace: `Dynamic Map Builder`.
- Its layout is persisted independently from the normal session workspace in `~/.tabletopcontrol/dynamic-map-builder-layout.conf`.
- The runtime/help identity is `dynamicmap_builder`, including the extracted user-doc path `~/.tabletopcontrol/userdocs/dynamicmap_builder/UserDoc.html`.

### Main Builder Pane

- The main `Dynamic Map Builder` pane is intentionally kept lean.
- It owns document-level controls such as:
  - map size
  - layer visibility
  - background texture load/clear
  - background calibration
  - the shared editing canvas
- Tool-selection buttons should not live at the top of the main builder pane.
- The status line remains the quick summary for:
  - active tool
  - selected light preset
  - wall count
  - light count
  - pointer position

### Light Workflow

- Light presets are chosen in the `Light Browser` pane.
- Selecting a preset immediately arms light placement in the main builder canvas.
- The `Light Browser` also owns light-specific session actions such as:
  - stopping placement mode
  - clearing all placed lights
- Static point lights are the only implemented light type so far.

### Wall Workflow

- Wall controls live in a dedicated `Wall Tools` pane instead of the main builder pane.
- The wall pane currently owns:
  - wall line mode
  - wall rectangle mode
  - stop editing
  - clear all walls
- Wall editing remains shared with the same main builder canvas.

### Outline Workflow

- The `Outline Browser` pane is the structural overview for placed builder elements.
- It groups the current draft by user-defined groups and by element type, currently listing:
  - groups
  - lights
  - walls
- Selecting an outline item should highlight the same element on the main builder canvas.
- Selecting a user-defined group should select every element linked to that group, using the normal shared element selection event.
- Holding Ctrl while clicking another outline item adds it to the current selection.
- Multi-selection is currently introduced through the outline only; other builder surfaces may continue to replace the selection with one item.
- Grouping actions live in the outline right-click menu to keep the pane uncluttered:
  - create a group from the current element selection
  - remove group metadata without deleting grouped elements
- Group and element naming also lives in the outline right-click menu.
- Walls, lights, and groups all carry persisted labels in the internal builder draft.
- The outline pane owns item-level management actions that are not tied to one placement tool, such as:
  - remove selected elements
  - enable or disable a selected light
- Groups are saved in the internal builder draft and are pruned automatically when their linked elements are deleted.
- Future builder element types should join the outline instead of creating separate one-off management lists.

### Construction Site Workflow

- Builder scenes are called `Construction Sites`.
- Construction-site controls live in the top-level DM toolbar and are only visible while the `Dynamic Map Builder` workspace is active.
- The construction-site toolbar owns named builder-scene lifecycle actions:
  - create a new empty construction site
  - save the current builder document as a new construction site
  - save the current active construction site
  - load an existing construction site
  - delete a saved construction-site file
  - export the current builder document as a gameplay bundle
- Choosing a construction site in the toolbar selector loads it.
- Loading a construction site replaces the current builder document and clears the shared element selection.
- Editing an active construction site autosaves back to its folder-backed file so the existing persistence workflow remains low-friction.
- The previous single draft file remains a scratch/legacy fallback when no construction site is active.

### Gameplay Export

- Gameplay export is triggered from the same top-level toolbar row as the construction-site controls.
- Export writes a `.dynamicmap` zip-backed bundle rather than another editable construction-site file.
- The first gameplay export format contains only runtime-relevant map data:
  - map dimensions
  - bundled background texture when the source image is available
  - background calibration
  - wall geometry
  - light geometry, ranges, colour, and enabled state
- Export deliberately strips editor-only metadata:
  - construction-site name
  - wall and light labels
  - element ids
  - outline groups
  - editor layer visibility
- Bundled background textures use a generic path such as `background/background.png` so source filenames and local paths are not leaked into the gameplay bundle.
- Importing gameplay bundles back into the builder is still future work.

### Shared Editing Behavior

- The active placement/editing tool is shared across builder panes through Dynamic Map builder events.
- The current builder document and current element selection are shared across builder panes through Dynamic Map builder events.
- Blank-space right-click in the builder canvas should fall through to the normal DM pane layout context menu.
- Right-click near a wall or light should show builder-specific remove actions for that element.
- With no placement tool active, primary-drag on a selected wall or light moves the selected element set.
- Primary-drag on empty canvas space still pans the workspace view.
- Pressing `Escape` in the main builder pane should leave placement mode.
- Quarter-grid snapping (`0.25`) remains available in the main builder pane because it affects general placement, not only one side-pane.
- Quarter-grid snapping also controls movement increments for dragged selected elements.
- Arrow keys nudge the selected element set when the main builder pane has focus and no placement tool is active.
- Arrow key nudge sizes are fixed editor commands:
  - plain arrows move `0.25` tile
  - Shift plus arrows move `1` tile
  - Ctrl plus arrows move by one screen pixel converted to grid units, capped to remain a fine adjustment
- Workspace zoom and pan are view-only editor controls.
- Zooming or panning must not change grid coordinates, snapping, draft geometry, background calibration, or saved map data.
- Mouse hit-testing and placement must inverse-transform through the workspace viewport before converting to map/grid space.
- Primary-drag pans when no placement tool is active; middle-drag pans at any time; mouse wheel zooms the workspace view.
- Disabled lights should remain visible in the editor as subdued markers so the outline and canvas stay in sync when lights are toggled off.

### Background Texture Handling

- Background textures are calibrated against the builder grid instead of being stretched to the full map bounds.
- New textures are auto-fit to the current map bounds on load as the starting calibration.
- Background calibration is stored in map-relative units so it stays stable when the pane is resized.
- Two calibration workflows are implemented:
  - direct numeric calibration
  - guided two-step calibration matching the standard Map plugin workflow
- Manual background calibration offset rows include both small pixel nudges and one-tile arrow nudges.
- Tile nudges move the texture by the current preview tile size and are intended for fixing whole-square alignment errors after scale is correct.

### Persistence

- Named construction sites are stored as internal builder-scene files in `~/.tabletopcontrol/dynamic-map-construction-sites/`.
- The active construction site is tracked in `~/.tabletopcontrol/dynamic-map-builder-active-site.properties`.
- The builder still keeps a scratch/legacy autosave draft in `~/.tabletopcontrol/dynamic-map-builder-draft.properties` when no named construction site is active.
- The construction-site format is intentionally internal and separate from the gameplay export bundle.
- Public Dynamic Map import is still undecided and should be documented here once the format is agreed.

## Future Work Notes

- When new builder panes are added, define clearly whether they own:
  - a document concern
  - a tool selection concern
  - an asset/preset browser concern
- If a future decision changes how panes coordinate, record both the UX reason and the technical rule here.
