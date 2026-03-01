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
- **Music Control** — three independent audio layers:
  - Background music
  - Ambient sounds
  - Sound effects
- **Initiative Tracker** — manage turn order for players and enemies.
- **Monster HP Tracker** — track hit points for encounters.

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
