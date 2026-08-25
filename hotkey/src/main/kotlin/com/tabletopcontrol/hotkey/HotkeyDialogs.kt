package com.tabletopcontrol.hotkey

import com.tabletopcontrol.core.MusicControlOperation
import com.tabletopcontrol.core.persistence.LocalFiles
import com.tabletopcontrol.core.ui.color.ColorEditorDialog
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.core.ui.dialog.AudioFileChooserDialog
import com.tabletopcontrol.core.ui.dialog.FileChooserHistoryStore
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.MenuButton
import javafx.scene.control.MenuItem
import javafx.scene.control.ScrollPane
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Window
import javafx.util.StringConverter
import kotlin.math.roundToInt

internal object HotkeyDialogs {
    private const val SOUND_HISTORY_KEY = "hotkey.sound.browser"
    private const val MUSIC_HISTORY_KEY = "hotkey.music.browser"
    private const val UNASSIGNED = "Unassigned"
    private const val CUSTOM = "Custom shortcut"

    fun configureHotkey(
        owner: Window?,
        current: HotkeyDefinition,
        allHotkeys: List<HotkeyDefinition>,
    ): HotkeyDefinition? {
        val nameField = TextField(current.name)
        val iconField = TextField(current.icon.orEmpty()).apply {
            promptText = "Optional emoji or short icon"
        }

        var chosenColor = current.colorHex
        val colorLabel = Label(chosenColor ?: "Theme default")
        val colorButton = Button("Choose Color...")
        val clearColorButton = Button("Clear")
        colorButton.setOnAction {
            val initial = ColorHexCodec.parseOrDefault(chosenColor, Color.GRAY)
            ColorEditorDialog.showDialog(
                owner = colorButton.scene?.window,
                title = "Hotkey Button Color",
                prompt = "Choose a color for this hotkey button",
                initialColor = initial,
            )?.let { color ->
                chosenColor = ColorHexCodec.colorToHex(color)
                colorLabel.text = chosenColor
            }
        }
        clearColorButton.setOnAction {
            chosenColor = null
            colorLabel.text = "Theme default"
        }

        var selectedBinding = current.binding
        val presetCombo = ComboBox<String>().apply {
            items.addAll(UNASSIGNED)
            items.addAll((1..12).map { "F$it" })
            items.add(CUSTOM)
            maxWidth = Double.MAX_VALUE
        }
        val captureButton = Button(selectedBinding?.displayText() ?: "Press shortcut...").apply {
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip("Bare F1-F24 keys or Ctrl/Alt/Meta key combinations are supported")
        }
        val bindingStatus = Label()

        fun isPreset(binding: KeyBinding?): Boolean =
            binding != null && !binding.shift && !binding.control && !binding.alt && !binding.meta &&
                binding.code in (1..12).map { "F$it" }

        fun refreshCaptureVisibility() {
            val custom = presetCombo.value == CUSTOM
            captureButton.isVisible = custom
            captureButton.isManaged = custom
        }

        presetCombo.value = when {
            selectedBinding == null -> UNASSIGNED
            isPreset(selectedBinding) -> selectedBinding?.code
            else -> CUSTOM
        }
        refreshCaptureVisibility()

        val actions = FXCollections.observableArrayList(current.actions)
        val actionList = ListView<HotkeyAction>(actions).apply {
            prefHeight = 230.0
            setCellFactory {
                object : ListCell<HotkeyAction>() {
                    override fun updateItem(item: HotkeyAction?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = if (empty || item == null) null else actionSummary(item, allHotkeys)
                    }
                }
            }
        }
        val addMenu = MenuButton("+ Add Action")
        val editButton = Button("Edit")
        val removeButton = Button("Remove")
        val upButton = Button("Up")
        val downButton = Button("Down")
        val actionCount = Label()

        fun refreshActionControls() {
            val index = actionList.selectionModel.selectedIndex
            addMenu.isDisable = actions.size >= MAX_ACTIONS_PER_HOTKEY
            editButton.isDisable = index !in actions.indices
            removeButton.isDisable = index !in actions.indices
            upButton.isDisable = index <= 0
            downButton.isDisable = index !in 0 until actions.lastIndex
            actionCount.text = "${actions.size}/$MAX_ACTIONS_PER_HOTKEY actions"
        }

        fun addAction(factory: () -> HotkeyAction?) {
            if (actions.size >= MAX_ACTIONS_PER_HOTKEY) return
            factory()?.let {
                actions += it
                actionList.selectionModel.selectLast()
                refreshActionControls()
            }
        }

        addMenu.items.addAll(
            MenuItem("Control Light / Segment...").apply {
                setOnAction { addAction { editLightAction(owner, null) } }
            },
            MenuItem("Play Sound File...").apply {
                setOnAction { addAction { editSoundAction(owner, null) } }
            },
            MenuItem("Control Music Track...").apply {
                setOnAction { addAction { editMusicAction(owner, null) } }
            },
            MenuItem("Trigger Another Hotkey...").apply {
                setOnAction { addAction { editTriggerAction(owner, null, current.id, allHotkeys) } }
            },
            MenuItem("Wait...").apply {
                setOnAction { addAction { editWaitAction(owner, null) } }
            },
        )

        fun editSelected() {
            val index = actionList.selectionModel.selectedIndex
            val action = actions.getOrNull(index) ?: return
            val edited = when (action) {
                is LightAction -> editLightAction(owner, action)
                is PlaySoundAction -> editSoundAction(owner, action)
                is MusicAction -> editMusicAction(owner, action)
                is TriggerHotkeyAction -> editTriggerAction(owner, action, current.id, allHotkeys)
                is WaitAction -> editWaitAction(owner, action)
            } ?: return
            actions[index] = edited
            actionList.refresh()
        }

        editButton.setOnAction { editSelected() }
        removeButton.setOnAction {
            val index = actionList.selectionModel.selectedIndex
            if (index in actions.indices) actions.removeAt(index)
            refreshActionControls()
        }
        upButton.setOnAction {
            val index = actionList.selectionModel.selectedIndex
            if (index > 0) {
                val action = actions.removeAt(index)
                actions.add(index - 1, action)
                actionList.selectionModel.select(index - 1)
            }
        }
        downButton.setOnAction {
            val index = actionList.selectionModel.selectedIndex
            if (index in 0 until actions.lastIndex) {
                val action = actions.removeAt(index)
                actions.add(index + 1, action)
                actionList.selectionModel.select(index + 1)
            }
        }
        actionList.selectionModel.selectedIndexProperty().addListener { _, _, _ -> refreshActionControls() }
        actionList.setOnMouseClicked { event ->
            if (event.button == MouseButton.PRIMARY && event.clickCount == 2) editSelected()
        }
        refreshActionControls()

        val generalGrid = GridPane().apply {
            hgap = 8.0
            vgap = 8.0
            add(Label("Name:"), 0, 0)
            add(nameField, 1, 0)
            add(Label("Icon:"), 0, 1)
            add(iconField, 1, 1)
            add(Label("Color:"), 0, 2)
            add(HBox(6.0, colorButton, clearColorButton, colorLabel).apply { alignment = Pos.CENTER_LEFT }, 1, 2)
            add(Label("Shortcut:"), 0, 3)
            add(presetCombo, 1, 3)
            add(captureButton, 1, 4)
            add(bindingStatus, 1, 5)
            GridPane.setHgrow(nameField, Priority.ALWAYS)
            GridPane.setHgrow(iconField, Priority.ALWAYS)
            GridPane.setHgrow(presetCombo, Priority.ALWAYS)
        }

        val content = VBox(
            10.0,
            generalGrid,
            Label("Actions run together until a Wait action starts the next step."),
            actionList,
            HBox(6.0, addMenu, editButton, removeButton, upButton, downButton, actionCount).apply {
                alignment = Pos.CENTER_LEFT
            },
        ).apply { padding = Insets(10.0) }

        val dialog = Dialog<HotkeyDefinition>().apply {
            title = "Configure Hotkey"
            headerText = "Configure the button, shortcut, and action sequence."
            dialogPane.content = content
            dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
            isResizable = true
            if (owner != null) initOwner(owner)
        }
        val okButton = dialog.dialogPane.lookupButton(ButtonType.OK)

        fun refreshValidation() {
            val duplicate = selectedBinding != null && allHotkeys.any {
                it.id != current.id && it.binding == selectedBinding
            }
            bindingStatus.text = when {
                selectedBinding == null -> "No keyboard shortcut assigned."
                duplicate -> "That shortcut is already assigned to another hotkey."
                else -> selectedBinding?.displayText().orEmpty()
            }
            okButton.isDisable = nameField.text.isBlank() || duplicate ||
                (presetCombo.value == CUSTOM && selectedBinding == null)
        }

        presetCombo.valueProperty().addListener { _, _, value ->
            when {
                value == UNASSIGNED -> selectedBinding = null
                value?.matches(Regex("F(?:[1-9]|1[0-2])")) == true -> selectedBinding = KeyBinding(value)
                value == CUSTOM && isPreset(selectedBinding) -> selectedBinding = null
            }
            captureButton.text = selectedBinding?.displayText() ?: "Press shortcut..."
            refreshCaptureVisibility()
            refreshValidation()
        }
        captureButton.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            val captured = KeyBinding.from(event)
            if (captured.resolvedCode != null && captured.isSuitable()) {
                selectedBinding = captured
                captureButton.text = captured.displayText()
                refreshValidation()
            } else if (!event.code.isModifierKeyForCapture()) {
                bindingStatus.text = "Use F1-F24, or add Ctrl, Alt, or Meta to another key."
            }
            event.consume()
        }
        captureButton.setOnAction {
            captureButton.text = "Press shortcut now..."
            captureButton.requestFocus()
        }
        nameField.textProperty().addListener { _, _, _ -> refreshValidation() }
        refreshValidation()

