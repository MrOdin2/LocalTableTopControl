# Graph Report - C:\Users\Fabia\IdeaProjects\TabletopControl  (2026-08-25)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 3910 nodes · 8563 edges · 206 communities (176 shown, 30 thin omitted)
- Extraction: 88% EXTRACTED · 12% INFERRED · 0% AMBIGUOUS · INFERRED: 1024 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `47ab6c99`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- DynamicSightlineMesh.kt
- DynamicMapBuilderView
- DynamicMapEvents.kt
- DynamicMapGeometry.kt
- PlayerWebServer
- .showGuidedCalibrationDialog
- TrackerPlugin
- ThemeManagerTest
- DynamicMapBuilderController
- DynamicMapPoint
- PresetLibrary
- .showGuidedCalibrationDialog
- MapTokenSyncService
- MapSettingsService
- ActorTracker
- AdvancedLightJson.kt
- WledSerialSenderTest
- ActorFeatures
- MapSettingsService
- DynamicMapModel.kt
- MusicTrackService
- SoundboardSlotState
- LightPlugin
- MapMeasurementService
- LayoutSerializerTest
- DmPlugin
- MapMeasurementService
- LightEffect
- FakeManagedMediaPlayer
- SoundboardPlugin
- CanvasItemNode
- MapOperationModels.kt
- FakeManagedMediaPlayer
- DmLayoutManager
- DynamicMapBackgroundCalibration
- Boolean
- MapOperationModels.kt
- MusicPlugin
- MeasurementOverlay
- AdvancedLightSerialCoordinator
- MeasurementOverlay
- SceneLibrary
- DynamicMapBundle
- WledSerialSender
- Actor
- MapSettingsSerializerTest
- LightSerialCoordinator
- AdvancedLightPlugin
- LightControllerTest
- MapSettingsSerializer
- SoundboardSettingsStore.kt
- AdvancedLightController
- MapSettingsSerializerTest
- MapUiController
- MapUiController
- MusicSettings
- ManagedMediaPlayer
- SoundboardSlotModels.kt
- FakeManagedMediaPlayer
- DynamicSightlineMesh
- MapSettingsSerializer
- PlayerWebServerTest
- ActorImageSettings
- .showGuidedCalibrationDialog
- ActorPresetService
- Layer
- MusicTrackService.kt
- MediaTrackController
- CanvasPlugin
- .installDropTarget
- .parseProperties
- App
- SceneManager
- SceneSection
- DynamicMapOutlineBrowserPlugin.kt
- MapCalibration
- Int
- MapRenderer
- MapCalibration
- SoundboardPluginTest
- DynamicMapRuntimePoint
- .action
- guidedCalibrationStep2
- .deserialize
- LightController
- guidedCalibrationStep2
- nextAvailableTokenPlacement
- CanvasItem
- computeExtendOptions
- FogOfWarStateTest
- FogOfWarStateTest
- SoundboardSlotPlaybackPhase
- .createDialog
- reachableMovementCells
- GridCalibration
- GridConfigTest
- MapViewportState
- nextAvailableTokenPlacement
- LightOperationResult.kt
- GridConfigTest
- MapViewportState
- MapFogOfWarService
- .attachToEventBus
- App.kt
- RecordingListener
- FakeManagedMediaPlayer
- DynamicMapBundle.kt
- GridCalibration
- .deserialize
- .show
- String
- .build
- Token
- .attachToEventBus
- MapRenderer
- CanvasModel
- DynamicMapRuntimeWall
- DynamicPcSightlineContribution
- AdvancedLightPreferences
- AdvancedLightSegmentSendQueue
- TrackerSceneState
- MediaTrackStatus
- SceneBrowserDialog
- MenuAction
- MeasurementTool
- Token
- Effect
- FileChooserHistoryStore
- movementBlocked
- Double
- MapTokenSyncService
- tableViewportSceneBounds
- TokenSize
- .reorderMutableListFromDrop
- EventBusTest
- FileChooserHistoryStoreTest
- ReorderSupportTest
- FogOfWarState
- AdvancedLightSegmentState
- LightSerialCommand
- FogOfWarState
- MeasurementTool
- .export
- DynamicMapPlugin
- LightSceneState
- LightOperatorFeedbackPresenter
- MapPlugin
- effectsVisibleToRenderer
- RecentColorsStoreTest
- DynamicMapBuilderPlugin
- MusicTrackPlaybackPhase
- .chooseAudioFile
- MusicSettingsSerializerTest
- SoundboardSettingsStoreTest
- EventBus
- ConfigFiles
- ColorHexCodec
- AppConfigPathsTest
- SafeConfigIOTest
- MapSettingsServiceTest
- AppConfigPaths
- .textColorHexForBackground
- .showOpenDialog
- LayoutSerializerPersistenceTest
- LocalFilesTest
- .showUnitsDialog
- AdvancedLightPreferencesStoreTest
- Double
- MapCalibrationDialogs.kt
- .showUnitsDialog
- HelpManager
- removeNode
- .showDialog
- .load
- TokenImageChangedEventTest
- DialogFlowsTest
- MenuSectionTest
- MapCalibrationServiceTest
- MapCalibrationServiceTest
- TokenMoveDirection
- ColorContrastTest
- ColorHexCodecTest
- DynamicMapRuntimeWallKind
- DynamicMapRenderMode
- MapFogSceneMode
- MapMeasurementServiceTest
- MapFogSceneMode
- DynamicMapBundleLoaderTest
- EmptyDynamicMapArenaTest
- gradlew
- AdvancedLightJsonTest
- SceneStorageFormat
- DynamicMapDraftSerializerTest
- .addChangeListener

