package it.droneskycheck.app.data.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HelpImageResolverTest {
    @Test
    fun relativeUrlResolvesAgainstHelpImagesBaseUrl() {
        val resolved = HelpImageResolver.resolve(
            value = "weather.webp",
            baseUrl = "https://www.kwos.org/appoggio/droni/DroneSkyCheck/help/images/"
        )

        assertEquals(
            "https://www.kwos.org/appoggio/droni/DroneSkyCheck/help/images/weather.webp",
            resolved?.url
        )
        assertEquals("weather", resolved?.localDrawableName)
    }

    @Test
    fun absoluteHttpsUrlStaysUnchanged() {
        val resolved = HelpImageResolver.resolve("https://example.com/weather.webp")

        assertEquals("https://example.com/weather.webp", resolved?.url)
        assertNull(resolved?.localDrawableName)
    }

    @Test
    fun unsupportedSchemesAreRejected() {
        assertNull(HelpImageResolver.resolve("file:///tmp/weather.webp"))
        assertNull(HelpImageResolver.resolve("content://app/weather.webp"))
        assertNull(HelpImageResolver.resolve("javascript:alert(1)"))
    }

    @Test
    fun pathTraversalIsRejected() {
        assertNull(HelpImageResolver.resolve("../../secret.png"))
        assertNull(HelpImageResolver.resolve("manual/../secret.png"))
    }

    @Test
    fun unsupportedExtensionsAreRejected() {
        assertNull(HelpImageResolver.resolve("weather.svg"))
        assertNull(HelpImageResolver.resolve("https://example.com/weather.gif"))
    }
}
