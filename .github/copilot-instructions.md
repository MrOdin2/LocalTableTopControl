# Copilot Instructions — TabletopControl

## Project Summary

TabletopControl is a **Kotlin / JavaFX** desktop application for tabletop RPG (D&D, Pathfinder, etc.) sessions. It drives two screens simultaneously:

- **Table screen** — full-screen, physical-scale map rendered on a JavaFX `Canvas`, optionally overlaid with a grid.
- **DM screen** — control panel for maps, fog of war, audio, initiative, and monster HP.

The application must run on **Linux**, including low-power SBCs such as the Raspberry Pi.

---

## Build, Test & Run

**Prerequisites:** JDK 17 (Temurin or any OpenJDK 17 distribution). No separate Gradle installation is required — the project ships with the Gradle wrapper.

| Task | Command |
|------|---------|
| Build | `./gradlew build` |
| Run | `./gradlew :core:run` |
| Test | `./gradlew test` |
| Build distribution ZIP | `./gradlew :core:distZip` |
| Install distribution locally | `./gradlew :core:installDist` |

- Always use `./gradlew` (not a system-installed `gradle`).
- Tests use **JUnit Jupiter 5** and **MockK**; no additional setup is required.
- There is no dedicated lint step — use standard Kotlin compiler warnings and `official` Kotlin code style (configured in `gradle.properties`).

---

## Repository Layout

```
TabletopControl/
├── .github/workflows/release.yml   # Multi-platform CI/CD release pipeline
├── build.gradle.kts                # Root Gradle build
├── settings.gradle.kts             # Module declarations
├── gradle.properties               # JVM heap (2 GB) + official Kotlin style
├── README.md                       # User-facing documentation
├── Copilot.md                      # Extended Copilot context (architecture details)
│
├── core/       # Application lifecycle, event bus, plugin loader, screen management, DM layout
├── map/        # Canvas-based map renderer, grid overlay, obstruction placement
├── audio/      # Three-layer audio engine (music / ambient / SFX)
├── tracker/    # Initiative tracker and Monster HP tracker
└── light/      # Lighting / visual effects plugin
```

Each module follows the standard Gradle source layout: `src/main/kotlin/...` and `src/test/kotlin/...`.

### Key Source Files

| Path | Purpose |
|------|---------|
| `core/src/.../App.kt` | Application entry point, screen initialisation |
| `core/src/.../EventBus.kt` | Central event bus for inter-module communication |
| `core/src/.../PluginLoader.kt` | Discovers and loads `DmPlugin` implementations |
| `core/src/.../PaneNode.kt` | Immutable binary-tree model for the DM panel layout |
| `core/src/.../LayoutSerializer.kt` | S-expression serialiser/deserialiser for layout persistence |
| `core/src/.../DmLayoutManager.kt` | JavaFX UI builder, context menu, and divider-sync logic |
| `map/src/.../MapRenderer.kt` | JavaFX Canvas map renderer |
| `audio/src/.../AudioEngine.kt` | Three-layer audio playback engine |

---

## Architecture

- **Plugin system** — each DM-screen feature is a self-contained plugin implementing the `DmPlugin` interface, registered via `ServiceLoader` or a simple registry. Plugins communicate exclusively through the event bus.
- **Two-screen management** — `core` manages two JavaFX `Stage` instances (table view + DM panel).
- **DM panel layout** — a recursive split-pane layout modelled as an immutable binary tree (`PaneNode`). The tree is serialised as an S-expression and persisted to `~/.tabletopcontrol/dm-layout.conf`.
- **Event-driven rendering** — the Canvas redraws only on triggered events (scene load, fog-of-war change, token move), not in a real-time loop.

---

## Coding Conventions

- **Kotlin idioms:** data classes, extension functions, sealed classes, coroutines where appropriate.
- **Naming:** `PascalCase` for classes/interfaces, `camelCase` for functions/properties, `SCREAMING_SNAKE_CASE` for constants.
- **KDoc** on every public class and function; inline comments for non-obvious logic.
- **Composition over inheritance**; keep classes small and single-responsibility.
- **Error handling:** `Result` / sealed class for recoverable errors; unrecoverable errors propagate to a top-level handler that logs and shows a user-friendly message.
- **No UI/business-logic mixing:** JavaFX controllers contain only UI wiring; all business logic lives in separate classes.

---

## Documentation

Each plugin module ships a **`UserDoc.html`** file (e.g. `light/UserDoc.html`,
`audio/UserDoc.html`, `map/UserDoc.html`, `tracker/UserDoc.html`) that is the
primary user-facing reference for that plugin.

**Whenever you add, change, or remove a feature in any plugin, you must also
update the corresponding `UserDoc.html`:**

- Add new effects, controls, or parameters to the relevant table or section.
- Update descriptions if behaviour changes.
- Add new ToC entries for significant new sections.
- The docs index (`docs/index.html`) and cross-plugin guide (`docs/cross-plugin.html`)
  should also be updated if you introduce cross-plugin interactions.

Failing to keep `UserDoc.html` files current is considered an incomplete change.

---

## What to Avoid

- No WebView, WebGL, or embedded browser for rendering.
- No heavy animations or 3D models.
- No mandatory internet connection at runtime.
- No large runtime dependencies that are hard to install on a minimal Linux system.
- Do not add third-party UI libraries unless absolutely necessary.