## God Nodes (most connected - your core abstractions)
1. `MapRenderer` - 96 edges
2. `DynamicMapPoint` - 68 edges
3. `ActorTracker` - 68 edges
4. `Actor` - 66 edges
5. `DynamicMapBuilderView` - 60 edges
6. `PlayerWebServer` - 54 edges
7. `DynamicMapBuilderController` - 47 edges
8. `WledSerialSenderTest` - 46 edges
9. `LayoutSerializerTest` - 44 edges
10. `SoundboardSlotState` - 40 edges

## Surprising Connections (you probably didn't know these)
- `MusicPlugin` --implements--> `DmPlugin`  [EXTRACTED]
  audio/src/main/kotlin/com/tabletopcontrol/audio/MusicPlugin.kt → core/src/main/kotlin/com/tabletopcontrol/core/DmPlugin.kt
- `MusicPlugin` --implements--> `SceneParticipant`  [EXTRACTED]
  audio/src/main/kotlin/com/tabletopcontrol/audio/MusicPlugin.kt → core/src/main/kotlin/com/tabletopcontrol/core/scene/SceneParticipant.kt
- `MusicPlugin` --references--> `DropIndicator`  [EXTRACTED]
  audio/src/main/kotlin/com/tabletopcontrol/audio/MusicPlugin.kt → core/src/main/kotlin/com/tabletopcontrol/core/ui/DropIndicator.kt
- `SoundboardPlugin` --implements--> `DmPlugin`  [EXTRACTED]
  audio/src/main/kotlin/com/tabletopcontrol/audio/SoundboardPlugin.kt → core/src/main/kotlin/com/tabletopcontrol/core/DmPlugin.kt
- `SoundboardPlugin` --implements--> `SceneParticipant`  [EXTRACTED]
  audio/src/main/kotlin/com/tabletopcontrol/audio/SoundboardPlugin.kt → core/src/main/kotlin/com/tabletopcontrol/core/scene/SceneParticipant.kt

## Import Cycles
- None detected.

## Communities (206 total, 30 thin omitted)

### Community 0 - "DynamicSightlineMesh.kt"
Cohesion: 0.05
Nodes (69): addPointLightContribution(), circleArea(), combine(), dimLightContribution(), DynamicLightMask, DynamicLightTintContribution, fromBundle(), fromPcTokenLights() (+61 more)

### Community 1 - "DynamicMapBuilderView"
Cohesion: 0.09
Nodes (24): DynamicMapBuilderView, DynamicMapMoveDrag, isWallPlacementTool(), Boolean, Color, Double, GraphicsContext, HBox (+16 more)

### Community 2 - "DynamicMapEvents.kt"
Cohesion: 0.05
Nodes (47): java, cleanConstructionSiteName(), constructionSiteText(), DynamicMapConstructionSiteStore, DynamicMapConstructionSiteSummary, isValidSiteId(), Boolean, File (+39 more)

### Community 3 - "DynamicMapGeometry.kt"
Cohesion: 0.09
Nodes (56): addEndpointGapConnectors(), addEndpointToSegmentGapConnectors(), addUniqueGapConnector(), boundsForSelections(), canMergeWallCandidates(), clampMovementDelta(), closeSmallWallGaps(), compareWallEndpoints() (+48 more)

### Community 4 - "PlayerWebServer"
Cohesion: 0.10
Nodes (15): Boolean, File, String, LocalFiles, ExecutorService, HttpExchange, HttpServer, Boolean (+7 more)

### Community 5 - ".showGuidedCalibrationDialog"
Cohesion: 0.09
Nodes (29): CalibrationOffsetAxis, HORIZONTAL, VERTICAL, DynamicMapCalibrationDialogs, DynamicMapCalibrationFormSupport, DynamicMapCalibrationInput, GUIDED_TILE_SPAN, OFFSET_X (+21 more)

### Community 6 - "TrackerPlugin"
Cohesion: 0.08
Nodes (29): ColorEditorDialog, Color, Double, String, Window, allowOnlyNonNegativeIntegers(), formatDouble(), InputHelpers (+21 more)

### Community 7 - "ThemeManagerTest"
Cohesion: 0.06
Nodes (16): isValidHexColor(), Boolean, String, ThemeConfig, ThemeMode, DARK, LIGHT, ThemeChangedEvent (+8 more)

### Community 8 - "DynamicMapBuilderController"
Cohesion: 0.09
Nodes (10): cleanBuilderLabel(), DynamicMapBuilderController, Boolean, Int, List, Set, String, Unit (+2 more)

### Community 9 - "DynamicMapPoint"
Cohesion: 0.12
Nodes (17): DynamicMapDraftSerializer, String, buildRectangleWalls(), moveSelections(), optimizeWallTopology(), DynamicMapDocument, DynamicMapElementGroup, DynamicMapElementSelection (+9 more)

### Community 10 - "PresetLibrary"
Cohesion: 0.09
Nodes (20): ByteArray, ConcurrentHashMap, DynamicMapGameplayExporterTest, java, Map, Path, String, Boolean (+12 more)

### Community 11 - ".showGuidedCalibrationDialog"
Cohesion: 0.08
Nodes (27): File, String, T, SafeConfigIO, DmLayoutManagerContextMenuTest, File, String, CalibrationOffsetAxis (+19 more)

