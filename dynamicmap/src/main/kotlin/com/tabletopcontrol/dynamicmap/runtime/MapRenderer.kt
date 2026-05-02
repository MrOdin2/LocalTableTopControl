package com.tabletopcontrol.dynamicmap.runtime

import javafx.scene.SnapshotParameters
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.effect.BlendMode
import javafx.scene.effect.ColorAdjust
import javafx.scene.image.Image
import javafx.scene.paint.Color
import javafx.scene.shape.StrokeLineCap
import javafx.scene.text.Font
import javafx.scene.text.TextAlignment
import com.tabletopcontrol.core.ActiveTokenChangedEvent
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.ThemeChangedEvent
import com.tabletopcontrol.core.ThemeManager
import com.tabletopcontrol.core.TokenAddedEvent
import com.tabletopcontrol.core.TokenImageChangedEvent
import com.tabletopcontrol.core.TokenMovedEvent
import com.tabletopcontrol.core.TokenRemovedEvent
import com.tabletopcontrol.core.TokensResetEvent
import com.tabletopcontrol.core.persistence.LocalFiles
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicLightMask
import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicLightTintContribution
import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicSightlineMesh
import com.tabletopcontrol.dynamicmap.runtime.logic.FogOfWarState
import com.tabletopcontrol.dynamicmap.runtime.logic.GridCalibration
import com.tabletopcontrol.dynamicmap.runtime.logic.GridConfig
import com.tabletopcontrol.dynamicmap.runtime.logic.MapCalibration
import com.tabletopcontrol.dynamicmap.runtime.logic.TableMapOffset
import com.tabletopcontrol.dynamicmap.runtime.logic.Token
import com.tabletopcontrol.dynamicmap.runtime.logic.nextAvailableTokenPlacement
import com.tabletopcontrol.dynamicmap.runtime.logic.tokenDrawBounds
import com.tabletopcontrol.dynamicmap.runtime.logic.tokenOccupiedCells
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Renders the tabletop map onto a JavaFX [Canvas].
 *
 * The renderer is **event-driven, not a real-time loop**: it redraws only when
 * explicitly requested — for example when a new map image is loaded or the
 * fog-of-war state changes.  This keeps CPU usage low on modest hardware.
 *
 * Rendering is composed of layers drawn in order:
 * 1. Background fill — configurable via [backgroundColor] (defaults to black).
 * 2. Map image — scaled from the canvas centre according to [mapCalibration].
 * 3. Grid overlay — drawn when [gridConfig] is non-null and visible, using
 *    [gridCalibration] with the canvas centre as the scale origin.
 * 4. Fog-of-war and DynamicMap sightlines - unrevealed or unseen areas are covered with
 *    overlays matching their configured opacity.
 *
 * Both the grid and map image are calibrated independently and both use the canvas
 * centre as their scale origin, so the grid can be shown without any map image loaded.
 *
 * ### Viewport pan/zoom (minimap use-case)
 *
 * [viewportScale], [viewportOffsetX], and [viewportOffsetY] let a second renderer
 * instance (e.g. the DM-panel minimap) zoom and pan its view independently from
 * the table-view renderer.  These fields are **not** driven by any [EventBus] event
 * and default to an identity transform (scale 1.0, no offset), so the table-view
 * renderer is unaffected.
 *
 * The viewport transform is applied around the canvas centre:
 * - Positive [viewportScale] values zoom in/out from the canvas centre.
 * - [viewportOffsetX]/[viewportOffsetY] shift the entire rendered scene in
 *   canvas-space pixels (positive = shift content right/down).
 *
 * @param canvas the [Canvas] to draw on; must be attached to a scene before
 *               calling [redraw].
 */
class MapRenderer(private val canvas: Canvas) {

    private val gc: GraphicsContext = canvas.graphicsContext2D

    /** Currently loaded map image, or `null` if no map has been loaded yet. */
    var mapImage: Image? = null
        private set

    /** Currently loaded Dynamic Map bundle, or `null` when the plugin is empty. */
    var dynamicMapBundle: DynamicMapBundle? = null
        private set

    private var dynamicMapBackgroundImage: Image? = null

    /** Calibration parameters controlling scale and centre-offset of the map image. */
    var mapCalibration: MapCalibration = MapCalibration()

    /** Calibration parameters controlling cell size, scale, and centre-offset of the grid. */
    var gridCalibration: GridCalibration = GridCalibration()

    /** Grid overlay configuration, or `null` to disable the grid. */
    var gridConfig: GridConfig? = null

    /**
     * Background fill colour used behind the map image.
     *
     * When no map image is loaded this colour is the sole visible canvas content,
     * acting as a plain-colour map.  When an image is loaded it is drawn on top of
     * this fill.  Defaults to [Color.BLACK] to preserve the original behaviour.
     */
    var backgroundColor: Color = Color.BLACK

    /** Fog-of-war cell state, or `null` when fog of war is not active. */
    var fogOfWar: FogOfWarState? = null

    /** Cached DynamicMap player line-of-sight triangle mesh, or `null` when no dynamic bundle is loaded. */
    private var dynamicSightlineMesh: DynamicSightlineMesh? = null

    /** Accumulated DynamicMap areas that have been seen by PCs at least once. */
    private var dynamicSeenSightlineMesh: DynamicSightlineMesh? = null

    /** Static authored light mask used for visibility clipping and visible light tint. */
    private var dynamicLightMask: DynamicLightMask? = null

    /** Currently visible terrain that is revealed only through PC darkvision. */
    private var dynamicDarkvisionMesh: DynamicSightlineMesh? = null

    private var fogLayerVersion: Long = 0L
    private var dynamicSightlineLayerVersion: Long = 0L
    private var dynamicMapBaseLayerCache: CachedLayer<DynamicMapBaseLayerKey>? = null
    private var dynamicMapGrayscaleLayerCache: CachedLayer<DynamicMapBaseLayerKey>? = null
    private var fogLayerCache: CachedLayer<FogLayerKey>? = null
    private val dynamicSightlineLayerCaches = mutableMapOf<DynamicSightlineLayerRole, CachedLayer<DynamicSightlineLayerKey>>()
    private var dynamicLightTintLayerCache: CachedLayer<DynamicLightTintLayerKey>? = null

    /**
     * Opacity of unrevealed fog-of-war tiles, in the range [0.0, 1.0].
     *
     * Set to `1.0` for the table view so players cannot see through the fog at all,
     * and to a lower value (e.g. `0.5`) for the DM minimap so the underlying map
     * remains visible beneath the fog.  Defaults to `1.0`.
     */
    var fogOpacity: Double = 1.0

    /** Tint used by the DynamicMap sightline overlay. Defaults to black to match fog of war. */
    var dynamicSightlineTint: Color = Color.BLACK

    /**
     * Opacity of the DynamicMap sightline overlay. When `null`, [fogOpacity] is used so the
     * table view stays opaque and the DM minimap stays translucent by default.
     */
    var dynamicSightlineOpacity: Double? = null

    /** Grid-column offset: fog array column 0 corresponds to grid column [fogColOffset]. */
    private var fogColOffset: Int = 0

    /** Grid-row offset: fog array row 0 corresponds to grid row [fogRowOffset]. */
    private var fogRowOffset: Int = 0

    /**
     * Clockwise rotation applied to the map image, in degrees.
     *
     * Only multiples of 90 are meaningful; all other rendering layers (grid,
     * fog, tokens) are unaffected so the grid remains stationary while the
     * image spins behind it.  Rotation is around the canvas centre, which is
     * the same reference point used for all calibration operations.
     * Defaults to `0` (no rotation).
     */
    var mapRotationDegrees: Int = 0

    /** Shared displacement applied to the entire rendered table map. */
    var tableMapOffset: TableMapOffset = TableMapOffset()

    /** Whether this renderer should apply the shared [tableMapOffset]. */
    var applyTableMapOffset: Boolean = true

    /** Whether this renderer should draw a dashed outline of the table viewport. */
    var showTableViewportOutline: Boolean = false

    /** When `true` a yellow crosshair is drawn at the canvas centre. */
    private var gridCalibrationMode: Boolean = false

    /** When `true` a red dot is drawn at the canvas centre. */
    private var mapCalibrationMode: Boolean = false

    /**
     * Viewport zoom factor applied to this canvas only, independent of [mapCalibration]
     * and [gridCalibration].  Values > 1 zoom in; values < 1 zoom out.  Defaults to
     * `1.0` (no zoom).  The zoom origin is the canvas centre.
     */
    var viewportScale: Double = 1.0

    /**
     * Viewport horizontal pan offset in canvas-space pixels.  Positive values shift
     * all rendered content to the right.  Defaults to `0.0`.
     */
    var viewportOffsetX: Double = 0.0

    /**
     * Viewport vertical pan offset in canvas-space pixels.  Positive values shift
     * all rendered content downwards.  Defaults to `0.0`.
     */
    var viewportOffsetY: Double = 0.0

    /**
     * When `true`, tokens whose grid cell is covered by unrevealed fog-of-war or
     * whose centre is outside the DynamicMap sightline mesh are not drawn. Set this to `true` for
     * the player-facing table view; leave it at `false` (the default) for the DM
     * minimap so tokens remain visible and can be dragged regardless of visibility.
     */
    var hideTokensInFog: Boolean = false

    /**
     * When `true`, player-facing DynamicMap sightlines remember previously seen terrain.
     *
     * This affects only the sightline mask rendering. Token visibility still uses
     * [dynamicSightlineMesh], so players cannot see NPC movement in remembered areas.
     */
    var usePersistentVision: Boolean = false

    /**
     * When `true`, each token's display name is drawn below its circle so that
     * players can identify which token belongs to which combatant.  Defaults to
     * `false`.  Toggled via [ShowTokenNamesEvent].
     */
    var showTokenNames: Boolean = false

    /**
     * When `true`, PC tokens are redrawn above fog and DynamicMap sightline overlays on the
     * player-facing table view so the party's own positions are always visible.
     */
    var forcePlayerCharacterTokensVisible: Boolean = false

    /** Whether DM-only measurement overlays should be rendered on this renderer instance. */
    var showDmOnlyMeasurements: Boolean = true

    /** Whether runtime light source markers should be drawn in addition to light halos. */
    var showDynamicLightMarkers: Boolean = true

    /** Whether hidden DynamicMap door icons should be shown by this renderer. */
    var showHiddenDynamicDoorIcons: Boolean = true

    /** Current DynamicMap wall/light visualisation mode. */
    var dynamicMapRenderMode: DynamicMapRenderMode = DynamicMapRenderMode.RENDER

    /** Current list of tokens to draw on the map. */
    private val tokens = mutableListOf<Token>()

    /**
     * Cache of [Image] objects keyed by their URI string.
     *
     * Loading a [javafx.scene.image.Image] from disk is expensive; caching by URI ensures
     * that the same file is not decoded multiple times even when multiple tokens share the
     * same picture.  Entries are added on [TokenImageChangedEvent] and removed when the
     * last token that references a URI is removed or its image is replaced.
     */
    private val imageCache = mutableMapOf<String, Image>()

    /** Stable ID of the currently active combatant's token, or `null` when none is active. */
    private var activeTokenId: String? = null

    /** Active measurement overlays keyed by their stable IDs. */
    private val measurements = linkedMapOf<String, MeasurementOverlay>()

    /** Runtime-open DynamicMap door ids. Open doors keep their icon but no longer draw as blockers. */
    private val openDynamicDoorIds = linkedSetOf<String>()

    /** Latest grid colour, retained even while the visible grid is disabled. */
    private var lastGridColor: Color = GridConfig().color

