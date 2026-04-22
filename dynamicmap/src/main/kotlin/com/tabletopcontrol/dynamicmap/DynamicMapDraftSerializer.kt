package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

/**
 * Persists the current builder draft independently from the public import/export format.
 *
 * This gives the standalone builder a stable autosave while the eventual exchange
 * format for Dynamic Map files is still being designed.
 */
object DynamicMapDraftSerializer {
    internal const val CONFIG_NAME = "dynamic-map-builder-draft.properties"

    private val configFile
        get() = AppConfigPaths.configFile(CONFIG_NAME)

    fun serialize(document: DynamicMapDocument): String {
        val props = Properties()
        props.setProperty("map.cols", document.cols.toString())
        props.setProperty("map.rows", document.rows.toString())
        props.setProperty("visibility.background", document.visibility.background.toString())
        props.setProperty("visibility.walls", document.visibility.walls.toString())
        props.setProperty("visibility.lights", document.visibility.lights.toString())
        props.setProperty("visibility.grid", document.visibility.grid.toString())
        document.backgroundImageUri?.let { props.setProperty("background.imageUri", it) }
        document.backgroundDisplayPath?.let { props.setProperty("background.displayPath", it) }

        props.setProperty("walls.count", document.walls.size.toString())
        document.walls.forEachIndexed { index, wall ->
            val prefix = "wall.$index"
            props.setProperty("$prefix.id", wall.id)
            props.setProperty("$prefix.startX", wall.start.x.toString())
            props.setProperty("$prefix.startY", wall.start.y.toString())
            props.setProperty("$prefix.endX", wall.end.x.toString())
            props.setProperty("$prefix.endY", wall.end.y.toString())
        }

        props.setProperty("lights.count", document.lights.size.toString())
        document.lights.forEachIndexed { index, light ->
            val prefix = "light.$index"
            props.setProperty("$prefix.id", light.id)
            props.setProperty("$prefix.label", light.label)
            props.setProperty("$prefix.posX", light.position.x.toString())
            props.setProperty("$prefix.posY", light.position.y.toString())
            props.setProperty("$prefix.brightRadius", light.brightRadius.toString())
            props.setProperty("$prefix.dimRadius", light.dimRadius.toString())
            props.setProperty("$prefix.colorHex", light.colorHex)
            props.setProperty("$prefix.enabled", light.enabled.toString())
        }

        val writer = StringWriter()
        props.store(writer, "Dynamic Map Builder draft")
        return writer.toString()
    }

    fun deserialize(text: String): DynamicMapDocument? {
        if (text.isBlank()) return null
        return try {
            val props = Properties()
            props.load(StringReader(text))
            val cols = props.getProperty("map.cols")?.toIntOrNull()?.coerceAtLeast(5) ?: 30
            val rows = props.getProperty("map.rows")?.toIntOrNull()?.coerceAtLeast(5) ?: 20
            val visibility = DynamicMapLayerVisibility(
                background = props.getProperty("visibility.background")?.toBooleanStrictOrNull() ?: true,
                walls = props.getProperty("visibility.walls")?.toBooleanStrictOrNull() ?: true,
                lights = props.getProperty("visibility.lights")?.toBooleanStrictOrNull() ?: true,
                grid = props.getProperty("visibility.grid")?.toBooleanStrictOrNull() ?: true,
            )

            val walls = buildList {
                val count = props.getProperty("walls.count")?.toIntOrNull() ?: 0
                repeat(count) { index ->
                    val prefix = "wall.$index"
                    val startX = props.getProperty("$prefix.startX")?.toDoubleOrNull() ?: return@repeat
                    val startY = props.getProperty("$prefix.startY")?.toDoubleOrNull() ?: return@repeat
                    val endX = props.getProperty("$prefix.endX")?.toDoubleOrNull() ?: return@repeat
                    val endY = props.getProperty("$prefix.endY")?.toDoubleOrNull() ?: return@repeat
                    add(
                        DynamicMapWall(
                            id = props.getProperty("$prefix.id") ?: "wall-$index",
                            start = DynamicMapPoint(startX, startY),
                            end = DynamicMapPoint(endX, endY),
                        ),
                    )
                }
            }

            val lights = buildList {
                val count = props.getProperty("lights.count")?.toIntOrNull() ?: 0
                repeat(count) { index ->
                    val prefix = "light.$index"
                    val posX = props.getProperty("$prefix.posX")?.toDoubleOrNull() ?: return@repeat
                    val posY = props.getProperty("$prefix.posY")?.toDoubleOrNull() ?: return@repeat
                    val brightRadius = props.getProperty("$prefix.brightRadius")?.toDoubleOrNull() ?: return@repeat
                    val dimRadius = props.getProperty("$prefix.dimRadius")?.toDoubleOrNull() ?: return@repeat
                    add(
                        DynamicMapLight(
                            id = props.getProperty("$prefix.id") ?: "light-$index",
                            label = props.getProperty("$prefix.label") ?: "Light",
                            position = DynamicMapPoint(posX, posY),
                            brightRadius = brightRadius,
                            dimRadius = dimRadius,
                            colorHex = props.getProperty("$prefix.colorHex") ?: DynamicMapLightPresets.defaultPreset.colorHex,
                            enabled = props.getProperty("$prefix.enabled")?.toBooleanStrictOrNull() ?: true,
                        ),
                    )
                }
            }

            DynamicMapDocument(
                cols = cols,
                rows = rows,
                backgroundImageUri = props.getProperty("background.imageUri"),
                backgroundDisplayPath = props.getProperty("background.displayPath"),
                visibility = visibility,
                walls = walls,
                lights = lights,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(document: DynamicMapDocument) {
        SafeConfigIO.writeText(configFile, serialize(document))
    }

    fun load(): DynamicMapDocument =
        SafeConfigIO.readOrElse(DynamicMapDocument()) {
            deserialize(configFile.readText()) ?: DynamicMapDocument()
        }
}
