package com.tabletopcontrol.light

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.light.ui.LightDebugConsoleSection
import com.tabletopcontrol.light.ui.LightEffectParamsSection
import com.tabletopcontrol.light.ui.LightMainControlsSection
import com.tabletopcontrol.light.ui.LightOperatorFeedbackPresenter
import com.tabletopcontrol.light.ui.LightPresetSection
import com.tabletopcontrol.light.ui.LightSection
import com.tabletopcontrol.light.ui.LightSerialSection
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.layout.VBox

/**
 * DM-panel plugin for controlling physical ambient lighting via WLED.
 *
 * State validation lives in [LightController]. Serial connection lifecycle and
 * coalesced writes live in [LightSerialCoordinator]. This plugin composes the
 * section controllers that render the UI.
 */
class LightPlugin : DmPlugin {
    override val displayName: String = "Lights"

    private val controller = LightController()
    private val serialCoordinator = LightSerialCoordinator(controller)
    private val feedback = LightOperatorFeedbackPresenter()
    private val activeSections = mutableListOf<LightSection>()

    override fun createView(): Node {
        disposeSections()

        val effectParamsSection = LightEffectParamsSection(controller, feedback).createSection()
        val serialSection = LightSerialSection(serialCoordinator, feedback).createSection()
        val mainControlsSection = LightMainControlsSection(
            controller = controller,
            feedback = feedback,
            effectParamsNode = effectParamsSection.node,
        ).createSection()
        val presetSection = LightPresetSection(controller, feedback).createSection()
        val debugSection = LightDebugConsoleSection(serialCoordinator).createSection()

        activeSections += listOf(
            effectParamsSection,
            serialSection,
            mainControlsSection,
            presetSection,
            debugSection,
        )

        val root = VBox(8.0).apply {
            padding = Insets(10.0)
            children.addAll(
                Label("Ambient Light Controls"),
                Separator(),
                serialSection.node,
                Separator(),
                mainControlsSection.node,
                Separator(),
                presetSection.node,
                Separator(),
                debugSection.node,
            )
        }

        return ScrollPane(root).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        }
    }

    override fun onShutdown() {
        disposeSections()
        serialCoordinator.shutdown()
    }

    private fun disposeSections() {
        activeSections.forEach { it.dispose() }
        activeSections.clear()
    }
}