    /** Current theme accent colour used for builder-style DebugMode walls. */
    private var dynamicMapDebugWallColor: Color =
        ColorHexCodec.parseOrDefault(ThemeManager.currentTheme.accentColor, Color.DODGERBLUE)

    /** Current player-facing table canvas width in pixels. */
    private var tableViewportWidth: Double = 0.0

    /** Current player-facing table canvas height in pixels. */
    private var tableViewportHeight: Double = 0.0

    /**
     * All active [EventBus.Subscription] handles for this renderer.
     * Populated in [attachToEventBus] and released en masse in [dispose].
     */
    private val subscriptions = mutableListOf<EventBus.Subscription>()

    private fun isSupportedTokenImageUri(uri: String): Boolean = LocalFiles.fileFromUriOrPath(uri) != null

    init {
        attachToEventBus()
    }

    /**
     * Subscribes to map-related events published by [MapPlugin] via [EventBus]
     * so that this renderer can update the canvas in response.
     *
     * Every subscription handle is stored in [subscriptions] so that [dispose]
     * can unregister them all when the renderer is no longer needed.
     */
    private fun attachToEventBus() {
        subscriptions += EventBus.subscribe<MapLoadEvent> { event ->
            loadImage(event.resourcePath)
        }
        subscriptions += EventBus.subscribe<DynamicMapLoadEvent> { event ->
            loadDynamicMap(event.bundle)
        }
        subscriptions += EventBus.subscribe<DynamicMapRenderModeEvent> { event ->
            dynamicMapRenderMode = event.mode
            redraw()
        }
        subscriptions += EventBus.subscribe<DynamicMapDoorStateChangedEvent> { event ->
            if (event.open) {
                openDynamicDoorIds += event.wallId
            } else {
                openDynamicDoorIds -= event.wallId
            }
            redraw()
        }
        subscriptions += EventBus.subscribe<DynamicSightlineMeshUpdatedEvent> { event ->
            val mesh = event.mesh
            if (mesh == null) {
                dynamicSightlineMesh = null
                dynamicSeenSightlineMesh = null
                dynamicLightMask = null
                dynamicDarkvisionMesh = null
                dynamicSightlineLayerVersion++
                dynamicSightlineLayerCaches.clear()
                dynamicLightTintLayerCache = null
                redraw()
            } else {
                val bundle = dynamicMapBundle ?: return@subscribe
                if (bundle.cols == mesh.cols && bundle.rows == mesh.rows) {
                    dynamicSightlineMesh = mesh
                    dynamicSeenSightlineMesh = event.seenMesh?.takeIf {
                        it.cols == bundle.cols && it.rows == bundle.rows
                    }
                    dynamicLightMask = event.lightMask?.takeIf {
                        it.cols == bundle.cols && it.rows == bundle.rows
                    }
                    dynamicDarkvisionMesh = event.darkvisionMesh?.takeIf {
                        it.cols == bundle.cols && it.rows == bundle.rows
                    }
                    dynamicSightlineLayerVersion++
                    dynamicSightlineLayerCaches.clear()
                    dynamicLightTintLayerCache = null
                    redraw()
                }
            }
        }
        subscriptions += EventBus.subscribe<ThemeChangedEvent> { event ->
            dynamicMapDebugWallColor = ColorHexCodec.parseOrDefault(event.theme.accentColor, Color.DODGERBLUE)
            redraw()
        }
        subscriptions += EventBus.subscribe<MapClearEvent> {
            clearImage()
        }
        subscriptions += EventBus.subscribe<MapBackgroundEvent> { event ->
            backgroundColor = event.color
            redraw()
        }
        subscriptions += EventBus.subscribe<MapCalibrationEvent> { event ->
            mapCalibration = event.calibration
            redraw()
        }
        subscriptions += EventBus.subscribe<GridCalibrationEvent> { event ->
            gridCalibration = event.calibration
            redraw()
        }
        subscriptions += EventBus.subscribe<GridUpdateEvent> { event ->
            gridConfig = event.config
            event.config?.color?.let { lastGridColor = it }
            redraw()
        }
        subscriptions += EventBus.subscribe<FogOfWarResetEvent> { event ->
            if (event.revealAll) fogOfWar?.revealAll() else fogOfWar?.hideAll()
            fogLayerVersion++
            fogLayerCache = null
            redraw()
        }
        subscriptions += EventBus.subscribe<FogOfWarCellEvent> { event ->
            if (event.revealed) fogOfWar?.revealCell(event.col, event.row)
            else fogOfWar?.hideCell(event.col, event.row)
            fogLayerVersion++
            fogLayerCache = null
            redraw()
        }
        subscriptions += EventBus.subscribe<FogOfWarSetupEvent> { event ->
            fogColOffset = event.colOffset
            fogRowOffset = event.rowOffset
            fogOfWar = FogOfWarState(event.cols, event.rows)
            fogLayerVersion++
            fogLayerCache = null
            redraw()
        }
        subscriptions += EventBus.subscribe<GridCalibrationModeEvent> { event ->
            gridCalibrationMode = event.active
            redraw()
        }
        subscriptions += EventBus.subscribe<MapCalibrationModeEvent> { event ->
            mapCalibrationMode = event.active
            redraw()
        }
        subscriptions += EventBus.subscribe<MapRotationEvent> { event ->
            mapRotationDegrees = ((event.degrees % 360) + 360) % 360
            redraw()
        }
        subscriptions += EventBus.subscribe<TableMapOffsetEvent> { event ->
            tableMapOffset = event.offset
            redraw()
        }
        subscriptions += EventBus.subscribe<TableViewportChangedEvent> { event ->
            tableViewportWidth = event.width
            tableViewportHeight = event.height
            redraw()
        }
        subscriptions += EventBus.subscribe<TokenAddedEvent> { event ->
            val existingIndex = tokens.indexOfFirst { it.id == event.id }
            if (existingIndex >= 0) {
                val existing = tokens[existingIndex]
                tokens[existingIndex] = existing.copy(
                    name = event.name,
                    color = event.color,
                    size = event.size,
                    isPlayerCharacter = event.isPlayerCharacter,
                    darkvisionRangeCells = event.darkvisionRangeCells,
                    lightSource = event.lightSource,
                )
            } else {
                val (nextTokenCol, nextTokenRow) = nextAvailableTokenPlacement(tokens, event.size)
                tokens.add(
                    Token(
                        event.id,
                        event.name,
                        nextTokenCol,
                        nextTokenRow,
                        event.size,
                        event.color,
                        isPlayerCharacter = event.isPlayerCharacter,
                        darkvisionRangeCells = event.darkvisionRangeCells,
                        lightSource = event.lightSource,
                    ),
                )
            }
            redraw()
        }
        subscriptions += EventBus.subscribe<TokenRemovedEvent> { event ->
            val removed = tokens.find { it.id == event.id }
            tokens.removeIf { it.id == event.id }
            // Evict the removed token's image from the cache if no other token uses it.
            val uri = removed?.imageUri
            if (uri != null && tokens.none { it.imageUri == uri }) {
                imageCache.remove(uri)
            }
            redraw()
        }
        subscriptions += EventBus.subscribe<TokenMovedEvent> { event ->
            val idx = tokens.indexOfFirst { it.id == event.id }
            if (idx >= 0) {
                val previous = tokens[idx]
                if (previous.col == event.col && previous.row == event.row) return@subscribe
                tokens[idx] = previous.copy(col = event.col, row = event.row)
                redraw()
            }
        }
        subscriptions += EventBus.subscribe<ActiveTokenChangedEvent> { event ->
            activeTokenId = event.id
            redraw()
        }
        subscriptions += EventBus.subscribe<TokensResetEvent> {
            tokens.clear()
            imageCache.clear()
            activeTokenId = null
            redraw()
        }
        subscriptions += EventBus.subscribe<TokenImageChangedEvent> { event ->
            val idx = tokens.indexOfFirst { it.id == event.id }
            if (idx >= 0) {
                val old = tokens[idx]
                val updated = old.copy(
                    imageUri = event.imageUri,
                    imageScaleX = event.imageScaleX,
                    imageScaleY = event.imageScaleY,
                    imageOffsetX = event.imageOffsetX,
                    imageOffsetY = event.imageOffsetY,
                )
                tokens[idx] = updated
                // Evict the old cached image if no other token still references it.
                val oldUri = old.imageUri
                if (oldUri != null && tokens.none { it.imageUri == oldUri }) {
                    imageCache.remove(oldUri)
                }
                // Pre-load the new image off-thread and cache it once fully loaded.
                val newUri = updated.imageUri
                if (newUri != null && isSupportedTokenImageUri(newUri) && !imageCache.containsKey(newUri)) {
                    try {
                        // Use backgroundLoading=true so image decoding does not block the
                        // synchronous EventBus publish thread.
                        val image = Image(newUri, /* backgroundLoading = */ true)
                        if (!image.isError) {
                            cacheTokenImageWhenReady(newUri, image, imageCache, ::redraw)
                        }
                    } catch (e: IllegalArgumentException) {
                        // Malformed URI or similar: treat as a load error and skip caching.
                    } catch (e: Exception) {
                        // Any other unexpected error during image construction: also skip caching.
                    }
                }
                redraw()
            }
        }
        subscriptions += EventBus.subscribe<ShowTokenNamesEvent> { event ->
            showTokenNames = event.show
            redraw()
        }
        subscriptions += EventBus.subscribe<ForcePcTokensVisibleEvent> { event ->
            forcePlayerCharacterTokensVisible = event.force
            redraw()
        }
        subscriptions += EventBus.subscribe<MeasurementAddedEvent> { event ->
            measurements[event.overlay.id] = event.overlay
            redraw()
        }
        subscriptions += EventBus.subscribe<MeasurementUpdatedEvent> { event ->
            measurements[event.overlay.id] = event.overlay
            redraw()
        }
        subscriptions += EventBus.subscribe<MeasurementRemovedEvent> { event ->
            measurements.remove(event.id)
            redraw()
        }
        subscriptions += EventBus.subscribe<MeasurementsClearedEvent> {
            measurements.clear()
            redraw()
        }
    }

    /**
     * Unregisters all [EventBus] subscriptions held by this renderer.
     *
     * Call this when the renderer's canvas is removed from the scene graph to
     * prevent the renderer from processing events and redrawing after it is no
     * longer visible, and to allow it to be garbage-collected.
     */
    fun dispose() {
        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()
    }

    /**
     * Loads the map image from [resourcePath] and triggers a full redraw.
     *
     * The image is loaded synchronously (background-load is `false`) so the
     * canvas is immediately ready after this call returns.
     *
     * @param resourcePath file-system path or classpath URI of the image file.
     * @return [MapResult.Success] when the image is loaded and drawn;
     *         [MapResult.Failure] with a descriptive error when loading fails.
     */
    fun loadImage(resourcePath: String): MapResult<Unit> {
        val image = Image(resourcePath, false)
        return if (image.isError) {
            MapResult.failure(
                MapOperationError.ImageLoadFailed(
                    resourcePath = resourcePath,
                    causeMessage = image.exception?.message ?: "unknown image loading error.",
                ),
            )
        } else {
            dynamicMapBundle = null
            dynamicMapBackgroundImage = null
            dynamicSightlineMesh = null
            dynamicSeenSightlineMesh = null
            dynamicLightMask = null
            dynamicDarkvisionMesh = null
            openDynamicDoorIds.clear()
            dynamicSightlineLayerVersion++
            clearLayerCaches()
            mapImage = image
            redraw()
            MapResult.success(Unit)
        }
    }

