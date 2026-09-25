package com.texfi.w0y

import com.texfi.w0y.data.YouTubeLocaleFix
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeLocaleFixTest {
    @Test
    fun stripsUnicodeExtensions() {
        val dirty = Locale.forLanguageTag("ru-RU-u-ca-gregory-nu-latn")
        assertEquals("ru-RU", YouTubeLocaleFix.sanitize(dirty, emptyList()).toLanguageTag())
    }

    @Test
    fun fillsMissingCountryFromSystem() {
        val noCountry = Locale.forLanguageTag("ru")
        assertEquals("ru-RU", YouTubeLocaleFix.sanitize(noCountry, listOf("", "ru")).toLanguageTag())
    }

    @Test
    fun fallsBackWhenNothingKnown() {
        val noCountry = Locale.forLanguageTag("ru")
        assertEquals("ru-US", YouTubeLocaleFix.sanitize(noCountry, emptyList()).toLanguageTag())
    }

    @Test
    fun keepsCleanLocaleAsIs() {
        val clean = Locale.forLanguageTag("en-US")
        assertEquals("en-US", YouTubeLocaleFix.sanitize(clean, emptyList()).toLanguageTag())
    }
}
