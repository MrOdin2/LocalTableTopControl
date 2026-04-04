# Phase 3 — Plugin Refactor Status

This checklist tracks plugin decomposition progress for:
- class split status (god-class reduction),
- shared routine relocation into `core`,
- error handling migration toward `Result` / sealed outcomes.

## Core shared routine relocation snapshot

- [x] `core.ui.color.ColorHexCodec` extracted and reused by:
  - [x] `core/App.kt` theme dialog color conversions/parsing
  - [x] `audio/SoundboardPlugin.kt` color parsing/serialization + contrast input parsing
  - [x] `light/LightPlugin.kt` color editor and recents parsing/conversion
  - [x] `tracker/TrackerPlugin.kt` token swatch hex conversion

## Plugin status

- [ ] **audio / MusicPlugin**
  - [ ] Class split
  - [ ] Shared routine relocation complete
  - [ ] Error handling revamp complete
- [ ] **audio / SoundboardPlugin**
  - [ ] Class split
  - [x] Shared routine relocation complete (color codec extraction applied)
  - [ ] Error handling revamp complete
- [ ] **light / LightPlugin**
  - [ ] Class split (controller extracted; additional decomposition pending)
  - [x] Shared routine relocation complete (color codec extraction applied)
  - [ ] Error handling revamp complete
- [ ] **map / MapPlugin**
  - [ ] Class split
  - [ ] Shared routine relocation complete
  - [ ] Error handling revamp complete
- [ ] **tracker / TrackerPlugin**
  - [ ] Class split
  - [x] Shared routine relocation complete (token swatch hex conversion now shared)
  - [ ] Error handling revamp complete

## Before/after code sample links (for PR review)

- Before (duplicated color helpers):
  - `light/src/main/kotlin/com/tabletopcontrol/light/LightPlugin.kt` (`colorToHex`, `Color.web(...)`)
  - `audio/src/main/kotlin/com/tabletopcontrol/audio/SoundboardPlugin.kt` (`colorToHex`, `Color.web(...)`)
  - `tracker/src/main/kotlin/com/tabletopcontrol/tracker/TrackerPlugin.kt` (`colorToHex`)
  - `core/src/main/kotlin/com/tabletopcontrol/core/App.kt` (`colorToHex`, direct parsing)
- After (shared):
  - `core/src/main/kotlin/com/tabletopcontrol/core/ui/color/ColorHexCodec.kt`
  - call sites in the same plugin/core files above now use `ColorHexCodec`.