    fun loadDynamicMap(bundle: DynamicMapBundle): MapResult<Unit> {
        dynamicMapBundle = bundle
        dynamicMapBackgroundImage = bundle.createBackgroundImage()
        mapImage = null
        mapRotationDegrees = 0
        dynamicSightlineMesh = DynamicSightlineMesh.hidden(bundle.cols, bundle.rows)
        dynamicSeenSightlineMesh = DynamicSightlineMesh.hidden(bundle.cols, bundle.rows)
        dynamicLightMask = null
        dynamicDarkvisionMesh = null
        openDynamicDoorIds.clear()
        dynamicSightlineLayerVersion++
        clearLayerCaches()
        redraw()
        return MapResult.success(Unit)
    }

    /**
     * Removes the current map image and redraws the canvas using the plain
     * background colour and any remaining overlays.
     */
    fun clearImage() {
        mapImage = null
        dynamicMapBundle = null
        dynamicMapBackgroundImage = null
        dynamicSightlineMesh = null
        dynamicSeenSightlineMesh = null
        dynamicLightMask = null
        dynamicDarkvisionMesh = null
        openDynamicDoorIds.clear()
        dynamicSightlineLayerVersion++
        clearLayerCaches()
        redraw()
    }

    /**
     * Clears the canvas and redraws the current map state.
     *
     * Call this whenever something that affects the visible state changes
     * (new map loaded, fog-of-war updated, grid toggled, calibration changed, etc.).
     *
     * The [viewportScale]/[viewportOffsetX]/[viewportOffsetY] viewport transform is
     * applied around the canvas centre before all content layers are drawn, so each
     * renderer instance can have an independent pan/zoom view.
     */
    fun redraw() {
        // Background always fills the entire canvas regardless of viewport transform.
        gc.fill = backgroundColor
        gc.fillRect(0.0, 0.0, canvas.width, canvas.height)

        // Apply viewport transform: zoom from the canvas centre then pan.
        gc.save()
        val cx = canvas.width / 2.0
        val cy = canvas.height / 2.0
        gc.translate(cx + viewportOffsetX, cy + viewportOffsetY)
        gc.scale(viewportScale, viewportScale)
        gc.translate(-cx, -cy)
        if (applyTableMapOffset) {
            gc.translate(tableMapOffset.offsetX, tableMapOffset.offsetY)
        }

        if (dynamicMapBundle != null) {
            drawDynamicMapBaseLayer()
            drawDynamicDarkvisionLayer()
            drawDynamicRememberedBaseLayer()
            if (dynamicMapRenderMode == DynamicMapRenderMode.DEBUG) {
                drawDynamicMapSunlightAreas()
                drawDynamicMapLightHalos()
            }
        } else {
            drawMapImage()
        }
        drawGrid()
        if (dynamicMapRenderMode == DynamicMapRenderMode.DEBUG) {
            drawDynamicMapWalls()
        }
        drawDynamicDoorIcons()
        drawDynamicLightTintLayer()
        drawFogOfWar()
        drawTokens()
        drawDynamicSightlineLayer()
        drawForcedPlayerCharacterTokens()
        drawMeasurements()
        if (dynamicMapRenderMode == DynamicMapRenderMode.DEBUG) {
            drawDynamicMapLightMarkers()
        }

        gc.restore()
        drawTableViewportOutline()
    }

    // -------------------------------------------------------------------------
    // Private rendering helpers
    // -------------------------------------------------------------------------

    /**
     * Draws the map image centred on the canvas, applying [mapCalibration] and
     * [mapRotationDegrees].
     *
     * The canvas centre is the fixed point for both scaling and rotation: zooming
     * or rotating keeps the image centred on the same reference point, and
     * [MapCalibration.offsetX]/[MapCalibration.offsetY] displace the image centre
     * from the canvas centre in the rotated coordinate system.
     *
     * Rotation is applied around the canvas centre (the grid origin reference),
     * so the grid overlay remains stationary while the image rotates behind it.
     */
    private fun drawMapImage() {
        val image = mapImage ?: return
        val destWidth = image.width * mapCalibration.scale
        val destHeight = image.height * mapCalibration.scale

        // Image centre is placed at (canvas centre + calibration offset).
        val drawX = canvas.width / 2.0 - destWidth / 2.0 + mapCalibration.offsetX
        val drawY = canvas.height / 2.0 - destHeight / 2.0 + mapCalibration.offsetY

        if (mapRotationDegrees == 0) {
            gc.drawImage(image, drawX, drawY, destWidth, destHeight)
        } else {
            // Rotate around the canvas centre (= grid origin with default calibration).
            val cx = canvas.width / 2.0
            val cy = canvas.height / 2.0
            gc.save()
            gc.translate(cx, cy)
            gc.rotate(mapRotationDegrees.toDouble())
            gc.translate(-cx, -cy)
            gc.drawImage(image, drawX, drawY, destWidth, destHeight)
            gc.restore()
        }
    }

    private fun drawDynamicMapBaseLayer() {
        val bundle = dynamicMapBundle ?: return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val image = dynamicMapBaseLayerImage(bundle, cellPx)
        if (image != null) {
            gc.drawImage(image, dynamicMapOriginX(), dynamicMapOriginY())
            return
        }
        drawDynamicMapSurface()
        drawDynamicMapBackground()
    }

    private fun dynamicMapBaseLayerImage(bundle: DynamicMapBundle, cellPx: Double): Image? {
        val width = bundle.cols * cellPx
        val height = bundle.rows * cellPx
        if (!isCacheableLayerSize(width, height)) return null

        val key = dynamicMapBaseLayerKey(bundle, cellPx)
        dynamicMapBaseLayerCache?.takeIf { it.key == key }?.let { return it.image }

        val layerCanvas = Canvas(ceil(width), ceil(height))
        val layerGc = layerCanvas.graphicsContext2D
        drawDynamicMapSurface(layerGc, bundle, cellPx, originX = 0.0, originY = 0.0)
        drawDynamicMapBackground(layerGc, bundle, dynamicMapBackgroundImage, cellPx, originX = 0.0, originY = 0.0)
        val image = layerCanvas.snapshot(transparentSnapshotParameters(), null)
        dynamicMapBaseLayerCache = CachedLayer(key, image)
        return image
    }

    private fun dynamicMapGrayscaleLayerImage(bundle: DynamicMapBundle, cellPx: Double): Image? {
        val width = bundle.cols * cellPx
        val height = bundle.rows * cellPx
        if (!isCacheableLayerSize(width, height)) return null

        val key = dynamicMapBaseLayerKey(bundle, cellPx)
        dynamicMapGrayscaleLayerCache?.takeIf { it.key == key }?.let { return it.image }

        val layerCanvas = Canvas(ceil(width), ceil(height))
        val layerGc = layerCanvas.graphicsContext2D
        layerGc.setEffect(GRAYSCALE_EFFECT)
        drawDynamicMapSurface(layerGc, bundle, cellPx, originX = 0.0, originY = 0.0)
        drawDynamicMapBackground(layerGc, bundle, dynamicMapBackgroundImage, cellPx, originX = 0.0, originY = 0.0)
        layerGc.setEffect(null)
        val image = layerCanvas.snapshot(transparentSnapshotParameters(), null)
        dynamicMapGrayscaleLayerCache = CachedLayer(key, image)
        return image
    }

    private fun dynamicMapBaseLayerKey(bundle: DynamicMapBundle, cellPx: Double): DynamicMapBaseLayerKey {
        val backgroundImage = dynamicMapBackgroundImage
        return DynamicMapBaseLayerKey(
            sourcePath = bundle.sourcePath,
            cols = bundle.cols,
            rows = bundle.rows,
            backgroundImageId = backgroundImage?.let(System::identityHashCode) ?: 0,
            backgroundImageWidth = backgroundImage?.width ?: 0.0,
            backgroundImageHeight = backgroundImage?.height ?: 0.0,
            backgroundScale = bundle.backgroundCalibration.scale,
            backgroundOffsetX = bundle.backgroundCalibration.offsetX,
            backgroundOffsetY = bundle.backgroundCalibration.offsetY,
            cellPx = cellPx,
            backgroundColor = backgroundColor,
        )
    }

    private fun drawDynamicDarkvisionLayer() {
        val bundle = dynamicMapBundle ?: return
        val mesh = dynamicDarkvisionMesh ?: return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return

        drawDynamicGrayscaleBaseLayer(
            bundle = bundle,
            mesh = mesh,
            cellPx = cellPx,
            originX = dynamicMapOriginX(),
            originY = dynamicMapOriginY(),
        )
    }

    private fun drawDynamicRememberedBaseLayer() {
        if (!usePersistentVision) return
        val bundle = dynamicMapBundle ?: return
        val currentMesh = dynamicSightlineMesh ?: return
        val seenMesh = dynamicSeenSightlineMesh ?: return
        val rememberedMesh = rememberedSightlineMesh(currentMesh, seenMesh) ?: return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return

        drawDynamicGrayscaleBaseLayer(
            bundle = bundle,
            mesh = rememberedMesh,
            cellPx = cellPx,
            originX = dynamicMapOriginX(),
            originY = dynamicMapOriginY(),
        )
    }

    private fun drawDynamicGrayscaleBaseLayer(
        bundle: DynamicMapBundle,
        mesh: DynamicSightlineMesh,
        cellPx: Double,
        originX: Double,
        originY: Double,
    ) {
        if (!mesh.hasVisibleArea()) return
        gc.save()
        gc.beginPath()
        mesh.drawVisibleArea(
            moveTo = { point -> gc.moveTo(originX + point.x * cellPx, originY + point.y * cellPx) },
            lineTo = { point -> gc.lineTo(originX + point.x * cellPx, originY + point.y * cellPx) },
            closePath = { gc.closePath() },
        )
        gc.clip()

        val grayscaleImage = dynamicMapGrayscaleLayerImage(bundle, cellPx)
        if (grayscaleImage != null) {
            gc.drawImage(grayscaleImage, originX, originY)
        } else {
            gc.setEffect(GRAYSCALE_EFFECT)
            drawDynamicMapSurface(gc, bundle, cellPx, originX, originY)
            drawDynamicMapBackground(gc, bundle, dynamicMapBackgroundImage, cellPx, originX, originY)
            gc.setEffect(null)
        }
        gc.restore()
    }

    private fun drawDynamicMapSurface() {
        val bundle = dynamicMapBundle ?: return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        drawDynamicMapSurface(gc, bundle, cellPx, dynamicMapOriginX(), dynamicMapOriginY())
    }

    private fun drawDynamicMapSurface(
        target: GraphicsContext,
        bundle: DynamicMapBundle,
        cellPx: Double,
        originX: Double,
        originY: Double,
    ) {
        target.fill = backgroundColor.deriveColor(0.0, 1.0, 0.85, 1.0)
        target.fillRect(originX, originY, bundle.cols * cellPx, bundle.rows * cellPx)
    }

    private fun drawDynamicMapBackground() {
        val bundle = dynamicMapBundle ?: return
        val image = dynamicMapBackgroundImage ?: return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        drawDynamicMapBackground(gc, bundle, image, cellPx, dynamicMapOriginX(), dynamicMapOriginY())
    }

    private fun drawDynamicMapBackground(
        target: GraphicsContext,
        bundle: DynamicMapBundle,
        image: Image?,
        cellPx: Double,
        originX: Double,
        originY: Double,
    ) {
        image ?: return
        val calibration = bundle.backgroundCalibration
        val destWidth = image.width * calibration.scale * cellPx
        val destHeight = image.height * calibration.scale * cellPx
        if (!destWidth.isFinite() || !destHeight.isFinite() || destWidth <= 0.0 || destHeight <= 0.0) return

        val mapCenterX = originX + bundle.cols * cellPx / 2.0
        val mapCenterY = originY + bundle.rows * cellPx / 2.0
        target.drawImage(
            image,
            mapCenterX - destWidth / 2.0 + calibration.offsetX * cellPx,
            mapCenterY - destHeight / 2.0 + calibration.offsetY * cellPx,
            destWidth,
            destHeight,
        )
    }

