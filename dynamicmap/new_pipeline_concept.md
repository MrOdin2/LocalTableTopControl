# DynamicMap New Rendering Pipeline Concept

## Goals

The new pipeline should keep DynamicMap event-driven and JavaFX-only while making PC movement responsive on wall-heavy maps.

Primary goals:

- no synchronous visibility rebuilds on the JavaFX event thread;
- no duplicated visibility computation between the table view and DM minimap;
- cache static map, grid, fog, visibility, light, token image, and debug layers separately;
- redraw only dirty layers;
- use JavaFX Canvas and JavaFX image primitives for final rendering;
- avoid `java.awt.geom.Area` in the movement hot path;
- support dynamic light, persistence of vision, transparent walls, one-way sight blockers, and doors.

Non-goals:

- no real-time render loop;
- no WebView, WebGL, OpenGL, or third-party UI stack;
- no required internet/runtime dependency.

## High-Level Architecture

```text
EventBus
  -> DynamicMapRenderStateStore
       owns scene state, view-independent tokens, fog, walls, doors, lights
  -> RenderInvalidationQueue
       coalesces dirty flags and render revisions
  -> VisibilityEngine worker
       computes sight/light masks off the JavaFX thread
  -> LayerCache
       stores world-space cached images and masks
  -> CanvasViewRenderer instances
       table view and minimap compose shared caches with different view settings
```

The key split is:

- `RenderStateStore` owns what the scene is.
- `VisibilityEngine` owns what can be seen or lit.
- `LayerCache` owns reusable images and masks.
- `CanvasViewRenderer` owns only a Canvas, a viewport transform, and view-specific composition rules.

The table view and minimap should subscribe to completed render frames, not recompute scene visibility independently.

## Core Data Types

Suggested model types:

```kotlin
data class DynamicMapRenderScene(
    val bundleId: String?,
    val cols: Int,
    val rows: Int,
    val background: BackgroundSpec?,
    val grid: GridSpec,
    val walls: List<WallSegment>,
    val doors: Map<String, DoorState>,
    val lights: List<LightSource>,
    val tokens: List<TokenRenderState>,
    val manualFog: FogMaskState,
    val renderVersions: RenderVersions,
)

data class RenderView(
    val kind: RenderViewKind, // TABLE or DM_MINIMAP
    val canvasWidth: Double,
    val canvasHeight: Double,
    val viewport: ViewportTransform,
    val fogOpacity: Double,
    val sightOpacity: Double,
    val showDebugGeometry: Boolean,
)

data class VisibilityFrame(
    val revision: Long,
    val currentSightMask: AlphaMask,
    val currentLightMask: LightMask,
    val currentlyVisibleMask: AlphaMask,
    val previouslySeenMask: AlphaMask,
    val tokenVisibility: Map<String, TokenVisibility>,
)
```

`RenderVersions` should contain separate counters for static map content, grid calibration, wall topology, door state, light topology, manual fog, token positions, token art, measurements, and view transforms. Dirty checks should compare those counters rather than falling back to a full redraw.

## Layer Model

Render all finite map-bound layers in world/map pixel coordinates, then let each view draw those images through its viewport transform.

Recommended cached layers:

| Layer | Contains | Dirty when |
|---|---|---|
| Static map color | surface fill, background image, static map decoration | bundle, background image, background calibration, grid cell pixel size |
| Static map grayscale | grayscale copy of static map color for remembered areas | static map color changes |
| Grid layer | grid lines clipped to map bounds or configured world bounds | grid config/calibration changes |
| Debug geometry layer | wall and door outlines, light handles, labels if needed | wall/light/door/debug theme changes |
| Manual fog mask | manual fog alpha by cell | fog paint/reset/setup changes |
| Sight mask | geometric PC sight before light/fog | PC origins, sight-blocking topology, door state |
| Light mask | bright/dim light intensity after occlusion | lights, light-blocking topology, door state |
| Current visibility mask | `sightMask AND lightMask`, plus special vision rules | sight or light changes |
| Seen mask | accumulated `seenMask OR currentVisibilityMask` | current visibility advances, fog reset, scene load |
| Token image cache | decoded token images by URI | token image URI/calibration changes |
| Token visibility cache | per-token visible/hidden/partial classification | token positions, current visibility mask, manual fog |
| Measurement layer | active measurement overlays | measurements or view mirror flags change |

The onscreen Canvas remains the final output target. Cached layers can be `WritableImage`, `PixelBuffer`-backed images, or snapshots of offscreen JavaFX `Canvas` layers. Heavy pixel/mask generation should happen off the JavaFX thread when it does not touch JavaFX scene graph objects.

## Player View Composition

For the player-facing table view:

```text
clear canvas
draw visibility-composited map image:
  - current visible pixels: color static map
  - previously seen but not current: grayscale static map
  - unseen: black
draw manual fog overlay, if manual fog hides additional cells
draw currently visible tokens only
draw mirrored measurements
```

Token rules:

- NPCs are visible only if their footprint intersects `currentlyVisibleMask` and is not hidden by manual fog.
- PCs can be rendered even if outside current light if the game rules require it, but that should be an explicit rule in `TokenVisibility`.
- Tokens in remembered-only areas should not be drawn as live objects. Persistence of vision remembers terrain, not current creature positions.

## DM Minimap Composition

For the DM minimap:

```text
clear canvas
draw color static map
draw grid
draw all tokens
draw translucent manual fog overlay
draw translucent current sight/light overlay
draw remembered-area overlay if enabled
draw measurements
draw debug geometry when DebugMode is active
draw table viewport outline
```

The minimap should reuse the same layer caches and visibility frame as the table view. Its main difference is opacity and debug visibility, not recomputation.

## PC Movement Path

A PC move should follow this path:

```text
TokenMovedEvent
  -> RenderStateStore updates token position
  -> dirty: token positions, moved PC sight contribution, current visibility, token visibility
  -> Canvas views optionally compose immediately with last completed visibility frame
  -> VisibilityEngine receives revision N
  -> worker computes only changed PC contribution and derived masks
  -> JavaFX thread accepts completed frame if revision N is still current
  -> views recompose cached layers
```

Important behavior:

- Keep the last valid visibility frame onscreen while a new one computes.
- Coalesce rapid token moves so obsolete visibility jobs are abandoned.
- During drag, allow a fast preview mask at lower resolution or lower ray precision.
- On mouse release, compute the exact final mask.
- Never let a stale worker result overwrite a newer render revision.

## Visibility Engine

### Geometry Preprocessing

Build a `VisibilityGeometryCache` when the bundle loads or door/wall topology changes.

It should contain:

- normalized wall segments in map coordinates;
- map border segments;
- unique endpoint list with aggressive dedupe;
- segment bounding boxes;
- wall behavior flags;
- spatial index, such as a uniform grid or BVH;
- topology version number.

This avoids rebuilding static ray input on every PC move.

### Ray Casting

For each PC origin:

1. Use cached unique endpoints and map corners to build ray angles.
2. Add small offset angles around blockers only where needed.
3. Query the spatial index to test likely segments first.
4. Keep nearest valid hit for each angle.
5. Produce a visibility polygon or fan for that PC.
6. Cache the result by `(origin cell/subcell, token size, sight rules, topology version)`.

With spatial indexing, a ray should test nearby candidate segments instead of every wall.

### Mask Generation

Convert PC visibility polygons into a raster `AlphaMask` in map pixel coordinates:

- use scanline polygon fill or a small dedicated rasterizer;
- OR multiple PC masks together;
- maintain a contribution count or per-PC mask cache so moving one PC can remove its old contribution and add the new one;
- avoid AWT `Area` union/subtract operations;
- expose both a precise mask and an optional lower-resolution preview mask.

The hidden overlay should be derived from masks during composition, not from an AWT hidden path.

## Dynamic Lighting

Lighting should be a separate engine from sight:

```text
currentSightMask = what PCs can geometrically see
lightMask = what light sources illuminate after light blockers
currentlyVisibleMask = currentSightMask AND lightMask
```

Extensions:

- PC darkvision or special senses can contribute to `lightMask` or bypass it through explicit rules.
- Bright and dim light should be separate channels in `LightMask`.
- Static lights can cache their occluded masks by `(light id, topology version, light settings version)`.
- Token-carried lights invalidate only that token's light contribution when it moves.
- DebugMode can draw light source masks and blocker hits from the same cached data.

This model avoids mixing light rendering with wall debug halos.

Current bridge implementation:

- Runtime bundle format `1` already consumes builder-authored static point lights and sunlight/outside areas.
- The existing async sightline service builds an AWT-area light mask on bundle load and intersects it with PC sight.
- Static point lights are fixed to the grid. Their bright radius uses the same exported wall blockers as PC sight, while their dim radius is a cheap unblocked soft reach for player visibility.
- The renderer draws each point light's exported colour as a cached tint layer clipped to the current visible mask, using screen blending so tint brightens the map instead of muddying it.
- Sunlight/outside polygons are treated as always-lit authored regions.
- PC darkvision is treated as a token-origin light contribution clipped from the cached PC sight area, then rendered as a grayscale-only reveal.
- Persistent-vision memory redraws remembered-only terrain from the grayscale static map cache before applying the grey memory overlay, so remembered areas never leak colour information.
- Bundles with no authored lighting and no PC darkvision remain sight-only until the DM opts into lighting data.
- Token-carried lights, moving lights, and typed light-blocker rules remain future pipeline work.

## Persistence of Vision

Maintain an accumulated seen mask:

```text
previouslySeenMask = previouslySeenMask OR currentlyVisibleMask
rememberedOnlyMask = previouslySeenMask AND NOT currentlyVisibleMask
```

Composition rules:

- current visible area uses the color static map layer;
- remembered-only area uses the grayscale static map layer;
- unseen area is black;
- live tokens, doors that moved after being seen, and dynamic lights are shown only in current visible area unless the DM enables a special debug view.

Manual fog should remain an explicit DM override. A conservative rule is:

