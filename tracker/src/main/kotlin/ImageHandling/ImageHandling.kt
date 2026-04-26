package com.tabletopcontrol.new_tracker.ImageHandling

import com.tabletopcontrol.core.persistence.LocalFiles
import com.tabletopcontrol.core.ui.dialog.DialogFlows
import com.tabletopcontrol.core.ui.dialog.FileChooserHistoryStore
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorImageSettings
import javafx.geometry.Insets
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle
import javafx.scene.transform.Scale
import javafx.stage.FileChooser
import javafx.stage.Window
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale
import kotlin.math.abs

class ImageHandling {

    fun createPictureButton(
        actorProvider: () -> Actor?,
        onActorUpdated: (Actor) -> Unit,
        onRefresh: () -> Unit,
    ): Button {
        val button = Button()
        applyPictureButtonState(
            button,
            actorProvider()?.imageSettings ?: ActorImageSettings(),
        )
        button.setOnAction { event ->
            val currentActor = actorProvider() ?: return@setOnAction
            val owner = (event.source as? Button)?.scene?.window
            val chosen = showActorImageDialog(owner, currentActor.imageSettings) ?: return@setOnAction
            if (chosen == currentActor.imageSettings) {
                return@setOnAction
            }
            onActorUpdated(currentActor.copy(imageSettings = chosen))
            onRefresh()
        }
        return button
    }



    internal fun pictureButtonText(settings: ActorImageSettings): String =
        if (settings.uri == null) "Pic" else "Pic*"

    internal fun pictureButtonTooltip(settings: ActorImageSettings): String =
        if (settings.uri == null) {
            "Add a picture for this actor"
        } else {
            "Picture already set - click to change it"
        }

    private fun applyPictureButtonState(button: Button, settings: ActorImageSettings) {
        val hasPicture = settings.uri != null
        button.text = pictureButtonText(settings)
        button.accessibleText = if (hasPicture) "Actor picture set" else "No actor picture set"
        button.tooltip = Tooltip(pictureButtonTooltip(settings))
    }

