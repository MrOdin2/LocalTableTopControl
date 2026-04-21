package com.tabletopcontrol.core

import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
import javafx.geometry.Orientation

/**
 * Serialises and deserialises a [PaneNode] layout tree to/from a plain-text config file.
 *
 * **On-disk format** — a recursive S-expression:
 * ```
 * leaf(MapPlugin)
 * split(HORIZONTAL,0.5,leaf(MapPlugin),leaf(AudioPlugin))
 * split(HORIZONTAL,0.5,leaf(Map),split(VERTICAL,0.3,leaf(Audio),leaf(Tracker)))
 * ```
 *
 * Plugin names may contain any character except an unbalanced `)`. Commas are allowed.
 *
 * The config file is stored at `~/.tabletopcontrol/dm-layout.conf`.
 */
object LayoutSerializer {

    internal const val CONFIG_NAME = "dm-layout.conf"

    private val configFile
        get() = AppConfigPaths.configFile(CONFIG_NAME)

    // ── Serialisation ────────────────────────────────────────────────────────

    /** Converts [node] to the compact S-expression string representation. */
    fun serialize(node: PaneNode): String = when (node) {
        is PaneNode.Leaf -> "leaf(${node.pluginName})"
        is PaneNode.Split -> {
            val o = node.orientation.name
            val d = node.dividerPosition
            "split($o,$d,${serialize(node.first)},${serialize(node.second)})"
        }
    }

    // ── Deserialisation ──────────────────────────────────────────────────────

    /**
     * Parses [text] into a [PaneNode] tree.
     *
     * @return The parsed tree, or `null` if [text] is empty, blank, or malformed.
     */
    fun deserialize(text: String): PaneNode? {
        return try {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                null
            } else {
                val node = Parser(trimmed).parseNode()
                val canonical = serialize(node)
                if (canonical == trimmed) node else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    /**
     * Writes [node] to [configFile]. I/O failures are silently swallowed so that a
     * missing or read-only config directory never crashes the application.
     */
    fun save(node: PaneNode) {
        SafeConfigIO.writeText(configFile, serialize(node))
    }

    /**
     * Reads and parses the config file.
     *
     * @return The saved layout tree, or `null` if the file is absent or unparseable.
     */
    fun load(): PaneNode? = SafeConfigIO.readOrElse(null) { deserialize(configFile.readText()) }

    // ── Internal parser ──────────────────────────────────────────────────────

    /**
     * Simple recursive-descent parser for the S-expression layout format.
     *
     * Parenthesis depth is tracked inside [readUntil] so that plugin names
     * containing balanced parentheses are handled correctly.
     */
    private class Parser(private val input: String) {
        private var pos = 0

        fun parseNode(): PaneNode = when {
            input.startsWith("leaf(", pos) -> parseLeaf()
            input.startsWith("split(", pos) -> parseSplit()
            else -> error("Unknown node type at position $pos: '${input.substring(pos).take(20)}'")
        }

        private fun parseLeaf(): PaneNode.Leaf {
            expect("leaf(")
            val name = readUntil(')')
            expect(")")
            return PaneNode.Leaf(name)
        }

        private fun parseSplit(): PaneNode.Split {
            expect("split(")
            val orientation = Orientation.valueOf(readUntil(','))
            expect(",")
            val divider = readUntil(',').toDouble()
            expect(",")
            val first = parseNode()
            expect(",")
            val second = parseNode()
            expect(")")
            return PaneNode.Split(orientation, divider, first, second)
        }

        /**
         * Reads characters from [pos] until [delimiter] is encountered at depth 0.
         *
         * Depth increases on `(` and decreases on `)`. This allows plugin names to
         * contain balanced parentheses without breaking the format.
         */
        private fun readUntil(delimiter: Char): String {
            val start = pos
            var depth = 0
            while (pos < input.length) {
                when (input[pos]) {
                    '(' -> depth++
                    ')' -> if (depth == 0) break else depth--
                    delimiter -> if (depth == 0) break
                }
                pos++
            }
            return input.substring(start, pos)
        }

        private fun expect(s: String) {
            check(input.startsWith(s, pos)) {
                "Expected '$s' at position $pos, got '${input.substring(pos).take(20)}'"
            }
            pos += s.length
        }
    }
}