### Community 12 - "MapTokenSyncService"
Cohesion: 0.11
Nodes (15): ActiveTokenChangedEvent, Boolean, TokenAddedEvent, TokenEffect, TokenEffectsChangedEvent, TokenImageChangedEvent, TokenMovedEvent, TokenMovementDashRequestedEvent (+7 more)

### Community 13 - "MapSettingsService"
Cohesion: 0.10
Nodes (23): Color, File, GridCalibration, GridConfig, MapCalibration, MapSceneState, TableMapOffset, MapSettingsService (+15 more)

### Community 14 - "ActorTracker"
Cohesion: 0.14
Nodes (11): TokenRemovedEvent, ActorMovementBudget, ActorTracker, Boolean, Color, Double, InitiativeTieResolver, Int (+3 more)

### Community 15 - "AdvancedLightJson.kt"
Cohesion: 0.12
Nodes (24): AdvancedLightJson, arrayValue(), asArrayOrNull(), asIntOrNull(), asObjectOrNull(), booleanValue(), intValue(), JsonArray (+16 more)

### Community 17 - "ActorFeatures"
Cohesion: 0.08
Nodes (20): ActorFeatures, ActorLightSource, ActorType, NPC, PC, DistanceRange, DistanceUnit, FEET (+12 more)

### Community 18 - "MapSettingsService"
Cohesion: 0.11
Nodes (20): Color, GridCalibration, GridConfig, Int, MapCalibration, MapSceneState, TableMapOffset, MapSettingsService (+12 more)

### Community 19 - "DynamicMapModel.kt"
Cohesion: 0.06
Nodes (37): containsSelection(), DynamicMapElementKind, LIGHT, SUNLIGHT_AREA, WALL, DynamicMapLayer, BACKGROUND, GRID (+29 more)

### Community 20 - "MusicTrackService"
Cohesion: 0.15
Nodes (14): Activated, Failed, Boolean, Double, Int, List, Long, String (+6 more)

### Community 21 - "SoundboardSlotState"
Cohesion: 0.15
Nodes (10): SoundboardSlotConfig, SoundboardSlotPlaybackResult, SoundboardSlotSnapshot, SoundboardSlotState, Boolean, Int, List, String (+2 more)

### Community 22 - "LightPlugin"
Cohesion: 0.08
Nodes (15): Int, Node, String, LightPlugin, LightDebugConsoleSection, LightEffectParamsSection, Double, HBox (+7 more)

### Community 23 - "MapMeasurementService"
Cohesion: 0.10
Nodes (14): Boolean, GuidedCalibrationAxis, MapResult, MeasurementOverlay, MeasurementType, String, Unit, MapCalibrationService (+6 more)

### Community 24 - "LayoutSerializerTest"
Cohesion: 0.09
Nodes (3): findAncestry(), replaceNodeByRef(), LayoutSerializerTest

### Community 25 - "DmPlugin"
Cohesion: 0.07
Nodes (23): javafx, toolbarViewsForWorkspace(), DmPlugin, Boolean, Node, Set, String, DmWorkspaceId (+15 more)

### Community 26 - "MapMeasurementService"
Cohesion: 0.12
Nodes (13): Boolean, GuidedCalibrationAxis, MapResult, MeasurementOverlay, MeasurementType, String, Unit, MapCalibrationService (+5 more)

### Community 27 - "LightEffect"
Cohesion: 0.06
Nodes (32): LightEffect, BLINK, BOUNCING_BALLS, CANDLE, CANDLE_MULTI, CHASE_COLOR, COLOR_LOOP, COLOR_WIPE (+24 more)

### Community 28 - "FakeManagedMediaPlayer"
Cohesion: 0.12
Nodes (12): FakeControllerFactory, FakeManagedMediaPlayer, FakeSettingsStore, ChangeListener, Double, Duration, FakeManagedMediaPlayer, Int (+4 more)

### Community 29 - "SoundboardPlugin"
Cohesion: 0.14
Nodes (13): clampButtonCount(), columnsForWidth(), Boolean, Button, Double, Int, Label, Node (+5 more)

### Community 30 - "CanvasItemNode"
Cohesion: 0.11
Nodes (13): CanvasDmView, CanvasItemNode, Any, Boolean, ContextMenu, Double, javafx, List (+5 more)

### Community 31 - "MapOperationModels.kt"
Cohesion: 0.10
Nodes (29): DynamicMapLoadFailed, Failure, getOrNull(), GuidedCalibrationBaseMissing, GuidedCalibrationTargetTooClose, ImageLoadFailed, MapOperationError, Nothing (+21 more)

### Community 32 - "FakeManagedMediaPlayer"
Cohesion: 0.13
Nodes (7): FakeManagedMediaPlayer, ChangeListener, Double, Duration, Int, Unit, MediaTrackControllerTest

### Community 33 - "DmLayoutManager"
Cohesion: 0.17
Nodes (11): DmLayoutManager, Boolean, BorderPane, ContextMenu, Map, Node, Orientation, String (+3 more)

### Community 34 - "DynamicMapBackgroundCalibration"
Cohesion: 0.13
Nodes (15): computeEditorMetrics(), drawCalibratedBackgroundImage(), DynamicMapBackgroundCalibration, DynamicMapGuidedCalibrationAxis, HORIZONTAL, VERTICAL, DynamicMapWorkspaceViewport, fittedBackgroundCalibration() (+7 more)

