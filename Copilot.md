# Copilot Instructions — TabletopControl

This file provides structured context for GitHub Copilot and AI coding agents working on the **TabletopControl** project.

---

## Project Summary

TabletopControl is a **Kotlin / JavaFX** desktop application for tabletop RPG sessions.  
It drives two screens simultaneously:

- **Table screen** — full-screen, physical-scale map (JavaFX Canvas), optionally overlaid with a grid.
- **DM screen** — control panel for maps, fog of war, audio, initiative, and monster HP.

The application must run on **Linux**, including low-power SBCs such as the Raspberry Pi.

---

## Language & Framework

- **Language:** Kotlin (JVM)
- **UI:** JavaFX (no third-party UI libraries unless absolutely necessary)
- **Map rendering:** JavaFX `Canvas` API — no WebView, no embedded browser
- **Build system:** Gradle (Kotlin DSL preferred)
- **Audio:** JavaFX `MediaPlayer` or a minimal JVM audio library — three independent layers (music, ambient, SFX)

---

## High-Level Architecture

```
tabletopcontrol/
├── core/               # Framework: application lifecycle, event bus, plugin loader
├── map/                # Canvas-based map renderer, grid overlay, obstruction placement
├── audio/              # Three-layer audio engine (music / ambient / SFX)
├── tracker/            # Initiative tracker and Monster HP tracker
├── api/                # REST/Webhook HTTP interface (planned)
└── plugins/            # Runtime-loaded DM-screen plugin modules
```

### Core Principles

1. **Framework first** — establish the plugin host, event bus, and screen management before adding feature plugins.
2. **Plugin system** — each DM-screen feature (audio, trackers, map controls) is a self-contained plugin that registers itself with the core.
3. **Two-screen management** — the core manages two `Stage` instances (or a multi-screen layout); one is always the table view, the other the DM panel.
4. **Separation of concerns** — UI, business logic, and data are kept in separate layers; no business logic inside JavaFX controllers.

---

## DM Panel Layout System

The DM Panel uses a **recursive split-pane layout** so the DM can view and control all plugins simultaneously from a single window, without switching tabs.

### Design

- The layout is modelled as an immutable binary tree (`PaneNode`):
  - `Leaf(pluginName)` — shows one plugin.
  - `Split(orientation, dividerPosition, first, second)` — divides the space between two child `PaneNode`s.
- `DmLayoutManager` converts the tree into a live JavaFX node hierarchy (nested `SplitPane`s) and handles structural changes.
- On a structural change (split / close / change plugin) the entire sub-tree is rebuilt from the new tree model.
- On shutdown, `syncDividers` walks the live JavaFX hierarchy to read the current divider positions back into the tree before persisting it.

### Persistence

- The layout is serialised as a compact S-expression and stored in `~/.tabletopcontrol/dm-layout.conf`:
  ```
  leaf(Map)
  split(HORIZONTAL,0.5,leaf(Map),split(VERTICAL,0.3,leaf(Audio),leaf(Tracker)))
  ```
- On startup the saved file is loaded and the layout restored; if the file is absent or corrupt the default layout (single pane, first plugin) is used.

### Right-Click Context Menu

Right-click anywhere on a leaf pane to access:
- **Add Panel to Left**  — splits horizontally; new pane appears on the left.
- **Add Panel to Right** — splits horizontally; new pane appears on the right.
- **Add Panel Above** — splits vertically; new pane appears above.
- **Add Panel Below** — splits vertically; new pane appears below.
- **Change Plugin…** — replace the plugin shown in this pane (choice dialog).
- **Close Pane** — remove this pane (disabled when only one pane remains).
- **Extend Left / Right / Above / Below** — expand this pane into exactly one neighbouring
  leaf pane. Only directions where the immediate neighbour in that direction is a single leaf
  pane are offered; directions whose immediate neighbour is a split containing multiple panes
  are omitted. Other panes may be resized or restructured but are not discarded.

#### Extend logic

Five kinds of extension are supported, evaluated in priority order (higher priority wins when two directions collide):

