package com.tabletopcontrol.core.persistence

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Shared helpers for config and library files stored under the application data directory.
 */
object ConfigFiles {

    fun sanitizeFilename(name: String, maxLength: Int = 200): String =
        name.map { char ->
            if (char.isLetterOrDigit() || char in " .-_") {
                char
            } else {
                '_'
            }
        }
            .joinToString("")
            .trim()
            .ifEmpty { "_" }
            .take(maxLength)

    fun writeTextAtomically(target: File, text: String) {
        var tmp: File? = null
        try {
            val parent = target.parentFile ?: return
            parent.mkdirs()
            val tempPrefix = target.nameWithoutExtension.ifBlank { target.name.ifBlank { "config" } }
            tmp = Files.createTempFile(parent.toPath(), tempPrefix, ".tmp").toFile()
            tmp.writeText(text)
            try {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
            SafeConfigIO.run { tmp?.delete() }
        }
    }

    fun openDirectory(directory: File) {
        val target = directory.also { it.mkdirs() }
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(target)
            } else {
                ProcessBuilder("xdg-open", target.absolutePath).start()
            }
        } catch (_: Exception) {
            try {
                ProcessBuilder("xdg-open", target.absolutePath).start()
            } catch (_: Exception) {
                // Best effort only.
            }
        }
    }
}
