package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.util.Locale
import java.util.Properties

private const val CONSTRUCTION_SITES_DIR = "dynamic-map-construction-sites"
private const val ACTIVE_SITE_CONFIG_NAME = "dynamic-map-builder-active-site.properties"
private const val SITE_FILE_EXTENSION = "properties"
private const val SITE_NAME_PROPERTY = "constructionSite.name"
private const val ACTIVE_SITE_ID_PROPERTY = "active.id"

data class DynamicMapConstructionSiteSummary(
    val id: String,
    val name: String,
    val lastModifiedMillis: Long,
)

class DynamicMapConstructionSiteStore(
    private val sitesDir: File = AppConfigPaths.configSubDir(CONSTRUCTION_SITES_DIR),
    private val activeSiteFile: File = AppConfigPaths.configFile(ACTIVE_SITE_CONFIG_NAME),
) {
    fun listSites(): List<DynamicMapConstructionSiteSummary> =
        SafeConfigIO.readOrElse(emptyList()) {
            sitesDir.mkdirs()
            sitesDir
                .listFiles { file -> file.isFile && file.extension == SITE_FILE_EXTENSION }
                ?.mapNotNull(::summaryForFile)
                ?.sortedWith(compareBy({ it.name.lowercase(Locale.US) }, { it.id }))
                .orEmpty()
        }

    fun createSite(
        requestedName: String,
        document: DynamicMapDocument,
    ): DynamicMapConstructionSiteSummary? {
        val name = cleanConstructionSiteName(requestedName)
        val id = uniqueSiteId(siteIdForName(name))
        return saveSite(id = id, name = name, document = document)
    }

    fun saveSite(
        id: String,
        name: String,
        document: DynamicMapDocument,
    ): DynamicMapConstructionSiteSummary? {
        if (!isValidSiteId(id)) return null
        sitesDir.mkdirs()
        val file = fileForId(id)
        val cleanedName = cleanConstructionSiteName(name)
        val text = constructionSiteText(cleanedName, document)
        SafeConfigIO.writeText(file, text)
        return summaryForFile(file) ?: DynamicMapConstructionSiteSummary(
            id = id,
            name = cleanedName,
            lastModifiedMillis = file.lastModified(),
        )
    }

    fun loadSite(id: String): DynamicMapDocument? {
        if (!isValidSiteId(id)) return null
        val file = fileForId(id)
        return SafeConfigIO.readOrElse(null) {
            DynamicMapDraftSerializer.deserialize(file.readText())
        }
    }

    fun deleteSite(id: String) {
        if (!isValidSiteId(id)) return
        SafeConfigIO.run {
            fileForId(id).delete()
        }
    }

    fun summaryForId(id: String): DynamicMapConstructionSiteSummary? {
        if (!isValidSiteId(id)) return null
        return summaryForFile(fileForId(id))
    }

    fun loadActiveSite(): DynamicMapConstructionSiteSummary? =
        SafeConfigIO.readOrElse(null) {
            if (!activeSiteFile.exists()) return@readOrElse null
            val props = Properties()
            props.load(StringReader(activeSiteFile.readText()))
            props.getProperty(ACTIVE_SITE_ID_PROPERTY)?.let(::summaryForId)
        }

    fun saveActiveSite(site: DynamicMapConstructionSiteSummary?) {
        if (site == null) {
            SafeConfigIO.run { activeSiteFile.delete() }
            return
        }
        val props = Properties()
        props.setProperty(ACTIVE_SITE_ID_PROPERTY, site.id)
        val writer = StringWriter()
        props.store(writer, "Dynamic Map Builder active construction site")
        SafeConfigIO.writeText(activeSiteFile, writer.toString())
    }

    private fun summaryForFile(file: File): DynamicMapConstructionSiteSummary? {
        if (!file.exists() || file.extension != SITE_FILE_EXTENSION) return null
        val id = file.nameWithoutExtension
        if (!isValidSiteId(id)) return null
        val name = readSiteName(file) ?: id.replace('-', ' ').replaceFirstChar { it.titlecase(Locale.US) }
        return DynamicMapConstructionSiteSummary(
            id = id,
            name = name,
            lastModifiedMillis = file.lastModified(),
        )
    }

    private fun readSiteName(file: File): String? =
        SafeConfigIO.readOrElse(null) {
            val props = Properties()
            props.load(StringReader(file.readText()))
            props.getProperty(SITE_NAME_PROPERTY)?.trim()?.takeIf { it.isNotEmpty() }
        }

    private fun uniqueSiteId(baseId: String): String {
        var candidate = baseId
        var suffix = 2
        while (fileForId(candidate).exists()) {
            candidate = "$baseId-$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun fileForId(id: String): File = File(sitesDir, "$id.$SITE_FILE_EXTENSION")
}

private fun constructionSiteText(
    name: String,
    document: DynamicMapDocument,
): String {
    val props = Properties()
    props.load(StringReader(DynamicMapDraftSerializer.serialize(document)))
    props.setProperty(SITE_NAME_PROPERTY, name)
    val writer = StringWriter()
    props.store(writer, "Dynamic Map Builder construction site")
    return writer.toString()
}

private fun cleanConstructionSiteName(name: String): String =
    name.trim().takeIf { it.isNotEmpty() }?.take(80) ?: "Untitled Construction Site"

private fun siteIdForName(name: String): String {
    val id = name
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return id.ifBlank { "construction-site" }
}

private fun isValidSiteId(id: String): Boolean =
    id.matches(Regex("[a-z0-9][a-z0-9-]*"))
