# TabletopControl

A lightweight, no-frills tabletop RPG companion application built with **Kotlin** and **JavaFX**.  
Designed to run on modest hardware (including a Raspberry Pi) and to be easily extended through a plugin system.

---

## Overview

TabletopControl puts a large, physical-scale map at the centre of your table — displayed on a screen or projector — while the Dungeon Master controls everything from a separate screen.  
No flashy animations, no heavy 3D models; just the tools you actually need at the table.

---

## Main Features

### Player / Table Screen
- Full-screen map view rendered on a **JavaFX Canvas**, scaled to work with physical miniatures.
- Optional grid overlay on the map.

### DM Control Screen
- Map selection and map-view control (pan, zoom, etc.).
- Placement of visual obstructions (fog of war / line-of-sight blockers).
- **Music Control** — dynamic, reorderable music track cards (1–16):
  - Music tracks
  - Ambient loops and soundscapes
  - Sound effects via a customizable soundboard (up to 32 buttons)
- **Initiative Tracker** — manage turn order for players and enemies.
- **Monster HP Tracker** — track hit points for encounters.
- **Split-Pane Layout** — display multiple plugin panels simultaneously without switching tabs.

---

## DM Panel Layout System

The DM Panel uses a recursive **split-pane layout** inspired by modern IDEs (VS Code, IntelliJ) and Blender.

### How it Works

- On first launch, the panel shows a single plugin pane (the first loaded plugin).
- **Right-click** anywhere on a pane to open the context menu:
  - *Add Panel to Left*  — splits the current pane left/right and places a new plugin on the left.
  - *Add Panel to Right* — splits the current pane left/right and places a new plugin on the right.
  - *Add Panel Above* — splits the current pane top/bottom and places a new plugin above.
  - *Add Panel Below* — splits the current pane top/bottom and places a new plugin below.
  - *Change Plugin…* — swaps the plugin shown in the current pane.
  - *Close Pane* — removes the current pane (disabled when it is the only remaining pane).
  - *Extend Left / Right / Above / Below* — expands the current pane into the space of exactly one
    neighbouring pane, resizing that neighbour without affecting any others. Only directions that
    would interact with a single neighbouring pane are offered.
- Both halves of a split can themselves be split again — the nesting is unlimited.
- When the application closes, the current layout (including divider positions) is saved to `~/.tabletopcontrol/dm-layout.conf` and automatically restored on the next start-up.

---

## Technical Stack

| Concern | Choice |
|---------|--------|
| Language | Kotlin |
| UI Framework | JavaFX |
| Map Rendering | JavaFX Canvas API |
| Target Platforms | Linux (including Raspberry Pi and similar SBCs) |
| External Integration | REST / Webhook endpoint (planned) |

---

## Architecture & Design Goals

1. **Framework first** — Build a solid, reusable core before adding feature modules.
2. **Plugin system** — DM-screen GUI elements are loaded as plugins so new features can be added without touching the core.
3. **Best-practice OOP** — Clean separation of concerns, meaningful abstractions, no god classes.
4. **Readable code** — Well-structured packages, descriptive names, and comments wherever the intent is not immediately obvious.

### Shared Media Lifecycle Utility

Audio plugins use a shared JavaFX media lifecycle controller
(`audio.shared.MediaTrackController`) to centralize MediaPlayer load/play/stop/progress/error/dispose handling.

---

## Planned REST / Webhook Integration

The application will expose a lightweight HTTP interface so that external devices (phones, tablets, custom controllers) can interact with it — for example, to trigger sound effects or update the initiative tracker.

---

## Code of Conduct / Contribution Guidelines

- Follow established **object-oriented best practices**.
- Keep code **well-structured** and organised into logical packages/modules.
- Add **comments where needed** — if a piece of logic is not self-explanatory, explain it.
- Write code that is **easy to read and understand** by someone who did not write it.

---

## Getting Started

### Prerequisites

- **JDK 17** (Temurin or any OpenJDK 17 distribution)
- No separate Gradle installation required — the project ships with the Gradle wrapper

### Build

```bash
./gradlew build
```

### Run

```bash
./gradlew :core:run
```

### Test

```bash
./gradlew test
```

---

## License

> _License information will be added._
