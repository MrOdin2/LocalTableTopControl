# DynamicMap Performance Improvements

| Proposal | Expected speed improvement | Cost | Compatibility | Futureproofing |
|---|---:|---:|---:|---:|
| Throttle sightline recompute while dragging PC tokens, e.g. max 15-30 Hz or only after mouse moves into a new subcell threshold | Medium to high during drag | Low | Very high | Medium |
| Cache static DynamicMap raycast inputs per loaded bundle: wall segments, map border segments, wall endpoints, angle offsets | Medium | Low-Medium | Very high | High |
| Split renderer into cached layers: background/grid/static bundle layer, fog layer, sightline layer, token layer; redraw only dirty layers | High | Medium | Very high | High |
| Replace `java.awt.geom.Area` union/subtract per PC move with direct Canvas even-odd fill: draw map bounds plus visible polygons as holes | Medium to high | Medium | High | Medium-High |
| Store one merged visible polygon per PC instead of many triangles, then render/intersect against those polygons | Medium | Medium | High | High |
| During drag, render a low-quality preview mesh; recompute exact mesh on release | High during drag | Medium | High | Medium |
| Replace token visibility `Area` intersection with cheap geometry checks: circle-vs-triangle / circle-vs-polygon intersection | Medium | Medium | Very high | High |
| Spatially index walls in a uniform grid/BVH so each ray tests only nearby wall segments instead of every wall | High on wall-heavy maps | Medium-High | High | Very high |
| Limit rays to unique visible wall endpoints plus map corners, dedupe aggressively, and skip near-identical angles after sorting | Low to medium | Low-Medium | Very high | Medium |
| Move sightline computation off the JavaFX thread, publish completed meshes back to UI; keep last mesh visible while next one computes | High perceived responsiveness | Medium-High | High | High |
| Use Kotlin coroutines or a small worker executor for parallel per-PC or per-ray computation | Medium to high on multi-core systems | Medium | High | Medium-High |
| Precompute wall topology into visibility graph / portals for static maps, then resolve PC visibility from graph regions | Very high | High | Medium-High | Very high |
| Rasterize visibility into a lower-resolution alpha mask image instead of vector geometry, then upscale smoothly | High | Medium | High | Medium |
| Use JavaFX `PixelBuffer` / `WritableImage` for a software visibility mask and composite it as an image layer | Medium to high | Medium-High | High | Medium-High |
| Switch renderer backend from JavaFX Canvas to a retained custom scene graph with cached nodes/polygons | Medium | High | Medium | Medium |
| Introduce LibGDX/OpenGL renderer backend for map/sight/token rendering while keeping JavaFX for DM controls | Very high | Very high | Low-Medium | Very high |
| Introduce a proper 2D game/render engine behind the table view only, with JavaFX as control shell | Very high | Very high | Low | Very high |
| Use GPU shaders for fog/sight masks, with walls and PC positions uploaded as buffers/textures | Very high | Very high | Low | Very high |
| Add dynamic quality settings: "fast drag preview", "exact on release", max ray count, sightline smoothing, overlay resolution | Medium to high | Medium | High | High |
