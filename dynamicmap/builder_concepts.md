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
- It groups the current draft by element type and currently lists:
  - lights
  - walls
- Selecting an outline item should highlight the same element on the main builder canvas.
- The outline pane owns item-level management actions that are not tied to one placement tool, such as:
  - remove selected element
  - enable or disable a selected light
- Future builder element types should join the outline instead of creating separate one-off management lists.

### Shared Editing Behavior

- The active placement/editing tool is shared across builder panes through Dynamic Map builder events.
- The current builder document and current element selection are shared across builder panes through Dynamic Map builder events.
- Blank-space right-click in the builder canvas should fall through to the normal DM pane layout context menu.
- Right-click near a wall or light should show builder-specific remove actions for that element.
- Pressing `Escape` in the main builder pane should leave placement mode.
- Quarter-grid snapping (`0.25`) remains available in the main builder pane because it affects general placement, not only one side-pane.
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

- The builder keeps an internal autosave draft in `~/.tabletopcontrol/dynamic-map-builder-draft.properties`.
- That autosave format is intentionally internal and separate from the future public import/export format.
- Public Dynamic Map import/export is still undecided and should be documented here once the format is agreed.

## Future Work Notes

- When new builder panes are added, define clearly whether they own:
  - a document concern
  - a tool selection concern
  - an asset/preset browser concern
- If a future decision changes how panes coordinate, record both the UX reason and the technical rule here.
