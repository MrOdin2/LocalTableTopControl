# Phase 3 follow-up sub-issues (plugin refactor backlog)

## Quick status check

| Plugin | Class split status | Shared utility status | Error handling status | Needs new sub-issue |
|---|---|---|---|---|
| `map` (`MapPlugin.kt`, ~1591 lines) | ⚠️ still a god class (UI + calibration + token + FoW + minimap + measurement) | ⚠️ uses some shared utilities (`ColorEditorDialog`, `DialogFlows`) but still has large inline dialog/business flows | ⚠️ mostly imperative/inline validation | ✅ |
| `tracker` (`TrackerPlugin.kt`, ~1094 lines) | ⚠️ still a god class (UI + tracker flow + token image + preset orchestration) | ⚠️ uses `DialogFlows` and `ReorderSupport`, but still contains custom dialog/preset orchestration in plugin | ⚠️ mixed `runCatching` + inline `Alert` handling, no plugin result model | ✅ |
| `audio/music` (`MusicPlugin.kt`, ~716 lines) | ⚠️ still mixes UI wiring with track lifecycle/business orchestration | ✅ already uses shared media utility (`MediaTrackController`) + shared reorder utility | ⚠️ no plugin-specific sealed `Result` domain for load/play failures | ✅ |
| `audio/soundboard` (`SoundboardPlugin.kt`, ~547 lines) | ⚠️ still mixes UI wiring with slot lifecycle/persistence business logic | ✅ uses shared media/color/persistence/reorder utilities | ⚠️ mostly boolean/imperative error flow | ✅ |
| `light` (`LightPlugin.kt`, ~775 lines) | ⚠️ partially split (`LightController` exists) but plugin still owns broad UI + orchestration responsibilities | ✅ uses shared color utilities | ⚠️ no sealed result flow around controller/write failures | ✅ |

## New sub-issues to open

### 1) Map plugin decomposition: split `MapPlugin` into UI controller + map domain services
- [ ] Extract map business logic into focused services (calibration, FoW, token sync, measurement, viewport/minimap state).
- [ ] Keep JavaFX controller layer to view wiring and event forwarding only.
- [ ] Move reusable dialog/form pieces into standalone classes (calibration + measurement unit dialogs).
- [ ] Introduce sealed `Result`/error model for map operations and conversion to user-facing messages.
- [ ] Add before/after code links in PR to show decomposition.

### 2) Tracker plugin decomposition: split `TrackerPlugin` and isolate preset/token-image workflows
- [ ] Create tracker service(s) for initiative/round progression and preset orchestration.
- [ ] Extract token-image and preset dialogs/components into dedicated classes.
- [ ] Remove direct `Alert` orchestration from plugin logic in favor of shared dialog helpers + mapped result states.
- [ ] Add sealed `Result` types for preset load/save/import/export flows.
- [ ] Add before/after code links in PR to show decomposition.

### 3) Music plugin refactor: separate track lifecycle domain from JavaFX view code
- [ ] Move track lifecycle/load/rebind/persist orchestration out of `MusicPlugin` into a dedicated service.
- [ ] Keep plugin/controller focused on rendering cards and binding UI events.
- [ ] Model load/playback failures with sealed results instead of implicit UI-side handling.
- [ ] Ensure shared `MediaTrackController` remains the single media lifecycle implementation.
- [ ] Add before/after code links in PR to show decomposition.

### 4) Soundboard plugin refactor: extract slot/domain service and dialog components
- [ ] Move slot state transitions (load/play/stop/clear/style state) into dedicated domain/service classes.
- [ ] Keep JavaFX button grid/controller focused on UI wiring.
- [ ] Extract slot edit/color/file dialogs into standalone reusable components where possible.
- [ ] Introduce sealed result flow for slot load/play/config persistence errors.
- [ ] Add before/after code links in PR to show decomposition.

### 5) Light plugin refactor completion: finish class split and error/result modeling
- [ ] Further split `LightPlugin` into focused UI controller sections (serial, effect params, presets, debug console).
- [ ] Move remaining business/state orchestration to `LightController` (or additional services) so plugin is thin.
- [ ] Introduce sealed result model for serial write/state update failures.
- [ ] Reuse shared dialog/error presentation helpers for consistent operator feedback.
- [ ] Add before/after code links in PR to show decomposition.
