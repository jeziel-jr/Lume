package com.nuvio.tv.data.xtream

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamEpgTextDecoderTest {

    private val jvmDecoder: (String) -> String? = { value ->
        runCatching { String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8) }.getOrNull()
    }

    @Test
    fun `valid base64 title is decoded to text`() {
        val encoded = Base64.getEncoder().encodeToString("Jornal da Globo".toByteArray(StandardCharsets.UTF_8))
        assertEquals("Jornal da Globo", decodeEpgText(encoded, jvmDecoder))
    }

    @Test
    fun `plain text stays unchanged`() {
        assertEquals("Jornal da Globo", decodeEpgText("Jornal da Globo"))
    }

    @Test
    fun `invalid base64 length stays unchanged`() {
        assertEquals("abc", decodeEpgText("abc"))
        assertEquals("abcd", decodeEpgText("abcd", jvmDecoder))
    }

    @Test
    fun `invalid decoding stays unchanged`() {
        assertEquals("abc=", decodeEpgText("abc=", jvmDecoder))
    }
}
