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
 * Rendering is composed of three layers drawn in order:
 * 1. Map image — scaled and translated according to the current [calibration].
 * 2. Grid overlay — drawn when [gridConfig] is non-null and [GridConfig.visible] is `true`.
 * 3. Fog-of-war — unrevealed cells in [fogOfWar] are covered with a semi-transparent overlay.
 *
 * @param canvas the [Canvas] to draw on; must be attached to a scene before
 *               calling [redraw].
 */
class MapRenderer(private val canvas: Canvas) {

    private val gc: GraphicsContext = canvas.graphicsContext2D

    /** Currently loaded map image, or `null` if no map has been loaded yet. */
    var mapImage: Image? = null
        private set

    /** Calibration parameters controlling scale and offset of the map image. */
    var calibration: MapCalibration = MapCalibration()

    /** Grid overlay configuration, or `null` to disable the grid. */
    var gridConfig: GridConfig? = null

    /** Fog-of-war cell state, or `null` when fog of war is not active. */
    var fogOfWar: FogOfWarState? = null

    init {
        attachToEventBus()
    }

    /**
     * Subscribes to map-related events published by [MapPlugin] via [EventBus]
     * so that this renderer can update the canvas in response.
     *
     * The exact mutation of [mapImage], [calibration], [gridConfig], and
     * [fogOfWar] may be performed elsewhere; this method at minimum ensures
     * that the renderer is event-driven and repaints when relevant events fire.
     */
    private fun attachToEventBus() {
        EventBus.subscribe<MapLoadEvent> { event ->
            loadImage(event.resourcePath)
        }
        EventBus.subscribe<MapCalibrationEvent> { event ->
            calibration = event.calibration
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
     * (new map loaded, fog-of-war updated, grid toggled, etc.).
     */
    fun redraw() {
        gc.fill = Color.BLACK
        gc.fillRect(0.0, 0.0, canvas.width, canvas.height)

        drawMapImage()
        drawGrid()
        drawFogOfWar()
    }

    // -------------------------------------------------------------------------
    // Private rendering helpers
    // -------------------------------------------------------------------------

    /** Draws the map image applying the current [calibration]. */
    private fun drawMapImage() {
        val image = mapImage ?: return
        val destWidth = image.width * calibration.scale
        val destHeight = image.height * calibration.scale
        gc.drawImage(
            image,
            calibration.offsetX, calibration.offsetY,
            destWidth, destHeight,
        )
    }

    /** Draws the grid overlay when [gridConfig] is non-null and visible. */
    private fun drawGrid() {
        val cfg = gridConfig?.takeIf { it.visible } ?: return
        val cellPx = calibration.cellSizeInPixels(cfg.cellSizeInUnits)
        if (cellPx <= 0) return

        gc.stroke = cfg.color
        gc.lineWidth = cfg.lineWidth

        val w = canvas.width
        val h = canvas.height

        // Vertical lines
        var x = calibration.offsetX % cellPx
        if (x < 0) x += cellPx
        while (x <= w) {
            gc.strokeLine(x, 0.0, x, h)
            x += cellPx
        }

        // Horizontal lines
        var y = calibration.offsetY % cellPx
        if (y < 0) y += cellPx
        while (y <= h) {
            gc.strokeLine(0.0, y, w, y)
            y += cellPx
        }
    }

    /** Covers unrevealed cells with a semi-transparent fog overlay. */
    private fun drawFogOfWar() {
        val fow = fogOfWar ?: return
        // Use the cell size declared on FogOfWarState so fog cells always align
        // with the grid regardless of whether gridConfig is currently set.
        val cellPx = calibration.cellSizeInPixels(fow.cellSizeInUnits)

        gc.fill = Color.color(0.0, 0.0, 0.0, 0.75)

        val startX = calibration.offsetX
        val startY = calibration.offsetY

        for (col in 0 until fow.cols) {
            for (row in 0 until fow.rows) {
                if (!fow.isRevealed(col, row)) {
                    gc.fillRect(
                        startX + col * cellPx,
                        startY + row * cellPx,
                        cellPx,
                        cellPx,
                    )
                }
            }
        }
    }
}
