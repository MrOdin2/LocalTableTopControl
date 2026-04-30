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
        props.setProperty("background.scale", document.backgroundCalibration.scale.toString())
        props.setProperty("background.offsetX", document.backgroundCalibration.offsetX.toString())
        props.setProperty("background.offsetY", document.backgroundCalibration.offsetY.toString())

        props.setProperty("walls.count", document.walls.size.toString())
        document.walls.forEachIndexed { index, wall ->
            val prefix = "wall.$index"
            props.setProperty("$prefix.id", wall.id)
            props.setProperty("$prefix.label", wall.label)
            props.setProperty("$prefix.startX", wall.start.x.toString())
            props.setProperty("$prefix.startY", wall.start.y.toString())
            props.setProperty("$prefix.endX", wall.end.x.toString())
            props.setProperty("$prefix.endY", wall.end.y.toString())
            props.setProperty("$prefix.kind", wall.kind.name)
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

        props.setProperty("sunlightAreas.count", document.sunlightAreas.size.toString())
        document.sunlightAreas.forEachIndexed { index, area ->
            val prefix = "sunlightArea.$index"
            props.setProperty("$prefix.id", area.id)
            props.setProperty("$prefix.label", area.label)
            props.setProperty("$prefix.points.count", area.points.size.toString())
            area.points.forEachIndexed { pointIndex, point ->
                val pointPrefix = "$prefix.point.$pointIndex"
                props.setProperty("$pointPrefix.x", point.x.toString())
                props.setProperty("$pointPrefix.y", point.y.toString())
            }
        }

        props.setProperty("groups.count", document.groups.size.toString())
        document.groups.forEachIndexed { index, group ->
            val prefix = "group.$index"
            props.setProperty("$prefix.id", group.id)
            props.setProperty("$prefix.label", group.label)
            props.setProperty("$prefix.elements.count", group.elements.size.toString())
            group.elements.forEachIndexed { elementIndex, selection ->
                val elementPrefix = "$prefix.element.$elementIndex"
                props.setProperty("$elementPrefix.kind", selection.kind.name)
                props.setProperty("$elementPrefix.id", selection.elementId)
            }
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
            val backgroundCalibration = DynamicMapBackgroundCalibration(
                scale = props.getProperty("background.scale")?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0,
                offsetX = props.getProperty("background.offsetX")?.toDoubleOrNull() ?: 0.0,
                offsetY = props.getProperty("background.offsetY")?.toDoubleOrNull() ?: 0.0,
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
                            label = props.getProperty("$prefix.label") ?: "Wall ${index + 1}",
                            start = DynamicMapPoint(startX, startY),
                            end = DynamicMapPoint(endX, endY),
                            kind = DynamicMapWallKind.fromPersistence(props.getProperty("$prefix.kind")),
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

            val sunlightAreas = buildList {
                val count = props.getProperty("sunlightAreas.count")?.toIntOrNull() ?: 0
                repeat(count) { index ->
                    val prefix = "sunlightArea.$index"
                    val pointCount = props.getProperty("$prefix.points.count")?.toIntOrNull() ?: return@repeat
                    val points = buildList {
                        repeat(pointCount) { pointIndex ->
                            val pointPrefix = "$prefix.point.$pointIndex"
                            val x = props.getProperty("$pointPrefix.x")?.toDoubleOrNull() ?: return@repeat
                            val y = props.getProperty("$pointPrefix.y")?.toDoubleOrNull() ?: return@repeat
                            add(DynamicMapPoint(x, y))
                        }
                    }
                    val polygon = sanitizedSunlightPolygon(points)
                    if (isValidSunlightPolygon(polygon)) {
                        add(
                            DynamicMapSunlightArea(
                                id = props.getProperty("$prefix.id") ?: "sunlight-area-$index",
                                label = props.getProperty("$prefix.label") ?: "Sunlight Area ${index + 1}",
                                points = polygon,
                            ),
                        )
                    }
                }
            }

            val groups = buildList {
                val count = props.getProperty("groups.count")?.toIntOrNull() ?: 0
                repeat(count) { index ->
                    val prefix = "group.$index"
                    val elements = buildSet {
                        val elementCount = props.getProperty("$prefix.elements.count")?.toIntOrNull() ?: 0
                        repeat(elementCount) { elementIndex ->
                            val elementPrefix = "$prefix.element.$elementIndex"
                            val kindName = props.getProperty("$elementPrefix.kind") ?: return@repeat
                            val kind = DynamicMapElementKind.values().firstOrNull { it.name == kindName } ?: return@repeat
                            val elementId = props.getProperty("$elementPrefix.id") ?: return@repeat
                            add(DynamicMapElementSelection(kind = kind, elementId = elementId))
                        }
                    }
                    if (elements.isNotEmpty()) {
                        add(
                            DynamicMapElementGroup(
                                id = props.getProperty("$prefix.id") ?: "group-$index",
                                label = props.getProperty("$prefix.label") ?: "Group ${index + 1}",
                                elements = elements,
                            ),
                        )
                    }
                }
            }

            DynamicMapDocument(
                cols = cols,
                rows = rows,
                backgroundImageUri = props.getProperty("background.imageUri"),
                backgroundDisplayPath = props.getProperty("background.displayPath"),
                backgroundCalibration = backgroundCalibration,
                visibility = visibility,
                walls = walls,
                lights = lights,
                sunlightAreas = sunlightAreas,
                groups = groups,
            ).pruneInvalidGroups()
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
