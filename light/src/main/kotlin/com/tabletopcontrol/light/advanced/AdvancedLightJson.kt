package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightController
import com.tabletopcontrol.light.LightEffect
import kotlin.math.roundToInt

internal object AdvancedLightJson {
    private val HEX_COLOR_REGEX = Regex("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$")

    fun isValidHexColor(value: String): Boolean = HEX_COLOR_REGEX.matches(value)

    fun parseDeviceSnapshot(rawText: String): WledDeviceSnapshot {
        val jsonText = extractFirstJsonObject(rawText)
            ?: throw IllegalArgumentException("No JSON object found in WLED response")
        val root = SimpleJsonParser(jsonText).parse() as? JsonValue.JsonObject
            ?: throw IllegalArgumentException("WLED response root must be an object")
        val state = root.objectValue("state")
            ?: throw IllegalArgumentException("WLED response is missing state")
        val segmentArray = state.arrayValue("seg").orEmpty()

        return WledDeviceSnapshot(
            segments = segmentArray.mapNotNull { it.asObjectOrNull()?.toSegmentSnapshotOrNull() },
        )
    }

    fun buildSegmentCommand(commands: List<AdvancedLightSegmentCommand>): String {
        require(commands.isNotEmpty()) { "At least one segment command is required" }
        val segmentPayload = commands.joinToString(separator = ",") { command ->
            require(command.brightness in 0.0..1.0) {
                "Brightness must be 0.0-1.0, was ${command.brightness}"
            }
            require(command.effectSpeed in 0..255) {
                "Speed must be 0-255, was ${command.effectSpeed}"
            }
            require(command.effectIntensity in 0..255) {
                "Intensity must be 0-255, was ${command.effectIntensity}"
            }
            val (red, green, blue) = hexToRgb(command.color)
            val bri = (command.brightness * 255.0).roundToInt().coerceIn(0, 255)
            """{"id":${command.id},"on":${command.on},"bri":$bri,"col":[[$red,$green,$blue]],"fx":${command.effect.wledEffectId},"sx":${command.effectSpeed},"ix":${command.effectIntensity}}"""
        }
        return """{"seg":[$segmentPayload]}"""
    }

    internal fun extractFirstJsonObject(rawText: String): String? {
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false

        rawText.forEachIndexed { index, char ->
            if (start < 0) {
                if (char == '{') {
                    start = index
                    depth = 1
                }
                return@forEachIndexed
            }

            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString && char == '{' -> depth++
                !inString && char == '}' -> {
                    depth--
                    if (depth == 0) {
                        return rawText.substring(start, index + 1)
                    }
                }
            }
        }

        return null
    }

    private fun JsonValue.JsonObject.toSegmentSnapshotOrNull(): WledSegmentSnapshot? {
        val id = intValue("id") ?: return null
        val on = booleanValue("on") ?: true
        val brightness = ((intValue("bri") ?: 255).coerceIn(0, 255)) / 255.0
        val effect = effectFromWledId(intValue("fx") ?: LightEffect.NONE.wledEffectId)
        val speed = (intValue("sx") ?: LightController.DEFAULT_EFFECT_SPEED).coerceIn(0, 255)
        val intensity = (intValue("ix") ?: LightController.DEFAULT_EFFECT_INTENSITY).coerceIn(0, 255)

        return WledSegmentSnapshot(
            id = id,
            on = on,
            selectedForEdit = booleanValue("sel") ?: true,
            color = colorValue("col") ?: "#FFFFFF",
            brightness = brightness,
            effect = effect,
            effectSpeed = speed,
            effectIntensity = intensity,
        )
    }

    private fun JsonValue.JsonObject.colorValue(key: String): String? {
        val colorArrays = arrayValue(key) ?: return null
        val firstColor = colorArrays.firstOrNull()?.asArrayOrNull() ?: return null
        val red = firstColor.getOrNull(0)?.asIntOrNull()?.coerceIn(0, 255) ?: return null
        val green = firstColor.getOrNull(1)?.asIntOrNull()?.coerceIn(0, 255) ?: return null
        val blue = firstColor.getOrNull(2)?.asIntOrNull()?.coerceIn(0, 255) ?: return null
        return "#%02X%02X%02X".format(red, green, blue)
    }

    private fun hexToRgb(hex: String): Triple<Int, Int, Int> {
        require(isValidHexColor(hex)) { "Color must be #RGB or #RRGGBB, was $hex" }
        val clean = hex.substring(1)
        return when (clean.length) {
            3 -> Triple(
                clean[0].digitToInt(16) * 17,
                clean[1].digitToInt(16) * 17,
                clean[2].digitToInt(16) * 17,
            )
            else -> Triple(
                clean.substring(0, 2).toInt(16),
                clean.substring(2, 4).toInt(16),
                clean.substring(4, 6).toInt(16),
            )
        }
    }
}

