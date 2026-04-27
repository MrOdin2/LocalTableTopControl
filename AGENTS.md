# TabletopControl AI Agent Guidelines

This document provides essential context and rules for AI coding agents working on the TabletopControl project.

## 🏗 Architecture & Modules
TabletopControl is a dual-screen Kotlin/JavaFX application for tabletop RPGs.
- **Two Screens:** Manages a full-screen Table map (JavaFX `Canvas`) and a DM control panel window.
- **Plugin System:** Features (Map, Audio, Tracker, Lights) exist as separate Gradle modules and are loaded as `DmPlugin` instances at runtime.
- **Strict Decoupling:** Plugins communicate **exclusively** via the central `EventBus` (`core/src/.../EventBus.kt`). Do not establish direct references or dependencies between distinct feature plugins.
- **DM Layout Model:** The DM panel is a recursive split-pane defined by an immutable binary tree (`PaneNode`). It persists automatically as S-expressions via `LayoutSerializer`. 

## 🚀 Workflows & Commands
Always use the Gradle wrapper (`./gradlew`).
- **Run the Application:** `./gradlew :core:run`
- **Build All:** `./gradlew build`
- **Run Tests:** `./gradlew test` (Uses JUnit Jupiter 5 & MockK)
- **Build Distribution ZIP:** `./gradlew :core:distZip`
- **Install Distribution Locally:** `./gradlew :core:installDist`
- **Code Style:** Standard Kotlin `official` style natively applied. No standalone linting step is required.

## 🧩 Patterns & Conventions
- **Event-Driven UI/Map:** The Map `Canvas` is strictly event-driven to save resources on low-power devices. Do NOT implement real-time animation loops (like 60fps frame loops). Redraws trigger only on explicit layout, token, or fog updates.
- **No Web Tech / 3rd-Party UI:** Rely on standard JavaFX controls. WebView, WebGL, or heavy 3D rendering are strictly prohibited to maintain performance on minimal Linux builds (e.g., Raspberry Pi 4). No internet connection should be required at runtime.
- **Theming via CSS Variables:** UI elements correctly adapt to Dark/Light mode by leveraging semantic CSS variables. Never hardcode colors (e.g., `#FFFFFF`). Use `-tc-bg`, `-tc-surface`, `-tc-accent`, and `-tc-text` in styling strings. For Canvas rendering, intercept `ThemeChangedEvent` for color updates.
- **Configuration IO:** Never sprinkle raw configuration/file logic (`java.io.File`). Always use `AppConfigPaths` and `SafeConfigIO` (in `core.persistence`) to handle configuration read/writes safely in the standard user directory.
- **Domain Errors:** Rely on `Result` types or `sealed class` domain errors for recoverable situations; allow unrecoverable crashes to propagate up for standard logging.
- **Modularization of Plugins:** Large plugins like `TrackerPlugin` should be modularized into helper components for better maintainability. For example:
  - Separate UI construction and layout logic.
  - Isolate token synchronization with the `EventBus`.
  - Extract drag-and-drop reordering logic into reusable utilities.

## 📝 Documentation Mandate
- **`UserDoc.html` Updates are Mandatory:** Every plugin (e.g., `audio`, `map`, `tracker`) contains a `UserDoc.html`. If you add a new feature, change a UI button, or modify a behavior, you MUST update the corresponding `UserDoc.html` file using user-centric language (what it does, not how it's coded!).
- **Cross-Plugin Documentation:** If your changes introduce cross-plugin interactions, update `docs/cross-plugin.html` to reflect these changes. This ensures consistency and clarity across the entire system.
