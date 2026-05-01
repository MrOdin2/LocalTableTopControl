package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.ui.dialog.FileChooserHistoryStore
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DynamicMapGameplayExportSummary(
    val targetFile: File,
    val includedBackground: Boolean,
    val missingBackground: Boolean,
)

/**
 * Writes the gameplay-facing Dynamic Map bundle.
 *
 * This is intentionally separate from [DynamicMapDraftSerializer], which stores
 * editor metadata such as labels and groups for ongoing construction-site work.
 */
object DynamicMapGameplayExporter {
    const val DEFAULT_EXTENSION = "dynamicmap"
    const val MANIFEST_ENTRY = "dynamic-map.properties"

    fun export(
        document: DynamicMapDocument,
        targetFile: File,
    ): Result<DynamicMapGameplayExportSummary> = runCatching {
        val exportDocument = document.optimizeWallTopology()
        val backgroundFile = exportDocument.backgroundImageUri
            ?.let(FileChooserHistoryStore::fileFromUri)
            ?.takeIf { it.exists() && it.isFile }
        val backgroundEntry = backgroundFile?.let(::backgroundEntryName)

        targetFile.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(targetFile))).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(
                DynamicMapGameplayExportSerializer
                    .serialize(exportDocument, backgroundEntry)
                    .toByteArray(StandardCharsets.UTF_8),
            )
            zip.closeEntry()

            if (backgroundFile != null && backgroundEntry != null) {
                zip.putNextEntry(ZipEntry(backgroundEntry))
                Files.copy(backgroundFile.toPath(), zip)
                zip.closeEntry()
            }
        }

        DynamicMapGameplayExportSummary(
            targetFile = targetFile,
            includedBackground = backgroundFile != null,
            missingBackground = exportDocument.backgroundImageUri != null && backgroundFile == null,
        )
    }

    private fun backgroundEntryName(file: File): String {
        val extension = file.extension
            .lowercase(Locale.US)
            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
            ?: "bin"
        return "background/background.$extension"
    }
}

object DynamicMapGameplayExportSerializer {
    fun serialize(
        document: DynamicMapDocument,
        backgroundEntry: String?,
    ): String {
        val props = Properties()
        props.setProperty("format", "tabletopcontrol.dynamic-map")
        props.setProperty("format.version", "1")
        props.setProperty("map.cols", document.cols.toString())
        props.setProperty("map.rows", document.rows.toString())
        backgroundEntry?.let { props.setProperty("background.image", it) }
        props.setProperty("background.scale", document.backgroundCalibration.scale.toString())
        props.setProperty("background.offsetX", document.backgroundCalibration.offsetX.toString())
        props.setProperty("background.offsetY", document.backgroundCalibration.offsetY.toString())

        props.setProperty("walls.count", document.walls.size.toString())
        document.walls.forEachIndexed { index, wall ->
            val prefix = "wall.$index"
            props.setProperty("$prefix.startX", wall.start.x.toString())
            props.setProperty("$prefix.startY", wall.start.y.toString())
            props.setProperty("$prefix.endX", wall.end.x.toString())
            props.setProperty("$prefix.endY", wall.end.y.toString())
            props.setProperty("$prefix.kind", wall.kind.name)
        }

        props.setProperty("lights.count", document.lights.size.toString())
        document.lights.forEachIndexed { index, light ->
            val prefix = "light.$index"
            props.setProperty("$prefix.posX", light.position.x.toString())
            props.setProperty("$prefix.posY", light.position.y.toString())
            props.setProperty("$prefix.brightRadius", light.brightRadius.toString())
            props.setProperty("$prefix.dimRadius", light.dimRadius.toString())
            props.setProperty("$prefix.colorHex", light.colorHex)
            props.setProperty("$prefix.enabled", light.enabled.toString())
        }

        props.setProperty("sunlightAreas.count", document.sunlightAreas.size.toString())
        document.sunlightAreas.forEachIndexed { index, area ->
            val prefix = "sunlightArea.$index"
            props.setProperty("$prefix.points.count", area.points.size.toString())
            area.points.forEachIndexed { pointIndex, point ->
                val pointPrefix = "$prefix.point.$pointIndex"
                props.setProperty("$pointPrefix.x", point.x.toString())
                props.setProperty("$pointPrefix.y", point.y.toString())
            }
        }

        val writer = StringWriter()
        props.store(writer, "Dynamic Map gameplay export")
        return writer.toString()
    }
}
