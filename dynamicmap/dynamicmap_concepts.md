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
  - `RenderMode` is the normal play mode. Exported walls, light handles, and sunlight/outside
    polygons are not drawn directly. Exported walls are still used as blockers for player-character
    sightlines and static point-light bright cores.
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
- When a bundle contains authored lighting, the shared sightline service also builds a static light
  mask from enabled point lights and sunlight/outside polygons. Point lights use their exported dim
  radius as an unblocked soft reach, while their bright radius is occluded by exported walls.
  Sunlight/outside polygons are treated as always-lit authored areas. The current player-visible mesh
  is `PC sight AND authored light`, plus any PC darkvision area.
- Tracker darkvision ranges are published as token metadata in grid cells. The sightline service treats
  each PC's darkvision as another wall-blocked light contribution from that PC's token centre, reusing
  the cached PC sight contribution and clipping it to the darkvision radius rather than raycasting again.
- Renderers receive the shared static light mask together with the visible mesh and use each enabled
  light's exported colour to draw a cached tint layer. The tint is clipped to the current visible mesh,
  drawn with screen blending, and kept separate from sunlight/outside areas, which reveal visibility
  without adding a colour cast.
- Renderers also receive a darkvision-only mesh. That layer redraws the current map art in grayscale
  before the black sightline overlay is applied, so terrain revealed only by darkvision appears black
  and white instead of using normal light colour.
- Bundles without authored lights, sunlight/outside polygons, or PC darkvision keep the previous
  sight-only behavior for backward compatibility and for maps that are not ready to use lighting yet.
- Each PC token's sightline contribution is cached independently by token position, size, and wall
  topology. Moving one PC recomputes only that PC's contribution, then combines it with the other
  cached PC contributions.
- Sightline computation runs off the JavaFX thread. Renderers keep the last completed mesh visible
  while a newer PC move is being calculated, so token dragging does not block the UI thread.
- The DynamicMap visible mesh is composited as its own cached Canvas image layer, drawn with the
  same style as fog of war: fully opaque black on the player-facing table view, and a transparent
  grey/black overlay on the DM minimap.
- The player-facing table view keeps an accumulated seen-area mesh. Areas seen by PCs at least once
  but not currently visible are covered with the same grey sightline tint used on the DM minimap;
  areas never seen by PCs stay fully black.
- Visible meshes are recalculated when a PC token moves or when relevant bundle/PC token metadata
  changes; normal redraws, NPC token movement, and view pan/zoom reuse the last completed mesh.
- DynamicMap base art, manual fog, and sightline overlays are cached as separate renderer layers
  when their pixel size is within the configured cache budget.
- Player-facing NPC token visibility is based on intersection between the token's drawn circle and
  the current visible mesh, including authored lighting when active, so even a small exposed edge
  reveals the token. Previously seen areas do not reveal NPCs or their movement.
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

- Current lighting is the first static runtime slice: builder-authored point lights and sunlight/outside
  polygons are fixed to the grid and combined with PC sight. Point-light bright cores are wall-occluded,
  while dim light intentionally ignores walls as a cheap soft-light approximation.
  Light colour tint is rendered for visible point-light areas. PC darkvision is supported as a
  wall-blocked grayscale reveal from the token centre. Token-carried lights, moving lights, and typed
  light-blocker rules are still future work.
- Future door/opening rules should extend the wall-blocker model rather than bypassing the cached
  PC sightline mesh.
- If bundle format version `2` is introduced, record the migration and backward compatibility behavior here.
- If DynamicMap gains public import back into the builder, keep the runtime format rules separate from
  builder construction-site persistence.
