package com.tabletopcontrol.core.persistence

import java.io.File

/**
 * Non-fatal read/write wrappers for config persistence.
 */
object SafeConfigIO {

    /** Runs [block], swallowing exceptions and returning [fallback] on failure. */
    inline fun <T> readOrElse(fallback: T, block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        fallback
    }

    /** Returns file text, or `null` on failure. */
    fun readTextOrNull(file: File): String? = readOrElse(null) { file.readText() }

    /** Writes [text], swallowing exceptions. */
    fun writeText(file: File, text: String) {
        try {
            file.writeText(text)
        } catch (_: Exception) {
            // non-fatal — proceed without persistence
        }
    }

    /** Runs [block], swallowing exceptions. */
    inline fun run(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            // non-fatal — proceed without persistence
        }
    }
}