    private fun showActorImageDialog(owner: Window?, initial: ActorImageSettings): ActorImageSettings? {
        var working = initial

        val imageView = ImageView().apply {
            fitWidth = PREVIEW_RADIUS * 2
            fitHeight = PREVIEW_RADIUS * 2
            isPreserveRatio = true
            isMouseTransparent = true
        }
        val scaleTransform = Scale(working.scaleX, working.scaleY, PREVIEW_RADIUS, PREVIEW_RADIUS)
        imageView.transforms.setAll(scaleTransform)

        val previewFrame = Rectangle(PREVIEW_SIZE, PREVIEW_SIZE).apply {
            style = "-fx-fill: -tc-surface; -fx-stroke: -tc-border;"
        }

        val previewImagePane = StackPane(imageView).apply {
            minWidth = PREVIEW_SIZE
            prefWidth = PREVIEW_SIZE
            maxWidth = PREVIEW_SIZE
            minHeight = PREVIEW_SIZE
            prefHeight = PREVIEW_SIZE
            maxHeight = PREVIEW_SIZE
            clip = Circle(PREVIEW_CENTER, PREVIEW_CENTER, PREVIEW_RADIUS)
        }

        val tokenCircle = Circle(PREVIEW_CENTER, PREVIEW_CENTER, PREVIEW_RADIUS).apply {
            style = "-fx-fill: transparent; -fx-stroke: -tc-border; -fx-stroke-width: 1.5;"
            isMouseTransparent = true
        }

        val previewPane = StackPane(
            previewFrame,
            previewImagePane,
            tokenCircle,
        ).apply {
            minWidth = PREVIEW_SIZE
            prefWidth = PREVIEW_SIZE
            maxWidth = PREVIEW_SIZE
            minHeight = PREVIEW_SIZE
            prefHeight = PREVIEW_SIZE
            maxHeight = PREVIEW_SIZE
        }

        fun applyTransforms(settings: ActorImageSettings) {
            scaleTransform.x = settings.scaleX
            scaleTransform.y = settings.scaleY
            imageView.translateX = settings.offsetX
            imageView.translateY = settings.offsetY
        }

        loadImage(working.uri, imageView)
        applyTransforms(working)

        val scaleXSlider = Slider(0.1, 10.0, working.scaleX).apply { isShowTickLabels = true }
        val scaleYSlider = Slider(0.1, 10.0, working.scaleY).apply { isShowTickLabels = true }
        val offsetXSlider = Slider(-80.0, 80.0, working.offsetX).apply { isShowTickLabels = true }
        val offsetYSlider = Slider(-80.0, 80.0, working.offsetY).apply { isShowTickLabels = true }

        val scaleXField = TextField().apply { prefColumnCount = 6 }
        val scaleYField = TextField().apply { prefColumnCount = 6 }
        val offsetXField = TextField().apply { prefColumnCount = 6 }
        val offsetYField = TextField().apply { prefColumnCount = 6 }
        val lockScaleRatioButton = ToggleButton("Lock scale ratio").apply {
            tooltip = Tooltip("Keep X and Y scaling linked using the current ratio.")
        }

        bindSliderToField(scaleXSlider, scaleXField, decimals = 2)
        bindSliderToField(scaleYSlider, scaleYField, decimals = 2)
        bindSliderToField(offsetXSlider, offsetXField, decimals = 1)
        bindSliderToField(offsetYSlider, offsetYField, decimals = 1)

        var syncScaleSliders = false
        var lockedScaleRatio = scaleRatio(scaleXSlider.value, scaleYSlider.value)

        fun applyScaleSliders() {
            working = working.copy(
                scaleX = scaleXSlider.value,
                scaleY = scaleYSlider.value,
            )
            applyTransforms(working)
        }

        fun syncLockedScale(source: Slider, value: Double) {
            if (syncScaleSliders) {
                return
            }
            if (!lockScaleRatioButton.isSelected) {
                applyScaleSliders()
                return
            }

            try {
                if (source === scaleXSlider) {
                    val syncedScaleY = (value * lockedScaleRatio).coerceIn(scaleYSlider.min, scaleYSlider.max)
                    if (abs(syncedScaleY - scaleYSlider.value) > SLIDER_VALUE_EPSILON) {
                        scaleYSlider.value = syncedScaleY
                    }
                } else {
                    val ratio = if (abs(lockedScaleRatio) <= SLIDER_VALUE_EPSILON) 1.0 else lockedScaleRatio
                    val syncedScaleX = (value / ratio).coerceIn(scaleXSlider.min, scaleXSlider.max)
                    if (abs(syncedScaleX - scaleXSlider.value) > SLIDER_VALUE_EPSILON) {
                        scaleXSlider.value = syncedScaleX
                    }
                }
                applyScaleSliders()
            } finally {
                syncScaleSliders = false
            }
        }

        scaleXSlider.valueProperty().addListener { _, _, value ->
            syncLockedScale(scaleXSlider, value.toDouble())
        }
        scaleYSlider.valueProperty().addListener { _, _, value ->
            syncLockedScale(scaleYSlider, value.toDouble())
        }
        offsetXSlider.valueProperty().addListener { _, _, value ->
            working = working.copy(offsetX = value.toDouble())
            applyTransforms(working)
        }
        offsetYSlider.valueProperty().addListener { _, _, value ->
            working = working.copy(offsetY = value.toDouble())
            applyTransforms(working)
        }
        lockScaleRatioButton.selectedProperty().addListener { _, _, selected ->
            if (selected) {
                lockedScaleRatio = scaleRatio(scaleXSlider.value, scaleYSlider.value)
            }
        }

        val clearButton = Button("Clear Picture").apply {
            isDisable = working.uri == null
            setOnAction {
                working = working.copy(uri = null)
                loadImage(null, imageView)
                applyTransforms(working)
                isDisable = true
            }
        }

        val chooseButton = Button("Choose Picture...").apply {
            setOnAction {
                val chooser = FileChooser().apply {
                    title = "Select actor picture"
                    extensionFilters.addAll(
                        FileChooser.ExtensionFilter(
                            "Image files",
                            "*.png",
                            "*.jpg",
                            "*.jpeg",
                            "*.bmp",
                            "*.gif",
                        ),
                        FileChooser.ExtensionFilter("All files", "*.*"),
                    )
                    FileChooserHistoryStore.configureInitialDirectory(
                        chooser = this,
                        key = TOKEN_IMAGE_HISTORY_KEY,
                        fallbackSelection = FileChooserHistoryStore.fileFromUri(working.uri),
                    )
                }
                val file = chooser.showOpenDialog(owner)
                if (file != null) {
                    FileChooserHistoryStore.rememberSelection(TOKEN_IMAGE_HISTORY_KEY, file)
                    val uri = file.toURI().toString()
                    if (loadImage(uri, imageView)) {
                        working = working.copy(uri = uri)
                        applyTransforms(working)
                        clearButton.isDisable = false
                    }
                }
            }
        }

        val content = VBox(
            10.0,
            previewPane,
            HBox(8.0, chooseButton, clearButton),
            lockScaleRatioButton,
            Label("Scale X"),
            HBox(8.0, scaleXSlider, scaleXField).apply { HBox.setHgrow(scaleXSlider, Priority.ALWAYS) },
            Label("Scale Y"),
            HBox(8.0, scaleYSlider, scaleYField).apply { HBox.setHgrow(scaleYSlider, Priority.ALWAYS) },
            Label("Offset X"),
            HBox(8.0, offsetXSlider, offsetXField).apply { HBox.setHgrow(offsetXSlider, Priority.ALWAYS) },
            Label("Offset Y"),
            HBox(8.0, offsetYSlider, offsetYField).apply { HBox.setHgrow(offsetYSlider, Priority.ALWAYS) },
        ).apply {
            padding = Insets(10.0)
        }

        return DialogFlows.showResultDialog(
            owner = owner,
            title = "Actor Picture",
            headerText = "Select a picture and adjust scale or position",
            content = content,
            buttonTypes = listOf(ButtonType.OK, ButtonType.CANCEL),
        ) { button ->
            DialogFlows.resultForButton(button) { working }
        }
    }

