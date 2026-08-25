package com.tabletopcontrol.light

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.LightControlEvent
import com.tabletopcontrol.core.LightControlTarget
import com.tabletopcontrol.core.scene.SceneParticipant
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
class LightPlugin : DmPlugin, SceneParticipant {
    override val displayName: String = "Lights"
    override val sceneKey: String = "lights"
    override val sceneDisplayName: String = displayName
    override val sceneLoadOrder: Int = 500

    private val controller = LightController()
    private val serialCoordinator = LightSerialCoordinator(controller)
    private val feedback = LightOperatorFeedbackPresenter()
    private val activeSections = mutableListOf<LightSection>()
    private val commandSubscription = EventBus.subscribe<LightControlEvent>(::applyHotkeyCommand)

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
        commandSubscription.unsubscribe()
        disposeSections()
        serialCoordinator.shutdown()
    }

    override fun captureSceneState(): String = LightSceneCodec.serialize(controller.sceneSnapshot())

    override fun applySceneState(payload: String) {
        val sceneState = requireNotNull(LightSceneCodec.deserialize(payload)) {
            "Invalid light scene payload"
        }
        controller.applySceneState(sceneState)
    }

    private fun disposeSections() {
        activeSections.forEach { it.dispose() }
        activeSections.clear()
    }

    private fun applyHotkeyCommand(command: LightControlEvent) {
        if (command.target != LightControlTarget.Global) return

        command.power?.let(controller::setPower)
        command.colorHex?.let(controller::setColor)
        command.effectId?.let { effectId ->
            LightEffect.entries.firstOrNull { it.wledEffectId == effectId }?.let(controller::setEffect)
        }
        command.brightness?.let(controller::setBrightness)
        command.effectSpeed?.let(controller::setEffectSpeed)
        command.effectIntensity?.let(controller::setEffectIntensity)
    }
}
