package com.tabletopcontrol.hotkey

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.KeyboardShortcutParticipant
import com.tabletopcontrol.core.LightControlEvent
import com.tabletopcontrol.core.LightControlTarget
import com.tabletopcontrol.core.MusicControlEvent
import com.tabletopcontrol.core.scene.SceneParticipant
import com.tabletopcontrol.core.ui.ContextMenuRenderer
import com.tabletopcontrol.core.ui.MenuAction
import com.tabletopcontrol.core.ui.MenuSection
import com.tabletopcontrol.core.ui.color.ColorContrast
import javafx.animation.PauseTransition
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import javafx.util.Duration
import java.util.UUID

/** Configurable button matrix for click- and keyboard-triggered action sequences. */
class HotkeyPlugin : DmPlugin, KeyboardShortcutParticipant, SceneParticipant {
    override val displayName: String = "Hotkeys"
    override val sceneKey: String = "hotkeys"
    override val sceneDisplayName: String = displayName
    override val sceneLoadOrder: Int = 600

    private var settings = HotkeySettingsStore.load()
    private val soundPlayer = HotkeySoundPlayer()
    private val pendingDelays = mutableSetOf<PauseTransition>()
    private val pressedKeys = mutableSetOf<KeyCode>()
    private val visibleButtons = mutableMapOf<String, Button>()
    private var buttonGrid: GridPane? = null
    private var countLabel: Label? = null
    private var addButton: Button? = null
    private var columnCombo: ComboBox<Int>? = null

    private val executor = HotkeyExecutor(
        definitions = { settings.hotkeys },
        scheduler = HotkeyScheduler { delayMillis, action ->
            val delay = PauseTransition(Duration.millis(delayMillis.toDouble()))
            pendingDelays += delay
            delay.setOnFinished {
                pendingDelays -= delay
                action()
            }
            delay.play()
        },
        sink = HotkeyActionSink(::executeAction),
    )