    private fun drawDynamicMapLightHalos() {
        val bundle = dynamicMapBundle ?: return
        if (bundle.lights.isEmpty()) return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val originX = dynamicMapOriginX()
        val originY = dynamicMapOriginY()

        bundle.lights.filter { it.enabled }.forEach { light ->
            val color = ColorHexCodec.parseOrDefault(light.colorHex, Color.WHITE)
            val centerX = originX + light.position.x * cellPx
            val centerY = originY + light.position.y * cellPx
            val dimRadius = light.dimRadius * cellPx
            val brightRadius = light.brightRadius * cellPx

            if (dimRadius > 0.0) {
                gc.fill = color.deriveColor(0.0, 1.0, 1.0, DYNAMIC_LIGHT_DIM_OPACITY)
                gc.fillOval(centerX - dimRadius, centerY - dimRadius, dimRadius * 2.0, dimRadius * 2.0)
                gc.stroke = color.deriveColor(0.0, 1.0, 1.0, DYNAMIC_LIGHT_RING_OPACITY)
                gc.lineWidth = dynamicLightRingWidth(cellPx)
                gc.strokeOval(centerX - dimRadius, centerY - dimRadius, dimRadius * 2.0, dimRadius * 2.0)
            }
            if (brightRadius > 0.0) {
                gc.fill = color.deriveColor(0.0, 1.0, 1.0, DYNAMIC_LIGHT_BRIGHT_OPACITY)
                gc.fillOval(centerX - brightRadius, centerY - brightRadius, brightRadius * 2.0, brightRadius * 2.0)
                gc.stroke = color.deriveColor(0.0, 1.0, 1.0, DYNAMIC_LIGHT_RING_OPACITY)
                gc.lineWidth = dynamicLightRingWidth(cellPx)
                gc.strokeOval(centerX - brightRadius, centerY - brightRadius, brightRadius * 2.0, brightRadius * 2.0)
            }
        }
    }

    private fun drawDynamicMapSunlightAreas() {
        val bundle = dynamicMapBundle ?: return
        if (bundle.sunlightAreas.isEmpty()) return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val originX = dynamicMapOriginX()
        val originY = dynamicMapOriginY()
        val baseColor = dynamicWallColor().deriveColor(35.0, 0.6, 1.2, 1.0)

        bundle.sunlightAreas.forEach { area ->
            if (area.points.size < 3) return@forEach
            val xs = DoubleArray(area.points.size) { index -> originX + area.points[index].x * cellPx }
            val ys = DoubleArray(area.points.size) { index -> originY + area.points[index].y * cellPx }

            gc.fill = baseColor.deriveColor(0.0, 1.0, 1.0, DYNAMIC_SUNLIGHT_AREA_FILL_OPACITY)
            gc.fillPolygon(xs, ys, area.points.size)
            gc.stroke = baseColor.deriveColor(0.0, 1.0, 1.0, DYNAMIC_SUNLIGHT_AREA_STROKE_OPACITY)
            gc.lineWidth = dynamicLightRingWidth(cellPx)
            gc.strokePolygon(xs, ys, area.points.size)
        }
    }

    private fun drawDynamicLightTintLayer() {
        val bundle = dynamicMapBundle ?: return
        val mesh = dynamicSightlineMesh ?: return
        val lightMask = dynamicLightMask ?: return
        if (!lightMask.lightingActive || lightMask.tintContributions.isEmpty() || !mesh.hasVisibleArea()) return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val originX = dynamicMapOriginX()
        val originY = dynamicMapOriginY()

        val image = dynamicLightTintLayerImage(bundle, mesh, lightMask, cellPx)
        if (image != null) {
            gc.save()
            gc.globalBlendMode = BlendMode.SCREEN
            gc.drawImage(image, originX, originY)
            gc.restore()
            return
        }

        drawDynamicLightTintLayer(
            target = gc,
            mesh = mesh,
            lightMask = lightMask,
            cellPx = cellPx,
            originX = originX,
            originY = originY,
            screenBlend = true,
        )
    }

    private fun dynamicLightTintLayerImage(
        bundle: DynamicMapBundle,
        mesh: DynamicSightlineMesh,
        lightMask: DynamicLightMask,
        cellPx: Double,
    ): Image? {
        val width = bundle.cols * cellPx
        val height = bundle.rows * cellPx
        if (!isCacheableLayerSize(width, height)) return null

        val key = DynamicLightTintLayerKey(
            version = dynamicSightlineLayerVersion,
            cols = bundle.cols,
            rows = bundle.rows,
            cellPx = cellPx,
            lightMaskId = System.identityHashCode(lightMask),
        )
        dynamicLightTintLayerCache?.takeIf { it.key == key }?.let { return it.image }

        val layerCanvas = Canvas(ceil(width), ceil(height))
        val layerGc = layerCanvas.graphicsContext2D
        drawDynamicLightTintLayer(
            target = layerGc,
            mesh = mesh,
            lightMask = lightMask,
            cellPx = cellPx,
            originX = 0.0,
            originY = 0.0,
            screenBlend = false,
        )
        val image = layerCanvas.snapshot(transparentSnapshotParameters(), null)
        dynamicLightTintLayerCache = CachedLayer(key, image)
        return image
    }

    private fun drawDynamicLightTintLayer(
        target: GraphicsContext,
        mesh: DynamicSightlineMesh,
        lightMask: DynamicLightMask,
        cellPx: Double,
        originX: Double,
        originY: Double,
        screenBlend: Boolean,
    ) {
        target.save()
        if (screenBlend) {
            target.globalBlendMode = BlendMode.SCREEN
        }
        target.beginPath()
        mesh.drawVisibleArea(
            moveTo = { point -> target.moveTo(originX + point.x * cellPx, originY + point.y * cellPx) },
            lineTo = { point -> target.lineTo(originX + point.x * cellPx, originY + point.y * cellPx) },
            closePath = { target.closePath() },
        )
        target.clip()

        lightMask.tintContributions.forEach { contribution ->
            drawDynamicLightTintContribution(target, contribution, cellPx, originX, originY)
        }
        target.restore()
    }

    private fun drawDynamicLightTintContribution(
        target: GraphicsContext,
        contribution: DynamicLightTintContribution,
        cellPx: Double,
        originX: Double,
        originY: Double,
    ) {
        val color = ColorHexCodec.parseOrDefault(contribution.colorHex, Color.WHITE)
        if (contribution.hasDimTint) {
            target.fill = color.withOpacity(DYNAMIC_LIGHT_TINT_DIM_OPACITY)
            target.beginPath()
            contribution.drawDimArea(
                moveTo = { point -> target.moveTo(originX + point.x * cellPx, originY + point.y * cellPx) },
                lineTo = { point -> target.lineTo(originX + point.x * cellPx, originY + point.y * cellPx) },
                closePath = { target.closePath() },
            )
            target.fill()
        }

        if (contribution.hasBrightTint) {
            target.fill = color.withOpacity(DYNAMIC_LIGHT_TINT_BRIGHT_OPACITY)
            target.beginPath()
            contribution.drawBrightArea(
                moveTo = { point -> target.moveTo(originX + point.x * cellPx, originY + point.y * cellPx) },
                lineTo = { point -> target.lineTo(originX + point.x * cellPx, originY + point.y * cellPx) },
                closePath = { target.closePath() },
            )
            target.fill()
        }
    }

    private fun drawDynamicMapWalls() {
        val bundle = dynamicMapBundle ?: return
        if (bundle.walls.isEmpty()) return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val originX = dynamicMapOriginX()
        val originY = dynamicMapOriginY()

        bundle.walls.forEach { wall ->
            if (wall.isDoor() && wall.id in openDynamicDoorIds) return@forEach
            if (wall.kind == DynamicMapRuntimeWallKind.FEATURE) {
                drawDynamicFeatureWallSides(wall, cellPx, originX, originY)
                drawDynamicFeatureWallArrow(wall, cellPx, originX, originY)
                return@forEach
            }
            configureDynamicWallStroke(wall.kind, cellPx)
            gc.strokeLine(
                originX + wall.start.x * cellPx,
                originY + wall.start.y * cellPx,
                originX + wall.end.x * cellPx,
                originY + wall.end.y * cellPx,
            )
            gc.setLineDashes()
        }
    }

    private fun drawDynamicDoorIcons() {
        val bundle = dynamicMapBundle ?: return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val originX = dynamicMapOriginX()
        val originY = dynamicMapOriginY()

        bundle.walls
            .filter { it.isDoor() }
            .filter { it.doorVisible || showHiddenDynamicDoorIcons }
            .forEach { wall ->
                drawDynamicDoorIcon(
                    wall = wall,
                    cellPx = cellPx,
                    originX = originX,
                    originY = originY,
                    isOpen = wall.id in openDynamicDoorIds,
                )
            }
    }

    private fun drawDynamicMapLightMarkers() {
        if (!showDynamicLightMarkers) return
        val bundle = dynamicMapBundle ?: return
        if (bundle.lights.isEmpty()) return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val originX = dynamicMapOriginX()
        val originY = dynamicMapOriginY()
        val radius = (cellPx * DYNAMIC_LIGHT_MARKER_SCALE).coerceAtLeast(4.0)

        bundle.lights.forEach { light ->
            val color = ColorHexCodec.parseOrDefault(light.colorHex, Color.WHITE)
            val centerX = originX + light.position.x * cellPx
            val centerY = originY + light.position.y * cellPx
            gc.fill = if (light.enabled) color else color.deriveColor(0.0, 0.2, 1.0, 0.45)
            gc.fillOval(centerX - radius, centerY - radius, radius * 2.0, radius * 2.0)
            gc.stroke = dynamicWallColor()
            gc.lineWidth = 1.0
            if (!light.enabled) {
                gc.setLineDashes(4.0, 4.0)
            }
            gc.strokeOval(centerX - radius, centerY - radius, radius * 2.0, radius * 2.0)
            gc.setLineDashes()
        }
    }

    private fun dynamicMapOriginX(): Double = canvas.width / 2.0 + gridCalibration.offsetX

    private fun dynamicMapOriginY(): Double = canvas.height / 2.0 + gridCalibration.offsetY

    private fun dynamicWallColor(): Color =
        dynamicMapDebugWallColor

    private fun configureDynamicWallStroke(kind: DynamicMapRuntimeWallKind, cellPx: Double) {
        val baseWidth = (cellPx * DYNAMIC_WALL_WIDTH_SCALE).coerceAtLeast(2.0)
        gc.stroke = dynamicWallColor(kind)
        gc.lineWidth = when (kind) {
            DynamicMapRuntimeWallKind.SOFT -> baseWidth
            DynamicMapRuntimeWallKind.HARD -> baseWidth * 1.15
            DynamicMapRuntimeWallKind.DOOR -> baseWidth * 1.15
            DynamicMapRuntimeWallKind.FEATURE -> baseWidth * 1.05
        }
        if (kind == DynamicMapRuntimeWallKind.SOFT || kind == DynamicMapRuntimeWallKind.FEATURE) {
            gc.setLineDashes(
                (cellPx * 0.18).coerceIn(5.0, 14.0),
                (cellPx * 0.12).coerceIn(4.0, 10.0),
            )
        } else {
            gc.setLineDashes()
        }
    }

