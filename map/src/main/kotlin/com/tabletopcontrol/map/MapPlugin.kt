package com.tabletopcontrol.map

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.EventBus
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.VBox
import javafx.stage.FileChooser

/**
 * DM-panel plugin that exposes map-viewer controls.
 *
 * Controls provided:
 * - **Load map** — opens a file chooser and publishes [MapLoadEvent].
 * - **Scale / calibration** — input fields for pixels-per-unit and scale factor,
 *   publishing [MapCalibrationEvent] on apply.
 * - **Grid** — toggle checkbox and cell-size field, publishing [GridUpdateEvent].
 * - **Fog of war** — reveal-all / hide-all buttons, publishing [FogOfWarResetEvent].
 */
class MapPlugin : DmPlugin {

    override val displayName: String = "Map"
    override val iconPath: String? = null

    /**
     * Creates the DM-panel [Node] containing all map controls.
     *
     * Each control publishes an event on the [EventBus]; the [MapRenderer]
     * (running on the table screen) subscribes to those events and redraws.
     */
    override fun createView(): Node {
        val vbox = VBox(8.0).apply { padding = Insets(10.0) }

        vbox.children.addAll(
            Label("Map Controls"),
            Separator(),
            buildLoadSection(),
            Separator(),
            buildCalibrationSection(),
            Separator(),
            buildGridSection(),
            Separator(),
            buildFogOfWarSection(),
        )

        return vbox
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    /** Builds the "Load map" control row. */
    private fun buildLoadSection(): VBox {
        val pathField = TextField().apply {
            isEditable = false
            promptText = "No map loaded"
            tooltip = Tooltip("Path to the currently loaded map image")
        }

        val loadBtn = Button("Load map…").apply {
            tooltip = Tooltip("Open a map image file")
            setOnAction {
                val chooser = FileChooser().apply {
                    title = "Select map image"
                    extensionFilters.addAll(
                        FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
                        FileChooser.ExtensionFilter("All files", "*.*"),
                    )
                }
                val file = chooser.showOpenDialog(null)
                if (file != null) {
                    pathField.text = file.absolutePath
                    EventBus.publish(MapLoadEvent(file.toURI().toString()))
                }
            }
        }

        return VBox(4.0, Label("Map image"), loadBtn, pathField)
    }

    /** Builds the calibration (scale + offset) control section. */
    private fun buildCalibrationSection(): VBox {
        val pxPerUnitField = TextField("50").apply {
            tooltip = Tooltip("Image pixels that equal one game unit (e.g. 5 ft)")
        }
        val scaleField = TextField("1.0").apply {
            tooltip = Tooltip("Uniform zoom factor (1.0 = no zoom)")
        }
        val offsetXField = TextField("0").apply {
            tooltip = Tooltip("Horizontal offset in canvas pixels")
        }
        val offsetYField = TextField("0").apply {
            tooltip = Tooltip("Vertical offset in canvas pixels")
        }

        val applyBtn = Button("Apply calibration").apply {
            setOnAction {
                val ppu = pxPerUnitField.text.toDoubleOrNull() ?: return@setOnAction
                val scale = scaleField.text.toDoubleOrNull() ?: return@setOnAction
                val ox = offsetXField.text.toDoubleOrNull() ?: return@setOnAction
                val oy = offsetYField.text.toDoubleOrNull() ?: return@setOnAction
                if (ppu > 0 && scale > 0) {
                    EventBus.publish(MapCalibrationEvent(MapCalibration(ppu, ox, oy, scale)))
                }
            }
        }

        return VBox(
            4.0,
            Label("Calibration"),
            Label("Pixels per unit:"), pxPerUnitField,
            Label("Scale:"), scaleField,
            Label("Offset X:"), offsetXField,
            Label("Offset Y:"), offsetYField,
            applyBtn,
        )
    }

    /** Builds the grid overlay control section. */
    private fun buildGridSection(): VBox {
        val cellSizeField = TextField("1.0").apply {
            tooltip = Tooltip("Grid cell size in game units")
        }
        val visibleCheck = CheckBox("Show grid").apply { isSelected = false }

        val applyBtn = Button("Apply grid").apply {
            setOnAction {
                // Hiding the grid does not require a valid cell size.
                if (!visibleCheck.isSelected) {
                    EventBus.publish(GridUpdateEvent(null))
                    return@setOnAction
                }
                val cellSize = cellSizeField.text.toDoubleOrNull() ?: return@setOnAction
                if (cellSize > 0) {
                    EventBus.publish(GridUpdateEvent(GridConfig(cellSizeInUnits = cellSize)))
                }
            }
        }

        return VBox(4.0, Label("Grid"), Label("Cell size (units):"), cellSizeField, visibleCheck, applyBtn)
    }

    /** Builds the fog-of-war control section. */
    private fun buildFogOfWarSection(): VBox {
        val revealAllBtn = Button("Reveal all").apply {
            tooltip = Tooltip("Remove fog from the entire map")
            setOnAction { EventBus.publish(FogOfWarResetEvent(revealAll = true)) }
        }

        val hideAllBtn = Button("Hide all").apply {
            tooltip = Tooltip("Cover the entire map with fog")
            setOnAction { EventBus.publish(FogOfWarResetEvent(revealAll = false)) }
        }

        return VBox(4.0, Label("Fog of war"), revealAllBtn, hideAllBtn)
    }
}
