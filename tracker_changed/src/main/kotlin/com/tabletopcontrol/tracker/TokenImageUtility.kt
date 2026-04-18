package com.tabletopcontrol.tracker

import javafx.scene.control.Alert
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.transform.Scale
import java.io.File
import java.net.URI
import java.util.Locale
import kotlin.text.isEmpty

class TokenImageUtility {



    fun loadImage(uri: String?, imageView: ImageView): Boolean {
        if (uri == null) {
            imageView.image = null
            return true
        }
        val normalizedUri = normalizeSupportedTokenImageUri(uri)
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
        } catch (e: Exception) {
            Alert(Alert.AlertType.ERROR).apply {
                title = "Image load failed"
                headerText = "Could not load image"
                contentText = buildString {
                    appendLine("Failed to load image from:")
                    appendLine(uri)
                    val message = e.message
                    if (!message.isNullOrBlank()) {
                        appendLine()
                        append("Details: ")
                        append(message)
                    }
                }
            }.showAndWait()
            return false
        }

        if (image.isError) {
            val message = image.exception?.message
            Alert(Alert.AlertType.ERROR).apply {
                title = "Image load failed"
                headerText = "Could not load image"
                contentText = buildString {
                    appendLine("Failed to load image from:")
                    appendLine(uri)
                    if (!message.isNullOrBlank()) {
                        appendLine()
                        append("Details: ")
                        append(message)
                    }
                }
            }.showAndWait()
            return false
        }

        imageView.image = image
        return true
    }

    //methodResponsibility: Token Image Handling, Utility/Formatting
    internal fun normalizeSupportedTokenImageUri(uri: String): String? {
        return try {
            val trimmed = uri.trim()
            if (trimmed.isEmpty()) {
                return null
            }

            val parsed = URI(trimmed)
            val scheme = parsed.scheme?.lowercase(Locale.ROOT)

            when {
                // No scheme → treat as a local file system path, return canonical file: URI.
                scheme == null || scheme.isEmpty() ->
                    File(trimmed).canonicalFile.toURI().toString()

                // Explicit file: URI → normalize the URI representation and return it.
                scheme == "file" ->
                    parsed.normalize().toString()

                // Any other scheme (http, https, etc.) is not supported.
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun initImageView(): ImageView {
        val imageView = ImageView().apply {
            fitWidth = TrackerPlugin.PREVIEW_RADIUS * 2
            fitHeight = TrackerPlugin.PREVIEW_RADIUS * 2
            isPreserveRatio = true
        }
        val scaleTransform = Scale(working.scaleX, working.scaleY, TrackerPlugin.PREVIEW_RADIUS, TrackerPlugin.PREVIEW_RADIUS)
        imageView.transforms.setAll(scaleTransform)
    }
}