    private fun dynamicWallColor(kind: DynamicMapRuntimeWallKind): Color =
        when (kind) {
            DynamicMapRuntimeWallKind.SOFT -> dynamicMapDebugWallColor.deriveColor(0.0, 0.55, 1.2, 0.72)
            DynamicMapRuntimeWallKind.HARD -> dynamicMapDebugWallColor.deriveColor(0.0, 1.0, 0.95, 0.98)
            DynamicMapRuntimeWallKind.DOOR -> dynamicMapDebugWallColor.deriveColor(38.0, 0.95, 1.05, 0.98)
            DynamicMapRuntimeWallKind.FEATURE -> dynamicMapDebugWallColor.deriveColor(145.0, 0.9, 1.05, 0.95)
        }

    private fun drawDynamicFeatureWallSides(
        wall: DynamicMapRuntimeWall,
        cellPx: Double,
        originX: Double,
        originY: Double,
    ) {
        val startX = originX + wall.start.x * cellPx
        val startY = originY + wall.start.y * cellPx
        val endX = originX + wall.end.x * cellPx
        val endY = originY + wall.end.y * cellPx
        val dx = endX - startX
        val dy = endY - startY
        val length = hypot(dx, dy)
        if (length <= 0.0) return

        val normalX = -dy / length
        val normalY = dx / length
        val offset = dynamicFeatureWallSideOffset(cellPx)
        drawDynamicFeatureWallSideStroke(
            cellPx = cellPx,
            startX = startX + normalX * offset,
            startY = startY + normalY * offset,
            endX = endX + normalX * offset,
            endY = endY + normalY * offset,
            behavior = wall.frontBehavior,
        )
        drawDynamicFeatureWallSideStroke(
            cellPx = cellPx,
            startX = startX - normalX * offset,
            startY = startY - normalY * offset,
            endX = endX - normalX * offset,
            endY = endY - normalY * offset,
            behavior = wall.backBehavior,
        )
    }

    private fun drawDynamicFeatureWallSideStroke(
        cellPx: Double,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        behavior: DynamicMapRuntimeWallSideBehavior,
    ) {
        gc.save()
        gc.stroke = dynamicFeatureWallSideColor(behavior)
        gc.lineWidth = dynamicFeatureWallSideLineWidth(cellPx, behavior)
        when (behavior) {
            DynamicMapRuntimeWallSideBehavior.OPEN -> {
                gc.lineCap = StrokeLineCap.ROUND
                gc.setLineDashes(
                    (cellPx * 0.035).coerceIn(1.0, 2.5),
                    (cellPx * 0.12).coerceIn(4.0, 9.0),
                )
            }
            DynamicMapRuntimeWallSideBehavior.SOFT -> {
                gc.lineCap = StrokeLineCap.BUTT
                gc.setLineDashes(
                    (cellPx * 0.18).coerceIn(5.0, 14.0),
                    (cellPx * 0.12).coerceIn(4.0, 10.0),
                )
            }
            DynamicMapRuntimeWallSideBehavior.HARD -> {
                gc.lineCap = StrokeLineCap.BUTT
                gc.setLineDashes()
            }
        }
        gc.strokeLine(startX, startY, endX, endY)
        gc.restore()
    }

    private fun dynamicFeatureWallSideOffset(cellPx: Double): Double =
        (cellPx * 0.085).coerceIn(3.0, 7.0)

    private fun dynamicFeatureWallSideLineWidth(
        cellPx: Double,
        behavior: DynamicMapRuntimeWallSideBehavior,
    ): Double {
        val baseWidth = (cellPx * 0.085).coerceAtLeast(1.8)
        return when (behavior) {
            DynamicMapRuntimeWallSideBehavior.OPEN -> baseWidth * 0.8
            DynamicMapRuntimeWallSideBehavior.SOFT -> baseWidth
            DynamicMapRuntimeWallSideBehavior.HARD -> baseWidth * 1.15
        }
    }

    private fun dynamicFeatureWallSideColor(behavior: DynamicMapRuntimeWallSideBehavior): Color =
        when (behavior) {
            DynamicMapRuntimeWallSideBehavior.OPEN -> dynamicMapDebugWallColor.deriveColor(145.0, 0.32, 1.3, 0.62)
            DynamicMapRuntimeWallSideBehavior.SOFT -> dynamicMapDebugWallColor.deriveColor(145.0, 0.72, 1.18, 0.82)
            DynamicMapRuntimeWallSideBehavior.HARD -> dynamicMapDebugWallColor.deriveColor(145.0, 1.0, 0.95, 0.98)
        }

    private fun drawDynamicFeatureWallArrow(
        wall: DynamicMapRuntimeWall,
        cellPx: Double,
        originX: Double,
        originY: Double,
    ) {
        val startX = originX + wall.start.x * cellPx
        val startY = originY + wall.start.y * cellPx
        val endX = originX + wall.end.x * cellPx
        val endY = originY + wall.end.y * cellPx
        val dx = endX - startX
        val dy = endY - startY
        val length = hypot(dx, dy)
        if (length <= 0.0) return

        val centerX = (startX + endX) / 2.0
        val centerY = (startY + endY) / 2.0
        val normalX = -dy / length
        val normalY = dx / length
        val arrowLength = (cellPx * 0.28).coerceIn(7.0, 18.0)
        val headLength = (cellPx * 0.12).coerceIn(4.0, 8.0)
        val headWidth = (cellPx * 0.09).coerceIn(3.0, 7.0)
        val shaftStartX = centerX + normalX * (arrowLength * 0.15)
        val shaftStartY = centerY + normalY * (arrowLength * 0.15)
        val tipX = centerX + normalX * arrowLength
        val tipY = centerY + normalY * arrowLength
        val sideX = dx / length
        val sideY = dy / length
        val opacity = if (
            wall.frontBehavior == DynamicMapRuntimeWallSideBehavior.OPEN ||
            wall.backBehavior == DynamicMapRuntimeWallSideBehavior.OPEN
        ) {
            0.92
        } else {
            0.78
        }
        val arrowColor = dynamicMapDebugWallColor.deriveColor(145.0, 0.95, 1.15, opacity)

        gc.save()
        gc.stroke = arrowColor
        gc.fill = arrowColor
        gc.lineWidth = (cellPx * 0.04).coerceIn(1.5, 3.5)
        gc.strokeLine(shaftStartX, shaftStartY, tipX, tipY)
        gc.fillPolygon(
            doubleArrayOf(
                tipX,
                tipX - normalX * headLength + sideX * headWidth,
                tipX - normalX * headLength - sideX * headWidth,
            ),
            doubleArrayOf(
                tipY,
                tipY - normalY * headLength + sideY * headWidth,
                tipY - normalY * headLength - sideY * headWidth,
            ),
            3,
        )
        gc.restore()
    }

    private fun drawDynamicDoorIcon(
        wall: DynamicMapRuntimeWall,
        cellPx: Double,
        originX: Double,
        originY: Double,
        isOpen: Boolean,
    ) {
        val startX = originX + wall.start.x * cellPx
        val startY = originY + wall.start.y * cellPx
        val endX = originX + wall.end.x * cellPx
        val endY = originY + wall.end.y * cellPx
        val dx = endX - startX
        val dy = endY - startY
        val length = hypot(dx, dy)
        if (length <= 0.0) return

        val centerX = (startX + endX) / 2.0
        val centerY = (startY + endY) / 2.0
        val wallAngle = atan2(dy, dx)
        val doorAngle = if (isOpen) wallAngle - Math.toRadians(60.0) else wallAngle
        val alongX = cos(doorAngle)
        val alongY = sin(doorAngle)
        val normalX = -sin(wallAngle)
        val normalY = cos(wallAngle)
        val halfLong = (cellPx * 0.3).coerceIn(7.0, 20.0)
        val halfShort = (cellPx * 0.16).coerceIn(4.0, 12.0)
        val iconColor = dynamicDoorIconColor(wall, isOpen)

        gc.save()
        gc.stroke = iconColor
        gc.fill = iconColor.deriveColor(0.0, 1.0, 1.0, if (wall.doorVisible) 0.24 else 0.1)
        gc.lineWidth = (cellPx * 0.045).coerceIn(2.0, 4.5)
        if (!wall.doorVisible) {
            gc.setLineDashes(4.0, 4.0)
        }

        val xs = doubleArrayOf(
            centerX + alongX * halfLong,
            centerX + normalX * halfShort,
            centerX - alongX * halfLong,
            centerX - normalX * halfShort,
        )
        val ys = doubleArrayOf(
            centerY + alongY * halfLong,
            centerY + normalY * halfShort,
            centerY - alongY * halfLong,
            centerY - normalY * halfShort,
        )
        gc.fillPolygon(xs, ys, xs.size)
        gc.strokePolygon(xs, ys, xs.size)
        gc.setLineDashes()

        if (isOpen) {
            val hingeRadius = (cellPx * 0.055).coerceIn(2.5, 5.5)
            gc.fill = iconColor
            gc.fillOval(centerX - hingeRadius, centerY - hingeRadius, hingeRadius * 2.0, hingeRadius * 2.0)
        } else if (!wall.doorVisible) {
            gc.stroke = iconColor
            gc.lineWidth = (cellPx * 0.04).coerceIn(1.5, 3.0)
            gc.strokeLine(xs[0], ys[0], xs[2], ys[2])
        }
        gc.restore()
    }

    private fun dynamicDoorIconColor(wall: DynamicMapRuntimeWall, isOpen: Boolean): Color =
        when {
            isOpen -> dynamicMapDebugWallColor.deriveColor(105.0, 0.85, 1.15, 0.92)
            wall.doorVisible -> dynamicMapDebugWallColor.deriveColor(38.0, 0.95, 1.1, 0.95)
            else -> dynamicMapDebugWallColor.deriveColor(38.0, 0.35, 1.25, 0.72)
        }

    private fun dynamicLightRingWidth(cellPx: Double): Double =
        (cellPx * DYNAMIC_LIGHT_RING_WIDTH_SCALE).coerceIn(1.0, 4.0)

    /**
     * Returns the world-space rectangle visible on the canvas after the viewport
     * transform is applied, as `[xMin, xMax, yMin, yMax]`.
     *
     * The viewport transform maps world coordinates to canvas coordinates via:
     * ```
     * canvasX = cx + viewportOffsetX + viewportScale * (worldX − cx)
     * ```
     * Inverting this gives the world-space position of each canvas edge.
     *
     * When the viewport is the identity transform (scale=1, offsets=0) the result
     * is exactly `[0, canvasWidth, 0, canvasHeight]`, preserving the original
     * table-view behaviour.
     */
    private fun visibleWorldBounds(): DoubleArray {
        val w = canvas.width
        val h = canvas.height
        val cx = w / 2.0
        val cy = h / 2.0
        val sceneOffsetX = if (applyTableMapOffset) tableMapOffset.offsetX else 0.0
        val sceneOffsetY = if (applyTableMapOffset) tableMapOffset.offsetY else 0.0
        val xMin = (0.0 - cx - viewportOffsetX) / viewportScale + cx - sceneOffsetX
        val xMax = (w   - cx - viewportOffsetX) / viewportScale + cx - sceneOffsetX
        val yMin = (0.0 - cy - viewportOffsetY) / viewportScale + cy - sceneOffsetY
        val yMax = (h   - cy - viewportOffsetY) / viewportScale + cy - sceneOffsetY
        return doubleArrayOf(xMin, xMax, yMin, yMax)
    }

