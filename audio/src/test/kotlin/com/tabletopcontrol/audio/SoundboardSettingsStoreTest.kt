package com.tabletopcontrol.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SoundboardSettingsStoreTest {

    @Test
    fun `serialize and parse config round trip`() {
        val input = listOf(
            SoundboardSlotConfig(label = "Door Slam", uri = "file:///tmp/door.mp3", colorHex = "#FF0000"),
            SoundboardSlotConfig(),
        )

        val text = SoundboardSettingsCodec.serialize(input)
        val parsed = SoundboardSettingsCodec.parse(text)

        assertNotNull(parsed)
        assertEquals(input, parsed)
    }

    @Test
    fun `parse returns null for empty payload`() {
        assertNull(SoundboardSettingsCodec.parse("   \n  "))
    }

    @Test
    fun `serialize and parse preserves color hex values`() {
        val text = SoundboardSettingsCodec.serialize(
            listOf(
                SoundboardSlotConfig(label = "Bell", uri = "file:///tmp/bell.mp3", colorHex = "#123ABC"),
            ),
        )

        val parsed = SoundboardSettingsCodec.parse(text)
        assertEquals("#123ABC", parsed?.firstOrNull()?.colorHex)
    }

    @Test
    fun `serialize and parse drops invalid color values`() {
        val malformed = """
            version=1
            count=1
            slot=QmVsbA|ZmlsZTovLy90bXAvYmVsbC5tcDM|bm90LWEtY29sb3I
        """.trimIndent()

        val parsed = SoundboardSettingsCodec.parse(malformed)
        assertNull(parsed?.firstOrNull()?.colorHex)
    }

    @Test
    fun `parse returns null when version header is missing`() {
        val noVersion = """
            count=1
            slot=QmVsbA|ZmlsZTovLy90bXAvYmVsbC5tcDM|IzEyM0FCQw
        """.trimIndent()

        assertNull(SoundboardSettingsCodec.parse(noVersion))
    }

    @Test
    fun `parse returns null for unsupported version`() {
        val unsupportedVersion = """
            version=999
            count=1
            slot=QmVsbA|ZmlsZTovLy90bXAvYmVsbC5tcDM|IzEyM0FCQw
        """.trimIndent()

        assertNull(SoundboardSettingsCodec.parse(unsupportedVersion))
    }
}
