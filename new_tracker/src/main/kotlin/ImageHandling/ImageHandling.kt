package com.tabletopcontrol.new_tracker.ImageHandling

import com.tabletopcontrol.core.ui.dialog.DialogFlows
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorImageSettings
import javafx.geometry.Insets
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle
import javafx.scene.shape.Shape
import javafx.scene.transform.Scale
import javafx.stage.FileChooser
import javafx.stage.Window
import java.io.File
import java.net.URI
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

    internal fun normalizeSupportedActorImageUri(uri: String): String? {
        return try {
            val trimmed = uri.trim()
            if (trimmed.isEmpty()) {
                return null
            }

            if (WINDOWS_ABSOLUTE_PATH.matches(trimmed) || trimmed.startsWith("\\\\")) {
                return File(trimmed).canonicalFile.toURI().toString()
            }

            val parsed = URI(trimmed)
            val scheme = parsed.scheme?.lowercase(Locale.ROOT)

            when {
                scheme == null || scheme.isEmpty() -> File(trimmed).canonicalFile.toURI().toString()
                scheme == "file" -> parsed.normalize().toString()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
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

        val tokenCircle = Circle(PREVIEW_CENTER, PREVIEW_CENTER, PREVIEW_RADIUS).apply {
            style = "-fx-fill: transparent; -fx-stroke: -tc-border; -fx-stroke-width: 1.5;"
            isMouseTransparent = true
        }

        val outsideOverlay = Shape.subtract(
            Rectangle(0.0, 0.0, PREVIEW_SIZE, PREVIEW_SIZE),
            Circle(PREVIEW_CENTER, PREVIEW_CENTER, PREVIEW_RADIUS),
        ).apply {
            style = "-fx-fill: -tc-bg;"
            isMouseTransparent = true
        }

        val previewPane = StackPane(
            previewFrame,
            imageView,
            outsideOverlay,
            tokenCircle,
        ).apply {
            minWidth = PREVIEW_SIZE
            maxWidth = PREVIEW_SIZE
            minHeight = PREVIEW_SIZE
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

        val scaleXSlider = Slider(0.3, 3.0, working.scaleX).apply { isShowTickLabels = true }
        val scaleYSlider = Slider(0.3, 3.0, working.scaleY).apply { isShowTickLabels = true }
        val offsetXSlider = Slider(-80.0, 80.0, working.offsetX).apply { isShowTickLabels = true }
        val offsetYSlider = Slider(-80.0, 80.0, working.offsetY).apply { isShowTickLabels = true }

        val scaleXField = TextField().apply { prefColumnCount = 6 }
        val scaleYField = TextField().apply { prefColumnCount = 6 }
        val offsetXField = TextField().apply { prefColumnCount = 6 }
        val offsetYField = TextField().apply { prefColumnCount = 6 }

        bindSliderToField(scaleXSlider, scaleXField, decimals = 2)
        bindSliderToField(scaleYSlider, scaleYField, decimals = 2)
        bindSliderToField(offsetXSlider, offsetXField, decimals = 1)
        bindSliderToField(offsetYSlider, offsetYField, decimals = 1)

        scaleXSlider.valueProperty().addListener { _, _, value ->
            working = working.copy(scaleX = value.toDouble())
            applyTransforms(working)
        }
        scaleYSlider.valueProperty().addListener { _, _, value ->
            working = working.copy(scaleY = value.toDouble())
            applyTransforms(working)
        }
        offsetXSlider.valueProperty().addListener { _, _, value ->
            working = working.copy(offsetX = value.toDouble())
            applyTransforms(working)
        }
        offsetYSlider.valueProperty().addListener { _, _, value ->
            working = working.copy(offsetY = value.toDouble())
            applyTransforms(working)
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
                }
                val file = chooser.showOpenDialog(owner)
                if (file != null) {
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

    private companion object {
        val WINDOWS_ABSOLUTE_PATH = Regex("^[a-zA-Z]:[\\\\/].*")
        const val PREVIEW_SIZE = 160.0
        const val PREVIEW_RADIUS = 70.0
        const val PREVIEW_CENTER = PREVIEW_SIZE / 2
        const val SLIDER_VALUE_EPSILON = 1e-9
    }
}