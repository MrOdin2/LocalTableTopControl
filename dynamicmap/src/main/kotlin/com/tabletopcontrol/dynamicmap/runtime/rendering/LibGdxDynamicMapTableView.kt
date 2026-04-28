package com.tabletopcontrol.dynamicmap.runtime.rendering

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.value.ChangeListener
import javafx.geometry.Bounds
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.stage.Window
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class LibGdxWindowState(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val visible: Boolean,
)

/**
 * JavaFX table-view placeholder that owns the real LibGDX/LWJGL3 table window.
 *
 * The node participates in the existing JavaFX table-content selector, but the
 * player-facing pixels are rendered by a borderless native LibGDX window placed
 * over the JavaFX table stage. No player interaction is routed through this
 * surface; DynamicMap state continues to arrive through EventBus and snapshots.
 */
internal class LibGdxDynamicMapTableView(
    private val snapshotProvider: () -> DynamicMapRenderSnapshot,
    private val onViewportChanged: (width: Double, height: Double) -> Unit,
    private val onDispose: () -> Unit,
) : Pane() {
    private val renderer = LibGdxDynamicMapRenderer()
    private var applicationThread: Thread? = null
    private var disposed = false
    private var syncScheduled = false
    private var currentScene: Scene? = null
    private var currentWindow: Window? = null
    private var lastViewportWidth = -1.0
    private var lastViewportHeight = -1.0
    private var lastWindowState: LibGdxWindowState? = null

    private val syncInvalidationListener = InvalidationListener { scheduleSync() }
    private val windowNumberListener = ChangeListener<Number> { _, _, _ -> scheduleSync() }
    private val windowShowingListener = ChangeListener<Boolean> { _, _, _ -> scheduleSync() }
    private val sceneWindowListener = ChangeListener<Window> { _, oldWindow, newWindow ->
        detachWindow(oldWindow)
        attachWindow(newWindow)
        scheduleSync()
    }

    init {
        style = "-fx-background-color: black;"
        minWidth = 0.0
        minHeight = 0.0
        isFocusTraversable = false

        widthProperty().addListener(syncInvalidationListener)
        heightProperty().addListener(syncInvalidationListener)
        visibleProperty().addListener(syncInvalidationListener)
        managedProperty().addListener(syncInvalidationListener)
        boundsInLocalProperty().addListener(syncInvalidationListener)
        localToSceneTransformProperty().addListener(syncInvalidationListener)
        sceneProperty().addListener { _, oldScene, newScene ->
            detachScene(oldScene)
            attachScene(newScene)
            if (newScene == null) {
                dispose()
            } else {
                scheduleSync()
            }
        }
    }

    fun submitCurrentSnapshot() {
        if (disposed) return
        renderer.submitSnapshot(snapshotProvider())
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        detachScene(currentScene)
        renderer.syncWindow(hiddenWindowState())
        renderer.closeWindow()
        onDispose()
    }

    override fun layoutChildren() {
        super.layoutChildren()
        notifyViewportChangedIfNeeded()
        scheduleSync()
    }

    private fun attachScene(scene: Scene?) {
        currentScene = scene
        scene?.windowProperty()?.addListener(sceneWindowListener)
        attachWindow(scene?.window)
    }

    private fun detachScene(scene: Scene?) {
        if (currentScene === scene) {
            currentScene = null
        }
        scene?.windowProperty()?.removeListener(sceneWindowListener)
        detachWindow(scene?.window)
    }

    private fun attachWindow(window: Window?) {
        if (currentWindow === window) return
        currentWindow = window
        window ?: return
        window.xProperty().addListener(windowNumberListener)
        window.yProperty().addListener(windowNumberListener)
        window.widthProperty().addListener(windowNumberListener)
        window.heightProperty().addListener(windowNumberListener)
        window.showingProperty().addListener(windowShowingListener)
    }

    private fun detachWindow(window: Window?) {
        if (currentWindow === window) {
            currentWindow = null
        }
        window ?: return
        window.xProperty().removeListener(windowNumberListener)
        window.yProperty().removeListener(windowNumberListener)
        window.widthProperty().removeListener(windowNumberListener)
        window.heightProperty().removeListener(windowNumberListener)
        window.showingProperty().removeListener(windowShowingListener)
    }

    private fun scheduleSync() {
        if (disposed || syncScheduled) return
        syncScheduled = true
        Platform.runLater {
            syncScheduled = false
            syncWindowNow()
        }
    }

    private fun syncWindowNow() {
        if (disposed) return
        notifyViewportChangedIfNeeded()

        val state = desiredWindowState()
        if (state.visible) {
            ensureLibGdxStarted(state)
            submitCurrentSnapshot()
        }
        if (state != lastWindowState) {
            renderer.syncWindow(state)
            lastWindowState = state
        }
    }

    private fun notifyViewportChangedIfNeeded() {
        if (!width.isFinite() || !height.isFinite() || width <= 0.0 || height <= 0.0) return
        if (abs(width - lastViewportWidth) < VIEWPORT_EPSILON && abs(height - lastViewportHeight) < VIEWPORT_EPSILON) {
            return
        }
        lastViewportWidth = width
        lastViewportHeight = height
        onViewportChanged(width, height)
    }

    private fun ensureLibGdxStarted(initialState: LibGdxWindowState) {
        if (applicationThread != null) return
        renderer.syncWindow(initialState.copy(visible = false))
        applicationThread = Thread(
            {
                try {
                    Lwjgl3Application(
                        renderer,
                        LibGdxDynamicMapHostConfig.create(
                            width = initialState.width,
                            height = initialState.height,
                        ).apply {
                            setWindowPosition(initialState.x, initialState.y)
                        },
                    )
                } finally {
                    Platform.runLater {
                        applicationThread = null
                        lastWindowState = null
                    }
                }
            },
            "DynamicMap-LibGDX-TableView",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun desiredWindowState(): LibGdxWindowState {
        if (!shouldShowNativeWindow()) return hiddenWindowState()
        val bounds = localToScreen(boundsInLocal) ?: return hiddenWindowState()
        return visibleWindowState(bounds)
    }

    private fun shouldShowNativeWindow(): Boolean =
        scene?.window?.isShowing == true &&
            isVisible &&
            isManaged &&
            width.isFinite() &&
            height.isFinite() &&
            width > 0.0 &&
            height > 0.0

    private fun visibleWindowState(bounds: Bounds): LibGdxWindowState =
        LibGdxWindowState(
            x = bounds.minX.roundToInt(),
            y = bounds.minY.roundToInt(),
            width = bounds.width.roundToInt().coerceAtLeast(1),
            height = bounds.height.roundToInt().coerceAtLeast(1),
            visible = true,
        )

    private fun hiddenWindowState(): LibGdxWindowState =
        LibGdxWindowState(
            x = 0,
            y = 0,
            width = 1,
            height = 1,
            visible = false,
        )

    private companion object {
        private const val VIEWPORT_EPSILON = 0.5
    }
}