    private fun bindSliderToField(slider: Slider, field: TextField, decimals: Int) {
        val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
            isGroupingUsed = false
        }

        fun formatValue(value: Double): String = numberFormat.format(value)

        fun parseValue(text: String): Double? {
            val raw = text.trim()
            if (raw.isEmpty()) {
                return null
            }
            val position = ParsePosition(0)
            val parsed = numberFormat.parse(raw, position) ?: return null
            if (position.index != raw.length) {
                return null
            }
            return parsed.toDouble()
        }

        slider.valueProperty().addListener { _, _, value ->
            if (!field.isFocused) {
                field.text = formatValue(value.toDouble())
            }
        }

        field.text = formatValue(slider.value)
        field.textProperty().addListener { _, _, text ->
            if (!field.isFocused) {
                return@addListener
            }
            val parsed = parseValue(text) ?: return@addListener
            val clamped = parsed.coerceIn(slider.min, slider.max)
            if (abs(clamped - slider.value) > SLIDER_VALUE_EPSILON) {
                slider.value = clamped
            }
        }

        field.focusedProperty().addListener { _, _, focused ->
            if (!focused) {
                val parsed = parseValue(field.text)
                if (parsed == null) {
                    field.text = formatValue(slider.value)
                } else {
                    val clamped = parsed.coerceIn(slider.min, slider.max)
                    if (abs(clamped - slider.value) > SLIDER_VALUE_EPSILON) {
                        slider.value = clamped
                    } else {
                        field.text = formatValue(slider.value)
                    }
                }
            }
        }
    }

    private fun loadImage(uri: String?, imageView: ImageView): Boolean {
        if (uri == null) {
            imageView.image = null
            return true
        }

        val normalizedUri = normalizeSupportedActorImageUri(uri)
        if (normalizedUri == null) {
            Alert(Alert.AlertType.ERROR).apply {
                title = "Image load failed"
                headerText = "Unsupported image location"
                contentText = "Only local file images are supported."
            }.showAndWait()
            return false
        }

        val image = try {
            Image(normalizedUri, false)
        } catch (exception: Exception) {
            showLoadError(uri, exception.message)
            return false
        }

        if (image.isError) {
            showLoadError(uri, image.exception?.message)
            return false
        }

        imageView.image = image
        return true
    }

    private fun showLoadError(uri: String, message: String?) {
        Alert(Alert.AlertType.ERROR).apply {
            title = "Image load failed"
            headerText = "Could not load image"
            contentText = buildString {
                appendLine("Failed to load image from:")
                append(uri)
                if (!message.isNullOrBlank()) {
                    appendLine()
                    appendLine()
                    append("Details: ")
                    append(message)
                }
            }
        }.showAndWait()
    }

    private fun normalizeSupportedActorImageUri(uri: String): String? {
        return LocalFiles.normalizeLocalFileUri(uri)
    }

    private fun scaleRatio(scaleX: Double, scaleY: Double): Double =
        if (abs(scaleX) <= SLIDER_VALUE_EPSILON) 1.0 else scaleY / scaleX

    private companion object {
        const val TOKEN_IMAGE_HISTORY_KEY = "tracker.token-image"
        const val PREVIEW_SIZE = 160.0
        const val PREVIEW_RADIUS = 70.0
        const val PREVIEW_CENTER = PREVIEW_SIZE / 2
        const val SLIDER_VALUE_EPSILON = 1e-9
    }
}