### Community 35 - "Boolean"
Cohesion: 0.13
Nodes (10): displayIcon(), effectsVisibleToRenderer(), Boolean, com, Int, Iterable, List, Pair (+2 more)

### Community 36 - "MapOperationModels.kt"
Cohesion: 0.10
Nodes (28): Failure, getOrNull(), GuidedCalibrationBaseMissing, GuidedCalibrationTargetTooClose, ImageLoadFailed, MapOperationError, Nothing, String (+20 more)

### Community 37 - "MusicPlugin"
Cohesion: 0.13
Nodes (14): Button, Duration, HBox, Int, Node, String, VBox, MusicPlugin (+6 more)

### Community 38 - "MeasurementOverlay"
Cohesion: 0.13
Nodes (14): distancePointToSegment(), formatMeasure(), Boolean, Double, Int, String, MeasurementOverlay, MeasurementType (+6 more)

### Community 39 - "AdvancedLightSerialCoordinator"
Cohesion: 0.17
Nodes (7): AdvancedLightSerialCoordinator, Boolean, Int, List, String, Unit, QueryResult

### Community 40 - "MeasurementOverlay"
Cohesion: 0.13
Nodes (14): distancePointToSegment(), formatMeasure(), Boolean, Double, Int, String, MeasurementOverlay, MeasurementType (+6 more)

### Community 41 - "SceneLibrary"
Cohesion: 0.21
Nodes (7): Boolean, File, List, String, ParsedScene, SceneLibrary, SavedScene

### Community 42 - "DynamicMapBundle"
Cohesion: 0.19
Nodes (3): DynamicMapBundle, Image, GraphicsContext

### Community 43 - "WledSerialSender"
Cohesion: 0.19
Nodes (9): Closeable, Boolean, Double, Int, List, String, Triple, WledSerialSender (+1 more)

### Community 44 - "Actor"
Cohesion: 0.12
Nodes (7): TokenMovementBudgetChangedEvent, Actor, AddActorDialog, Window, ActorTrackerTest, Int, String

### Community 46 - "LightSerialCoordinator"
Cohesion: 0.15
Nodes (8): Exception, Boolean, Int, java, List, String, Unit, LightSerialCoordinator

### Community 47 - "AdvancedLightPlugin"
Cohesion: 0.18
Nodes (12): AdvancedLightPlugin, ConnectionPanel, EditorContext, ContextMenu, Double, Int, javafx, Label (+4 more)

### Community 49 - "MapSettingsSerializer"
Cohesion: 0.21
Nodes (9): Color, GridCalibration, Int, MapCalibration, String, TableMapOffset, MapSavedSettings, MapSettingsSerializer (+1 more)

### Community 50 - "SoundboardSettingsStore.kt"
Cohesion: 0.17
Nodes (17): Failed, InvalidFormat, File, Int, List, String, Loaded, Missing (+9 more)

### Community 51 - "AdvancedLightController"
Cohesion: 0.23
Nodes (7): AdvancedLightController, Boolean, Double, Int, List, String, AdvancedLightSegmentCommand

### Community 53 - "MapUiController"
Cohesion: 0.16
Nodes (12): TokenEffectsReplayRequestedEvent, Canvas, Color, ComboBox, List, MapRenderer, MeasurementOverlay, MeasurementType (+4 more)

### Community 54 - "MapUiController"
Cohesion: 0.17
Nodes (12): DynamicMapDoorStateChangedEvent, Canvas, Color, ComboBox, List, MapRenderer, MeasurementOverlay, Node (+4 more)

### Community 55 - "MusicSettings"
Cohesion: 0.17
Nodes (8): List, Properties, String, MusicSettings, MusicSettingsSerializer, PersistedMusicTrack, MusicTrackSettingsStore, SerializerMusicTrackSettingsStore

### Community 56 - "ManagedMediaPlayer"
Cohesion: 0.17
Nodes (7): createJavaFxManagedPlayer(), JavaFxManagedMediaPlayer, Double, Int, String, Unit, ManagedMediaPlayer

### Community 57 - "SoundboardSlotModels.kt"
Cohesion: 0.13
Nodes (19): Failed, IdleColored, IdleDefault, String, Loaded, NoActiveTrack, PlayerError, PlayingColored (+11 more)

### Community 58 - "FakeManagedMediaPlayer"
Cohesion: 0.13
Nodes (7): FakeManagedMediaPlayer, ChangeListener, Double, Duration, Int, Unit, SoundboardSlotVisualsTest

### Community 59 - "DynamicSightlineMesh"
Cohesion: 0.14
Nodes (13): DynamicSightlineMesh, CachedLayer, DynamicLightTintLayerKey, DynamicMapBaseLayerKey, DynamicSightlineLayerKey, DynamicSightlineLayerRole, CURRENT, NEVER_SEEN (+5 more)

### Community 60 - "MapSettingsSerializer"
Cohesion: 0.28
Nodes (8): Color, GridCalibration, Int, MapCalibration, String, TableMapOffset, MapSavedSettings, MapSettingsSerializer

### Community 61 - "PlayerWebServerTest"
Cohesion: 0.22
Nodes (4): HttpResponse, Int, String, PlayerWebServerTest

### Community 62 - "ActorImageSettings"
Cohesion: 0.19
Nodes (11): ImageView, ImageHandling, Boolean, Button, Double, Int, Slider, String (+3 more)

