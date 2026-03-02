package com.tabletopcontrol.map

import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.Image
import javafx.scene.paint.Color
import com.tabletopcontrol.core.EventBus

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

    init {
        attachToEventBus()
    }

    /**
     * Subscribes to map-related events published by [MapPlugin] via [EventBus]
     * so that this renderer can update the canvas in response.
     */
    private fun attachToEventBus() {
        EventBus.subscribe<MapLoadEvent> { event ->
            loadImage(event.resourcePath)
        }
        EventBus.subscribe<MapCalibrationEvent> { event ->
            mapCalibration = event.calibration
            redraw()
        }
        EventBus.subscribe<GridCalibrationEvent> { event ->
            gridCalibration = event.calibration
            redraw()
        }
        EventBus.subscribe<GridUpdateEvent> { event ->
            gridConfig = event.config
            redraw()
        }
        EventBus.subscribe<FogOfWarResetEvent> { event ->
            if (event.revealAll) fogOfWar?.revealAll() else fogOfWar?.hideAll()
            redraw()
        }
        EventBus.subscribe<FogOfWarCellEvent> { event ->
            if (event.revealed) fogOfWar?.revealCell(event.col, event.row)
            else fogOfWar?.hideCell(event.col, event.row)
            redraw()
        }
        EventBus.subscribe<GridCalibrationModeEvent> { event ->
            gridCalibrationMode = event.active
            redraw()
        }
        EventBus.subscribe<MapCalibrationModeEvent> { event ->
            mapCalibrationMode = event.active
            redraw()
        }
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
     * align with the grid.
     */
    private fun drawFogOfWar() {
        val fow = fogOfWar ?: return
        val cellPx = gridCalibration.effectiveCellSizeInPixels()

        gc.fill = Color.color(0.0, 0.0, 0.0, 0.75)

        // Fog cell (0, 0) starts at the grid origin (canvas centre + offset).
        val originX = canvas.width / 2.0 + gridCalibration.offsetX
        val originY = canvas.height / 2.0 + gridCalibration.offsetY

        for (col in 0 until fow.cols) {
            for (row in 0 until fow.rows) {
                if (!fow.isRevealed(col, row)) {
                    gc.fillRect(
                        originX + col * cellPx,
                        originY + row * cellPx,
                        cellPx,
                        cellPx,
                    )
                }
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
     */
    private fun drawMapCalibrationOverlay() {
        if (!mapCalibrationMode) return
        val cx = canvas.width / 2.0
        val cy = canvas.height / 2.0
        val r = 6.0

        gc.fill = Color.RED
        gc.fillOval(cx - r, cy - r, r * 2, r * 2)
    }
}
