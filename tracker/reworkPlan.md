#### Refined Subtasks:
1. **Tracker Services**:
   - [ ] Design and implement a `TrackerService` for initiative and round progression.
   - [ ] Create a `PresetService` to handle preset orchestration (load, save, import, export).

2. **Dialog Extraction**:
   - [ ] Extract token-image workflows into a `TokenImageDialog` class.
   - [ ] Extract preset workflows into a `PresetDialog` class.
   - [ ] Implement shared dialog helpers for consistent `Alert` handling using `DialogFlows`.
   - [ ] Leverage `DialogFlows` for dialog creation, result handling, and validation.

3. **Result Types**:
   - [ ] Define sealed `Result` types for preset operations (e.g., `PresetLoadResult`, `PresetSaveResult`).
   - [ ] Refactor existing preset workflows to return `Result` types.

4. **Code Cleanup**:
   - [ ] Remove direct `Alert` orchestration from `TrackerPlugin`.
   - [ ] Replace inline dialog logic with calls to shared dialog helpers.

5. **Testing**:
   - [ ] Write unit tests for `TrackerService` and `PresetService`.
   - [ ] Write unit tests for `TokenImageDialog` and `PresetDialog`.
   - [ ] Add integration tests to verify end-to-end functionality.

6. **Documentation**:
   - [ ] Update `UserDoc.html` with new dialog workflows and services.
   - [ ] Add before/after code links in the PR description to illustrate decomposition.


## Plan: Refactor TrackerPlugin by Responsibility

Refactor `TrackerPlugin` by extracting cohesive responsibilities into new, focused classes/services. This will reduce bloat, improve maintainability, and align with project modularization guidelines.

### Steps

1. **Extract UI Construction & Layout**
   - Create `TrackerUiBuilder` for all UI-building methods:
      - `createView`
      - `preferredOrientationForBounds`
      - `buildCardPane`
      - `buildCard`
   - Move orientation/layout logic and card/toolbar construction here.

2. **Extract Drag-and-Drop/Reordering**
   - Create `TrackerReorderSupport`:
      - DragDropContext setup and logic from `buildCardPane`/`buildCard`
      - Any helper logic for drag sources, drop targets, and indicator management.

3. **Extract Token Image Handling**
   - Create `TokenImageManager`:
      - `showTokenImageDialog`
      - `normalizeSupportedTokenImageUri`
      - Token image state management (e.g., `tokenImages`, `TokenImageSettings`).

4. **Extract Preset Library Management**
   - Create `PresetLibraryDialogManager`:
      - `showPresetsDialog`
      - Preset save/load/delete logic from card and dialog flows.

5. **Centralize EventBus Synchronization**
   - Create `TrackerEventSyncAdapter`:
      - All EventBus publish/subscribe logic (e.g., for token add/remove/reset/active/image events).
      - Decouple from UI and state logic.

6. **Isolate State Management**
   - Create `TrackerState`:
      - `tracker`, `tokenIds`, `tokenColors`, `tokenColorIndex`, and related state.
      - Expose mutation/query methods for use by UI and sync adapters.

7. **Utility/Formatting**
   - Move formatting helpers (e.g., `cardStyle`, `roundText`) to a `TrackerUiUtils` or as extension functions.

### Further Considerations

1. Should UI builders be split further (e.g., separate card vs. toolbar builders)?
2. Should state be fully immutable, or allow controlled mutation via `TrackerState`?
3. Consider dependency injection for managers/services to improve testability and decoupling.