### Community 63 - ".showGuidedCalibrationDialog"
Cohesion: 0.23
Nodes (14): Collection, Double, HBox, Label, Map, MapCalibrationService, MapInputField, MapOperationError (+6 more)

### Community 64 - "ActorPresetService"
Cohesion: 0.12
Nodes (8): ActorPresetService, Boolean, List, String, toActor(), toPreset(), ActorPresetServiceTest, java

### Community 65 - "Layer"
Cohesion: 0.12
Nodes (9): AudioEngine, Boolean, Double, String, Layer, AMBIENT, MUSIC, SFX (+1 more)

### Community 66 - "MusicTrackService.kt"
Cohesion: 0.20
Nodes (12): MusicTrackLoadFailure, MusicTrackPlaybackAction, PAUSE, PLAY, STOP, MusicTrackPlaybackFailure, MusicTrackPlaybackResult, NoActiveTrack (+4 more)

### Community 67 - "MediaTrackController"
Cohesion: 0.14
Nodes (5): ChangeListener, Duration, MediaTrackController, current, total

### Community 68 - "CanvasPlugin"
Cohesion: 0.13
Nodes (11): CanvasPlugin, Boolean, Node, String, CanvasTableItemView, CanvasTableOverlay, Double, List (+3 more)

### Community 69 - ".installDropTarget"
Cohesion: 0.15
Nodes (11): DragDropContext, DragDropSupport, Double, Int, Node, Orientation, ScrollPane, String (+3 more)

### Community 70 - ".parseProperties"
Cohesion: 0.27
Nodes (9): DynamicMapBundleLoader, fromPersistence(), File, Int, List, MapResult, Nothing, Properties (+1 more)

### Community 71 - "App"
Cohesion: 0.25
Nodes (11): Application, App, applyTablePresentationMode(), BorderPane, List, Scene, moveTableStageToScreen(), TableViewOption (+3 more)

### Community 72 - "SceneManager"
Cohesion: 0.15
Nodes (10): Boolean, List, String, SceneManager, Int, String, SceneLoadResult, SceneParticipant (+2 more)

### Community 73 - "SceneSection"
Cohesion: 0.12
Nodes (8): SceneSection, java, SceneLibraryTest, Int, java, MutableList, String, SceneManagerTest

### Community 74 - "DynamicMapOutlineBrowserPlugin.kt"
Cohesion: 0.22
Nodes (19): buildGroupMemberOutlineNode(), buildLightOutlineLabel(), buildSunlightAreaOutlineLabel(), buildWallOutlineLabel(), displayWallKind(), DynamicMapOutlineBrowserPlugin, DynamicMapOutlineElementNode, DynamicMapOutlineGroupNode (+11 more)

### Community 76 - "Int"
Cohesion: 0.18
Nodes (9): FogOfWarState, Int, MapFogSceneState, Pair, MapFogOfWarService, FogOfWarCellEvent, FogOfWarResetEvent, FogOfWarSetupEvent (+1 more)

### Community 77 - "MapRenderer"
Cohesion: 0.16
Nodes (6): Color, GridCalibration, GridConfig, Long, MapCalibration, MapRenderer

### Community 80 - "DynamicMapRuntimePoint"
Cohesion: 0.30
Nodes (6): TokenLightSource, DynamicMapRuntimeLight, DynamicMapRuntimePoint, DynamicMapRuntimeSunlightArea, DynamicLightMaskTest, List

### Community 81 - ".action"
Cohesion: 0.17
Nodes (7): MenuSection, APPEARANCE, ARRANGE, BASIC, DANGER_ZONE, ContextMenuRendererLogicTest, String

### Community 82 - "guidedCalibrationStep2"
Cohesion: 0.16
Nodes (9): GuidedCalibrationAxis, HORIZONTAL, VERTICAL, guidedCalibrationStep1(), guidedCalibrationStep2(), Double, Int, MapCalibration (+1 more)

### Community 83 - ".deserialize"
Cohesion: 0.19
Nodes (7): TableMapOffset, Properties, String, MapFogSceneState, MapSceneCodec, MapSceneState, MapSceneCodecTest

### Community 84 - "LightController"
Cohesion: 0.30
Nodes (6): Boolean, Double, Int, String, LightController, LightOperationResult

### Community 85 - "guidedCalibrationStep2"
Cohesion: 0.16
Nodes (9): GuidedCalibrationAxis, HORIZONTAL, VERTICAL, guidedCalibrationStep1(), guidedCalibrationStep2(), Double, Int, MapCalibration (+1 more)

### Community 86 - "nextAvailableTokenPlacement"
Cohesion: 0.20
Nodes (14): draggedTokenOrigin(), Boolean, Double, Int, Iterable, List, Pair, Token (+6 more)

### Community 88 - "computeExtendOptions"
Cohesion: 0.14
Nodes (11): ChildPos, FIRST, SECOND, computeExtendOptions(), directionLabel(), Map, Orientation, String (+3 more)

### Community 91 - "SoundboardSlotPlaybackPhase"
Cohesion: 0.21
Nodes (9): SoundboardSlotPlaybackPhase, EMPTY, PLAYING, READY, STOPPED, UNAVAILABLE, Int, String (+1 more)

### Community 92 - ".createDialog"
Cohesion: 0.25
Nodes (10): ButtonType, ConfirmationTracker, DialogFlows, Boolean, Dialog, List, Node, String (+2 more)