    /**
     * Draws the grid overlay using [gridCalibration] with the canvas centre as origin.
     *
     * Lines are drawn across the full world-space visible extent (computed from the
     * current [viewportScale]/[viewportOffsetX]/[viewportOffsetY]) so that the grid
     * fills the entire canvas regardless of how far the DM has panned or zoomed the
     * minimap.  For the table-view renderer (identity viewport) the visible extent
     * equals the canvas bounds, so behaviour is unchanged.
     *
     * The grid works independently of whether a map image is loaded.
     */
    private fun drawGrid() {
        val cfg = gridConfig?.takeIf { it.visible } ?: return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0) return

        gc.stroke = cfg.color
        gc.lineWidth = cfg.lineWidth

        val w = canvas.width
        val h = canvas.height

        // The grid origin (a line intersection) is at canvas centre + calibration offset.
        val originX = w / 2.0 + gridCalibration.offsetX
        val originY = h / 2.0 + gridCalibration.offsetY

        val bounds = visibleWorldBounds()
        val xMin = bounds[0]; val xMax = bounds[1]; val yMin = bounds[2]; val yMax = bounds[3]

        // Vertical lines: first line at or to the right of xMin via ceil.
        var x = originX + Math.ceil((xMin - originX) / cellPx) * cellPx
        while (x <= xMax) {
            gc.strokeLine(x, yMin, x, yMax)
            x += cellPx
        }

