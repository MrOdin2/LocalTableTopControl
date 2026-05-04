package com.tabletopcontrol.light.advanced

/**
 * Parses the JSON response returned by the WLED `s` serial command.
 *
 * The response follows the WLED HTTP JSON API structure with top-level
 * `state` and `info` objects.  Only the fields required by the Advanced
 * Lighting plugin are extracted; the rest are ignored.
 *
 * This parser is intentionally dependency-free.  It uses simple bracket
 * matching and regular expressions rather than a JSON library so that no
 * additional Gradle dependency is required.
 */
internal object WledJsonParser {

    /**
     * A WLED segment extracted from the `state.seg` array.
     *
     * @param id     Segment index as reported by the device.
     * @param start  First LED index in this segment.
     * @param stop   One-past-last LED index.
     * @param len    Number of LEDs.
     * @param on     Whether the segment is on.
     * @param bri    Raw brightness (0–255).
     * @param r      Red component of the first color (0–255).
     * @param g      Green component of the first color.
     * @param b      Blue component of the first color.
     * @param fx     Effect index.
     * @param sx     Effect speed (0–255).
     * @param ix     Effect intensity (0–255).
     */
    data class WledSegment(
        val id: Int,
        val start: Int,
        val stop: Int,
        val len: Int,
        val on: Boolean,
        val bri: Int,
        val r: Int,
        val g: Int,
        val b: Int,
        val fx: Int,
        val sx: Int,
        val ix: Int,
    )

    /**
     * Parses the segments from the WLED `s` command JSON response.
     *
     * Returns an empty list if the JSON cannot be parsed or contains no segments.
     */
    fun parseSegments(json: String): List<WledSegment> {
        val stateBlock = extractObjectForKey(json, "state") ?: return emptyList()
        val segArray = extractArrayForKey(stateBlock, "seg") ?: return emptyList()
        return parseObjectsInArray(segArray).mapNotNull { parseSegment(it) }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Extracts the raw JSON object value for the given [key] inside [json].
     *
     * Handles `"key":{...}` at any depth.  Returns `null` when not found.
     */
    private fun extractObjectForKey(json: String, key: String): String? {
        val searchKey = "\"$key\":"
        val keyIdx = json.indexOf(searchKey)
        if (keyIdx < 0) return null
        val objStart = json.indexOf('{', keyIdx + searchKey.length)
        if (objStart < 0) return null
        return extractBracketedContent(json, objStart, '{', '}')
    }

    /**
     * Extracts the raw JSON array value for the given [key] inside [json].
     *
     * Handles `"key":[...]` at any depth.  Returns `null` when not found.
     */
    private fun extractArrayForKey(json: String, key: String): String? {
        val searchKey = "\"$key\":"
        val keyIdx = json.indexOf(searchKey)
        if (keyIdx < 0) return null
        val arrStart = json.indexOf('[', keyIdx + searchKey.length)
        if (arrStart < 0) return null
        return extractBracketedContent(json, arrStart, '[', ']')
    }

    /**
     * Extracts the content from [start] up to and including the matching
     * closing bracket of [open]/[close], handling nesting.
     */
    private fun extractBracketedContent(text: String, start: Int, open: Char, close: Char): String? {
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /**
     * Splits a JSON array string (e.g. `[{...},{...}]`) into a list of raw
     * top-level object strings.
     */
    private fun parseObjectsInArray(arrayStr: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var objStart = -1
        for (i in arrayStr.indices) {
            when (arrayStr[i]) {
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objStart >= 0) {
                        result += arrayStr.substring(objStart, i + 1)
                        objStart = -1
                    }
                }
            }
        }
        return result
    }

    /**
     * Parses a single segment JSON object string into a [WledSegment].
     *
     * Returns `null` if the mandatory `id` field is missing or malformed.
     */
    private fun parseSegment(obj: String): WledSegment? {
        val id = getInt(obj, "id") ?: return null
        val start = getInt(obj, "start") ?: 0
        val stop = getInt(obj, "stop") ?: 0
        val len = getInt(obj, "len") ?: 0
        val on = getBool(obj, "on") ?: true
        val bri = getInt(obj, "bri") ?: 255
        val (r, g, b) = getFirstColor(obj) ?: Triple(255, 255, 255)
        val fx = getInt(obj, "fx") ?: 0
        val sx = getInt(obj, "sx") ?: 128
        val ix = getInt(obj, "ix") ?: 128
        return WledSegment(id, start, stop, len, on, bri, r, g, b, fx, sx, ix)
    }

    private val intRegexCache = mutableMapOf<String, Regex>()
    private val boolRegexCache = mutableMapOf<String, Regex>()

    private fun getInt(obj: String, key: String): Int? {
        val regex = intRegexCache.getOrPut(key) { Regex("\"$key\":(\\d+)") }
        return regex.find(obj)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun getBool(obj: String, key: String): Boolean? {
        val regex = boolRegexCache.getOrPut(key) { Regex("\"$key\":(true|false)") }
        return regex.find(obj)?.groupValues?.get(1)?.let {
            when (it) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
    }

    /**
     * Extracts the first `[R,G,B]` triple from the `"col":[[R,G,B],...]` field.
     */
    private fun getFirstColor(obj: String): Triple<Int, Int, Int>? {
        val colIdx = obj.indexOf("\"col\":")
        if (colIdx < 0) return null
        val outerStart = obj.indexOf('[', colIdx + 6)
        if (outerStart < 0) return null
        val innerStart = obj.indexOf('[', outerStart + 1)
        if (innerStart < 0) return null
        val innerEnd = obj.indexOf(']', innerStart)
        if (innerEnd < 0) return null
        val parts = obj.substring(innerStart + 1, innerEnd).split(',')
        if (parts.size < 3) return null
        val r = parts[0].trim().toIntOrNull() ?: return null
        val g = parts[1].trim().toIntOrNull() ?: return null
        val b = parts[2].trim().toIntOrNull() ?: return null
        return Triple(r, g, b)
    }
}
