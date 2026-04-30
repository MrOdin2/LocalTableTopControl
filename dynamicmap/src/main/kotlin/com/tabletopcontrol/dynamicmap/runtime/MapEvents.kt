package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicLightMask
import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicSightlineMesh
import com.tabletopcontrol.dynamicmap.runtime.logic.GridCalibration
import com.tabletopcontrol.dynamicmap.runtime.logic.GridConfig
import com.tabletopcontrol.dynamicmap.runtime.logic.MapCalibration
import com.tabletopcontrol.dynamicmap.runtime.logic.TableMapOffset
import javafx.scene.paint.Color

/**
 * Event fired when the DM loads a new map image.
 *
 * @property resourcePath file-system path or classpath URI of the image to load.
 */
data class MapLoadEvent(val resourcePath: String)

/**
 * Event fired when the DM loads a Dynamic Map gameplay bundle.
 *
 * The bundle is zip-backed and may include an embedded background texture plus
 * runtime wall and light geometry exported by the Dynamic Map Builder.
 */
data class DynamicMapLoadEvent(val bundle: DynamicMapBundle)

/**
 * Controls how builder-exported DynamicMap wall and light data is visualised.
 */
enum class DynamicMapRenderMode(val displayName: String) {
    RENDER("RenderMode"),
    DEBUG("DebugMode"),
    ;

    companion object {
        fun fromPersisted(value: String?): DynamicMapRenderMode? = when (value?.trim()?.uppercase()) {
            "RENDER", "RENDERMODE" -> RENDER
            "DEBUG", "DEBUGMODE" -> DEBUG
            else -> null
        }
    }
}

/**
 * Event fired when the DM changes DynamicMap render/debug visualisation.
 */
data class DynamicMapRenderModeEvent(val mode: DynamicMapRenderMode)

/**
 * Internal renderer event carrying the latest shared DynamicMap player-visible mesh.
 *
 * The mesh, optional light mask, and optional darkvision-only mesh are computed once by
 * [com.tabletopcontrol.dynamicmap.runtime.logic.MapDynamicSightlineService] and consumed by every
 * active view renderer. `mesh == null` clears DynamicMap sightline state.
 */
internal data class DynamicSightlineMeshUpdatedEvent(
    val revision: Long,
    val mesh: DynamicSightlineMesh?,
    val seenMesh: DynamicSightlineMesh? = null,
    val lightMask: DynamicLightMask? = null,
    val darkvisionMesh: DynamicSightlineMesh? = null,
)

/**
 * Event fired when the DM removes the current map image.
 *
 * Renderers should clear any previously loaded image and show only the plain
 * background colour, grid, fog, tokens, and measurements.
 */
data object MapClearEvent

/**
 * Event fired when the DM changes the grid visibility or configuration.
 *
 * @property config the new [com.tabletopcontrol.dynamicmap.runtime.logic.GridConfig] to apply; `null` to hide the grid.
 */
data class GridUpdateEvent(val config: GridConfig?)

/**
 * Event fired when a single fog-of-war cell is toggled.
 *
 * @property col      zero-based column index of the cell.
 * @property row      zero-based row index of the cell.
 * @property revealed `true` to reveal the cell; `false` to hide it.
 */
data class FogOfWarCellEvent(val col: Int, val row: Int, val revealed: Boolean)

/**
 * Event fired when the DM resets the entire fog-of-war layer at once.
 *
 * @property revealAll `true` to reveal every cell; `false` to hide every cell.
 */
data class FogOfWarResetEvent(val revealAll: Boolean)

/**
 * Event fired when the DM applies new map-image calibration.
 *
 * @property calibration the new [com.tabletopcontrol.dynamicmap.runtime.logic.MapCalibration] to apply.
 */
data class MapCalibrationEvent(val calibration: MapCalibration)

/**
 * Event fired when the DM repositions the entire rendered table map.
 *
 * The offset applies to the whole scene together: map image, grid, fog,
 * tokens, and measurements.
 *
 * @property offset the shared table-map offset in canvas pixels.
 */
data class TableMapOffsetEvent(val offset: TableMapOffset)

