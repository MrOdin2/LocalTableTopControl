package com.tabletopcontrol.dynamicmap.runtime.rendering

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window
import com.badlogic.gdx.math.Matrix4
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRenderMode
import com.tabletopcontrol.dynamicmap.runtime.MeasurementType
import org.lwjgl.glfw.GLFW
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * LibGDX renderer for DynamicMap snapshots.
 *
 * The renderer is intentionally non-continuous: callers submit a new
 * [DynamicMapRenderSnapshot], and this backend requests exactly one frame from
 * LibGDX. The JavaFX table host wires resize/show events to the existing
 * DynamicMap services and calls [submitSnapshot] after each state change.
 */
internal class LibGdxDynamicMapRenderer : ApplicationAdapter() {
    private val pendingSnapshot = AtomicReference<DynamicMapRenderSnapshot?>()
    private val pendingWindowState = AtomicReference<LibGdxWindowState?>()
    private var currentSnapshot: DynamicMapRenderSnapshot? = null
    private var application: Lwjgl3Application? = null
    private var window: Lwjgl3Window? = null

    private lateinit var camera: OrthographicCamera
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var font: BitmapFont
    private val glyphLayout = GlyphLayout()

    private var backgroundTextureKey: Int? = null
    private var backgroundTexture: Texture? = null
    private var backgroundRegion: TextureRegion? = null
    private val tokenTextures = linkedMapOf<String, Texture>()
    private val tokenRegions = linkedMapOf<String, TextureRegion>()

    fun submitSnapshot(snapshot: DynamicMapRenderSnapshot) {
        pendingSnapshot.set(snapshot)
        postToRenderThread { Gdx.graphics.requestRendering() }
    }

    fun syncWindow(state: LibGdxWindowState) {
        pendingWindowState.set(state)
        postToRenderThread {
            applyWindowState(state)
            Gdx.graphics.requestRendering()
        }
    }

    fun closeWindow() {
        postToRenderThread {
            window?.closeWindow()
            application?.exit()
        }
    }

    override fun create() {
        application = Gdx.app as? Lwjgl3Application
        window = (Gdx.graphics as? Lwjgl3Graphics)?.window
        camera = OrthographicCamera()
        shapeRenderer = ShapeRenderer()
        spriteBatch = SpriteBatch()
        font = BitmapFont()
        Gdx.graphics.setContinuousRendering(false)
        window?.let(::makeWindowFloatAboveJavaFxTableStage)
        pendingWindowState.get()?.let(::applyWindowState)
        Gdx.graphics.requestRendering()
    }

    override fun resize(width: Int, height: Int) {
        currentSnapshot?.let(::configureCamera)
        Gdx.graphics.requestRendering()
    }

    override fun render() {
        pendingSnapshot.getAndSet(null)?.let { snapshot ->
            currentSnapshot = snapshot
            configureCamera(snapshot)
            refreshBackgroundTexture(snapshot.dynamicMap)
            refreshTokenTextures(snapshot.tokens)
        }

        val snapshot = currentSnapshot
        if (snapshot == null) {
            clear(RenderColor.Black)
            return
        }

        clear(snapshot.backgroundColor)
        drawSnapshot(snapshot)
    }

    override fun dispose() {
        if (::shapeRenderer.isInitialized) shapeRenderer.dispose()
        if (::spriteBatch.isInitialized) spriteBatch.dispose()
        if (::font.isInitialized) font.dispose()
        backgroundTexture?.dispose()
        tokenTextures.values.forEach { it.dispose() }
        tokenTextures.clear()
        tokenRegions.clear()
        window = null
        application = null
    }

    private fun postToRenderThread(action: () -> Unit) {
        val app = application ?: Gdx.app
        app?.postRunnable { action() }
    }

    private fun applyWindowState(state: LibGdxWindowState) {
        val activeWindow = window ?: (Gdx.graphics as? Lwjgl3Graphics)?.window?.also { window = it } ?: return
        val graphics = Gdx.graphics
        makeWindowFloatAboveJavaFxTableStage(activeWindow)
        if (state.width > 0 && state.height > 0) {
            graphics.setWindowedMode(state.width, state.height)
            activeWindow.setPosition(state.x, state.y)
        }
        activeWindow.setVisible(state.visible)
    }

    private fun makeWindowFloatAboveJavaFxTableStage(activeWindow: Lwjgl3Window) {
        runCatching {
            GLFW.glfwSetWindowAttrib(activeWindow.windowHandle, GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE)
        }
    }