```text
playerVisible = currentlyVisibleMask AND manualFogRevealedMask
playerRemembered = previouslySeenMask AND manualFogRevealedMask AND NOT playerVisible
```

If the DM hides all fog, both current and remembered output should go black until revealed again.

## Wall Types and Doors

Replace the current `DynamicMapRuntimeWall(start, end)` model with typed wall segments in the next bundle format.

Suggested runtime fields:

```kotlin
data class WallSegment(
    val id: String,
    val start: Point,
    val end: Point,
    val blocksSight: BlockingRule,
    val blocksLight: BlockingRule,
    val blocksMovement: Boolean,
    val doorId: String? = null,
)

sealed class BlockingRule {
    data object Never : BlockingRule()
    data object Always : BlockingRule()
    data class OneWay(val blocksFromNormalSide: Boolean) : BlockingRule()
}

data class DoorState(
    val id: String,
    val open: Boolean,
    val locked: Boolean = false,
)
```

Behavior:

- Opaque wall: blocks sight and light in both directions.
- Transparent wall: blocks movement if needed, but uses `BlockingRule.Never` for sight/light.
- One-way sight wall: blocks sight only when the ray crosses from the blocked side. This requires each segment to have a stable normal direction from the builder export.
- Door: when closed, applies its configured blocking rules; when open, sight/light blockers are removed from the active topology. Door state changes increment the topology version and invalidate sight/light masks, but not static map/background caches.

Bundle format version `1` can map all existing walls to opaque two-way blockers for backward compatibility. Bundle format version `2` should add wall IDs, wall type, optional door ID, and one-way normal data.

## Dirty Invalidation Rules

Use explicit dirty flags instead of calling one broad `redraw()`.

| Event | Dirty work |
|---|---|
| Bundle load | all scene layers, geometry cache, fog setup, visibility, light, composition |
| Background image/calibration | static color map, grayscale map, composition |
| Grid config/calibration | grid layer, coordinate mapper, composition |
| Manual fog cell paint | fog mask subrect, token visibility, composition |
| Manual fog reset | fog mask full rebuild, token visibility, composition |
| PC token move | moved PC sight contribution, current visibility, seen mask, token visibility, composition |
| NPC token move | token visibility for that token, token layer/composition |
| Token image change | token image cache, token layer/composition |
| Light move/change | changed light contribution, light mask, current visibility, token visibility, composition |
| Door open/close | geometry topology, affected sight/light masks, current visibility, composition |
| View pan/zoom | composition only |
| Canvas resize | view target resize, composition only unless cache resolution policy changes |
| Debug mode toggle | debug layer/composition only |

## Implementation Phases

### Phase 1: Instrument the Existing Pipeline

Add lightweight timing around:

- `refreshDynamicSightlineMesh()`;
- `DynamicSightlineMesh.compute(...)`;
- AWT visible/hidden area construction;
- `drawDynamicSightlineLayer()`;
- `drawFogOfWar()`;
- token visibility filtering;
- total redraw per renderer.

This gives baseline numbers and a regression target.

### Phase 2: Extract Shared Render State

Move EventBus subscriptions and mutable scene state out of `MapRenderer` into a shared render-state service. The renderer should become a view of immutable snapshots.

Expected result: the table view and minimap stop duplicating bundle, fog, token, and visibility state.

### Phase 3: Add Static Layer Caches

Introduce cached world-space images for:

- dynamic map surface/background;
- grid;
- debug wall/light markers;
- manual fog mask.

PC movement should no longer redraw static background, grid, or debug geometry from scratch.

### Phase 4: Replace AWT Sightlines with Async Mask-Based Visibility

Build the new visibility engine:

- static geometry preprocessing;
- spatial index;
- per-PC origin cache;
- worker-thread computation;
- raster mask output;
- revision checks;
- stale result cancellation.

Remove `java.awt.geom.Area` from the PC movement path.

### Phase 5: Add Persistent Vision

Add `previouslySeenMask`, grayscale static map cache, and player composition rules for current, remembered, and unseen areas.

Add scene persistence for the seen mask. Store it compactly, for example as run-length encoded cells or a compressed bitmap tied to map dimensions and bundle ID.

### Phase 6: Add Dynamic Light and Typed Walls

Extend bundle/runtime data to support wall behavior, door identity/state, and separate sight/light blockers.

Then add:

- light mask cache;
- bright/dim channels;
- door-state invalidation;
- DebugMode visualization for blockers, light masks, and one-way wall direction.

## Acceptance Targets

Performance targets should be measured on a realistic wall-heavy bundle:

- JavaFX event handlers for token movement should return quickly and never run full visibility computation inline.
- A PC move should schedule work and keep the UI responsive immediately.
- Final visibility should normally appear within a small fraction of a second on large maps, with fast drag preview available sooner.
- View pan/zoom should be composition-only and not recompute sight, light, fog, or static layers.
- The table view and minimap should share one visibility result.
- No AWT `Area` operation should be required for normal PC movement.

The practical definition of done is that dragging a PC token feels interactive even when exact visibility is still being computed in the background.

