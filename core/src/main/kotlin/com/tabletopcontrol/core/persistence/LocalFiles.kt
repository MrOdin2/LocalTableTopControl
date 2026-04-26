package com.tabletopcontrol.core.persistence

import java.io.File
import java.net.URI
import java.util.Locale

/**
 * Shared handling for local file paths and file URIs.
 */
object LocalFiles {
    fun fileFromUriOrPath(value: String?): File? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()

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

    fun normalizeLocalFileUri(value: String?): String? =
        runCatching { fileFromUriOrPath(value)?.canonicalFile?.toURI()?.toString() }.getOrNull()

    fun exists(value: String?): Boolean = runCatching { fileFromUriOrPath(value)?.exists() == true }.getOrDefault(false)

    fun absolutePath(value: String?): String? = runCatching { fileFromUriOrPath(value)?.absolutePath }.getOrNull()

    fun fileName(value: String?): String? =
        runCatching { fileFromUriOrPath(value)?.name?.takeIf { it.isNotBlank() } }.getOrNull()

    private val WINDOWS_ABSOLUTE_PATH = Regex("^[a-zA-Z]:[\\\\/].*")
}
