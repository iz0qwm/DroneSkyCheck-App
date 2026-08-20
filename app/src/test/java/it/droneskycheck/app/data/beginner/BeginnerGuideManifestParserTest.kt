package it.droneskycheck.app.data.beginner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BeginnerGuideManifestParserTest {
    @Test
    fun parse_acceptsStringContentVersionAndSortsPages() {
        val json = """
            {
              "schemaVersion": 1,
              "contentVersion": "1.0.0",
              "id": "principiante",
              "title": "Prima di volare",
              "description": "Le cose essenziali da sapere prima di usare un drone.",
              "language": "it-IT",
              "country": "IT",
              "audience": "beginner",
              "pages": [
                {
                  "id": "02",
                  "image": "02.png",
                  "title": "Seconda",
                  "accessibilityText": "Pagina seconda",
                  "order": 2
                },
                {
                  "id": "01",
                  "image": "01.png",
                  "title": "Prima",
                  "accessibilityText": "Pagina prima",
                  "order": 1
                }
              ]
            }
        """.trimIndent()

        val result = BeginnerGuideManifestParser.parse(json)

        assertNotNull(result.manifest)
        assertEquals("1.0.0", result.manifest?.contentVersion)
        assertEquals(listOf("01", "02"), result.manifest?.pages?.map { it.id })
    }

    @Test
    fun parse_rejectsManifestWithoutPages() {
        val json = """
            {
              "schemaVersion": 1,
              "contentVersion": "1.0.0",
              "id": "principiante",
              "title": "Prima di volare",
              "description": "Le cose essenziali da sapere prima di usare un drone.",
              "language": "it-IT",
              "country": "IT",
              "audience": "beginner"
            }
        """.trimIndent()

        val result = BeginnerGuideManifestParser.parse(json)

        assertNull(result.manifest)
    }
}