### Community 93 - "reachableMovementCells"
Cohesion: 0.20
Nodes (10): DynamicMapRuntimeBackgroundCalibration, Set, String, reachableMovementCells(), DynamicMovementReachabilityTest, Double, Int, List (+2 more)

### Community 94 - "GridCalibration"
Cohesion: 0.18
Nodes (3): GridCalibration, Double, GridCalibrationTest

### Community 95 - "GridConfigTest"
Cohesion: 0.20
Nodes (4): contrastingGridColor(), GridConfig, Color, GridConfigTest

### Community 96 - "MapViewportState"
Cohesion: 0.32
Nodes (3): Double, MapRenderer, MapViewportState

### Community 97 - "nextAvailableTokenPlacement"
Cohesion: 0.24
Nodes (14): draggedTokenOrigin(), Boolean, Double, Int, Iterable, List, Pair, Token (+6 more)

### Community 98 - "LightOperationResult.kt"
Cohesion: 0.29
Nodes (16): Applied, Failure, InvalidBaudRate, InvalidBrightness, InvalidColor, InvalidEffectIntensity, InvalidEffectSpeed, InvalidPreset (+8 more)

### Community 99 - "GridConfigTest"
Cohesion: 0.20
Nodes (4): contrastingGridColor(), GridConfig, Color, GridConfigTest

### Community 100 - "MapViewportState"
Cohesion: 0.32
Nodes (3): Double, MapRenderer, MapViewportState

### Community 101 - "MapFogOfWarService"
Cohesion: 0.21
Nodes (7): FogOfWarState, MapFogSceneState, MapFogOfWarService, FogOfWarCellEvent, FogOfWarResetEvent, FogOfWarSetupEvent, MapFogOfWarServiceTest

### Community 102 - ".attachToEventBus"
Cohesion: 0.15
Nodes (10): cacheTokenImageWhenReady(), displayIcon(), Boolean, FogOfWarState, Image, MapResult, MutableMap, String (+2 more)

### Community 103 - "App.kt"
Cohesion: 0.23
Nodes (10): Array, effectivePixelSpan(), formatScaleSuffix(), formatScreenLabel(), Double, Int, String, main() (+2 more)

### Community 104 - "RecordingListener"
Cohesion: 0.23
Nodes (7): FakeControllerFactory, FakeSettingsStore, FakeManagedMediaPlayer, List, String, RecordingListener, SoundboardSlotServiceTest

### Community 105 - "FakeManagedMediaPlayer"
Cohesion: 0.17
Nodes (6): FakeManagedMediaPlayer, ChangeListener, Double, Duration, Int, Unit

### Community 106 - "DynamicMapBundle.kt"
Cohesion: 0.25
Nodes (15): behaviorForPoint(), blocksAnySightOrBrightWhenClosed(), blocksDimLight(), blocksDimLightFrom(), blocksDimLightWhenClosed(), blocksSightAndBright(), blocksSightAndBrightFrom(), DynamicMapRuntimeWallSideBehavior (+7 more)

### Community 107 - "GridCalibration"
Cohesion: 0.20
Nodes (3): GridCalibration, Double, GridCalibrationTest

### Community 108 - ".deserialize"
Cohesion: 0.21
Nodes (6): TableMapOffset, String, MapFogSceneState, MapSceneCodec, MapSceneState, MapSceneCodecTest

### Community 109 - ".show"
Cohesion: 0.23
Nodes (9): InitiativeTieDialog, Dialog, Int, List, Node, String, VBox, Window (+1 more)

### Community 110 - "String"
Cohesion: 0.31
Nodes (4): Char, String, LayoutSerializer, Parser

### Community 111 - ".build"
Cohesion: 0.19
Nodes (8): ContextMenuRenderer, Any, ContextMenu, List, String, Any, List, MenuContributor

### Community 112 - "Token"
Cohesion: 0.20
Nodes (3): Token, TokenFootprintsTest, TokenTest

### Community 113 - ".attachToEventBus"
Cohesion: 0.19
Nodes (8): cacheTokenImageWhenReady(), FogOfWarState, Image, MapResult, MutableMap, Unit, MovementBudgetOverlay, MapRendererImageCacheTest

### Community 114 - "MapRenderer"
Cohesion: 0.23
Nodes (5): GraphicsContext, GridCalibration, GridConfig, MapCalibration, MapRenderer

### Community 115 - "CanvasModel"
Cohesion: 0.23
Nodes (5): CanvasItemsChangedEvent, CanvasModel, Boolean, List, String

### Community 116 - "DynamicMapRuntimeWall"
Cohesion: 0.30
Nodes (4): DynamicMapRuntimeWall, DynamicSightlineMeshTest, Int, Token

### Community 117 - "DynamicPcSightlineContribution"
Cohesion: 0.23
Nodes (7): darkvisionMeshFrom(), DynamicPcSightlineContribution, Iterable, DynamicDarkvisionMaskTest, Double, Int, Token

### Community 118 - "AdvancedLightPreferences"
Cohesion: 0.33
Nodes (6): AdvancedLightPreferences, AdvancedLightSegmentPreference, mergeDeviceSegment(), WledDeviceSnapshot, WledSegmentSnapshot, AdvancedLightControllerTest

### Community 119 - "AdvancedLightSegmentSendQueue"
Cohesion: 0.19
Nodes (5): AdvancedLightSegmentSendQueue, Boolean, List, AdvancedLightSegmentSendQueueTest, Int