        // Horizontal lines: first line at or above yMin via ceil.
        var y = originY + Math.ceil((yMin - originY) / cellPx) * cellPx
        while (y <= yMax) {
            gc.strokeLine(xMin, y, xMax, y)
            y += cellPx
        }
    }

    /**
     * Covers unrevealed fog-of-war cells with a semi-transparent overlay.
     *
     * Cell positions are determined by [gridCalibration] so that fog cells always
     * align with the grid, whether the grid is visible or not.  Only cells that
     * overlap the world-space visible area are drawn for performance.
     *
     * Opacity is controlled by [fogOpacity]: `1.0` for a fully opaque player-facing
     * table view; lower values (e.g. `0.5`) for the DM minimap.
     */
    private fun drawFogOfWar() {
        val fow = fogOfWar ?: return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return

        val fogImage = fogLayerImage(fow, cellPx)
        if (fogImage != null) {
            val originX = canvas.width / 2.0 + gridCalibration.offsetX + fogColOffset * cellPx
            val originY = canvas.height / 2.0 + gridCalibration.offsetY + fogRowOffset * cellPx
            gc.drawImage(fogImage, originX, originY)
            return
        }

        val opacity = fogOpacity.coerceIn(0.0, 1.0)
        if (opacity <= 0.0) return
        gc.fill = Color.color(0.0, 0.0, 0.0, opacity)

        // Fog cell (0, 0) is displaced from the grid origin by (fogColOffset, fogRowOffset) cells.
        val originX = canvas.width / 2.0 + gridCalibration.offsetX
        val originY = canvas.height / 2.0 + gridCalibration.offsetY

        // Cull cells outside the world-space visible area for performance.
        val bounds = visibleWorldBounds()
        val visXMin = bounds[0]; val visXMax = bounds[1]
        val visYMin = bounds[2]; val visYMax = bounds[3]

        for (col in 0 until fow.cols) {
            val cellX = originX + (col + fogColOffset) * cellPx
            if (cellX + cellPx <= visXMin || cellX >= visXMax) continue
            for (row in 0 until fow.rows) {
                if (fow.isRevealed(col, row)) continue
                val cellY = originY + (row + fogRowOffset) * cellPx
                if (cellY + cellPx <= visYMin || cellY >= visYMax) continue
                gc.fillRect(cellX, cellY, cellPx, cellPx)
            }
        }
    }

    private fun fogLayerImage(fow: FogOfWarState, cellPx: Double): Image? {
        val opacity = fogOpacity.coerceIn(0.0, 1.0)
        if (opacity <= 0.0) return null

        val width = fow.cols * cellPx
        val height = fow.rows * cellPx
        if (!isCacheableLayerSize(width, height)) return null

        val key = FogLayerKey(
            version = fogLayerVersion,
            cols = fow.cols,
            rows = fow.rows,
            cellPx = cellPx,
            opacity = opacity,
        )
        fogLayerCache?.takeIf { it.key == key }?.let { return it.image }

        val layerCanvas = Canvas(ceil(width), ceil(height))
        val layerGc = layerCanvas.graphicsContext2D
        layerGc.fill = Color.color(0.0, 0.0, 0.0, opacity)
        for (col in 0 until fow.cols) {
            for (row in 0 until fow.rows) {
                if (!fow.isRevealed(col, row)) {
                    layerGc.fillRect(col * cellPx, row * cellPx, cellPx, cellPx)
                }
            }
        }
        val image = layerCanvas.snapshot(transparentSnapshotParameters(), null)
        fogLayerCache = CachedLayer(key, image)
        return image
    }

    /**
     * Covers DynamicMap areas that are outside every PC token's current sightline.
     *
     * The expensive raycast mesh and hidden geometry are computed only when relevant
     * token or bundle state changes. Redraws paint only the cached hidden area.
     */
    private fun drawDynamicSightlineLayer() {
        val mesh = dynamicSightlineMesh ?: return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val originX = dynamicMapOriginX()
        val originY = dynamicMapOriginY()
        if (usePersistentVision) {
            drawPersistentVisionSightlineLayer(mesh, cellPx, originX, originY)
            return
        }

        val image = dynamicSightlineLayerImage(
            mesh = mesh,
            cellPx = cellPx,
            overlay = dynamicSightlineOverlayColor(),
            role = DynamicSightlineLayerRole.CURRENT,
        )
        if (image != null) {
            gc.drawImage(image, originX, originY)
            return
        }

        gc.fill = dynamicSightlineOverlayColor()
        gc.beginPath()
        mesh.drawHiddenArea(
            moveTo = { point -> gc.moveTo(originX + point.x * cellPx, originY + point.y * cellPx) },
            lineTo = { point -> gc.lineTo(originX + point.x * cellPx, originY + point.y * cellPx) },
            closePath = { gc.closePath() },
        )
        gc.fill()
    }

    private fun drawPersistentVisionSightlineLayer(
        currentMesh: DynamicSightlineMesh,
        cellPx: Double,
        originX: Double,
        originY: Double,
    ) {
        val seenMesh = dynamicSeenSightlineMesh
        if (seenMesh == null) {
            drawDynamicSightlineMask(
                mesh = currentMesh,
                cellPx = cellPx,
                originX = originX,
                originY = originY,
                overlay = dynamicSightlineOverlayColor(),
                role = DynamicSightlineLayerRole.CURRENT,
            )
            return
        }

        val rememberedOverlay = rememberedSightlineOverlayColor()
        drawDynamicSightlineMask(
            mesh = currentMesh,
            cellPx = cellPx,
            originX = originX,
            originY = originY,
            overlay = rememberedOverlay,
            role = DynamicSightlineLayerRole.REMEMBERED,
        )

        drawDynamicSightlineMask(
            mesh = seenMesh,
            cellPx = cellPx,
            originX = originX,
            originY = originY,
            overlay = dynamicSightlineOverlayColor(),
            role = DynamicSightlineLayerRole.NEVER_SEEN,
        )
    }

    private fun drawDynamicSightlineMask(
        mesh: DynamicSightlineMesh,
        cellPx: Double,
        originX: Double,
        originY: Double,
        overlay: Color,
        role: DynamicSightlineLayerRole,
    ) {
        if (overlay.opacity <= 0.0) return
        val image = dynamicSightlineLayerImage(mesh, cellPx, overlay, role)
        if (image != null) {
            gc.drawImage(image, originX, originY)
            return
        }

        gc.fill = overlay
        gc.beginPath()
        mesh.drawHiddenArea(
            moveTo = { point -> gc.moveTo(originX + point.x * cellPx, originY + point.y * cellPx) },
            lineTo = { point -> gc.lineTo(originX + point.x * cellPx, originY + point.y * cellPx) },
            closePath = { gc.closePath() },
        )
        gc.fill()
    }

    private fun dynamicSightlineLayerImage(
        mesh: DynamicSightlineMesh,
        cellPx: Double,
        overlay: Color,
        role: DynamicSightlineLayerRole,
    ): Image? {
        if (overlay.opacity <= 0.0) return null

        val width = mesh.cols * cellPx
        val height = mesh.rows * cellPx
        if (!isCacheableLayerSize(width, height)) return null

        val key = DynamicSightlineLayerKey(
            version = dynamicSightlineLayerVersion,
            cols = mesh.cols,
            rows = mesh.rows,
            cellPx = cellPx,
            tint = overlay,
            role = role,
        )
        dynamicSightlineLayerCaches[role]?.takeIf { it.key == key }?.let { return it.image }

        val layerCanvas = Canvas(ceil(width), ceil(height))
        val layerGc = layerCanvas.graphicsContext2D
        layerGc.fill = overlay
        layerGc.beginPath()
        mesh.drawHiddenArea(
            moveTo = { point -> layerGc.moveTo(point.x * cellPx, point.y * cellPx) },
            lineTo = { point -> layerGc.lineTo(point.x * cellPx, point.y * cellPx) },
            closePath = { layerGc.closePath() },
        )
        layerGc.fill()
        val image = layerCanvas.snapshot(transparentSnapshotParameters(), null)
        dynamicSightlineLayerCaches[role] = CachedLayer(key, image)
        return image
    }

    private fun dynamicSightlineOverlayColor(): Color {
        val opacity = (dynamicSightlineOpacity ?: fogOpacity).coerceIn(0.0, 1.0)
        return Color.color(
            dynamicSightlineTint.red,
            dynamicSightlineTint.green,
            dynamicSightlineTint.blue,
            opacity,
        )
    }

    private fun rememberedSightlineOverlayColor(): Color =
        Color.color(
            dynamicSightlineTint.red,
            dynamicSightlineTint.green,
            dynamicSightlineTint.blue,
            REMEMBERED_SIGHTLINE_OPACITY,
        )

    private fun Color.withOpacity(opacity: Double): Color =
        Color.color(red, green, blue, opacity.coerceIn(0.0, 1.0))

    /**
     * Converts canvas-space mouse coordinates to the corresponding grid cell indices,
     * accounting for the current viewport transform.
     *
     * This is the inverse of the grid-drawing transform and is used to determine
     * which grid cell the DM is pointing at, for token dragging and as the foundation
     * for [canvasCoordsToFogCell].
     *
     * @param canvasX canvas-space X coordinate (e.g. from a mouse event).
     * @param canvasY canvas-space Y coordinate.
     * @return zero-based `(col, row)` grid cell indices.
     */
    fun canvasCoordsToGridCell(canvasX: Double, canvasY: Double): Pair<Int, Int> {
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        val (worldX, worldY) = canvasToWorldCoords(canvasX, canvasY)
        val cx = canvas.width / 2.0
        val cy = canvas.height / 2.0
        val originX = cx + gridCalibration.offsetX
        val originY = cy + gridCalibration.offsetY
        val col = floor((worldX - originX) / cellPx).toInt()
        val row = floor((worldY - originY) / cellPx).toInt()
        return Pair(col, row)
    }

    /**
     * Converts canvas-space mouse coordinates to the corresponding fog-of-war
     * cell indices, accounting for the current viewport transform.
     *
     * Delegates to [canvasCoordsToGridCell] for the canvas→grid conversion, then
     * maps the grid indices to fog array indices using [fogColOffset]/[fogRowOffset]
     * and performs a bounds check against the active [fogOfWar] state.
     *
     * Returns `null` when no fog state is active or the coordinates fall outside
     * the fog grid bounds.
     *
     * @param canvasX canvas-space X coordinate (e.g. from a mouse event).
     * @param canvasY canvas-space Y coordinate.
     * @return zero-based `(col, row)` fog array indices, or `null` if out of bounds.
     */
    fun canvasCoordsToFogCell(canvasX: Double, canvasY: Double): Pair<Int, Int>? {
        val fow = fogOfWar ?: return null
        val (gridCol, gridRow) = canvasCoordsToGridCell(canvasX, canvasY)

        // Grid cell → fog array index.
        val fogCol = gridCol - fogColOffset
        val fogRow = gridRow - fogRowOffset

        // Bounds check.
        if (fogCol < 0 || fogCol >= fow.cols || fogRow < 0 || fogRow >= fow.rows) return null
        return Pair(fogCol, fogRow)
    }

    fun dynamicDoorAtCanvasCoords(
        canvasX: Double,
        canvasY: Double,
        includeHidden: Boolean = showHiddenDynamicDoorIcons,
    ): DynamicMapRuntimeWall? {
        val bundle = dynamicMapBundle ?: return null
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return null
        val (worldX, worldY) = canvasToWorldCoords(canvasX, canvasY)
        val originX = canvas.width / 2.0 + gridCalibration.offsetX
        val originY = canvas.height / 2.0 + gridCalibration.offsetY
        val hitRadius = (cellPx * 0.45).coerceAtLeast(16.0 / viewportScale.coerceAtLeast(0.1))

        return bundle.walls
            .asSequence()
            .filter { it.isDoor() }
            .filter { includeHidden || it.doorVisible }
            .map { wall ->
                val centerX = originX + ((wall.start.x + wall.end.x) / 2.0) * cellPx
                val centerY = originY + ((wall.start.y + wall.end.y) / 2.0) * cellPx
                wall to hypot(worldX - centerX, worldY - centerY)
            }
            .filter { (_, distance) -> distance <= hitRadius }
            .minByOrNull { it.second }
            ?.first
    }

    fun isDynamicDoorOpen(wallId: String): Boolean =
        wallId in openDynamicDoorIds

    /**
     * Returns the token whose grid cell contains the given canvas-space coordinates,
     * or `null` if no token occupies that cell.
     *
     * Used by the DM-panel minimap to detect which token the DM is about to drag.
     *
     * @param canvasX canvas-space X coordinate.
     * @param canvasY canvas-space Y coordinate.
     * @return the [Token] at that grid cell, or `null`.
     */
    fun tokenAtCanvasCoords(canvasX: Double, canvasY: Double): Token? {
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return null
        val (worldX, worldY) = canvasToWorldCoords(canvasX, canvasY)
        val originX = canvas.width / 2.0 + gridCalibration.offsetX
        val originY = canvas.height / 2.0 + gridCalibration.offsetY
        return tokensInHitTestOrder().firstOrNull { token ->
            tokenDrawBounds(token, originX, originY, cellPx).contains(worldX, worldY)
        }
    }

    /**
     * Draws all tokens above the fog-of-war layer using their configured footprint size.
     *
     * Medium tokens occupy one tile, large creatures expand to multi-tile footprints,
     * and tiny/small tokens render centred within a single anchor tile. Tokens are
     * drawn from largest to smallest so smaller creatures stay visually on top when
     * footprints overlap. When the player view hides tokens in fog, only the revealed
     * portion of a token footprint is drawn so oversized creatures do not leak through
     * unrevealed cells.
     */
    private fun drawTokens() {
        if (tokens.isEmpty()) return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0) return

        val filterFogVisibility = hideTokensInFog && fogOfWar != null
        val filterSightlineVisibility = hideTokensInFog && dynamicSightlineMesh != null

        drawTokenSet(
            tokensToDraw = tokensInDrawOrder()
                .asSequence()
                .filterNot(::shouldDrawPlayerCharacterInForcedPass)
                .filter { token -> !filterSightlineVisibility || isTokenVisibleInDynamicSightline(token) }
                .toList(),
            clipToFog = filterFogVisibility,
        )
    }

    private fun drawForcedPlayerCharacterTokens() {
        if (!hideTokensInFog || !forcePlayerCharacterTokensVisible || tokens.none { it.isPlayerCharacter }) return
        drawTokenSet(
            tokensToDraw = tokensInDrawOrder().filter { it.isPlayerCharacter },
            clipToFog = false,
        )
    }

    private fun drawTokenSet(
        tokensToDraw: Iterable<Token>,
        clipToFog: Boolean,
    ) {
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0) return
        val originX = canvas.width / 2.0 + gridCalibration.offsetX
        val originY = canvas.height / 2.0 + gridCalibration.offsetY

        if (showTokenNames) {
            gc.textAlign = TextAlignment.CENTER
        }

        for (token in tokensToDraw) {
            val occupiedCells = tokenOccupiedCells(token)
            val visibleCells = if (clipToFog) {
                occupiedCells.filter { (col, row) -> isFogVisibleCell(col, row) }
            } else {
                occupiedCells
            }
            if (visibleCells.isEmpty()) continue

            drawToken(
                token = token,
                occupiedCells = occupiedCells,
                visibleCells = visibleCells,
                originX = originX,
                originY = originY,
                cellPx = cellPx,
                clipToFog = clipToFog,
            )
        }
    }

    private fun drawToken(
        token: Token,
        occupiedCells: List<Pair<Int, Int>>,
        visibleCells: List<Pair<Int, Int>>,
        originX: Double,
        originY: Double,
        cellPx: Double,
        clipToFog: Boolean,
    ) {
        val drawBounds = tokenDrawBounds(token, originX, originY, cellPx)
        val activeOutlineWidth = (drawBounds.size * ACTIVE_TOKEN_OUTLINE_WIDTH_SCALE).coerceAtLeast(1.0)
        val tokenOutlineWidth = (drawBounds.size * TOKEN_OUTLINE_WIDTH_SCALE).coerceAtLeast(0.5)
        val isPartiallyHidden = clipToFog && visibleCells.size < occupiedCells.size

        if (isPartiallyHidden) {
            gc.save()
            gc.beginPath()
            visibleCells.forEach { (col, row) ->
                gc.rect(
                    originX + col * cellPx,
                    originY + row * cellPx,
                    cellPx,
                    cellPx,
                )
            }
            gc.closePath()
            gc.clip()
        }

        val img = token.imageUri?.let { imageCache[it] }
        if (img != null && !img.isError) {
            gc.save()
            gc.beginPath()
            gc.arc(drawBounds.centerX, drawBounds.centerY, drawBounds.size / 2.0, drawBounds.size / 2.0, 0.0, 360.0)
            gc.closePath()
            gc.clip()
            val drawW = drawBounds.size * token.imageScaleX
            val drawH = drawBounds.size * token.imageScaleY
            val drawX = drawBounds.centerX - (drawW / 2) + token.imageOffsetX
            val drawY = drawBounds.centerY - (drawH / 2) + token.imageOffsetY
            gc.drawImage(img, drawX, drawY, drawW, drawH)
            gc.restore()
        } else {
            gc.fill = token.color
            gc.fillOval(drawBounds.left, drawBounds.top, drawBounds.size, drawBounds.size)
        }

        if (token.id == activeTokenId) {
            gc.stroke = Color.ORANGE
            gc.lineWidth = activeOutlineWidth
            gc.strokeOval(drawBounds.left, drawBounds.top, drawBounds.size, drawBounds.size)
        }

        gc.stroke = token.color
        gc.lineWidth = tokenOutlineWidth
        gc.strokeOval(drawBounds.left, drawBounds.top, drawBounds.size, drawBounds.size)

        if (isPartiallyHidden) {
            gc.restore()
        }

        if (showTokenNames && token.name.isNotBlank() && !isPartiallyHidden) {
            val tokenNameFont = Font.font(
                (cellPx * token.size.footprintTiles * TOKEN_NAME_FONT_SCALE)
                    .coerceAtLeast(MIN_TOKEN_NAME_FONT_SIZE),
            )
            gc.font = tokenNameFont
            val textY = drawBounds.bottom + tokenNameFont.size
            gc.fill = Color.BLACK
            gc.fillText(
                token.name,
                drawBounds.centerX + TOKEN_NAME_SHADOW_OFFSET,
                textY + TOKEN_NAME_SHADOW_OFFSET,
            )
            gc.fill = Color.WHITE
            gc.fillText(token.name, drawBounds.centerX, textY)
        }
    }

    private fun shouldDrawPlayerCharacterInForcedPass(token: Token): Boolean =
        hideTokensInFog && forcePlayerCharacterTokensVisible && token.isPlayerCharacter

    /** Draws measurement overlays (line, cone, rectangle, circle) above tokens. */
    private fun drawMeasurements() {
        if (measurements.isEmpty()) return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0) return

        val originX = canvas.width / 2.0 + gridCalibration.offsetX
        val originY = canvas.height / 2.0 + gridCalibration.offsetY
        gc.textAlign = TextAlignment.LEFT
        gc.font = Font.font((cellPx * 0.25).coerceAtLeast(11.0))

        for (measurement in measurements.values) {
            if (!measurement.mirroredToTable && !showDmOnlyMeasurements) continue

            val sx = originX + (measurement.startCol + 0.5) * cellPx
            val sy = originY + (measurement.startRow + 0.5) * cellPx
            val ex = originX + (measurement.endCol + 0.5) * cellPx
            val ey = originY + (measurement.endRow + 0.5) * cellPx
            val lineWidth = (cellPx * 0.07).coerceIn(2.0, 5.0)
            gc.stroke = measurement.color
            gc.fill = Color.color(
                measurement.color.red,
                measurement.color.green,
                measurement.color.blue,
                MEASUREMENT_FILL_OPACITY,
            )
            gc.lineWidth = lineWidth

            when (measurement.type) {
                MeasurementType.LINE -> {
                    gc.strokeLine(sx, sy, ex, ey)
                }
                MeasurementType.RECTANGLE -> {
                    val minX = minOf(sx, ex) - cellPx / 2.0
                    val minY = minOf(sy, ey) - cellPx / 2.0
                    val w = (abs(ex - sx) + cellPx).coerceAtLeast(cellPx)
                    val h = (abs(ey - sy) + cellPx).coerceAtLeast(cellPx)
                    gc.fillRect(minX, minY, w, h)
                    gc.strokeRect(minX, minY, w, h)
                }
                MeasurementType.CIRCLE -> {
                    val r = hypot(ex - sx, ey - sy).coerceAtLeast(cellPx * 0.25)
                    gc.fillOval(sx - r, sy - r, r * 2.0, r * 2.0)
                    gc.strokeOval(sx - r, sy - r, r * 2.0, r * 2.0)
                }
                MeasurementType.CONE -> {
                    val r = hypot(ex - sx, ey - sy).coerceAtLeast(cellPx * 0.25)
                    val dir = atan2(ey - sy, ex - sx)
                    val half = Math.toRadians(measurement.coneAngleDegrees / 2.0)
                    val start = dir - half
                    val end = dir + half
                    val arcStart = -Math.toDegrees(end)
                    gc.fillArc(
                        sx - r,
                        sy - r,
                        r * 2.0,
                        r * 2.0,
                        arcStart,
                        measurement.coneAngleDegrees,
                        javafx.scene.shape.ArcType.ROUND,
                    )
                    gc.strokeLine(sx, sy, sx + r * cos(start), sy + r * sin(start))
                    gc.strokeLine(sx, sy, sx + r * cos(end), sy + r * sin(end))
                    gc.strokeArc(sx - r, sy - r, r * 2.0, r * 2.0, arcStart, measurement.coneAngleDegrees, javafx.scene.shape.ArcType.OPEN)
                }
            }

            val unitsLabel = measurement.dimensionText(DEFAULT_MEASUREMENT_CELL_SIZE_IN_UNITS)
            val fullLabel = if (measurement.unitLabel.isBlank()) unitsLabel else "${measurement.unitLabel}: $unitsLabel"
            val lx = (sx + ex) / 2.0 + 8.0
            val ly = (sy + ey) / 2.0 - 8.0
            gc.fill = Color.BLACK
            gc.fillText(fullLabel, lx + 1.0, ly + 1.0)
            gc.fill = Color.WHITE
            gc.fillText(fullLabel, lx, ly)
        }
    }

    /**
     * Draws a faint dashed rectangle on the DM minimap showing the current
     * player-facing table viewport.
     *
     * The outline is rendered in screen space so its stroke width and dash
     * pattern stay readable regardless of the DM minimap zoom level.
     */
    private fun drawTableViewportOutline() {
        if (!showTableViewportOutline) return

        val bounds = tableViewportSceneBounds(
            viewportWidth = tableViewportWidth,
            viewportHeight = tableViewportHeight,
            tableMapOffset = tableMapOffset,
        ) ?: return

        val topLeft = sceneToCanvasCoords(bounds[0], bounds[2])
        val bottomRight = sceneToCanvasCoords(bounds[1], bounds[3])
        val x = minOf(topLeft.first, bottomRight.first)
        val y = minOf(topLeft.second, bottomRight.second)
        val width = abs(bottomRight.first - topLeft.first)
        val height = abs(bottomRight.second - topLeft.second)
        if (width <= 0.0 || height <= 0.0) return

        gc.save()
        gc.stroke = tableViewportOutlineColor(lastGridColor)
        gc.lineWidth = TABLE_VIEWPORT_OUTLINE_LINE_WIDTH
        gc.setLineDashes(TABLE_VIEWPORT_OUTLINE_DASH_LENGTH, TABLE_VIEWPORT_OUTLINE_GAP_LENGTH)
        gc.strokeRect(x, y, width, height)
        gc.restore()
    }


    /**
     * Converts a scene-space coordinate to canvas-space, applying this
     * renderer's viewport transform and optional shared table offset.
     *
     * Scene-space uses the canvas centre as `(0, 0)`, which makes it stable
     * across differently sized renderer canvases.
     */
    private fun sceneToCanvasCoords(sceneX: Double, sceneY: Double): Pair<Double, Double> {
        val cx = canvas.width / 2.0
        val cy = canvas.height / 2.0
        val sceneOffsetX = if (applyTableMapOffset) tableMapOffset.offsetX else 0.0
        val sceneOffsetY = if (applyTableMapOffset) tableMapOffset.offsetY else 0.0
        val canvasX = cx + viewportOffsetX + viewportScale * (sceneX + sceneOffsetX)
        val canvasY = cy + viewportOffsetY + viewportScale * (sceneY + sceneOffsetY)
        return Pair(canvasX, canvasY)
    }

    private fun canvasToWorldCoords(canvasX: Double, canvasY: Double): Pair<Double, Double> {
        val cx = canvas.width / 2.0
        val cy = canvas.height / 2.0
        val sceneOffsetX = if (applyTableMapOffset) tableMapOffset.offsetX else 0.0
        val sceneOffsetY = if (applyTableMapOffset) tableMapOffset.offsetY else 0.0
        val worldX = (canvasX - cx - viewportOffsetX) / viewportScale + cx - sceneOffsetX
        val worldY = (canvasY - cy - viewportOffsetY) / viewportScale + cy - sceneOffsetY
        return Pair(worldX, worldY)
    }

    private fun isFogVisibleCell(col: Int, row: Int): Boolean {
        val fow = fogOfWar ?: return true
        val fogCol = col - fogColOffset
        val fogRow = row - fogRowOffset
        if (fogCol !in 0 until fow.cols || fogRow !in 0 until fow.rows) {
            return true
        }
        return fow.isRevealed(fogCol, fogRow)
    }

    private fun isTokenVisibleInDynamicSightline(token: Token): Boolean {
        val mesh = dynamicSightlineMesh ?: return true
        return mesh.intersectsToken(token)
    }

    private fun tokensInDrawOrder(): List<Token> =
        tokens.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<Token>> { it.value.size.footprintTiles }
                    .thenBy { it.index },
            )
            .map { it.value }

    private fun tokensInHitTestOrder(): List<Token> =
        tokens.withIndex()
            .sortedWith(
                compareBy<IndexedValue<Token>> { it.value.size.footprintTiles }
                    .thenByDescending { it.index },
            )
            .map { it.value }

    private fun clearLayerCaches() {
        dynamicMapBaseLayerCache = null
        dynamicMapGrayscaleLayerCache = null
        fogLayerCache = null
        dynamicSightlineLayerCaches.clear()
        dynamicLightTintLayerCache = null
    }

    private fun isCacheableLayerSize(width: Double, height: Double): Boolean =
        width.isFinite() &&
            height.isFinite() &&
            width > 0.0 &&
            height > 0.0 &&
            width <= MAX_CACHED_LAYER_DIMENSION &&
            height <= MAX_CACHED_LAYER_DIMENSION &&
            width * height <= MAX_CACHED_LAYER_PIXELS

    private fun transparentSnapshotParameters(): SnapshotParameters =
        SnapshotParameters().apply { fill = Color.TRANSPARENT }

    private data class CachedLayer<K>(
        val key: K,
        val image: Image,
    )

    private data class DynamicMapBaseLayerKey(
        val sourcePath: String,
        val cols: Int,
        val rows: Int,
        val backgroundImageId: Int,
        val backgroundImageWidth: Double,
        val backgroundImageHeight: Double,
        val backgroundScale: Double,
        val backgroundOffsetX: Double,
        val backgroundOffsetY: Double,
        val cellPx: Double,
        val backgroundColor: Color,
    )

    private data class FogLayerKey(
        val version: Long,
        val cols: Int,
        val rows: Int,
        val cellPx: Double,
        val opacity: Double,
    )

    private data class DynamicSightlineLayerKey(
        val version: Long,
        val cols: Int,
        val rows: Int,
        val cellPx: Double,
        val tint: Color,
        val role: DynamicSightlineLayerRole,
    )

    private data class DynamicLightTintLayerKey(
        val version: Long,
        val cols: Int,
        val rows: Int,
        val cellPx: Double,
        val lightMaskId: Int,
    )

    private enum class DynamicSightlineLayerRole {
        CURRENT,
        REMEMBERED,
        NEVER_SEEN,
    }

    companion object {
        /**
         * Font size as a fraction of grid cell size for token name labels.
         * At 0.28× cell size the text is proportionally readable at typical
         * map scales without overflowing into neighbouring cells.
         */
        private const val TOKEN_NAME_FONT_SCALE = 0.28

        /**
         * Minimum font size in points for token name labels in **world space**,
         * based on the calibrated grid cell size (`cellPx`).
         *
         * The effective on-screen size of token names is further affected by
         * [viewportScale]: on the main table view (viewportScale ≈ 1.0) this
         * threshold helps prevent labels from becoming illegible on small maps.
         * On the minimap, however, token names are drawn inside the viewport
         * transform, so zooming out (viewportScale < 1) can still reduce the
         * apparent text size below this constant.
         */
        private const val MIN_TOKEN_NAME_FONT_SIZE = 8.0

        /**
         * World-space offset applied to the drop-shadow copy of the token name text.
         * At a viewport scale of 1.0 this corresponds to a 1 px diagonal offset,
         * creating a subtle dark outline that keeps names readable against both
         * light and dark map backgrounds. The apparent size of the shadow scales
         * proportionally with [viewportScale] on the minimap.
         */
        private const val TOKEN_NAME_SHADOW_OFFSET = 1.0
        private const val ACTIVE_TOKEN_OUTLINE_WIDTH_SCALE = 0.10
        private const val TOKEN_OUTLINE_WIDTH_SCALE = 0.025
        private const val MEASUREMENT_FILL_OPACITY = 0.18
        private const val DEFAULT_MEASUREMENT_CELL_SIZE_IN_UNITS = 5.0
        private const val TABLE_VIEWPORT_OUTLINE_LINE_WIDTH = 1.5
        private const val TABLE_VIEWPORT_OUTLINE_DASH_LENGTH = 10.0
        private const val TABLE_VIEWPORT_OUTLINE_GAP_LENGTH = 6.0
        private const val DYNAMIC_LIGHT_DIM_OPACITY = 0.14
        private const val DYNAMIC_LIGHT_BRIGHT_OPACITY = 0.28
        private const val DYNAMIC_LIGHT_RING_OPACITY = 0.75
        private const val DYNAMIC_LIGHT_RING_WIDTH_SCALE = 0.025
        private const val DYNAMIC_WALL_WIDTH_SCALE = 0.12
        private const val DYNAMIC_LIGHT_MARKER_SCALE = 0.18
        private const val DYNAMIC_SUNLIGHT_AREA_FILL_OPACITY = 0.16
        private const val DYNAMIC_SUNLIGHT_AREA_STROKE_OPACITY = 0.82
        private const val DYNAMIC_LIGHT_TINT_DIM_OPACITY = 0.16
        private const val DYNAMIC_LIGHT_TINT_BRIGHT_OPACITY = 0.24
        private const val REMEMBERED_SIGHTLINE_OPACITY = 0.5
        private const val MAX_CACHED_LAYER_DIMENSION = 8192.0
        private const val MAX_CACHED_LAYER_PIXELS = 16_000_000.0
        private val GRAYSCALE_EFFECT = ColorAdjust(0.0, -1.0, 0.0, 0.0)
    }
}