        dialog.setResultConverter { button ->
            if (button != ButtonType.OK) return@setResultConverter null
            HotkeyDefinition(
                id = current.id,
                name = nameField.text.trim(),
                icon = iconField.text.trim().takeIf(String::isNotBlank),
                colorHex = chosenColor,
                binding = selectedBinding,
                actions = actions.toList(),
            )
        }
        return dialog.showAndWait().orElse(null)
    }

    private fun editLightAction(owner: Window?, current: LightAction?): LightAction? {
        val targetCombo = ComboBox<HotkeyLightTarget>().apply {
            items.setAll(*HotkeyLightTarget.entries.toTypedArray())
            value = current?.target ?: HotkeyLightTarget.GLOBAL
            converter = object : StringConverter<HotkeyLightTarget>() {
                override fun toString(value: HotkeyLightTarget?): String = when (value) {
                    HotkeyLightTarget.GLOBAL -> "Global light"
                    HotkeyLightTarget.ALL_SEGMENTS -> "All advanced-light segments"
                    HotkeyLightTarget.SEGMENTS -> "Specific segments"
                    null -> ""
                }

                override fun fromString(value: String?): HotkeyLightTarget = HotkeyLightTarget.GLOBAL
            }
        }
        val segmentField = TextField(current?.segmentIds?.sorted()?.joinToString(",").orEmpty()).apply {
            promptText = "Example: 0,1,2,3"
        }
        val powerCombo = ComboBox<String>().apply {
            items.setAll("Unchanged", "On", "Off")
            value = when (current?.power) {
                true -> "On"
                false -> "Off"
                null -> "Unchanged"
            }
        }

        var colorHex = current?.colorHex ?: "#FFFFFF"
        val colorCheck = CheckBox("Change color").apply { isSelected = current?.colorHex != null }
        val colorButton = Button(colorHex)
        colorButton.setOnAction {
            ColorEditorDialog.showDialog(
                owner = colorButton.scene?.window,
                title = "Light Color",
                prompt = "Choose the color applied by this action",
                initialColor = ColorHexCodec.parseOrDefault(colorHex, Color.WHITE),
            )?.let {
                colorHex = ColorHexCodec.colorToHex(it)
                colorButton.text = colorHex
            }
        }

        val effectChoices = listOf(EffectChoice(null, "Unchanged")) +
            HOTKEY_LIGHT_EFFECTS.map { EffectChoice(it.id, it.displayName) }
        val effectCombo = ComboBox<EffectChoice>().apply {
            items.setAll(effectChoices)
            value = effectChoices.firstOrNull { it.id == current?.effectId } ?: effectChoices.first()
            converter = object : StringConverter<EffectChoice>() {
                override fun toString(value: EffectChoice?): String = value?.label.orEmpty()
                override fun fromString(value: String?): EffectChoice = effectChoices.first()
            }
        }

        val brightnessCheck = CheckBox("Change brightness").apply { isSelected = current?.brightness != null }
        val brightnessSlider = Slider(0.0, 100.0, (current?.brightness ?: 1.0) * 100.0)
        val brightnessLabel = Label("${brightnessSlider.value.roundToInt()}%")
        brightnessSlider.valueProperty().addListener { _, _, value -> brightnessLabel.text = "${value.toDouble().roundToInt()}%" }

        val speedCheck = CheckBox("Change effect speed").apply { isSelected = current?.effectSpeed != null }
        val speedField = TextField((current?.effectSpeed ?: 128).toString())
        val intensityCheck = CheckBox("Change effect intensity").apply { isSelected = current?.effectIntensity != null }
        val intensityField = TextField((current?.effectIntensity ?: 128).toString())

        fun updateTarget() {
            segmentField.isDisable = targetCombo.value != HotkeyLightTarget.SEGMENTS
        }
        fun updateOptionalControls() {
            colorButton.isDisable = !colorCheck.isSelected
            brightnessSlider.isDisable = !brightnessCheck.isSelected
            speedField.isDisable = !speedCheck.isSelected
            intensityField.isDisable = !intensityCheck.isSelected
        }
        targetCombo.valueProperty().addListener { _, _, _ -> updateTarget() }
        listOf(colorCheck, brightnessCheck, speedCheck, intensityCheck).forEach {
            it.selectedProperty().addListener { _, _, _ -> updateOptionalControls() }
        }
        updateTarget()
        updateOptionalControls()

        val grid = GridPane().apply {
            hgap = 8.0
            vgap = 8.0
            add(Label("Target:"), 0, 0); add(targetCombo, 1, 0)
            add(Label("Segments:"), 0, 1); add(segmentField, 1, 1)
            add(Label("Power:"), 0, 2); add(powerCombo, 1, 2)
            add(colorCheck, 0, 3); add(colorButton, 1, 3)
            add(Label("Effect:"), 0, 4); add(effectCombo, 1, 4)
            add(brightnessCheck, 0, 5); add(HBox(6.0, brightnessSlider, brightnessLabel), 1, 5)
            add(speedCheck, 0, 6); add(speedField, 1, 6)
            add(intensityCheck, 0, 7); add(intensityField, 1, 7)
        }
        return showActionDialog(owner, "Light Action", grid) {
            val segments = segmentField.text.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }.toSet()
            if (targetCombo.value == HotkeyLightTarget.SEGMENTS && segments.isEmpty()) return@showActionDialog null
            val speed = speedField.text.toIntOrNull()
            val intensity = intensityField.text.toIntOrNull()
            if (speedCheck.isSelected && speed !in 0..255) return@showActionDialog null
            if (intensityCheck.isSelected && intensity !in 0..255) return@showActionDialog null
            LightAction(
                target = targetCombo.value,
                segmentIds = segments,
                power = when (powerCombo.value) { "On" -> true; "Off" -> false; else -> null },
                colorHex = colorHex.takeIf { colorCheck.isSelected },
                effectId = effectCombo.value.id,
                brightness = (brightnessSlider.value / 100.0).takeIf { brightnessCheck.isSelected },
                effectSpeed = speed?.takeIf { speedCheck.isSelected },
                effectIntensity = intensity?.takeIf { intensityCheck.isSelected },
            )
        }
    }

    private fun editSoundAction(owner: Window?, current: PlaySoundAction?): PlaySoundAction? {
        val uriField = TextField(current?.uri.orEmpty()).apply { isEditable = false }
        val browseButton = Button("Browse...")
        val volumeSlider = Slider(0.0, 100.0, (current?.volume ?: 1.0) * 100.0)
        val volumeLabel = Label("${volumeSlider.value.roundToInt()}%")
        volumeSlider.valueProperty().addListener { _, _, value -> volumeLabel.text = "${value.toDouble().roundToInt()}%" }
        browseButton.setOnAction {
            AudioFileChooserDialog.showOpenDialog(
                owner = browseButton.scene?.window,
                title = "Choose hotkey sound effect",
                audioPatterns = listOf("*.mp3", "*.wav", "*.aac", "*.m4a", "*.ogg"),
                historyKey = SOUND_HISTORY_KEY,
                fallbackSelection = FileChooserHistoryStore.fileFromUri(uriField.text),
            )?.let { uriField.text = it.toURI().toString() }
        }
        val content = VBox(
            8.0,
            Label("Sound file:"),
            HBox(6.0, uriField, browseButton).apply { HBox.setHgrow(uriField, Priority.ALWAYS) },
            HBox(6.0, Label("Volume:"), volumeSlider, volumeLabel).apply { HBox.setHgrow(volumeSlider, Priority.ALWAYS) },
        )
        return showActionDialog(owner, "Play Sound File", content) {
            uriField.text.takeIf(String::isNotBlank)?.let { PlaySoundAction(it, volumeSlider.value / 100.0) }
        }
    }

    private fun editMusicAction(owner: Window?, current: MusicAction?): MusicAction? {
        val operationCombo = ComboBox<MusicControlOperation>().apply {
            items.setAll(*MusicControlOperation.entries.toTypedArray())
            value = current?.operation ?: MusicControlOperation.START
        }
        val uriField = TextField(current?.uri.orEmpty()).apply { isEditable = false }
        val browseButton = Button("Browse...")
        val clearButton = Button("Clear")
        val hint = Label()
        browseButton.setOnAction {
            AudioFileChooserDialog.showOpenDialog(
                owner = browseButton.scene?.window,
                title = "Choose music track",
                audioPatterns = listOf("*.mp3", "*.wav", "*.aac", "*.m4a", "*.ogg"),
                historyKey = MUSIC_HISTORY_KEY,
                fallbackSelection = FileChooserHistoryStore.fileFromUri(uriField.text),
            )?.let { uriField.text = it.toURI().toString() }
        }
        clearButton.setOnAction { uriField.clear() }
        fun refreshHint() {
            hint.text = if (operationCombo.value == MusicControlOperation.STOP) {
                "Leave the track empty to stop all music."
            } else {
                "Choose the track to start. Switch stops other tracks first."
            }
        }
        operationCombo.valueProperty().addListener { _, _, _ -> refreshHint() }
        refreshHint()
        val grid = GridPane().apply {
            hgap = 8.0; vgap = 8.0
            add(Label("Operation:"), 0, 0); add(operationCombo, 1, 0)
            add(Label("Track:"), 0, 1)
            add(HBox(6.0, uriField, browseButton, clearButton).apply { HBox.setHgrow(uriField, Priority.ALWAYS) }, 1, 1)
            add(hint, 1, 2)
        }
        return showActionDialog(owner, "Music Action", grid) {
            val uri = uriField.text.takeIf(String::isNotBlank)
            if (operationCombo.value != MusicControlOperation.STOP && uri == null) null
            else MusicAction(operationCombo.value, uri)
        }
    }

    private fun editTriggerAction(
        owner: Window?,
        current: TriggerHotkeyAction?,
        editingHotkeyId: String,
        allHotkeys: List<HotkeyDefinition>,
    ): TriggerHotkeyAction? {
        val choices = allHotkeys.filter { it.id != editingHotkeyId }
        if (choices.isEmpty()) return null
        val combo = ComboBox<HotkeyDefinition>().apply {
            items.setAll(choices)
            value = choices.firstOrNull { it.id == current?.hotkeyId } ?: choices.first()
            converter = object : StringConverter<HotkeyDefinition>() {
                override fun toString(value: HotkeyDefinition?): String = value?.let {
                    "${it.name}${it.binding?.let { binding -> " (${binding.displayText()})" }.orEmpty()}"
                }.orEmpty()
                override fun fromString(value: String?): HotkeyDefinition = choices.first()
            }
            maxWidth = Double.MAX_VALUE
        }
        return showActionDialog(owner, "Trigger Another Hotkey", VBox(8.0, Label("Hotkey:"), combo)) {
            combo.value?.let { TriggerHotkeyAction(it.id) }
        }
    }

    private fun editWaitAction(owner: Window?, current: WaitAction?): WaitAction? {
        val secondsField = TextField(((current?.durationMillis ?: 1000L) / 1000.0).toString())
        val content = HBox(8.0, Label("Wait:"), secondsField, Label("seconds")).apply { alignment = Pos.CENTER_LEFT }
        return showActionDialog(owner, "Wait", content) {
            val seconds = secondsField.text.toDoubleOrNull() ?: return@showActionDialog null
            if (seconds < 0.0 || seconds * 1000.0 > MAX_WAIT_MILLIS) return@showActionDialog null
            WaitAction((seconds * 1000.0).roundToInt().toLong())
        }
    }

    private fun <T> showActionDialog(
        owner: Window?,
        title: String,
        content: javafx.scene.Node,
        result: () -> T?,
    ): T? {
        val dialog = Dialog<T>().apply {
            this.title = title
            dialogPane.content = ScrollPane(content).apply {
                isFitToWidth = true
                prefViewportWidth = 520.0
                padding = Insets(8.0)
            }
            dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
            isResizable = true
            if (owner != null) initOwner(owner)
            setResultConverter { if (it == ButtonType.OK) result() else null }
        }
        return dialog.showAndWait().orElse(null)
    }

    fun actionSummary(action: HotkeyAction, allHotkeys: List<HotkeyDefinition>): String = when (action) {
        is LightAction -> {
            val target = when (action.target) {
                HotkeyLightTarget.GLOBAL -> "global light"
                HotkeyLightTarget.ALL_SEGMENTS -> "all segments"
                HotkeyLightTarget.SEGMENTS -> "segments ${action.segmentIds.sorted().joinToString(", ")}"
            }
            val changes = buildList {
                action.power?.let { add(if (it) "on" else "off") }
                action.colorHex?.let { add(it) }
                action.effectId?.let { id -> add(HOTKEY_LIGHT_EFFECTS.firstOrNull { it.id == id }?.displayName ?: "effect $id") }
                action.brightness?.let { add("${(it * 100).roundToInt()}%") }
            }.joinToString(", ").ifBlank { "no changes" }
            "Light: $target — $changes"
        }

        is PlaySoundAction -> "Sound: ${LocalFiles.fileName(action.uri) ?: action.uri}"
        is MusicAction -> "Music ${action.operation.name.lowercase()}: ${LocalFiles.fileName(action.uri) ?: action.uri ?: "all tracks"}"
        is TriggerHotkeyAction -> "Trigger: ${allHotkeys.firstOrNull { it.id == action.hotkeyId }?.name ?: "missing hotkey"}"
        is WaitAction -> "Wait: ${action.durationMillis / 1000.0} seconds"
    }

    private fun javafx.scene.input.KeyCode.isModifierKeyForCapture(): Boolean =
        this == javafx.scene.input.KeyCode.SHIFT || this == javafx.scene.input.KeyCode.CONTROL ||
            this == javafx.scene.input.KeyCode.ALT || this == javafx.scene.input.KeyCode.META ||
            this == javafx.scene.input.KeyCode.COMMAND || this == javafx.scene.input.KeyCode.WINDOWS ||
            this == javafx.scene.input.KeyCode.ALT_GRAPH || this == javafx.scene.input.KeyCode.SHORTCUT

    private data class EffectChoice(val id: Int?, val label: String)
}