    override fun createView(): Node {
        val columns = ComboBox<Int>().apply {
            items.addAll((MIN_MATRIX_COLUMNS..MAX_MATRIX_COLUMNS).toList())
            value = settings.columns
            tooltip = Tooltip("Number of hotkey columns")
            valueProperty().addListener { _, _, value ->
                if (value != null && value != settings.columns) {
                    updateSettings(settings.copy(columns = value))
                }
            }
        }
        columnCombo = columns

        val add = Button("+ Add Hotkey").apply {
            tooltip = Tooltip("Add a hotkey button (up to $MAX_HOTKEY_COUNT)")
            setOnAction { addHotkey() }
        }
        addButton = add
        val count = Label()
        countLabel = count

        val controls = HBox(8.0, add, Label("Columns:"), columns, count).apply {
            alignment = Pos.CENTER_LEFT
            HBox.setHgrow(count, Priority.ALWAYS)
        }
        val grid = GridPane().apply {
            hgap = 6.0
            vgap = 6.0
            padding = Insets(4.0)
        }
        buttonGrid = grid

        val content = VBox(8.0, controls, grid).apply { padding = Insets(8.0) }
        renderButtons()
        return ScrollPane(content).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        }
    }

    override fun handleKeyPressed(event: KeyEvent): Boolean {
        val hotkey = settings.hotkeys.firstOrNull { it.binding?.matches(event) == true } ?: return false
        if (!pressedKeys.add(event.code)) return true
        trigger(hotkey)
        return true
    }

    override fun handleKeyReleased(event: KeyEvent) {
        pressedKeys.remove(event.code)
    }

    override fun captureSceneState(): String = HotkeySettingsCodec.serialize(settings)

    override fun applySceneState(payload: String) {
        val loaded = requireNotNull(HotkeySettingsCodec.parse(payload)) { "Invalid hotkey scene payload" }
        updateSettings(loaded)
    }

    override fun onShutdown() {
        HotkeySettingsStore.save(settings)
        pendingDelays.toList().forEach(PauseTransition::stop)
        pendingDelays.clear()
        soundPlayer.shutdown()
        pressedKeys.clear()
        visibleButtons.clear()
    }

    private fun renderButtons() {
        val pane = buttonGrid ?: return
        visibleButtons.clear()
        pane.children.clear()
        pane.columnConstraints.setAll(
            (0 until settings.columns).map {
                ColumnConstraints().apply {
                    percentWidth = 100.0 / settings.columns
                    hgrow = Priority.ALWAYS
                    isFillWidth = true
                }
            },
        )
        settings.hotkeys.forEachIndexed { index, hotkey ->
            pane.add(buildButton(hotkey), index % settings.columns, index / settings.columns)
        }
        refreshControls()
    }

    private fun buildButton(hotkey: HotkeyDefinition): Button {
        val button = Button(buttonText(hotkey)).apply {
            prefWidth = BUTTON_WIDTH
            minWidth = 0.0
            prefHeight = BUTTON_HEIGHT
            maxWidth = Double.MAX_VALUE
            isWrapText = true
            tooltip = Tooltip(buttonTooltip(hotkey))
            style = hotkey.colorHex?.let { color ->
                "-fx-background-color: $color; " +
                    "-fx-text-fill: ${ColorContrast.textColorHexForBackgroundHex(color)};"
            }.orEmpty()
            setOnAction { trigger(hotkey) }
        }
        visibleButtons[hotkey.id] = button

        button.setOnContextMenuRequested { event ->
            val actions = listOf(
                MenuAction(
                    id = "hotkey.configure",
                    label = "Configure...",
                    icon = "⚙",
                    onAction = { configureHotkey(hotkey, button) },
                ),
                MenuAction(
                    id = "hotkey.clear-actions",
                    label = "Clear Actions",
                    icon = "⌫",
                    isEnabled = hotkey.actions.isNotEmpty(),
                    onAction = { replaceHotkey(hotkey.copy(actions = emptyList())) },
                ),
                MenuAction(
                    id = "hotkey.remove",
                    label = "Remove Hotkey",
                    icon = "−",
                    section = MenuSection.DANGER_ZONE,
                    requiresConfirmation = true,
                    confirmationMessage = "Remove '${hotkey.name}'?",
                    onAction = { removeHotkey(hotkey.id) },
                ),
            )
            ContextMenuRenderer.build(actions).show(button, event.screenX, event.screenY)
            event.consume()
        }
        return button
    }

    private fun trigger(hotkey: HotkeyDefinition) {
        executor.trigger(hotkey.id)
    }

    private fun executeAction(action: HotkeyAction) {
        when (action) {
            is LightAction -> EventBus.publish(
                LightControlEvent(
                    target = when (action.target) {
                        HotkeyLightTarget.GLOBAL -> LightControlTarget.Global
                        HotkeyLightTarget.ALL_SEGMENTS -> LightControlTarget.Segments()
                        HotkeyLightTarget.SEGMENTS -> LightControlTarget.Segments(action.segmentIds)
                    },
                    power = action.power,
                    colorHex = action.colorHex,
                    effectId = action.effectId,
                    brightness = action.brightness,
                    effectSpeed = action.effectSpeed,
                    effectIntensity = action.effectIntensity,
                ),
            )

            is PlaySoundAction -> soundPlayer.play(action.uri, action.volume)
            is MusicAction -> EventBus.publish(MusicControlEvent(action.operation, action.uri))
            is TriggerHotkeyAction, is WaitAction -> Unit
        }
    }

    private fun configureHotkey(hotkey: HotkeyDefinition, button: Button) {
        HotkeyDialogs.configureHotkey(
            owner = button.scene?.window,
            current = hotkey,
            allHotkeys = settings.hotkeys,
        )?.let(::replaceHotkey)
    }

    private fun replaceHotkey(updated: HotkeyDefinition) {
        val index = settings.hotkeys.indexOfFirst { it.id == updated.id }
        if (index < 0) return
        val hotkeys = settings.hotkeys.toMutableList().also { it[index] = updated }
        updateSettings(settings.copy(hotkeys = hotkeys))
    }

    private fun addHotkey() {
        if (settings.hotkeys.size >= MAX_HOTKEY_COUNT) return
        val usedBindings = settings.hotkeys.mapNotNull(HotkeyDefinition::binding).toSet()
        val binding = (1..12)
            .map(KeyBinding::functionKey)
            .firstOrNull { it !in usedBindings }
        val nextNumber = settings.hotkeys.size + 1
        val added = HotkeyDefinition(
            id = UUID.randomUUID().toString(),
            name = "Hotkey $nextNumber",
            binding = binding,
        )
        updateSettings(settings.copy(hotkeys = settings.hotkeys + added))
    }

    private fun removeHotkey(id: String) {
        updateSettings(settings.copy(hotkeys = settings.hotkeys.filterNot { it.id == id }))
    }

    private fun updateSettings(updated: HotkeySettings) {
        settings = HotkeySettingsCodec.normalize(updated)
        HotkeySettingsStore.save(settings)
        if (columnCombo?.value != settings.columns) columnCombo?.value = settings.columns
        renderButtons()
    }

    private fun refreshControls() {
        addButton?.isDisable = settings.hotkeys.size >= MAX_HOTKEY_COUNT
        countLabel?.text = "${settings.hotkeys.size}/$MAX_HOTKEY_COUNT hotkeys"
    }

    private fun buttonText(hotkey: HotkeyDefinition): String = buildString {
        hotkey.icon?.let { append(it).append(' ') }
        append(hotkey.name)
        hotkey.binding?.let { append("\n").append(it.displayText()) }
    }

    private fun buttonTooltip(hotkey: HotkeyDefinition): String = buildString {
        append(hotkey.binding?.let { "Shortcut: ${it.displayText()}" } ?: "No shortcut assigned")
        append("\n")
        if (hotkey.actions.isEmpty()) {
            append("No actions. Right-click to configure.")
        } else {
            hotkey.actions.forEachIndexed { index, action ->
                append("\n${index + 1}. ${HotkeyDialogs.actionSummary(action, settings.hotkeys)}")
            }
        }
    }

    private companion object {
        private const val BUTTON_WIDTH = 112.0
        private const val BUTTON_HEIGHT = 64.0
    }
}