private sealed interface JsonValue {
    data class JsonObject(val values: Map<String, JsonValue>) : JsonValue
    data class JsonArray(val values: List<JsonValue>) : JsonValue
    data class JsonString(val value: String) : JsonValue
    data class JsonNumber(val value: Double) : JsonValue
    data class JsonBoolean(val value: Boolean) : JsonValue
    data object JsonNull : JsonValue
}

private fun JsonValue.asObjectOrNull(): JsonValue.JsonObject? = this as? JsonValue.JsonObject

private fun JsonValue.asArrayOrNull(): List<JsonValue>? =
    (this as? JsonValue.JsonArray)?.values

private fun JsonValue.asIntOrNull(): Int? =
    (this as? JsonValue.JsonNumber)?.value?.takeIf { it.isFinite() }?.roundToInt()

private fun JsonValue.JsonObject.objectValue(key: String): JsonValue.JsonObject? =
    values[key] as? JsonValue.JsonObject

private fun JsonValue.JsonObject.arrayValue(key: String): List<JsonValue>? =
    (values[key] as? JsonValue.JsonArray)?.values

private fun JsonValue.JsonObject.intValue(key: String): Int? =
    values[key]?.asIntOrNull()

private fun JsonValue.JsonObject.booleanValue(key: String): Boolean? =
    (values[key] as? JsonValue.JsonBoolean)?.value

private class SimpleJsonParser(
    private val text: String,
) {
    private var index = 0

    fun parse(): JsonValue {
        val value = parseValue()
        skipWhitespace()
        if (index != text.length) {
            throw IllegalArgumentException("Unexpected trailing JSON content at $index")
        }
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        if (index >= text.length) throw IllegalArgumentException("Unexpected end of JSON")

        return when (val char = text[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.JsonString(parseString())
            't' -> {
                expectLiteral("true")
                JsonValue.JsonBoolean(true)
            }
            'f' -> {
                expectLiteral("false")
                JsonValue.JsonBoolean(false)
            }
            'n' -> {
                expectLiteral("null")
                JsonValue.JsonNull
            }
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalArgumentException("Unexpected JSON character '$char' at $index")
        }
    }

    private fun parseObject(): JsonValue.JsonObject {
        expect('{')
        skipWhitespace()
        if (peek('}')) {
            index++
            return JsonValue.JsonObject(emptyMap())
        }

        val values = linkedMapOf<String, JsonValue>()
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            values[key] = parseValue()
            skipWhitespace()
            when {
                peek(',') -> index++
                peek('}') -> {
                    index++
                    return JsonValue.JsonObject(values)
                }
                else -> throw IllegalArgumentException("Expected ',' or '}' at $index")
            }
        }
    }

    private fun parseArray(): JsonValue.JsonArray {
        expect('[')
        skipWhitespace()
        if (peek(']')) {
            index++
            return JsonValue.JsonArray(emptyList())
        }

        val values = mutableListOf<JsonValue>()
        while (true) {
            values += parseValue()
            skipWhitespace()
            when {
                peek(',') -> index++
                peek(']') -> {
                    index++
                    return JsonValue.JsonArray(values)
                }
                else -> throw IllegalArgumentException("Expected ',' or ']' at $index")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (index < text.length) {
            val char = text[index++]
            when (char) {
                '"' -> return builder.toString()
                '\\' -> builder.append(parseEscape())
                else -> builder.append(char)
            }
        }
        throw IllegalArgumentException("Unterminated JSON string")
    }

    private fun parseEscape(): Char {
        if (index >= text.length) throw IllegalArgumentException("Unterminated JSON escape")
        return when (val escaped = text[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> throw IllegalArgumentException("Unsupported JSON escape '$escaped'")
        }
    }

    private fun parseUnicodeEscape(): Char {
        if (index + 4 > text.length) throw IllegalArgumentException("Incomplete unicode escape")
        val value = text.substring(index, index + 4).toInt(16)
        index += 4
        return value.toChar()
    }

    private fun parseNumber(): JsonValue.JsonNumber {
        val start = index
        if (peek('-')) index++
        consumeDigits()
        if (peek('.')) {
            index++
            consumeDigits()
        }
        if (peek('e') || peek('E')) {
            index++
            if (peek('+') || peek('-')) index++
            consumeDigits()
        }
        return JsonValue.JsonNumber(text.substring(start, index).toDouble())
    }

    private fun consumeDigits() {
        val start = index
        while (index < text.length && text[index].isDigit()) {
            index++
        }
        if (start == index) throw IllegalArgumentException("Expected JSON number digit at $index")
    }

    private fun expectLiteral(literal: String) {
        if (!text.startsWith(literal, index)) {
            throw IllegalArgumentException("Expected '$literal' at $index")
        }
        index += literal.length
    }

    private fun expect(char: Char) {
        if (!peek(char)) throw IllegalArgumentException("Expected '$char' at $index")
        index++
    }

    private fun peek(char: Char): Boolean =
        index < text.length && text[index] == char

    private fun skipWhitespace() {
        while (index < text.length && text[index].isWhitespace()) {
            index++
        }
    }
}
