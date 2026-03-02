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
     */
    fun redraw() {
        gc.fill = Color.BLACK
        gc.fillRect(0.0, 0.0, canvas.width, canvas.height)

        drawMapImage()
        drawGrid()
        drawFogOfWar()
        drawGridCalibrationOverlay()
        drawMapCalibrationOverlay()
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
     * Draws the grid overlay using [gridCalibration] with the canvas centre as origin.
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

        // Vertical lines: find the first line at or to the left of x = 0.
        var x = originX % cellPx
        if (x < 0) x += cellPx
        while (x <= w) {
            gc.strokeLine(x, 0.0, x, h)
            x += cellPx
        }

        // Horizontal lines: find the first line at or above y = 0.
        var y = originY % cellPx
        if (y < 0) y += cellPx
        while (y <= h) {
            gc.strokeLine(0.0, y, w, y)
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