| Priority | Kind | Condition | Effect |
|----------|------|-----------|--------|
| 1 | Same-level | Sibling within the parent split is a single `Leaf` | Equivalent to closing the sibling; the current pane takes its place. |
| 1b | Sibling-adjacent-child | Sibling within the parent split is itself a `Split`, and its child on the side closest to the leaf is a single `Leaf` | That child is removed, collapsing the sibling to its remaining child; the leaf stays in place — equivalent to closing just the adjacent sub-panel. |
| 2 | Cross-level | Uncle (parent's sibling within the grandparent split) is a single `Leaf` | The current pane grows across the grandparent boundary; the displaced sibling recombines with the uncle on the opposite side. |
| 3 | Same-uncle-orientation | Uncle is a `Split` with the *same* orientation as the parent, and the uncle's child at the leaf's position is a single `Leaf` | The grandparent is restructured: the leaf takes a full-width/height row/column; the displaced sibling and the uncle's surviving child are merged side-by-side into the other half. |
| 4 | Same-grandparent-orientation uncle | Uncle is a `Split` with the *same* orientation as the grandparent (but different from the parent), and the uncle's child on the side adjacent to the parent is a single `Leaf` | The adjacent child's space is shared with the leaf using the parent's orientation (leaf at its row/column position, adjacent child in the other half); the uncle is rebuilt with that shared node; the displaced sibling takes the parent's former slot. |
| 5 | Great-uncle-is-Leaf | Great-grandparent exists and its other child (the great-uncle) is a single `Leaf` | The great-grandparent is restructured: a new node on the great-uncle's side combines the leaf with the great-uncle (leaf at its own row/column position, displaced sibling filling the other slot); the uncle takes the grandparent's former slot. |

Example — layout `H(Lights, H(V(Tracker, Map), V(Music, Soundboard)))`:
- **Tracker** → "Extend Right" → `H(Lights, V(Tracker, H(Map, Soundboard)))`, "Extend Below" (same-level, removes Map), "Extend Left" → `H(V(Tracker, H(Lights, Map)), V(Music, Soundboard))`.
- **Map** → "Extend Right" → `H(Lights, V(H(Tracker, Music), Map))`, "Extend Above" (same-level, removes Tracker), "Extend Left" → `H(V(H(Lights, Tracker), Map), V(Music, Soundboard))`.
- **Music** → "Extend Left" → `H(Lights, V(Music, H(Map, Soundboard)))`, "Extend Below" (same-level, removes Soundboard).
- **Soundboard** → "Extend Left" → `H(Lights, V(H(Tracker, Music), Soundboard))`, "Extend Above" (same-level, removes Music).
- **Lights** → no extend options (adjacent to a multi-panel section).

Example — layout `H(H(V(Tracker, Lights), Map), V(Music, Soundboard))` (case 4):
- **Music** → "Extend Left" → `H(H(V(Tracker, Lights), V(Music, Map)), Soundboard)` — Music takes top half of Map's space, Map keeps bottom half, Soundboard takes parent's former slot.
- **Soundboard** → "Extend Left" → `H(H(V(Tracker, Lights), V(Map, Soundboard)), Music)` — Soundboard takes bottom half of Map's space, Map keeps top half, Music takes parent's former slot.

Example — layout `H(H(V(Tracker, Lights), Map), Soundboard)` (case 1b):
- **Soundboard** → "Extend Left" → `H(V(Tracker, Lights), Soundboard)` — Map is removed, sibling collapses to `V(Tracker, Lights)`.

### Key Files

| Path | Purpose |
|------|---------|
| `core/src/.../PaneNode.kt` | Immutable tree model; `replaceNode` / `replaceNodeByRef` / `removeNode` / `findAncestry` helpers |
| `core/src/.../LayoutSerializer.kt` | S-expression serialiser/deserialiser; file I/O |
| `core/src/.../DmLayoutManager.kt` | JavaFX UI builder, context menu (including extend options), divider sync |

---

## Theme System

TabletopControl supports **light and dark modes** plus user-selectable colours for the four concrete UI roles: accent/buttons, background, surfaces/panels, and borders.  The theme system is built on JavaFX CSS and is self-contained within the `core` module.

### Architecture

| File | Purpose |
|------|---------|
| `core/src/.../ThemeConfig.kt` | Immutable `ThemeConfig` data class; `ThemeMode` enum (LIGHT / DARK); built-in defaults; hex-color validation |
| `core/src/.../ThemeEvents.kt` | `ThemeChangedEvent` published on `EventBus` whenever the theme changes |
| `core/src/.../ThemeManager.kt` | Singleton — loads/saves theme, registers scenes, applies CSS stylesheets, validates loaded colors |
| `core/src/main/resources/.../theme-light.css` | Light-theme CSS: defines all `-tc-*` colour variables |
| `core/src/main/resources/.../theme-dark.css` | Dark-theme CSS: overrides JavaFX Modena base colours + defines `-tc-*` variables |

### CSS Looked-Up Colour Variables

Variables are grouped by role.  Any descendant node can reference them in inline `style` strings or CSS class rules **without importing any extra files**.

#### Group 1 — Background & Surfaces (user-configurable)

| Variable      | Purpose                        | Light default | Dark default |
|---------------|-------------------------------|---------------|--------------|
| `-tc-bg`      | Window / scene background      | `#f4f4f4`     | `#1e1e2e`    |
| `-tc-surface` | Panel and card surfaces        | `#ffffff`     | `#2d2d3e`    |
| `-tc-border`  | Panel edges, control borders   | `#c8c8c8`     | `#555577`    |

#### Group 2 — Interactive / Accent (user-configurable)

| Variable       | Purpose                                     | Light default | Dark default |
|----------------|---------------------------------------------|---------------|--------------|
| `-tc-accent`   | Buttons, links, active highlights           | `#1565c0`     | `#82b1ff`    |
| `-tc-on-accent`| Text on top of an accent-coloured surface   | `#ffffff`     | `#212121`    |

#### Group 3 — Text (fixed per mode)

| Variable         | Purpose                | Light default | Dark default |
|------------------|------------------------|---------------|--------------|
| `-tc-text`       | Primary text           | `#212121`     | `#e0e0e0`    |
| `-tc-text-muted` | Secondary / hint text  | `#888888`     | `#9e9e9e`    |

#### Group 4 — Status (fixed semantic)

| Variable      | Purpose              | Light default | Dark default |
|---------------|----------------------|---------------|--------------|
| `-tc-success` | Connected / OK state | `#00aa00`     | `#66bb6a`    |
| `-tc-error`   | Error state          | `#cc0000`     | `#ef9a9a`    |

#### Group 5 — Tracker Card States (derived)

| Variable                    | Derived from            |
|-----------------------------|-------------------------|
| `-tc-card-bg`               | `-tc-surface`           |
| `-tc-card-border`           | `-tc-border`            |
| `-tc-card-active-border`    | `-tc-accent`            |
| `-tc-card-active-bg`        | Fixed warm tint         |
| `-tc-card-dragover-border`  | `-tc-accent`            |
| `-tc-card-dragover-bg`      | Fixed cool tint         |

### Incorporating Themes in a New Plugin

1. **Use CSS variables** for any colour you set via `node.style`:
   ```kotlin
   label.style = "-fx-text-fill: -tc-error;"        // error state
   label.style = "-fx-text-fill: -tc-success;"      // success state
   label.style = "-fx-text-fill: -tc-text-muted;"   // secondary text
   button.style = "-fx-base: -tc-accent;"           // action button
   vbox.style = "-fx-background-color: -tc-surface;" // panel background
   ```
   Because these are JavaFX "looked-up colours", they resolve automatically from the active theme CSS — no event subscription needed.

2. **For Canvas-based rendering** (e.g. `MapRenderer`) that cannot use CSS, subscribe to `ThemeChangedEvent` on the `EventBus` and redraw with the new palette colours:
   ```kotlin
   EventBus.subscribe<ThemeChangedEvent> { (theme) ->
       // theme.accentColor is a CSS hex string such as "#82b1ff"
       redrawWithPalette(Color.web(theme.accentColor))
   }
   ```

3. **Avoid hardcoded hex colours** in `node.style` strings.  Always prefer the `-tc-*` semantic variables so the component automatically adapts to future themes.

### Persistence

The active theme is saved to `~/.tabletopcontrol/theme.conf` as simple `key=value` lines:

```
mode=DARK
accentColor=#82b1ff
bgColor=#1e1e2e
surfaceColor=#2d2d3e
borderColor=#555577
```

A small per-user override file (`~/.tabletopcontrol/theme-custom.css`) is also written and loaded as a stylesheet to apply the four colour overrides on top of the base theme.  Color values loaded from this file are validated as `#RGB` or `#RRGGBB` hex strings; invalid values silently fall back to the mode defaults.

### Adding a New Theme Variable

1. Add the variable to **both** `theme-light.css` and `theme-dark.css` under `.root`, in the appropriate group comment.
2. Document it in the relevant group table above.
3. Reference it in components using `node.style = "-fx-...: -tc-new-var;"`.

---

- **Language:** Kotlin idioms are preferred over Java-style patterns (data classes, extension functions, sealed classes, coroutines where appropriate).
- **Naming:** `PascalCase` for classes/interfaces, `camelCase` for functions and properties, `SCREAMING_SNAKE_CASE` for constants.
- **Comments:** Add KDoc on every public class and function. Add inline comments for any logic that is not immediately obvious.
- **OOP best practices:** Favour composition over inheritance; keep classes small and focused (Single Responsibility Principle).
- **No god classes:** If a class is growing large, split it into focused sub-components.
- **Error handling:** Use Kotlin's `Result`/`sealed class` pattern for recoverable errors; let unrecoverable errors propagate to a top-level handler that logs and shows a user-friendly message.

---

## Plugin System Guidelines

- A plugin is a class that implements the `DmPlugin` interface (to be defined in `core/`).
- Plugins declare their display name, icon, and the JavaFX `Node` they contribute to the DM panel.
- The core discovers plugins via a `ServiceLoader` or a simple registry; no reflection magic beyond that.
- Plugins communicate with the rest of the application exclusively through the **event bus** — they must not hold direct references to other plugins or core singletons.

---

## REST / Webhook Integration

- A lightweight embedded HTTP server (e.g., Ktor or a minimal servlet container) will expose a REST API.
- The API will allow external devices to trigger events (play SFX, update initiative, etc.).
- Authentication / security considerations must be addressed before enabling the endpoint on a network.
- The REST layer should map HTTP requests directly to event-bus events — no business logic in the HTTP handlers.

---

## Performance Notes

- The Canvas renderer is **event-driven, not a real-time animation loop**. It redraws only when:
  - A new scene/map is loaded.
  - Fog-of-war or obstruction state changes.
  - (Future) A token moves on the map.
  Sustained frame rate is therefore **not a concern**; correctness and low redraw latency on triggered events are what matter.
- Audio latency for SFX playback should be **< 200 ms**.
- Startup time should be reasonable on a Raspberry Pi 4; no hard target is set.

---

## What to Avoid

- No heavy animations, 3D models, or WebGL/WebView rendering.
- No mandatory internet connection at runtime.
- No large runtime dependencies that would be difficult to install on a minimal Linux system.
- Do not mix UI code with business logic.

---

## Testing Approach

- Unit-test business logic (trackers, audio layer management, plugin registry) with **JUnit 5 + MockK**.
- UI tests (if any) via **TestFX** — keep them minimal and focused on critical paths.
- Integration tests for the REST API using an embedded server instance.

---

## Key Files (once scaffold exists)

| Path | Purpose |
|------|---------|
| `core/src/.../App.kt` | Application entry point, screen initialisation |
| `core/src/.../EventBus.kt` | Central event bus for inter-module communication |
| `core/src/.../PluginLoader.kt` | Discovers and loads `DmPlugin` implementations |
| `map/src/.../MapRenderer.kt` | JavaFX Canvas map renderer |
| `audio/src/.../AudioEngine.kt` | Three-layer audio playback engine |
| `api/src/.../RestServer.kt` | Embedded HTTP server and route definitions |
