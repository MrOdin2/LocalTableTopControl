# DynamicMap Rendering Pipeline Weaknesses

## Scope Reviewed

The closest current concept document, `dynamicmap_concepts.md`, was reviewed, along with:

- `src/main/kotlin/com/tabletopcontrol/dynamicmap/runtime/MapRenderer.kt`
- `src/main/kotlin/com/tabletopcontrol/dynamicmap/runtime/logic/DynamicSightlineMesh.kt`
- `src/main/kotlin/com/tabletopcontrol/dynamicmap/runtime/MapUiController.kt`
- `src/main/kotlin/com/tabletopcontrol/dynamicmap/runtime/logic/MapServices.kt`
- `src/main/kotlin/com/tabletopcontrol/dynamicmap/runtime/DynamicMapBundle.kt`
- `performanceImprovements.md`

This document describes the current rendering pipeline and why PC token movement can stall for seconds on wall-heavy maps.

## Current Pipeline Summary

DynamicMap currently uses one `MapRenderer` per view:

- The player-facing table view creates a `MapRenderer` with opaque fog and hidden tokens.
- The DM minimap creates a second `MapRenderer` with translucent fog, independent pan/zoom, and debug controls.
- Both renderers subscribe directly to the same `EventBus` events.
- Each renderer owns its own copy of map image state, bundle state, fog state, token state, measurements, image cache, and dynamic sightline mesh.

The PC movement path is:

```text
DM drags token on minimap
  -> MapTokenSyncService.publishDraggedTokenMove(...)
  -> TokenMovedEvent
  -> table MapRenderer subscription
       -> update token
       -> if token is PC: refreshDynamicSightlineMesh()
       -> redraw()
  -> minimap MapRenderer subscription
       -> update token
       -> if token is PC: refreshDynamicSightlineMesh()
       -> redraw()
```

The redraw order inside `MapRenderer.redraw()` is currently:

```text
clear full canvas
apply viewport transform
draw dynamic map surface or normal map image
draw dynamic map background image
draw debug light halos, if debug mode
draw grid
draw debug walls, if debug mode
draw manual fog of war
draw tokens
draw dynamic sightline overlay
draw measurements
draw debug light markers, if debug mode
draw table viewport outline
```

This is event-driven rather than a frame loop, which is good for low-power devices, but every accepted event still does a synchronous full rebuild of all dirty work.

## Current Sightline Computation

`DynamicSightlineMesh.compute(...)` does the expensive part of PC visibility:

1. Rebuilds all sight segments from the map border plus every wall.
2. For every PC token, collects every segment endpoint.
3. Casts three rays per endpoint: `angle - epsilon`, `angle`, and `angle + epsilon`.
4. For every ray, checks intersection against every segment and keeps the nearest hit.
5. Sorts hits by angle and converts adjacent hit pairs into triangles.
6. Builds a `java.awt.geom.Area` union from all triangles.
7. Builds hidden area by subtracting that visible area from the full map rectangle.

For `S` total sight segments, one PC costs roughly `6 * S * S` ray/segment intersection checks before the AWT geometry work. A 500-wall map becomes millions of intersection checks per PC move, per renderer.

## Primary Weaknesses

### 1. PC visibility is recomputed synchronously on the JavaFX event thread

`refreshDynamicSightlineMesh()` runs directly inside the `TokenMovedEvent` subscriber. Because `EventBus` subscribers are synchronous, dragging a PC token blocks input handling, canvas redraw, and all other JavaFX UI work until the mesh is finished.

The current renderer does cache the resulting mesh between redraws, but it still recomputes that mesh immediately on every PC cell move.

### 2. The same visibility work is duplicated by both renderers

The table renderer and minimap renderer independently run `DynamicSightlineMesh.compute(...)` for the same bundle, same walls, and same PC positions. This doubles the hottest work in the exact moment the UI needs to stay responsive.

Visibility is scene data, not view data. The table and minimap should share one immutable visibility result and only differ in how they composite it.

### 3. Ray casting scales poorly with wall count

The current algorithm tests every generated ray against every segment. Since rays are generated from wall endpoints, both the ray count and segment count grow with wall count. There is no spatial index, no precomputed segment cache, and no aggressive endpoint dedupe before casting.

This makes visibility close to quadratic in wall count for each PC token.

### 4. Static ray inputs are rebuilt every time

The following data is static for a loaded bundle but is rebuilt during every sightline compute:

- map border segments
- wall segments
- wall endpoint angle sources
- zero-length filtering
- coordinate conversion into sight points

Only PC origins usually change during a drag. The static geometry should be preprocessed once per bundle or per blocker-version.

