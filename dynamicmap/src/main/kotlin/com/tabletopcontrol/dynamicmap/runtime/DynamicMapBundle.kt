package com.tabletopcontrol.dynamicmap.runtime

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.zip.ZipFile
import javafx.scene.image.Image

data class DynamicMapBundle(
    val sourcePath: String,
    val displayPath: String,
    val cols: Int,
    val rows: Int,
    val backgroundEntry: String?,
    val backgroundBytes: ByteArray?,
    val backgroundCalibration: DynamicMapRuntimeBackgroundCalibration,
    val walls: List<DynamicMapRuntimeWall>,
    val lights: List<DynamicMapRuntimeLight>,
) {
    fun createBackgroundImage(): Image? {
        val bytes = backgroundBytes ?: return null
        return runCatching {
            Image(ByteArrayInputStream(bytes))
        }.getOrNull()?.takeUnless { it.isError }
    }
}

data class DynamicMapRuntimeBackgroundCalibration(
    val scale: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
) {
    init {
        require(scale > 0.0) { "scale must be positive, was $scale" }
    }
}

data class DynamicMapRuntimePoint(
    val x: Double,
    val y: Double,
)

data class DynamicMapRuntimeWall(
    val start: DynamicMapRuntimePoint,
    val end: DynamicMapRuntimePoint,
)

data class DynamicMapRuntimeLight(
    val position: DynamicMapRuntimePoint,
    val brightRadius: Double,
    val dimRadius: Double,
    val colorHex: String,
    val enabled: Boolean,
)

object DynamicMapBundleLoader {
    const val DEFAULT_EXTENSION = "dynamicmap"
    const val MANIFEST_ENTRY = "dynamic-map.properties"

    fun load(file: File): MapResult<DynamicMapBundle> {
        if (!file.exists() || !file.isFile) {
            return MapResult.failure(
                MapOperationError.DynamicMapLoadFailed(
                    resourcePath = file.absolutePath,
                    causeMessage = "The selected file does not exist.",
                ),
            )
        }

        return try {
            ZipFile(file).use { zip ->
                val manifest = zip.getEntry(MANIFEST_ENTRY)
                    ?: return MapResult.failure(
                        MapOperationError.DynamicMapLoadFailed(
                            resourcePath = file.absolutePath,
                            causeMessage = "The bundle is missing $MANIFEST_ENTRY.",
                        ),
                    )
                val props = zip.getInputStream(manifest).use { input ->
                    Properties().apply {
                        load(InputStreamReader(input, StandardCharsets.UTF_8))
                    }
                }
                val parsed = parseProperties(file, props)
                if (parsed is MapResult.Failure) {
                    return parsed
                }
                val base = (parsed as MapResult.Success).value
                val backgroundBytes = base.backgroundEntry
                    ?.takeIf(::isSafeZipEntryName)
                    ?.let(zip::getEntry)
                    ?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }

                MapResult.success(base.copy(backgroundBytes = backgroundBytes))
            }
        } catch (error: Exception) {
            MapResult.failure(
                MapOperationError.DynamicMapLoadFailed(
                    resourcePath = file.absolutePath,
                    causeMessage = error.message ?: error::class.java.simpleName,
                ),
            )
        }
    }

    private fun parseProperties(file: File, props: Properties): MapResult<DynamicMapBundle> {
        if (props.getProperty("format") != "tabletopcontrol.dynamic-map") {
            return failure(file, "This is not a TabletopControl Dynamic Map bundle.")
        }
        if (props.getProperty("format.version") != "1") {
            return failure(file, "Unsupported Dynamic Map bundle version.")
        }

        val cols = props.positiveInt("map.cols") ?: return failure(file, "Missing or invalid map width.")
        val rows = props.positiveInt("map.rows") ?: return failure(file, "Missing or invalid map height.")
        val backgroundScale = props.positiveDouble("background.scale") ?: 1.0
        val backgroundCalibration = DynamicMapRuntimeBackgroundCalibration(
            scale = backgroundScale,
            offsetX = props.double("background.offsetX") ?: 0.0,
            offsetY = props.double("background.offsetY") ?: 0.0,
        )
        val backgroundEntry = props.getProperty("background.image")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return MapResult.success(
            DynamicMapBundle(
                sourcePath = file.absolutePath,
                displayPath = file.absolutePath,
                cols = cols,
                rows = rows,
                backgroundEntry = backgroundEntry,
                backgroundBytes = null,
                backgroundCalibration = backgroundCalibration,
                walls = parseWalls(props),
                lights = parseLights(props),
            ),
        )
    }

    private fun parseWalls(props: Properties): List<DynamicMapRuntimeWall> =
        buildList {
            val count = props.nonNegativeInt("walls.count") ?: 0
            repeat(count) { index ->
                val prefix = "wall.$index"
                val startX = props.double("$prefix.startX") ?: return@repeat
                val startY = props.double("$prefix.startY") ?: return@repeat
                val endX = props.double("$prefix.endX") ?: return@repeat
                val endY = props.double("$prefix.endY") ?: return@repeat
                add(
                    DynamicMapRuntimeWall(
                        start = DynamicMapRuntimePoint(startX, startY),
                        end = DynamicMapRuntimePoint(endX, endY),
                    ),
                )
            }
        }

    private fun parseLights(props: Properties): List<DynamicMapRuntimeLight> =
        buildList {
            val count = props.nonNegativeInt("lights.count") ?: 0
            repeat(count) { index ->
                val prefix = "light.$index"
                val posX = props.double("$prefix.posX") ?: return@repeat
                val posY = props.double("$prefix.posY") ?: return@repeat
                val brightRadius = props.nonNegativeDouble("$prefix.brightRadius") ?: return@repeat
                val dimRadius = props.nonNegativeDouble("$prefix.dimRadius") ?: return@repeat
                add(
                    DynamicMapRuntimeLight(
                        position = DynamicMapRuntimePoint(posX, posY),
                        brightRadius = brightRadius,
                        dimRadius = dimRadius,
                        colorHex = props.getProperty("$prefix.colorHex") ?: "#ffffff",
                        enabled = props.getProperty("$prefix.enabled")?.toBooleanStrictOrNull() ?: true,
                    ),
                )
            }
        }

    private fun failure(file: File, message: String): MapResult<Nothing> =
        MapResult.failure(
            MapOperationError.DynamicMapLoadFailed(
                resourcePath = file.absolutePath,
                causeMessage = message,
            ),
        )

    private fun isSafeZipEntryName(name: String): Boolean =
        name.isNotBlank() &&
            !name.startsWith("/") &&
            !name.startsWith("\\") &&
            !name.contains("..") &&
            !File(name).isAbsolute

    private fun Properties.double(key: String): Double? =
        getProperty(key)?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun Properties.positiveDouble(key: String): Double? =
        double(key)?.takeIf { it > 0.0 }

    private fun Properties.nonNegativeDouble(key: String): Double? =
        double(key)?.takeIf { it >= 0.0 }

    private fun Properties.positiveInt(key: String): Int? =
        getProperty(key)?.toIntOrNull()?.takeIf { it > 0 }

    private fun Properties.nonNegativeInt(key: String): Int? =
        getProperty(key)?.toIntOrNull()?.takeIf { it >= 0 }
}
