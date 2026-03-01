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

## Coding Conventions

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
