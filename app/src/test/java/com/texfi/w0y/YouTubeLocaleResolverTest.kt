package com.texfi.w0y

import com.texfi.w0y.data.YouTubeLocaleResolver
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeLocaleResolverTest {
    @Test
    fun keepsLanguageAndCountrySeparate() {
        // Ровно случай с телефона: английский язык, польский регион.
        val locale = YouTubeLocaleResolver.resolve(Locale.forLanguageTag("en-PL"), emptyList())
        assertEquals("en", locale.hl)
        assertEquals("PL", locale.gl)
    }

    @Test
    fun dropsUnicodeExtensions() {
        val locale =
            YouTubeLocaleResolver.resolve(Locale.forLanguageTag("ru-RU-u-ca-gregory-nu-latn"), emptyList())
        assertEquals("ru", locale.hl)
        assertEquals("RU", locale.gl)
    }

    @Test
    fun takesCountryFromSystemListWhenLocaleHasNone() {
        val locale = YouTubeLocaleResolver.resolve(Locale.forLanguageTag("ru"), listOf("", "pl"))
        assertEquals("ru", locale.hl)
        assertEquals("PL", locale.gl)
    }

    @Test
    fun fallsBackWhenCountryUnknown() {
        val locale = YouTubeLocaleResolver.resolve(Locale.forLanguageTag("ru"), emptyList())
        assertEquals("US", locale.gl)
    }
}
