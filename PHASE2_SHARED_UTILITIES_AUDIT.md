# Phase 2 — Shared Utility Candidates Across Plugins

This document captures duplicated/cross-plugin routines identified for extraction into shared utilities (primarily `core`), without changing runtime code yet.

## 1) Duplicated or Cross-Plugin Routines

- **Color conversion/parsing helpers duplicated in multiple plugins**
  - `light/LightPlugin`: `colorToHex`, `hexToColor`, repeated `Color.web(...)` parsing in `buildColorRow()`
  - `audio/SoundboardPlugin`: `colorToHex`, `textColorFor`, repeated `Color.web(...)` parsing
  - `tracker/TrackerPlugin`: local `colorToHex`
  - `core/App`: local color parsing + `colorToHex` for theme dialog

- **Custom color-picker UIs duplicated**
  - `light/LightPlugin.buildColorRow()` includes large wheel-based editor, validation, recents, and UI state flow.
  - `audio/SoundboardPlugin.showColorPicker()` includes another wheel + brightness + marker workflow.
  - Issue comment confirms this as a cross-plugin reuse target.

- **MediaPlayer lifecycle logic duplicated in audio plugins**
  - `audio/MusicPlugin`: load/bind/dispose/status/progress handlers.
  - `audio/SoundboardPlugin`: load/play/stop/dispose/status/style transitions.

- **Persistence/config-file patterns repeated**
  - Repeated `~/.tabletopcontrol` directory + file handling in:
    - `audio/MusicSettingsSerializer`
    - `audio/SoundboardPlugin` (`configFile`, `saveConfig`, `loadConfig`)
    - `map/MapSettingsSerializer`
    - `core/ThemeManager`
    - `tracker/PresetLibrary`

- **Dialog/form flow complexity repeated**
  - Complex custom dialogs in `map/MapPlugin`, `tracker/TrackerPlugin`, `audio/SoundboardPlugin`, `core/App`.
  - Repeated validation/apply/cancel wiring and result conversion patterns.

- **Reorder orchestration repeated above shared primitives**
  - `MusicPlugin`, `SoundboardPlugin`, and `TrackerPlugin` all implement plugin-specific reorder/index-adjustment flows on top of shared drag-drop primitives.

## 2) Grouped by Likely Shared-Library Category

## A. `core.ui.color`
- `ColorHexCodec` (hex ↔ `Color`, safe parse).
- `ColorContrast` (readable text color for background color).
- `ColorEditorPopover` (wheel + brightness + marker + optional RGB/HSV/Hex fields + recents).
- **Move from:** `LightPlugin.buildColorRow`, `SoundboardPlugin.showColorPicker`, scattered color helpers.

## B. `core.media` (or `audio.shared`)
- `MediaPlayerBinding` / `MediaTrackController` for lifecycle, status hooks, safe disposal, progress binding.
- **Move from:** `MusicPlugin` and `SoundboardPlugin` media control blocks.

## C. `core.persistence`
- `AppConfigPaths` (`~/.tabletopcontrol`, per-feature files/subdirs).
- `SafeConfigIO` (read/write wrappers with consistent non-fatal behavior).
- Optional key-value/properties helpers to reduce serializer boilerplate.
- **Move from:** serializer/path code in music, map, theme, soundboard, presets.

## D. `core.ui.dialog`
- Reusable dialog builders/helpers for confirm/cancel flows, validated forms, and common owner/result wiring.
- **Move from:** repeated custom dialog plumbing in map/tracker/audio/core.

## E. `core.ui.reorder`
- Helper for common “from/to adjusted index” behavior and list reorder operations.
- **Move from:** per-plugin reorder math and orchestration where currently duplicated.

## 3) Proposed Discussion Points for Team (Core Move Candidates)

- [ ] **P1 (High):** Shared color editor + color utility package in `core.ui.color`  
      Rationale: explicitly requested by issue comment; duplicated in Light + Soundboard; largest maintainability win.
- [ ] **P1 (High):** Shared media lifecycle wrapper for JavaFX `MediaPlayer`  
      Rationale: duplicated error-prone lifecycle logic across both audio plugins.
- [ ] **P2 (Medium):** Shared persistence path/IO abstraction  
      Rationale: reduces repeated `.tabletopcontrol` path + non-fatal I/O handling patterns.
- [ ] **P2 (Medium):** Shared dialog/form utility layer  
      Rationale: reduce repeated validation/result wiring in multiple plugins.
- [ ] **P3 (Medium/Low):** Shared reorder helpers above drag-drop primitives  
      Rationale: existing primitives already in core; this would standardize final per-plugin orchestration math.

## Suggested Migration Sequence

1. Extract `core.ui.color` first (directly addresses current review feedback).
2. Extract `core.media` wrapper for audio plugins.
3. Add `core.persistence` path/IO helpers and migrate serializers incrementally.
4. Extract dialog/reorder helpers as follow-up refactors.

