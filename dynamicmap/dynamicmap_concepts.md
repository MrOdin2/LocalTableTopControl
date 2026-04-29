# DynamicMap Runtime Concepts

This file records the current play-time design and feature decisions for DynamicMap.

Update this file whenever the DynamicMap runtime changes bundle support, rendering behavior,
standard map feature parity, scene persistence, or cross-plugin expectations.

## Core Direction

- DynamicMap is the session/runtime companion to the Dynamic Map Builder.
- It loads builder-exported gameplay bundles and lets the DM run them like standard maps.
- The runtime plugin stays decoupled from the standard Map plugin and the builder panes.
- Shared session behavior, such as tracker token sync, flows through the central `EventBus`.

## Implemented Decisions

### Plugin Identity

- The DM plugin display name is `DynamicMap`.
- The scene persistence key is `dynamicmap`.
- The runtime module is `:dynamicmap`.
- User documentation is bundled from `dynamicmap/UserDoc.html`.
- Runtime settings are stored separately from the standard Map plugin in
  `~/.tabletopcontrol/dynamicmap-settings.conf`.

### Bundle Loading

- DynamicMap loads zip-backed gameplay bundles exported by the Dynamic Map Builder.
- The accepted chooser filters are `*.dynamicmap` and `*.zip`.
- The gameplay manifest entry is `dynamic-map.properties`.
- Runtime bundle format version `1` is supported.
- Background textures are read from inside the bundle when `background.image` is present.
- The original source texture path used by the builder is not required at run time.
- Invalid or unsupported bundles show a user-facing load error instead of silently doing nothing.

### Runtime Rendering

- The loaded bundle provides map columns, rows, walls, lights, sunlight/outside areas, optional background texture,
  and background calibration.
- Loading a bundle centers the grid origin so exported cell coordinates run from `(0, 0)`
  at the top-left of the map bounds to `(cols, rows)` at the bottom-right.
- Background calibration follows the exported builder data and remains tied to the grid.
- The DM map settings expose two DynamicMap visualisation modes:
  - `RenderMode` is the normal play mode. Exported walls and lights are not drawn directly.
    Exported walls are still used as blockers for player-character sightlines.
  - `DebugMode` is the setup/verification mode. Exported walls are drawn clearly from bundle
    coordinates using the same opaque theme accent colour as the Dynamic Map Builder, and enabled
    point lights are drawn as diagnostic halos with distinct bright and dim radius areas.
    Exported sunlight/outside areas are drawn as translucent polygons for bundle verification.
- Light source point markers are visible only in `DebugMode` on the DM minimap and remain hidden
  on the player-facing table view.
- Disabled lights appear as subdued DM markers in `DebugMode`.
- Tokens whose tracker actors are marked `PC` define DynamicMap sight origins. A shared sightline
  service casts line-of-sight from those token centres through cached exported wall geometry and
  publishes one triangulated visibility mesh for all active DynamicMap renderers.
- Each PC token's sightline contribution is cached independently by token position, size, and wall
  topology. Moving one PC recomputes only that PC's contribution, then combines it with the other
  cached PC contributions.
- Sightline computation runs off the JavaFX thread. Renderers keep the last completed mesh visible
  while a newer PC move is being calculated, so token dragging does not block the UI thread.
- The DynamicMap sightline mesh is composited as its own cached Canvas image layer, drawn with the
  same style as fog of war: fully opaque black on the player-facing table view, and a transparent
  grey/black overlay on the DM minimap.
- The player-facing table view keeps an accumulated seen-area mesh. Areas seen by PCs at least once
  but not currently visible are covered with the same grey sightline tint used on the DM minimap;
  areas never seen by PCs stay fully black.
- Sightline meshes are recalculated when a PC token moves or when relevant bundle/PC token metadata
  changes; normal redraws, NPC token movement, and view pan/zoom reuse the last completed mesh.
- DynamicMap base art, manual fog, and sightline overlays are cached as separate renderer layers
  when their pixel size is within the configured cache budget.
- Player-facing NPC token visibility is based on intersection between the token's drawn circle and
  the current visible sightline mesh, so even a small exposed edge reveals the token. Previously
  seen areas do not reveal NPCs or their movement.
- Dynamic maps keep their exported orientation; standard image-map rotation is disabled in the UI.
- Standard image-map calibration is disabled because background, wall, and light geometry must stay aligned.
- The old disabled image calibration and rotation buttons are not shown in DynamicMap settings.

### Standard Map Feature Parity

- DynamicMap copies the standard Map plugin session controls for:
  - DM minimap
  - grid visibility and colour
  - grid calibration
  - fog of war paint, erase, reveal-all, and hide-all
  - tracker token sync, token dragging, token image rendering, and token names
  - measurement overlays with line, cone, rectangle, circle, units, labels, and table mirroring
  - whole-table map nudge and center controls
  - scene capture and restore
- Loading a DynamicMap bundle resets fog coverage to the exported map bounds.
- Scene restore preserves saved fog dimensions when a scene already contains fog state.

### Table View Selection

- Core now supports multiple plugin-provided table views.
- When more than one table view exists, the toolbar shows a `Table content` selector.
- The selector chooses whether the player-facing table window displays Map, DynamicMap,
  or another table-view provider.
- The physical display selector remains responsible only for showing, hiding, or moving the table window.

## Future Work Notes

- Dynamic lighting and light occlusion are not implemented yet; current lights are rendered as static halos.
  Sunlight/outside areas are loaded from gameplay bundles and visible in DebugMode, but they do not
  affect player-facing visibility until the lighting solver consumes them.
- Future door/opening rules should extend the wall-blocker model rather than bypassing the cached
  PC sightline mesh.
- If bundle format version `2` is introduced, record the migration and backward compatibility behavior here.
- If DynamicMap gains public import back into the builder, keep the runtime format rules separate from
  builder construction-site persistence.
