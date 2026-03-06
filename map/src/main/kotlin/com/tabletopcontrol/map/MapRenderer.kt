package com.tabletopcontrol.map

import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.Image
import javafx.scene.paint.Color
import com.tabletopcontrol.core.ActiveTokenChangedEvent
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenAddedEvent
import com.tabletopcontrol.core.TokenMovedEvent
import com.tabletopcontrol.core.TokenRemovedEvent
import com.tabletopcontrol.core.TokensResetEvent
import kotlin.math.floor

/**
 * Renders the tabletop map onto a JavaFX [Canvas].
 *
 * The renderer is **event-driven, not a real-time loop**: it redraws only when
 * explicitly requested — for example when a new map image is loaded or the
 * fog-of-war state changes.  This keeps CPU usage low on modest hardware.
 *
 * Rendering is composed of layers drawn in order:
 * 1. Background fill (black).
 * 2. Map image — scaled from the canvas centre according to [mapCalibration].
 * 3. Grid overlay — drawn when [gridConfig] is non-null and visible, using
 *    [gridCalibration] with the canvas centre as the scale origin.
 * 4. Fog-of-war — unrevealed cells in [fogOfWar] are covered with a semi-transparent overlay.
 * 5. Calibration overlays — only visible while the respective calibration dialog is open:
 *    - **Grid calibration**: a yellow crosshair through the canvas centre.
 *    - **Map calibration**: a red dot at the canvas centre.
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

    /** Calibration parameters controlling scale and centre-offset of the map image. */
    var mapCalibration: MapCalibration = MapCalibration()

    /** Calibration parameters controlling cell size, scale, and centre-offset of the grid. */
    var gridCalibration: GridCalibration = GridCalibration()

    /** Grid overlay configuration, or `null` to disable the grid. */
    var gridConfig: GridConfig? = null

    /** Fog-of-war cell state, or `null` when fog of war is not active. */
    var fogOfWar: FogOfWarState? = null

    /**
     * Opacity of unrevealed fog-of-war tiles, in the range [0.0, 1.0].
     *
     * Set to `1.0` for the table view so players cannot see through the fog at all,
     * and to a lower value (e.g. `0.5`) for the DM minimap so the underlying map
     * remains visible beneath the fog.  Defaults to `1.0`.
     */
    var fogOpacity: Double = 1.0

    /** Grid-column offset: fog array column 0 corresponds to grid column [fogColOffset]. */
    private var fogColOffset: Int = 0

    /** Grid-row offset: fog array row 0 corresponds to grid row [fogRowOffset]. */
    private var fogRowOffset: Int = 0

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
     * When `true`, tokens whose grid cell is covered by unrevealed fog-of-war are
     * not drawn.  Set this to `true` for the player-facing table view so that tokens
     * cannot be seen through the fog; leave it at `false` (the default) for the DM
     * minimap so tokens remain visible and can be dragged regardless of fog state.
     */
    var hideTokensInFog: Boolean = false

    /** Current list of tokens to draw on the map. */
    private val tokens = mutableListOf<Token>()

    /** Stable ID of the currently active combatant's token, or `null` when none is active. */
    private var activeTokenId: String? = null

    /**
     * Monotonically increasing counter used to assign a unique initial column to each
     * new token.  Never resets on removal, so columns are never reused after a token
     * is removed and a new one is added.
     */
    private var nextTokenCol: Int = 0

    /**
     * All active [EventBus.Subscription] handles for this renderer.
     * Populated in [attachToEventBus] and released en masse in [dispose].
     */
    private val subscriptions = mutableListOf<EventBus.Subscription>()

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
            redraw()
        }
        subscriptions += EventBus.subscribe<FogOfWarResetEvent> { event ->
            if (event.revealAll) fogOfWar?.revealAll() else fogOfWar?.hideAll()
            redraw()
        }
        subscriptions += EventBus.subscribe<FogOfWarCellEvent> { event ->
            if (event.revealed) fogOfWar?.revealCell(event.col, event.row)
            else fogOfWar?.hideCell(event.col, event.row)
            redraw()
        }
        subscriptions += EventBus.subscribe<FogOfWarSetupEvent> { event ->
            fogColOffset = event.colOffset
            fogRowOffset = event.rowOffset
            fogOfWar = FogOfWarState(event.cols, event.rows)
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
        subscriptions += EventBus.subscribe<TokenAddedEvent> { event ->
            // Place each new token at the next unused column at row 0.
            tokens.add(Token(event.id, event.name, nextTokenCol++, 0, event.color))
            redraw()
        }
        subscriptions += EventBus.subscribe<TokenRemovedEvent> { event ->
            tokens.removeIf { it.id == event.id }
            redraw()
        }
        subscriptions += EventBus.subscribe<TokenMovedEvent> { event ->
            val idx = tokens.indexOfFirst { it.id == event.id }
            if (idx >= 0) {
                tokens[idx] = tokens[idx].copy(col = event.col, row = event.row)
                redraw()
            }
        }
        subscriptions += EventBus.subscribe<ActiveTokenChangedEvent> { event ->
            activeTokenId = event.id
            redraw()
        }
        subscriptions += EventBus.subscribe<TokensResetEvent> {
            tokens.clear()
            activeTokenId = null
            nextTokenCol = 0
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
     * @return [Result.success] when the image is loaded and drawn; [Result.failure]
     *         with a descriptive exception when loading fails.
     */
    fun loadImage(resourcePath: String): Result<Unit> {
        val image = Image(resourcePath, false)
        return if (image.isError) {
            val cause = image.exception
            if (cause != null) {
                Result.failure(cause)
            } else {
                Result.failure(
                    IllegalStateException("Failed to load map image from '$resourcePath': unknown image loading error.")
                )
            }
        } else {
            mapImage = image
            redraw()
            Result.success(Unit)
        }
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
        gc.fill = Color.BLACK
        gc.fillRect(0.0, 0.0, canvas.width, canvas.height)

        // Apply viewport transform: zoom from the canvas centre then pan.
        gc.save()
        val cx = canvas.width / 2.0
        val cy = canvas.height / 2.0
        gc.translate(cx + viewportOffsetX, cy + viewportOffsetY)
        gc.scale(viewportScale, viewportScale)
        gc.translate(-cx, -cy)

        drawMapImage()
        drawGrid()
        drawFogOfWar()
        drawTokens()
        drawGridCornerDots()
        drawGridCalibrationOverlay()
        drawMapCalibrationOverlay()

        gc.restore()
    }

    // -------------------------------------------------------------------------
    // Private rendering helpers
    // -------------------------------------------------------------------------

    /**
     * Draws the map image centred on the canvas, applying [mapCalibration].
     *
     * The canvas centre is the fixed point for scaling: zooming in or out keeps
     * the image centred, and [MapCalibration.offsetX]/[MapCalibration.offsetY]
     * displace the image centre from the canvas centre.
     */
    private fun drawMapImage() {
        val image = mapImage ?: return
        val destWidth = image.width * mapCalibration.scale
        val destHeight = image.height * mapCalibration.scale

        // Image centre is placed at (canvas centre + calibration offset).
        val drawX = canvas.width / 2.0 - destWidth / 2.0 + mapCalibration.offsetX
        val drawY = canvas.height / 2.0 - destHeight / 2.0 + mapCalibration.offsetY

        gc.drawImage(image, drawX, drawY, destWidth, destHeight)
    }

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
        val xMin = (0.0 - cx - viewportOffsetX) / viewportScale + cx
        val xMax = (w   - cx - viewportOffsetX) / viewportScale + cx
        val yMin = (0.0 - cy - viewportOffsetY) / viewportScale + cy
        val yMax = (h   - cy - viewportOffsetY) / viewportScale + cy
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

        gc.fill = Color.color(0.0, 0.0, 0.0, fogOpacity.coerceIn(0.0, 1.0))

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
        val cx = canvas.width / 2.0
        val cy = canvas.height / 2.0
        val worldX = (canvasX - cx - viewportOffsetX) / viewportScale + cx
        val worldY = (canvasY - cy - viewportOffsetY) / viewportScale + cy
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
        val (col, row) = canvasCoordsToGridCell(canvasX, canvasY)
        return tokens.find { it.col == col && it.row == row }
    }

    /**
     * Draws all tokens as filled circles above the fog-of-war layer.
     *
     * Each token fills its grid cell (radius ≈ 45 % of the cell size) and is
     * centred on the cell.  The active token receives an additional orange outline
     * so the DM and players can immediately see whose turn it is.
     *
     * When [hideTokensInFog] is `true`, tokens whose grid cell is not yet revealed
     * in [fogOfWar] are skipped — this prevents players from seeing token positions
     * that are hidden behind the fog on the table view.  Tokens outside the fog grid
     * bounds, or when fog is not active, are always drawn.
     */
    private fun drawTokens() {
        if (tokens.isEmpty()) return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0) return

        val originX = canvas.width / 2.0 + gridCalibration.offsetX
        val originY = canvas.height / 2.0 + gridCalibration.offsetY
        val r = cellPx * 0.45

        val fow = fogOfWar

        for (token in tokens) {
            // Hide tokens that are in unrevealed fog cells on the player-facing view.
            if (hideTokensInFog && fow != null) {
                val fogCol = token.col - fogColOffset
                val fogRow = token.row - fogRowOffset
                if (fogCol >= 0 && fogCol < fow.cols && fogRow >= 0 && fogRow < fow.rows
                    && !fow.isRevealed(fogCol, fogRow)
                ) continue
            }

            val cx = originX + (token.col + 0.5) * cellPx
            val cy = originY + (token.row + 0.5) * cellPx

            // Fill the token circle.
            gc.fill = token.color
            gc.fillOval(cx - r, cy - r, r * 2, r * 2)

            // Draw an orange outline on the active token.
            if (token.id == activeTokenId) {
                gc.stroke = Color.ORANGE
                gc.lineWidth = r * 0.2
                gc.strokeOval(cx - r, cy - r, r * 2, r * 2)
            }
        }
    }

    /**
     * Draws a yellow crosshair through the canvas centre when grid calibration
     * mode is active.  The intersection marks the scale origin for the grid, so
     * the DM can align a grid line corner to a known physical reference.
     */
    private fun drawGridCalibrationOverlay() {
        if (!gridCalibrationMode) return
        val cx = canvas.width / 2.0
        val cy = canvas.height / 2.0

        gc.stroke = Color.YELLOW
        gc.lineWidth = 1.5
        gc.strokeLine(cx, 0.0, cx, canvas.height)  // vertical arm
        gc.strokeLine(0.0, cy, canvas.width, cy)    // horizontal arm
    }

    /**
     * Draws a red dot at the canvas centre when map calibration mode is active.
     * The dot marks the scale origin so the DM can align a known reference point
     * on the map image with the physical table centre.
     *
     * Grid corner dots are also drawn at every grid line intersection by
     * [drawGridCornerDots]; this overlay draws the larger centre dot on top so it
     * remains the most prominent marker.
     */
    private fun drawMapCalibrationOverlay() {
        if (!mapCalibrationMode) return
        val cx = canvas.width / 2.0
        val cy = canvas.height / 2.0
        val r = 6.0

        gc.fill = Color.RED
        gc.fillOval(cx - r, cy - r, r * 2, r * 2)
    }

    /**
     * Draws a small red dot at every grid line intersection when map calibration
     * mode is active.
     *
     * These markers let the DM spot scale or offset errors at the canvas edges
     * without having to trace individual grid lines — any drift of the dots away
     * from the underlying map's grid corners immediately reveals a mismatch.
     * The dots are 1.5 px in radius in world space, so they grow proportionally
     * when the DM zooms in for finer control.
     *
     * Dots are drawn whenever a [gridCalibration] is configured (regardless of
     * whether the grid lines themselves are visible), so they can serve as a
     * calibration aid even with the grid overlay hidden.
     */
    private fun drawGridCornerDots() {
        if (!mapCalibrationMode) return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0) return

        val w = canvas.width
        val h = canvas.height
        val originX = w / 2.0 + gridCalibration.offsetX
        val originY = h / 2.0 + gridCalibration.offsetY

        val bounds = visibleWorldBounds()
        val xMin = bounds[0]; val xMax = bounds[1]; val yMin = bounds[2]; val yMax = bounds[3]

        val r = 1.5
        gc.fill = Color.RED

        var x = originX + kotlin.math.ceil((xMin - originX) / cellPx) * cellPx
        while (x <= xMax) {
            var y = originY + kotlin.math.ceil((yMin - originY) / cellPx) * cellPx
            while (y <= yMax) {
                gc.fillOval(x - r, y - r, r * 2, r * 2)
                y += cellPx
            }
            x += cellPx
        }
    }
}