internal fun rememberedSightlineMesh(
    currentMesh: DynamicSightlineMesh,
    seenMesh: DynamicSightlineMesh,
): DynamicSightlineMesh? {
    if (currentMesh.cols != seenMesh.cols || currentMesh.rows != seenMesh.rows) return null

    val rememberedArea = seenMesh.copyVisibleArea().apply {
        subtract(currentMesh.copyVisibleArea())
    }
    if (rememberedArea.isEmpty) return null

    return DynamicSightlineMesh.fromVisibleArea(
        cols = seenMesh.cols,
        rows = seenMesh.rows,
        visibleArea = rememberedArea,
    )
}

internal fun cacheTokenImageWhenReady(
    uri: String,
    image: Image,
    imageCache: MutableMap<String, Image>,
    onReady: () -> Unit,
) {
    fun cacheIfReady() {
        if (image.isError || image.progress < 1.0 || imageCache[uri] === image) {
            return
        }
        imageCache[uri] = image
        onReady()
    }

    cacheIfReady()
    image.progressProperty().addListener { _, _, _ -> cacheIfReady() }
}

internal fun tableViewportSceneBounds(
    viewportWidth: Double,
    viewportHeight: Double,
    tableMapOffset: TableMapOffset,
): DoubleArray? {
    if (!viewportWidth.isFinite() || !viewportHeight.isFinite() || viewportWidth <= 0.0 || viewportHeight <= 0.0) {
        return null
    }
    return doubleArrayOf(
        -viewportWidth / 2.0 - tableMapOffset.offsetX,
        viewportWidth / 2.0 - tableMapOffset.offsetX,
        -viewportHeight / 2.0 - tableMapOffset.offsetY,
        viewportHeight / 2.0 - tableMapOffset.offsetY,
    )
}

internal fun tableViewportOutlineColor(base: Color): Color {
    val shiftedHue = (base.hue + 20.0) % 360.0
    val saturation = (base.saturation + 0.18).coerceIn(0.18, 1.0)
    val brightness = (base.brightness * 0.92 + 0.08).coerceIn(0.15, 1.0)
    val opacity = (base.opacity * 0.75).coerceIn(0.22, 0.5)
    return Color.hsb(shiftedHue, saturation, brightness, opacity)
}