### Community 120 - "TrackerSceneState"
Cohesion: 0.38
Nodes (5): List, Properties, String, TrackerSceneCodec, TrackerSceneState

### Community 121 - "MediaTrackStatus"
Cohesion: 0.15
Nodes (10): Boolean, MediaTrackStatus, DISPOSED, HALTED, PAUSED, PLAYING, READY, STALLED (+2 more)

### Community 122 - "SceneBrowserDialog"
Cohesion: 0.32
Nodes (6): Boolean, HBox, List, String, Window, SceneBrowserDialog

### Community 124 - "MeasurementTool"
Cohesion: 0.15
Nodes (11): FogTool, DRAW, ERASE, NONE, MeasurementType, MeasurementTool, CIRCLE, CONE (+3 more)

### Community 125 - "Token"
Cohesion: 0.22
Nodes (3): Token, TokenFootprintsTest, TokenTest

### Community 126 - "Effect"
Cohesion: 0.19
Nodes (8): Effect, EffectLibrary, EffectTemplate, List, String, ActorEffectsDialog, List, Window

### Community 127 - "FileChooserHistoryStore"
Cohesion: 0.38
Nodes (4): FileChooserHistoryStore, File, Properties, String

### Community 128 - "movementBlocked"
Cohesion: 0.30
Nodes (11): isReachableDestination(), Boolean, Double, Int, List, Pair, Token, movementBlocked() (+3 more)

### Community 129 - "Double"
Cohesion: 0.23
Nodes (4): Double, DoubleArray, TableMapOffset, tableViewportSceneBounds()

### Community 130 - "MapTokenSyncService"
Cohesion: 0.32
Nodes (4): List, Pair, Token, MapTokenSyncService

### Community 131 - "tableViewportSceneBounds"
Cohesion: 0.21
Nodes (6): Color, DoubleArray, TableMapOffset, tableViewportOutlineColor(), tableViewportSceneBounds(), MapRendererHelpersTest

### Community 132 - "TokenSize"
Cohesion: 0.22
Nodes (10): fromPersistence(), Int, String, TokenSize, GARGANTUAN, HUGE, LARGE, MEDIUM (+2 more)

### Community 133 - ".reorderMutableListFromDrop"
Cohesion: 0.35
Nodes (6): Boolean, Int, MutableList, T, Plan, ReorderSupport

### Community 134 - "EventBusTest"
Cohesion: 0.29
Nodes (3): EventBusTest, OtherEvent, SampleEvent

### Community 135 - "FileChooserHistoryStoreTest"
Cohesion: 0.18
Nodes (3): FileChooserHistoryStoreTest, File, String

### Community 137 - "FogOfWarState"
Cohesion: 0.31
Nodes (3): FogOfWarState, Boolean, Int

### Community 138 - "AdvancedLightSegmentState"
Cohesion: 0.29
Nodes (5): AdvancedLightSegmentState, Double, AdvancedLightPreferencesStore, List, String

### Community 139 - "LightSerialCommand"
Cohesion: 0.31
Nodes (5): String, LightSerialCommand, LightStateSnapshot, Preset, State

### Community 140 - "FogOfWarState"
Cohesion: 0.31
Nodes (3): FogOfWarState, Boolean, Int

### Community 141 - "MeasurementTool"
Cohesion: 0.18
Nodes (10): FogTool, DRAW, ERASE, NONE, MeasurementTool, CIRCLE, CONE, LINE (+2 more)

### Community 142 - ".export"
Cohesion: 0.29
Nodes (6): DynamicMapGameplayExporter, DynamicMapGameplayExportSerializer, DynamicMapGameplayExportSummary, File, String, Result

### Community 143 - "DynamicMapPlugin"
Cohesion: 0.27
Nodes (4): DynamicMapPlugin, Int, Node, String

### Community 144 - "LightSceneState"
Cohesion: 0.27
Nodes (4): String, LightSceneCodec, LightSceneState, LightSceneCodecTest

### Community 145 - "LightOperatorFeedbackPresenter"
Cohesion: 0.42
Nodes (4): Label, String, Window, LightOperatorFeedbackPresenter

### Community 146 - "MapPlugin"
Cohesion: 0.27
Nodes (4): Int, Node, String, MapPlugin

### Community 147 - "effectsVisibleToRenderer"
Cohesion: 0.29
Nodes (4): effectsVisibleToRenderer(), com, List, Token

### Community 148 - "RecentColorsStoreTest"
Cohesion: 0.22
Nodes (3): File, String, RecentColorsStoreTest

### Community 149 - "DynamicMapBuilderPlugin"
Cohesion: 0.28
Nodes (5): DynamicMapBuilderPlugin, Node, Set, String, DynamicMapConstructionSiteToolbar

### Community 150 - "MusicTrackPlaybackPhase"
Cohesion: 0.25
Nodes (8): MusicTrackPlaybackPhase, ENDED, PAUSED, PLAYING, READY, STOPPED, UNAVAILABLE, UNLOADED

### Community 151 - ".chooseAudioFile"
Cohesion: 0.32
Nodes (5): File, Int, String, Window, SoundboardSlotDialogs

### Community 152 - "MusicSettingsSerializerTest"
Cohesion: 0.25
Nodes (3): File, String, MusicSettingsSerializerTest

### Community 154 - "EventBus"
Cohesion: 0.29
Nodes (3): EventBus, Any, Subscription

### Community 155 - "ConfigFiles"
Cohesion: 0.32
Nodes (4): ConfigFiles, File, Int, String

