package com.tabletopcontrol.core

import javafx.application.HostServices
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Manages user-facing HTML documentation bundled inside the application JAR.
 *
 * On [openHelp], every documentation file is extracted from classpath resources
 * into `~/.tabletopcontrol/userdocs/`, preserving the relative path structure
 * required by inter-file links, and then the main index page is opened in the
 * system browser via JavaFX [HostServices].
 *
 * Extraction always overwrites existing files so the on-disk copy stays in sync
 * with the version bundled in the running JAR.
 */
object HelpManager {

    /** Classpath-rooted paths of all bundled documentation files. */
    private val DOC_RESOURCES = listOf(
        "userdocs/docs/index.html",
        "userdocs/docs/cross-plugin.html",
        "userdocs/docs/ui-primitives.html",
        "userdocs/map/UserDoc.html",
        "userdocs/audio/UserDoc.html",
        "userdocs/tracker/UserDoc.html",
        "userdocs/light/UserDoc.html",
    )

    /** Root directory where documentation files are extracted at runtime. */
    private val docsRoot: Path = Path.of(
        System.getProperty("user.home"), ".tabletopcontrol", "userdocs"
    )

    /**
     * Extracts all bundled documentation files to [docsRoot] and opens
     * `docs/index.html` in the system browser via [hostServices].
     *
     * Extraction runs on a daemon thread so the UI remains responsive.
     * [HostServices.showDocument] is called after extraction completes.
     * Any failure during extraction or browser launch is caught and logged
     * so the UI thread is never affected.
     */
    fun openHelp(hostServices: HostServices) {
        Thread {
            try {
                extractDocs()
                val indexUri = docsRoot.resolve("docs/index.html").toUri().toString()
                hostServices.showDocument(indexUri)
            } catch (e: Exception) {
                println("ERROR: Failed to open user documentation: ${e.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    /**
     * Copies every bundled documentation resource from the classpath into
     * [docsRoot], creating intermediate directories as needed.
     *
     * Missing resources are logged as warnings but do not abort the extraction
     * so the available docs can still be opened.
     */
    private fun extractDocs() {
        for (resourcePath in DOC_RESOURCES) {
            val relativePath = resourcePath.removePrefix("userdocs/")
            val dest = docsRoot.resolve(relativePath)
            Files.createDirectories(dest.parent)
            val stream = HelpManager::class.java.getResourceAsStream("/$resourcePath")
            if (stream != null) {
                stream.use { Files.copy(it, dest, StandardCopyOption.REPLACE_EXISTING) }
            } else {
                println("WARNING: Documentation resource not found in classpath: $resourcePath")
            }
        }
    }
}