    private fun configureCamera(snapshot: DynamicMapRenderSnapshot) {
        val width = snapshot.surfaceWidth.takeIf { it.isFinite() && it > 0.0 } ?: Gdx.graphics.width.toDouble()
        val height = snapshot.surfaceHeight.takeIf { it.isFinite() && it > 0.0 } ?: Gdx.graphics.height.toDouble()
        camera.setToOrtho(true, width.toFloat(), height.toFloat())
        camera.update()
        val projection: Matrix4 = camera.combined
        shapeRenderer.projectionMatrix = projection
        spriteBatch.projectionMatrix = projection
    }

    private fun clear(color: RenderColor) {
        Gdx.gl.glClearColor(color.red, color.green, color.blue, color.alpha)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_STENCIL_BUFFER_BIT)
    }

    private fun drawSnapshot(snapshot: DynamicMapRenderSnapshot) {
        val dynamicMap = snapshot.dynamicMap
        if (dynamicMap != null) {
            drawDynamicMapSurface(snapshot, dynamicMap)
            drawDynamicMapBackground(snapshot, dynamicMap)
            if (snapshot.dynamicMapRenderMode == DynamicMapRenderMode.DEBUG) {
                drawDynamicLightHalos(snapshot, dynamicMap)
            }
        }

        drawGrid(snapshot)
        if (snapshot.dynamicMapRenderMode == DynamicMapRenderMode.DEBUG && dynamicMap != null) {
            drawDynamicWalls(snapshot, dynamicMap)
        }
        drawFogOfWar(snapshot)
        drawTokens(snapshot)
        drawSightlineLayer(snapshot)
        drawMeasurements(snapshot)
        if (snapshot.dynamicMapRenderMode == DynamicMapRenderMode.DEBUG && dynamicMap != null) {
            drawDynamicLightMarkers(snapshot, dynamicMap)
        }
        drawTableViewportOutline(snapshot)
    }

    private fun drawDynamicMapSurface(snapshot: DynamicMapRenderSnapshot, dynamicMap: RenderDynamicMap) {
        val cellPx = snapshot.gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val origin = dynamicMapOrigin(snapshot)
        val rect = worldRect(snapshot, origin.x, origin.y, dynamicMap.cols * cellPx, dynamicMap.rows * cellPx)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = snapshot.backgroundColor.dimmed(0.85f).toGdxColor()
        shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height)
        shapeRenderer.end()
    }

    private fun drawDynamicMapBackground(snapshot: DynamicMapRenderSnapshot, dynamicMap: RenderDynamicMap) {
        val texture = backgroundTexture ?: return
        val region = backgroundRegion ?: return
        val cellPx = snapshot.gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return

        val calibration = dynamicMap.backgroundCalibration
        val width = texture.width * calibration.scale * cellPx
        val height = texture.height * calibration.scale * cellPx
        if (!width.isFinite() || !height.isFinite() || width <= 0.0 || height <= 0.0) return

        val origin = dynamicMapOrigin(snapshot)
        val centerX = origin.x + dynamicMap.cols * cellPx / 2.0
        val centerY = origin.y + dynamicMap.rows * cellPx / 2.0
        val rect = worldRect(
            snapshot,
            centerX - width / 2.0 + calibration.offsetX * cellPx,
            centerY - height / 2.0 + calibration.offsetY * cellPx,
            width,
            height,
        )

        spriteBatch.begin()
        spriteBatch.draw(region, rect.x, rect.y, rect.width, rect.height)
        spriteBatch.end()
    }

    private fun drawDynamicLightHalos(snapshot: DynamicMapRenderSnapshot, dynamicMap: RenderDynamicMap) {
        if (dynamicMap.lights.isEmpty()) return
        val cellPx = snapshot.gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val origin = dynamicMapOrigin(snapshot)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        dynamicMap.lights.filter { it.enabled }.forEach { light ->
            val center = worldPoint(
                snapshot,
                origin.x + light.position.x * cellPx,
                origin.y + light.position.y * cellPx,
            )
            val color = parseHexColor(light.colorHex)
            val scale = snapshot.viewportScale.toFloat()
            val dimRadius = (light.dimRadius * cellPx * scale).toFloat()
            val brightRadius = (light.brightRadius * cellPx * scale).toFloat()
            if (dimRadius > 0f) {
                shapeRenderer.color = color.withAlpha(DYNAMIC_LIGHT_DIM_OPACITY).toGdxColor()
                shapeRenderer.circle(center.x, center.y, dimRadius, circleSegments(dimRadius))
            }
            if (brightRadius > 0f) {
                shapeRenderer.color = color.withAlpha(DYNAMIC_LIGHT_BRIGHT_OPACITY).toGdxColor()
                shapeRenderer.circle(center.x, center.y, brightRadius, circleSegments(brightRadius))
            }
        }
        shapeRenderer.end()
    }

    private fun drawDynamicWalls(snapshot: DynamicMapRenderSnapshot, dynamicMap: RenderDynamicMap) {
        if (dynamicMap.walls.isEmpty()) return
        val cellPx = snapshot.gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val origin = dynamicMapOrigin(snapshot)
        val lineWidth = (cellPx * DYNAMIC_WALL_WIDTH_SCALE * snapshot.viewportScale).coerceAtLeast(2.0).toFloat()

        Gdx.gl.glLineWidth(lineWidth)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = snapshot.dynamicDebugWallColor.toGdxColor()
        dynamicMap.walls.forEach { wall ->
            val start = worldPoint(snapshot, origin.x + wall.start.x * cellPx, origin.y + wall.start.y * cellPx)
            val end = worldPoint(snapshot, origin.x + wall.end.x * cellPx, origin.y + wall.end.y * cellPx)
            shapeRenderer.line(start.x, start.y, end.x, end.y)
        }
        shapeRenderer.end()
        Gdx.gl.glLineWidth(1f)
    }

    private fun drawDynamicLightMarkers(snapshot: DynamicMapRenderSnapshot, dynamicMap: RenderDynamicMap) {
        if (!snapshot.showDynamicLightMarkers || dynamicMap.lights.isEmpty()) return
        val cellPx = snapshot.gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val origin = dynamicMapOrigin(snapshot)
        val radius = (cellPx * DYNAMIC_LIGHT_MARKER_SCALE * snapshot.viewportScale).coerceAtLeast(4.0).toFloat()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        dynamicMap.lights.forEach { light ->
            val center = worldPoint(snapshot, origin.x + light.position.x * cellPx, origin.y + light.position.y * cellPx)
            val color = parseHexColor(light.colorHex)
            shapeRenderer.color = if (light.enabled) {
                color.toGdxColor()
            } else {
                color.withAlpha(0.45f).dimmed(0.35f).toGdxColor()
            }
            shapeRenderer.circle(center.x, center.y, radius, circleSegments(radius))
        }
        shapeRenderer.end()

        Gdx.gl.glLineWidth(1f)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = snapshot.dynamicDebugWallColor.toGdxColor()
        dynamicMap.lights.forEach { light ->
            val center = worldPoint(snapshot, origin.x + light.position.x * cellPx, origin.y + light.position.y * cellPx)
            shapeRenderer.circle(center.x, center.y, radius, circleSegments(radius))
        }
        shapeRenderer.end()
    }

    private fun drawGrid(snapshot: DynamicMapRenderSnapshot) {
        val grid = snapshot.grid ?: return
        val cellPx = snapshot.gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return

        val bounds = visibleWorldBounds(snapshot)
        val originX = snapshot.surfaceWidth / 2.0 + snapshot.gridCalibration.offsetX
        val originY = snapshot.surfaceHeight / 2.0 + snapshot.gridCalibration.offsetY
        var x = originX + ceil((bounds.xMin - originX) / cellPx) * cellPx
        var y = originY + ceil((bounds.yMin - originY) / cellPx) * cellPx

        Gdx.gl.glLineWidth((grid.lineWidth * snapshot.viewportScale).coerceAtLeast(1.0).toFloat())
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = grid.color.toGdxColor()
        while (x <= bounds.xMax) {
            val start = worldPoint(snapshot, x, bounds.yMin)
            val end = worldPoint(snapshot, x, bounds.yMax)
            shapeRenderer.line(start.x, start.y, end.x, end.y)
            x += cellPx
        }
        while (y <= bounds.yMax) {
            val start = worldPoint(snapshot, bounds.xMin, y)
            val end = worldPoint(snapshot, bounds.xMax, y)
            shapeRenderer.line(start.x, start.y, end.x, end.y)
            y += cellPx
        }
        shapeRenderer.end()
        Gdx.gl.glLineWidth(1f)
    }

    private fun drawFogOfWar(snapshot: DynamicMapRenderSnapshot) {
        val fog = snapshot.fogOfWar ?: return
        val cellPx = snapshot.gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val originX = snapshot.surfaceWidth / 2.0 + snapshot.gridCalibration.offsetX
        val originY = snapshot.surfaceHeight / 2.0 + snapshot.gridCalibration.offsetY
        val bounds = visibleWorldBounds(snapshot)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = RenderColor(0f, 0f, 0f, snapshot.fogOpacity.toFloat().coerceIn(0f, 1f)).toGdxColor()
        for (col in 0 until fog.cols) {
            val cellX = originX + (col + fog.colOffset) * cellPx
            if (cellX + cellPx <= bounds.xMin || cellX >= bounds.xMax) continue
            for (row in 0 until fog.rows) {
                if (fog.isRevealed(col, row)) continue
                val cellY = originY + (row + fog.rowOffset) * cellPx
                if (cellY + cellPx <= bounds.yMin || cellY >= bounds.yMax) continue
                val rect = worldRect(snapshot, cellX, cellY, cellPx, cellPx)
                shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height)
            }
        }
        shapeRenderer.end()
    }

    private fun drawTokens(snapshot: DynamicMapRenderSnapshot) {
        if (snapshot.tokens.isEmpty()) return
        val cellPx = snapshot.gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val originX = snapshot.surfaceWidth / 2.0 + snapshot.gridCalibration.offsetX
        val originY = snapshot.surfaceHeight / 2.0 + snapshot.gridCalibration.offsetY

        for (token in tokensInDrawOrder(snapshot.tokens)) {
            if (snapshot.hideTokensInFog && !token.visibleInSightline) continue
            val visibleCells = if (snapshot.hideTokensInFog) {
                token.occupiedCells.filter { isFogVisibleCell(snapshot, it.col, it.row) }
            } else {
                token.occupiedCells
            }
            if (visibleCells.isEmpty()) continue

            val bounds = tokenBounds(token, originX, originY, cellPx)
            val rect = worldRect(snapshot, bounds.x, bounds.y, bounds.size, bounds.size)
            val texture = token.imagePath?.let { tokenTextures[it] }
            val region = token.imagePath?.let { tokenRegions[it] }

            if (texture != null && region != null) {
                val drawW = (bounds.size * token.imageScaleX * snapshot.viewportScale).toFloat()
                val drawH = (bounds.size * token.imageScaleY * snapshot.viewportScale).toFloat()
                val center = worldPoint(snapshot, bounds.x + bounds.size / 2.0, bounds.y + bounds.size / 2.0)
                val offsetX = (token.imageOffsetX * snapshot.viewportScale).toFloat()
                val offsetY = (token.imageOffsetY * snapshot.viewportScale).toFloat()
                drawClippedTokenTexture(
                    region = region,
                    center = center,
                    radius = rect.width / 2f,
                    drawX = center.x - drawW / 2f + offsetX,
                    drawY = center.y - drawH / 2f + offsetY,
                    drawW = drawW,
                    drawH = drawH,
                )
            } else {
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                shapeRenderer.color = token.color.toGdxColor()
                shapeRenderer.circle(rect.centerX, rect.centerY, rect.width / 2f, circleSegments(rect.width / 2f))
                shapeRenderer.end()
            }

            if (token.id == snapshot.activeTokenId) {
                Gdx.gl.glLineWidth((rect.width * ACTIVE_TOKEN_OUTLINE_WIDTH_SCALE).coerceAtLeast(1f))
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                shapeRenderer.color = RenderColor.Orange.toGdxColor()
                shapeRenderer.circle(rect.centerX, rect.centerY, rect.width / 2f, circleSegments(rect.width / 2f))
                shapeRenderer.end()
            }

            Gdx.gl.glLineWidth((rect.width * TOKEN_OUTLINE_WIDTH_SCALE).coerceAtLeast(1f))
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.color = token.color.toGdxColor()
            shapeRenderer.circle(rect.centerX, rect.centerY, rect.width / 2f, circleSegments(rect.width / 2f))
            shapeRenderer.end()
            Gdx.gl.glLineWidth(1f)

            if (snapshot.showTokenNames && token.name.isNotBlank()) {
                drawLabel(
                    snapshot = snapshot,
                    text = token.name,
                    worldX = bounds.x + bounds.size / 2.0,
                    worldY = bounds.y + bounds.size + cellPx * token.size.footprintTiles * TOKEN_NAME_FONT_SCALE,
                    center = true,
                    fontSize = (cellPx * token.size.footprintTiles * TOKEN_NAME_FONT_SCALE).coerceAtLeast(MIN_TOKEN_NAME_FONT_SIZE),
                )
            }
        }
    }

    private fun drawClippedTokenTexture(
        region: TextureRegion,
        center: RenderPointF,
        radius: Float,
        drawX: Float,
        drawY: Float,
        drawW: Float,
        drawH: Float,
    ) {
        Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_STENCIL_TEST)
        Gdx.gl.glColorMask(false, false, false, false)
        Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xFF)
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = RenderColor.White.toGdxColor()
        shapeRenderer.circle(center.x, center.y, radius, circleSegments(radius))
        shapeRenderer.end()

        Gdx.gl.glColorMask(true, true, true, true)
        Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 1, 0xFF)
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP)

        spriteBatch.begin()
        spriteBatch.draw(region, drawX, drawY, drawW, drawH)
        spriteBatch.end()

        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST)
    }

    private fun drawSightlineLayer(snapshot: DynamicMapRenderSnapshot) {
        val dynamicMap = snapshot.dynamicMap ?: return
        val sightline = snapshot.sightline ?: return
        val cellPx = snapshot.gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return

        val origin = dynamicMapOrigin(snapshot)
        Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_STENCIL_TEST)
        Gdx.gl.glColorMask(false, false, false, false)
        Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xFF)
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = RenderColor.White.toGdxColor()
        sightline.triangles.forEach { triangle ->
            val a = worldPoint(snapshot, origin.x + triangle.origin.x * cellPx, origin.y + triangle.origin.y * cellPx)
            val b = worldPoint(snapshot, origin.x + triangle.first.x * cellPx, origin.y + triangle.first.y * cellPx)
            val c = worldPoint(snapshot, origin.x + triangle.second.x * cellPx, origin.y + triangle.second.y * cellPx)
            shapeRenderer.triangle(a.x, a.y, b.x, b.y, c.x, c.y)
        }
        shapeRenderer.end()

        Gdx.gl.glColorMask(true, true, true, true)
        Gdx.gl.glStencilFunc(GL20.GL_NOTEQUAL, 1, 0xFF)
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP)

        val rect = worldRect(snapshot, origin.x, origin.y, dynamicMap.cols * cellPx, dynamicMap.rows * cellPx)
        val opacity = snapshot.sightlineOpacity.toFloat().coerceIn(0f, 1f)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = snapshot.sightlineTint.withAlpha(opacity).toGdxColor()
        shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height)
        shapeRenderer.end()

        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST)
    }

    private fun drawMeasurements(snapshot: DynamicMapRenderSnapshot) {
        if (snapshot.measurements.isEmpty()) return
        val cellPx = snapshot.gridCalibration.effectiveCellSizeInPixels()
        if (cellPx <= 0.0) return
        val originX = snapshot.surfaceWidth / 2.0 + snapshot.gridCalibration.offsetX
        val originY = snapshot.surfaceHeight / 2.0 + snapshot.gridCalibration.offsetY

        snapshot.measurements.forEach { measurement ->
            if (!measurement.mirroredToTable && !snapshot.showDmOnlyMeasurements) return@forEach
            val sx = originX + (measurement.startCol + 0.5) * cellPx
            val sy = originY + (measurement.startRow + 0.5) * cellPx
            val ex = originX + (measurement.endCol + 0.5) * cellPx
            val ey = originY + (measurement.endRow + 0.5) * cellPx
            val start = worldPoint(snapshot, sx, sy)
            val end = worldPoint(snapshot, ex, ey)
            val lineWidth = (cellPx * 0.07 * snapshot.viewportScale).coerceIn(2.0, 5.0).toFloat()

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = measurement.color.withAlpha(MEASUREMENT_FILL_OPACITY).toGdxColor()
            when (measurement.type) {
                MeasurementType.RECTANGLE -> {
                    val minX = minOf(sx, ex) - cellPx / 2.0
                    val minY = minOf(sy, ey) - cellPx / 2.0
                    val width = (abs(ex - sx) + cellPx).coerceAtLeast(cellPx)
                    val height = (abs(ey - sy) + cellPx).coerceAtLeast(cellPx)
                    val rect = worldRect(snapshot, minX, minY, width, height)
                    shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height)
                }
                MeasurementType.CIRCLE -> {
                    val radius = (hypot(ex - sx, ey - sy).coerceAtLeast(cellPx * 0.25) * snapshot.viewportScale).toFloat()
                    shapeRenderer.circle(start.x, start.y, radius, circleSegments(radius))
                }
                MeasurementType.CONE -> fillCone(snapshot, sx, sy, ex, ey, measurement)
                MeasurementType.LINE -> Unit
            }
            shapeRenderer.end()

            Gdx.gl.glLineWidth(lineWidth)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.color = measurement.color.toGdxColor()
            when (measurement.type) {
                MeasurementType.LINE -> shapeRenderer.line(start.x, start.y, end.x, end.y)
                MeasurementType.RECTANGLE -> {
                    val minX = minOf(sx, ex) - cellPx / 2.0
                    val minY = minOf(sy, ey) - cellPx / 2.0
                    val width = (abs(ex - sx) + cellPx).coerceAtLeast(cellPx)
                    val height = (abs(ey - sy) + cellPx).coerceAtLeast(cellPx)
                    val rect = worldRect(snapshot, minX, minY, width, height)
                    shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height)
                }
                MeasurementType.CIRCLE -> {
                    val radius = (hypot(ex - sx, ey - sy).coerceAtLeast(cellPx * 0.25) * snapshot.viewportScale).toFloat()
                    shapeRenderer.circle(start.x, start.y, radius, circleSegments(radius))
                }
                MeasurementType.CONE -> strokeCone(snapshot, sx, sy, ex, ey, measurement)
            }
            shapeRenderer.end()
            Gdx.gl.glLineWidth(1f)

            drawLabel(
                snapshot = snapshot,
                text = measurement.dimensionText,
                worldX = (sx + ex) / 2.0 + 8.0,
                worldY = (sy + ey) / 2.0 - 8.0,
                center = false,
                fontSize = (cellPx * 0.25).coerceAtLeast(11.0),
            )
        }
    }

    private fun drawTableViewportOutline(snapshot: DynamicMapRenderSnapshot) {
        if (!snapshot.showTableViewportOutline) return
        val tableWidth = snapshot.tableViewportWidth
        val tableHeight = snapshot.tableViewportHeight
        if (!tableWidth.isFinite() || !tableHeight.isFinite() || tableWidth <= 0.0 || tableHeight <= 0.0) return

        val xMin = -tableWidth / 2.0 - snapshot.tableMapOffset.offsetX
        val xMax = tableWidth / 2.0 - snapshot.tableMapOffset.offsetX
        val yMin = -tableHeight / 2.0 - snapshot.tableMapOffset.offsetY
        val yMax = tableHeight / 2.0 - snapshot.tableMapOffset.offsetY
        val topLeft = sceneToCanvas(snapshot, xMin, yMin)
        val bottomRight = sceneToCanvas(snapshot, xMax, yMax)
        val x = minOf(topLeft.x, bottomRight.x)
        val y = minOf(topLeft.y, bottomRight.y)
        val width = abs(bottomRight.x - topLeft.x)
        val height = abs(bottomRight.y - topLeft.y)
        if (width <= 0f || height <= 0f) return

        Gdx.gl.glLineWidth(TABLE_VIEWPORT_OUTLINE_LINE_WIDTH)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = snapshot.tableViewportOutlineColor.toGdxColor()
        shapeRenderer.rect(x, y, width, height)
        shapeRenderer.end()
        Gdx.gl.glLineWidth(1f)
    }

    private fun fillCone(
        snapshot: DynamicMapRenderSnapshot,
        sx: Double,
        sy: Double,
        ex: Double,
        ey: Double,
        measurement: RenderMeasurement,
    ) {
        val radius = hypot(ex - sx, ey - sy).coerceAtLeast(snapshot.gridCalibration.effectiveCellSizeInPixels() * 0.25)
        val direction = atan2(ey - sy, ex - sx)
        val half = Math.toRadians(measurement.coneAngleDegrees / 2.0)
        val segments = ((measurement.coneAngleDegrees / 8.0).roundToInt()).coerceIn(6, 48)
        val origin = worldPoint(snapshot, sx, sy)
        var previous = worldPoint(snapshot, sx + radius * cos(direction - half), sy + radius * sin(direction - half))
        for (i in 1..segments) {
            val t = i / segments.toDouble()
            val angle = direction - half + Math.toRadians(measurement.coneAngleDegrees) * t
            val next = worldPoint(snapshot, sx + radius * cos(angle), sy + radius * sin(angle))
            shapeRenderer.triangle(origin.x, origin.y, previous.x, previous.y, next.x, next.y)
            previous = next
        }
    }

    private fun strokeCone(
        snapshot: DynamicMapRenderSnapshot,
        sx: Double,
        sy: Double,
        ex: Double,
        ey: Double,
        measurement: RenderMeasurement,
    ) {
        val radius = hypot(ex - sx, ey - sy).coerceAtLeast(snapshot.gridCalibration.effectiveCellSizeInPixels() * 0.25)
        val direction = atan2(ey - sy, ex - sx)
        val half = Math.toRadians(measurement.coneAngleDegrees / 2.0)
        val start = worldPoint(snapshot, sx + radius * cos(direction - half), sy + radius * sin(direction - half))
        val end = worldPoint(snapshot, sx + radius * cos(direction + half), sy + radius * sin(direction + half))
        val origin = worldPoint(snapshot, sx, sy)
        shapeRenderer.line(origin.x, origin.y, start.x, start.y)
        shapeRenderer.line(origin.x, origin.y, end.x, end.y)

        val segments = ((measurement.coneAngleDegrees / 8.0).roundToInt()).coerceIn(6, 48)
        var previous = start
        for (i in 1..segments) {
            val t = i / segments.toDouble()
            val angle = direction - half + Math.toRadians(measurement.coneAngleDegrees) * t
            val next = worldPoint(snapshot, sx + radius * cos(angle), sy + radius * sin(angle))
            shapeRenderer.line(previous.x, previous.y, next.x, next.y)
            previous = next
        }
    }

    private fun drawLabel(
        snapshot: DynamicMapRenderSnapshot,
        text: String,
        worldX: Double,
        worldY: Double,
        center: Boolean,
        fontSize: Double,
    ) {
        val point = worldPoint(snapshot, worldX, worldY)
        val scale = ((fontSize * snapshot.viewportScale) / DEFAULT_BITMAP_FONT_SIZE).toFloat().coerceAtLeast(0.3f)
        font.data.setScale(scale)
        glyphLayout.setText(font, text)
        val x = if (center) point.x - glyphLayout.width / 2f else point.x
        val y = point.y + glyphLayout.height

        spriteBatch.begin()
        font.color = RenderColor.Black.toGdxColor()
        font.draw(spriteBatch, text, x + TOKEN_NAME_SHADOW_OFFSET, y + TOKEN_NAME_SHADOW_OFFSET)
        font.color = RenderColor.White.toGdxColor()
        font.draw(spriteBatch, text, x, y)
        spriteBatch.end()
    }

    private fun refreshBackgroundTexture(dynamicMap: RenderDynamicMap?) {
        val bytes = dynamicMap?.backgroundBytes
        val key = bytes?.contentHashCode()
        if (key == backgroundTextureKey) return
        backgroundTextureKey = key
        backgroundTexture?.dispose()
        backgroundTexture = null
        backgroundRegion = null
        if (bytes == null) return

        runCatching {
            val pixmap = Pixmap(bytes, 0, bytes.size)
            val texture = Texture(pixmap)
            pixmap.dispose()
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            backgroundTexture = texture
            backgroundRegion = TextureRegion(texture).apply { flip(false, true) }
        }
    }

    private fun refreshTokenTextures(tokens: List<RenderToken>) {
        val wantedPaths = tokens.mapNotNull { it.imagePath }.toSet()
        val removePaths = tokenTextures.keys - wantedPaths
        removePaths.forEach { path ->
            tokenTextures.remove(path)?.dispose()
            tokenRegions.remove(path)
        }
        wantedPaths.forEach { path ->
            if (tokenTextures.containsKey(path)) return@forEach
            runCatching {
                val texture = Texture(Gdx.files.absolute(path))
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                tokenTextures[path] = texture
                tokenRegions[path] = TextureRegion(texture).apply { flip(false, true) }
            }
        }
    }

    private fun dynamicMapOrigin(snapshot: DynamicMapRenderSnapshot): RenderPoint =
        RenderPoint(
            x = snapshot.surfaceWidth / 2.0 + snapshot.gridCalibration.offsetX,
            y = snapshot.surfaceHeight / 2.0 + snapshot.gridCalibration.offsetY,
        )

    private fun visibleWorldBounds(snapshot: DynamicMapRenderSnapshot): WorldBounds {
        val width = snapshot.surfaceWidth
        val height = snapshot.surfaceHeight
        val cx = width / 2.0
        val cy = height / 2.0
        val sceneOffsetX = if (snapshot.applyTableMapOffset) snapshot.tableMapOffset.offsetX else 0.0
        val sceneOffsetY = if (snapshot.applyTableMapOffset) snapshot.tableMapOffset.offsetY else 0.0
        return WorldBounds(
            xMin = (0.0 - cx - snapshot.viewportOffsetX) / snapshot.viewportScale + cx - sceneOffsetX,
            xMax = (width - cx - snapshot.viewportOffsetX) / snapshot.viewportScale + cx - sceneOffsetX,
            yMin = (0.0 - cy - snapshot.viewportOffsetY) / snapshot.viewportScale + cy - sceneOffsetY,
            yMax = (height - cy - snapshot.viewportOffsetY) / snapshot.viewportScale + cy - sceneOffsetY,
        )
    }

    private fun worldPoint(snapshot: DynamicMapRenderSnapshot, x: Double, y: Double): RenderPointF {
        val cx = snapshot.surfaceWidth / 2.0
        val cy = snapshot.surfaceHeight / 2.0
        val sceneOffsetX = if (snapshot.applyTableMapOffset) snapshot.tableMapOffset.offsetX else 0.0
        val sceneOffsetY = if (snapshot.applyTableMapOffset) snapshot.tableMapOffset.offsetY else 0.0
        return RenderPointF(
            x = (cx + snapshot.viewportOffsetX + snapshot.viewportScale * (x + sceneOffsetX - cx)).toFloat(),
            y = (cy + snapshot.viewportOffsetY + snapshot.viewportScale * (y + sceneOffsetY - cy)).toFloat(),
        )
    }

    private fun sceneToCanvas(snapshot: DynamicMapRenderSnapshot, x: Double, y: Double): RenderPointF {
        val cx = snapshot.surfaceWidth / 2.0
        val cy = snapshot.surfaceHeight / 2.0
        val sceneOffsetX = if (snapshot.applyTableMapOffset) snapshot.tableMapOffset.offsetX else 0.0
        val sceneOffsetY = if (snapshot.applyTableMapOffset) snapshot.tableMapOffset.offsetY else 0.0
        return RenderPointF(
            x = (cx + snapshot.viewportOffsetX + snapshot.viewportScale * (x + sceneOffsetX)).toFloat(),
            y = (cy + snapshot.viewportOffsetY + snapshot.viewportScale * (y + sceneOffsetY)).toFloat(),
        )
    }

    private fun worldRect(
        snapshot: DynamicMapRenderSnapshot,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
    ): RenderRectF {
        val topLeft = worldPoint(snapshot, x, y)
        val scaledWidth = (width * snapshot.viewportScale).toFloat()
        val scaledHeight = (height * snapshot.viewportScale).toFloat()
        return RenderRectF(topLeft.x, topLeft.y, scaledWidth, scaledHeight)
    }

    private fun tokenBounds(token: RenderToken, originX: Double, originY: Double, cellPx: Double): RenderTokenBounds {
        val anchorTiles = token.size.gridSpanCells.toDouble()
        val drawTiles = token.size.footprintTiles * TOKEN_DRAW_FOOTPRINT_SCALE
        val insetTiles = (anchorTiles - drawTiles) / 2.0
        val sizePx = cellPx * drawTiles
        return RenderTokenBounds(
            x = originX + (token.col + insetTiles) * cellPx,
            y = originY + (token.row + insetTiles) * cellPx,
            size = sizePx,
        )
    }

    private fun isFogVisibleCell(snapshot: DynamicMapRenderSnapshot, col: Int, row: Int): Boolean {
        val fog = snapshot.fogOfWar ?: return true
        val fogCol = col - fog.colOffset
        val fogRow = row - fog.rowOffset
        if (fogCol !in 0 until fog.cols || fogRow !in 0 until fog.rows) return true
        return fog.isRevealed(fogCol, fogRow)
    }

    private fun tokensInDrawOrder(tokens: List<RenderToken>): List<RenderToken> =
        tokens.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<RenderToken>> { it.value.size.footprintTiles }
                    .thenBy { it.index },
            )
            .map { it.value }

    private fun circleSegments(radius: Float): Int =
        ((radius * PI / 4.0).roundToInt()).coerceIn(20, 96)

    private data class WorldBounds(
        val xMin: Double,
        val xMax: Double,
        val yMin: Double,
        val yMax: Double,
    )

    private data class RenderPointF(
        val x: Float,
        val y: Float,
    )

    private data class RenderRectF(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    ) {
        val centerX: Float
            get() = x + width / 2f
        val centerY: Float
            get() = y + height / 2f
    }

    private data class RenderTokenBounds(
        val x: Double,
        val y: Double,
        val size: Double,
    )

    private companion object {
        private const val TOKEN_DRAW_FOOTPRINT_SCALE = 0.9
        private const val TOKEN_NAME_FONT_SCALE = 0.28
        private const val MIN_TOKEN_NAME_FONT_SIZE = 8.0
        private const val TOKEN_NAME_SHADOW_OFFSET = 1f
        private const val ACTIVE_TOKEN_OUTLINE_WIDTH_SCALE = 0.10f
        private const val TOKEN_OUTLINE_WIDTH_SCALE = 0.025f
        private const val MEASUREMENT_FILL_OPACITY = 0.18f
        private const val DEFAULT_BITMAP_FONT_SIZE = 15.0
        private const val TABLE_VIEWPORT_OUTLINE_LINE_WIDTH = 1.5f
        private const val DYNAMIC_LIGHT_DIM_OPACITY = 0.14f
        private const val DYNAMIC_LIGHT_BRIGHT_OPACITY = 0.28f
        private const val DYNAMIC_WALL_WIDTH_SCALE = 0.12
        private const val DYNAMIC_LIGHT_MARKER_SCALE = 0.18
    }
}

private fun RenderColor.toGdxColor(): com.badlogic.gdx.graphics.Color =
    com.badlogic.gdx.graphics.Color(red, green, blue, alpha)

private fun RenderColor.withAlpha(alpha: Float): RenderColor =
    copy(alpha = alpha.coerceIn(0f, 1f))

private fun RenderColor.dimmed(factor: Float): RenderColor =
    copy(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
    )

private fun parseHexColor(value: String): RenderColor {
    val trimmed = value.trim().removePrefix("#")
    if (trimmed.length != 6 && trimmed.length != 8) return RenderColor.White
    val parsed = trimmed.toLongOrNull(16) ?: return RenderColor.White
    val hasAlpha = trimmed.length == 8
    val alpha = if (hasAlpha) ((parsed shr 24) and 0xFF) / 255f else 1f
    val redShift = if (hasAlpha) 16 else 16
    val greenShift = if (hasAlpha) 8 else 8
    return RenderColor(
        red = ((parsed shr redShift) and 0xFF) / 255f,
        green = ((parsed shr greenShift) and 0xFF) / 255f,
        blue = (parsed and 0xFF) / 255f,
        alpha = alpha,
    )
}
