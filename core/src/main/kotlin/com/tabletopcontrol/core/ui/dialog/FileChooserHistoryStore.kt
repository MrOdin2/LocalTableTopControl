package com.tabletopcontrol.core.ui.dialog

import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
import javafx.stage.FileChooser
import java.io.File
import java.net.URI
import java.util.Locale
import java.util.Properties

/**
 * Persists the last-used directory for keyed file chooser flows.
 */
object FileChooserHistoryStore {
    internal const val CONFIG_NAME = "file-chooser-history.conf"

    private val configFile
        get() = AppConfigPaths.configFile(CONFIG_NAME)

    /**
     * Applies a remembered initial directory to [chooser] when one exists.
     *
     * If no directory has been persisted for [key], [fallbackSelection] is used instead.
     */
    fun configureInitialDirectory(
        chooser: FileChooser,
        key: String,
        fallbackSelection: File? = null,
    ) {
        initialDirectoryFor(key, fallbackSelection)?.let { directory ->
            SafeConfigIO.run {
                chooser.initialDirectory = directory
            }
        }
    }

    /**
     * Remembers the parent directory for the selected file.
     */
    fun rememberSelection(key: String, selection: File) {
        val directory = existingDirectoryFor(selection) ?: return
        val properties = loadProperties().apply {
            setProperty(key, directory.absolutePath)
        }
        SafeConfigIO.run {
            configFile.writer().use { writer ->
                properties.store(writer, "TabletopControl file chooser history")
            }
        }
    }

    /**
     * Converts a supported local-path URI into a [File] for chooser fallback purposes.
     */
    fun fileFromUri(uri: String?): File? {
        if (uri.isNullOrBlank()) return null
        val trimmed = uri.trim()

        return try {
            when {
                WINDOWS_ABSOLUTE_PATH.matches(trimmed) || trimmed.startsWith("\\\\") -> File(trimmed)
                else -> {
                    val parsed = URI(trimmed)
                    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
                    when {
                        scheme.isNullOrEmpty() -> File(trimmed)
                        scheme == "file" -> File(parsed)
                        else -> null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun initialDirectoryFor(key: String, fallbackSelection: File? = null): File? {
        val rememberedDirectory = loadProperties()
            .getProperty(key)
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.let(::existingDirectory)

        return rememberedDirectory ?: existingDirectoryFor(fallbackSelection)
    }

    internal fun existingDirectoryFor(selection: File?): File? {
        if (selection == null) return null
        existingDirectory(selection)?.let { return it }

        val parent = selection.parentFile
        return existingDirectory(parent)
    }

    private fun existingDirectory(directory: File?): File? =
        if (directory != null && directory.exists() && directory.isDirectory) directory else null

    private fun loadProperties(): Properties = SafeConfigIO.readOrElse(Properties()) {
        Properties().also { properties ->
            configFile.reader().use { reader ->
                properties.load(reader)
            }
        }
    }

    private val WINDOWS_ABSOLUTE_PATH = Regex("^[a-zA-Z]:[\\\\/].*")
}