### 5. `java.awt.geom.Area` is in the hot path

The current mesh converts every visibility triangle to a `Path2D`, unions those paths into a `java.awt.geom.Area`, subtracts it from a rectangle, and later iterates the hidden area's `PathIterator` for drawing.

This has several costs:

- geometric boolean operations are CPU-heavy;
- many temporary objects are created per PC move;
- AWT geometry is a separate rendering model from JavaFX Canvas;
- drawing still requires converting AWT path segments back into Canvas commands.

For a Canvas-based renderer, this should be replaced with cached mask images or direct Canvas paths that avoid AWT boolean operations in the movement path.

### 6. Full-canvas redraws happen for narrow changes

Every redraw clears and repaints the whole canvas. Moving one PC token can cause:

- full background redraw;
- full dynamic background image scaling;
- full grid redraw;
- full fog redraw;
- full token redraw;
- full dynamic sightline overlay redraw;
- full measurement redraw;
- full debug overlay redraw.

Most of these layers do not change when a PC token moves. The current renderer has no dirty flags, no layer cache, and no separation between scene changes and view transform changes.

### 7. Manual fog is redrawn cell by cell

`drawFogOfWar()` loops over fog cells and draws individual rectangles for hidden cells. It culls cells outside the visible world bounds, which helps, but fog still has no cached mask. Large fog grids and reset/replay operations can still trigger many Canvas calls.

Fog state is also duplicated inside each renderer instead of living as a shared render model.

### 8. Token visibility uses AWT geometry per token per redraw

When player-facing token hiding is enabled, `drawTokens()` calls `isTokenVisibleInDynamicSightline(...)`. That creates an AWT ellipse `Area`, intersects it with the visible `Area`, and checks emptiness.

This happens for every token on every redraw. It preserves the intended "any exposed edge reveals the token" rule, but it is expensive and tied to the same AWT visibility representation.

### 9. The renderer mixes state ownership, invalidation, visibility, and drawing

`MapRenderer` currently:

- subscribes to domain events;
- owns mutable scene state;
- owns view state;
- computes visibility;
- stores token images;
- performs hit testing;
- performs all Canvas drawing;
- decides token visibility.

That makes it hard to cache correctly. A PC move should invalidate only PC-origin visibility, current visibility masks, token visibility, and final composition. The current class can only call `redraw()`.

### 10. View transforms force full redraws instead of cheap recomposition

Minimap pan/zoom changes call `renderer.redraw()`. A view transform change should normally reuse world-space cached layers and only recompose them with a different transform. Current grid drawing adapts to the viewport, but the rest of the renderer does not distinguish "cache content changed" from "camera changed".

### 11. There is no render timing instrumentation

There are no timing counters around:

- sightline computation;
- AWT area construction;
- hidden path iteration;
- background draw/scaling;
- fog draw;
- token visibility filtering;
- total redraw time per renderer.

Without this, performance work has to be guided by subjective UI stalls instead of per-layer timings and regression thresholds.

## Future Feature Risks

### Dynamic light

Current lights are debug halos only. There is no light occlusion model, no light mask, no distinction between bright/dim light, and no composition rule for "visible because in sight and lit".

Adding dynamic light on top of the current sightline mesh would likely add another expensive raycast pass and another full redraw path.

### Persistence of vision

Manual fog is a boolean cell grid and dynamic sight is a transient triangle/Area mesh. There is no persistent "seen before" mask. A future grayscale memory layer needs at least three visibility states:

- unseen;
- seen previously but not currently visible;
- currently visible.

The current overlay-only approach cannot render grayscale remembered areas cleanly without either expensive clipping or a cached composited image.

### Wall types

`DynamicMapRuntimeWall` currently stores only `start` and `end`. Every wall blocks sight equally. This cannot express:

- transparent walls that do not block sight;
- walls that block sight in one direction only;
- doors with open/closed state;
- walls that block light differently than sight.

Wall behavior needs to become data-driven before visibility and lighting are rebuilt.

### Doors

Door state changes should update blocker caches and visibility/light masks without rebuilding unrelated static map layers. The current bundle wall list has no identity, type, or mutable door state, so door changes would currently require broad invalidation.

## Conclusions

The current renderer is slow because it performs expensive visibility computation synchronously, duplicates that computation per view, uses AWT geometric boolean operations in the hot path, and repaints every layer for every meaningful change.

The most important architectural change is to move from "renderer owns all mutable state and redraws everything" to "shared scene state produces cached layer images and masks, then each view cheaply composites those caches onto its Canvas."