/**
 * Event fired when the player-facing table canvas changes size.
 *
 * The DM minimap uses this to draw a preview outline of the portion of the
 * map currently visible on the table screen.
 *
 * @property width  table canvas width in pixels.
 * @property height table canvas height in pixels.
 */
data class TableViewportChangedEvent(val width: Double, val height: Double)

/**
 * Event fired when the DM applies new grid calibration.
 *
 * @property calibration the new [com.tabletopcontrol.dynamicmap.runtime.logic.GridCalibration] to apply.
 */
data class GridCalibrationEvent(val calibration: GridCalibration)

/**
 * Event fired to enable or disable the grid calibration overlay.
 *
 * When [active] is `true` the renderer draws a crosshair at the canvas centre
 * so the DM can align the grid origin.  Set to `false` when the calibration
 * dialog is closed.
 *
 * @property active `true` to show the crosshair; `false` to hide it.
 */
data class GridCalibrationModeEvent(val active: Boolean)

/**
 * Event fired to enable or disable the map calibration overlay.
 *
 * When [active] is `true` the renderer draws a red dot at the canvas centre
 * so the DM can align a reference point on the map image.  Set to `false`
 * when the calibration dialog is closed.
 *
 * @property active `true` to show the centre dot; `false` to hide it.
 */
data class MapCalibrationModeEvent(val active: Boolean)

/**
 * Event fired to initialise or reinitialise the fog-of-war grid.
 *
 * Both the table-view renderer and the DM-panel minimap renderer subscribe to
 * this event and create fresh, independent [com.tabletopcontrol.dynamicmap.runtime.logic.FogOfWarState] instances with the
 * given dimensions.  All prior cell state is discarded.
 *
 * Fog array cell `(0, 0)` corresponds to grid position `(colOffset, rowOffset)`,
 * so negative offsets centre the grid coverage around the grid origin.
 *
 * @property cols      number of columns in the fog grid; must be positive.
 * @property rows      number of rows in the fog grid; must be positive.
 * @property colOffset grid-column index that maps to fog array column 0.
 *                     Fog cells cover grid columns [colOffset .. colOffset+cols-1].
 * @property rowOffset grid-row index that maps to fog array row 0.
 *                     Fog cells cover grid rows [rowOffset .. rowOffset+rows-1].
 */
data class FogOfWarSetupEvent(val cols: Int, val rows: Int, val colOffset: Int, val rowOffset: Int)

/**
 * Event fired when the DM sets a plain-colour background for the map canvas.
 *
 * The renderer replaces its default black fill with this colour.  When no map
 * image is loaded, this colour is the sole visible background — effectively a
 * "plain colour map".  When an image is loaded, it is drawn on top of this fill.
 *
 * @property color the background fill colour to use.
 */
data class MapBackgroundEvent(val color: Color)

/**
 * Event fired when the DM rotates the map image in 90-degree steps.
 *
 * The renderer applies a clockwise rotation around the canvas centre (the grid
 * origin) so that the grid overlay remains stationary and the map image spins
 * behind it.  Only multiples of 90 degrees are supported; the value is
 * normalised to the range [0, 360).
 *
 * @property degrees clockwise rotation in degrees; must be a multiple of 90.
 */
data class MapRotationEvent(val degrees: Int)

/**
 * Event fired when the DM toggles the token-name overlay on the table view.
 *
 * When [show] is `true` the renderer draws each token's display name below its
 * circle so players can identify which token belongs to which combatant.
 *
 * @property show `true` to draw token names; `false` to hide them.
 */
data class ShowTokenNamesEvent(val show: Boolean)

/**
 * Event fired when a new measurement overlay is created on the DM map.
 *
 * @property overlay newly created measurement.
 */
data class MeasurementAddedEvent(val overlay: MeasurementOverlay)

/**
 * Event fired when an existing measurement overlay is updated (resize/move/label/mirror).
 *
 * @property overlay updated measurement.
 */
data class MeasurementUpdatedEvent(val overlay: MeasurementOverlay)

/**
 * Event fired to remove a single measurement overlay by [id].
 */
data class MeasurementRemovedEvent(val id: String)

/**
 * Event fired to clear all active measurement overlays.
 */
data object MeasurementsClearedEvent