### Community 156 - "ColorHexCodec"
Cohesion: 0.54
Nodes (3): ColorHexCodec, Color, String

### Community 157 - "AppConfigPathsTest"
Cohesion: 0.25
Nodes (3): AppConfigPathsTest, File, String

### Community 160 - "AppConfigPaths"
Cohesion: 0.52
Nodes (3): AppConfigPaths, File, String

### Community 161 - ".textColorHexForBackground"
Cohesion: 0.48
Nodes (4): ColorContrast, Color, Double, String

### Community 162 - ".showOpenDialog"
Cohesion: 0.29
Nodes (5): AudioFileChooserDialog, File, List, String, Window

### Community 163 - "LayoutSerializerPersistenceTest"
Cohesion: 0.29
Nodes (3): File, String, LayoutSerializerPersistenceTest

### Community 165 - ".showUnitsDialog"
Cohesion: 0.38
Nodes (4): List, String, Window, MapMeasurementDialogs

### Community 166 - "AdvancedLightPreferencesStoreTest"
Cohesion: 0.29
Nodes (3): AdvancedLightPreferencesStoreTest, File, String

### Community 167 - "Double"
Cohesion: 0.57
Nodes (3): Double, Int, Pair

### Community 168 - "MapCalibrationDialogs.kt"
Cohesion: 0.29
Nodes (6): CalibrationOffsetAxis, HORIZONTAL, VERTICAL, GuidedCalibrationStep, SELECT_CENTER, SELECT_SECOND_POINT

### Community 169 - ".showUnitsDialog"
Cohesion: 0.38
Nodes (4): List, String, Window, MapMeasurementDialogs

### Community 170 - "HelpManager"
Cohesion: 0.40
Nodes (3): HelpManager, Path, HostServices

### Community 172 - ".showDialog"
Cohesion: 0.33
Nodes (4): ColorEditorPopover, Color, String, Window

### Community 173 - ".load"
Cohesion: 0.47
Nodes (3): Color, List, RecentColorsStore

### Community 179 - "TokenMoveDirection"
Cohesion: 0.40
Nodes (5): TokenMoveDirection, EAST, NORTH, SOUTH, WEST

### Community 182 - "DynamicMapRuntimeWallKind"
Cohesion: 0.40
Nodes (5): DynamicMapRuntimeWallKind, DOOR, FEATURE, HARD, SOFT

### Community 183 - "DynamicMapRenderMode"
Cohesion: 0.40
Nodes (5): DynamicMapRenderMode, DEBUG, RENDER, fromPersisted(), String

### Community 184 - "MapFogSceneMode"
Cohesion: 0.40
Nodes (5): MapFogSceneMode, HIDDEN_ALL, PARTIAL_HIDDEN, PARTIAL_REVEALED, REVEALED_ALL

### Community 186 - "MapFogSceneMode"
Cohesion: 0.40
Nodes (5): MapFogSceneMode, HIDDEN_ALL, PARTIAL_HIDDEN, PARTIAL_REVEALED, REVEALED_ALL

### Community 189 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 191 - "SceneStorageFormat"
Cohesion: 0.67
Nodes (3): SceneStorageFormat, LEGACY, XML

## Knowledge Gaps
- **206 isolated node(s):** `MUSIC`, `AMBIENT`, `SFX`, `UNLOADED`, `READY` (+201 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **30 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MapRenderer` connect `MapRenderer` to `DynamicSightlineMesh.kt`, `Double`, `Boolean`, `DynamicMapBundle`, `.showGuidedCalibrationDialog`, `.attachToEventBus`, `DynamicMapRenderMode`, `DynamicSightlineMesh`?**
  _High betweenness centrality (0.134) - this node is a cross-community bridge._
- **Why does `MenuAction` connect `MenuAction` to `DynamicMapBuilderView`, `MusicPlugin`, `.build`, `.action`, `MapUiController`, `MapUiController`, `SoundboardPlugin`?**
  _High betweenness centrality (0.119) - this node is a cross-community bridge._
- **Why does `DmPlugin` connect `DmPlugin` to `DmLayoutManager`, `CanvasPlugin`, `MusicPlugin`, `TrackerPlugin`, `App`, `DynamicMapOutlineBrowserPlugin.kt`, `DynamicMapPlugin`, `AdvancedLightPlugin`, `MapPlugin`, `DynamicMapBuilderPlugin`, `LightPlugin`, `SoundboardPlugin`?**
  _High betweenness centrality (0.118) - this node is a cross-community bridge._
- **Are the 21 inferred relationships involving `DynamicMapPoint` (e.g. with `.deserialize()` and `.`construction sites save load list and track the active site`()`) actually correct?**
  _`DynamicMapPoint` has 21 INFERRED edges - model-reasoned connections that need verification._
- **Are the 30 inferred relationships involving `ActorTracker` (e.g. with `.`active NPC publishes movement budget for map overlays`()` and `.`adding a pc actor marks its token as player controlled`()`) actually correct?**
  _`ActorTracker` has 30 INFERRED edges - model-reasoned connections that need verification._
- **Are the 23 inferred relationships involving `Actor` (e.g. with `.deserializeLegacy()` and `.deserializeProperties()`) actually correct?**
  _`Actor` has 23 INFERRED edges - model-reasoned connections that need verification._
- **What connects `MUSIC`, `AMBIENT`, `SFX` to the rest of the system?**
  _206 weakly-connected nodes found - possible documentation gaps or missing edges._