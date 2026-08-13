package it.droneskycheck.app.data.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpManifestParserTest {
    @Test
    fun validJsonParsesManifestTopicsAndSteps() {
        val result = HelpManifestParser.parse(helpManifestJson())

        val manifest = result.manifest
        assertTrue(result.isValid)
        assertEquals(1, manifest?.schemaVersion)
        assertEquals(2, manifest?.contentVersion)
        assertEquals(1, manifest?.onboardingSteps?.size)
        assertEquals(HelpTourTarget.MAP, manifest?.onboardingSteps?.single()?.target)
        assertEquals(HelpTourAction.NONE, manifest?.onboardingSteps?.single()?.action)
        assertEquals("Meteo", manifest?.topics?.single()?.title)
    }

    @Test
    fun missingActionDefaultsToNone() {
        val result = HelpManifestParser.parse(helpManifestJson())

        assertEquals(HelpTourAction.NONE, result.manifest?.onboardingSteps?.single()?.action)
    }

    @Test
    fun knownActionParsesFromManifest() {
        val result = HelpManifestParser.parse(helpManifestJson(target = "zones_button", action = "open_zones"))

        assertEquals(HelpTourTarget.ZONES_BUTTON, result.manifest?.onboardingSteps?.single()?.target)
        assertEquals(HelpTourAction.OPEN_ZONES, result.manifest?.onboardingSteps?.single()?.action)
    }

    @Test
    fun unknownActionFallsBackToNoneWithWarning() {
        val result = HelpManifestParser.parse(helpManifestJson(action = "open_anything"))

        assertTrue(result.isValid)
        assertEquals(HelpTourAction.NONE, result.manifest?.onboardingSteps?.single()?.action)
        assertTrue(result.warnings.any { it.code == HelpManifestWarningCode.UNKNOWN_ACTION })
    }

    @Test
    fun malformedJsonFailsWithoutCrash() {
        val result = HelpManifestParser.parse("{not valid")

        assertFalse(result.isValid)
        assertEquals(HelpManifestWarningCode.JSON_MALFORMED, result.warnings.single().code)
    }

    @Test
    fun unsupportedSchemaFailsWholeManifest() {
        val result = HelpManifestParser.parse(helpManifestJson(schemaVersion = 2))

        assertFalse(result.isValid)
        assertEquals(HelpManifestWarningCode.UNSUPPORTED_SCHEMA, result.warnings.single().code)
    }

    @Test
    fun missingTopicsKeepsManifestWithWarning() {
        val json = """
            {
              "schemaVersion": 1,
              "contentVersion": 1,
              "onboardingVersion": 1,
              "onboarding": { "steps": [] }
            }
        """.trimIndent()

        val result = HelpManifestParser.parse(json)

        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.code == HelpManifestWarningCode.MISSING_FIELD })
        assertEquals(emptyList<HelpTopic>(), result.manifest?.topics)
    }

    @Test
    fun unknownTargetIsIgnoredWithWarning() {
        val result = HelpManifestParser.parse(helpManifestJson(target = "unknown_button"))

        assertTrue(result.isValid)
        assertEquals(emptyList<HelpOnboardingStep>(), result.manifest?.onboardingSteps)
        assertTrue(result.warnings.any { it.code == HelpManifestWarningCode.UNKNOWN_TARGET })
    }

    @Test
    fun duplicateIdsAreIgnoredWithWarning() {
        val json = """
            {
              "schemaVersion": 1,
              "contentVersion": 1,
              "onboardingVersion": 1,
              "onboarding": {
                "steps": [
                  {"id":"map","target":"map","title":"Uno","text":"Testo","order":1},
                  {"id":"map","target":"traffic_button","title":"Due","text":"Testo","order":2}
                ]
              },
              "topics": [
                {"id":"weather","title":"Meteo","summary":"Sintesi","order":1},
                {"id":"weather","title":"Meteo 2","summary":"Sintesi","order":2}
              ]
            }
        """.trimIndent()

        val result = HelpManifestParser.parse(json)

        assertTrue(result.isValid)
        assertEquals(1, result.manifest?.onboardingSteps?.size)
        assertEquals(1, result.manifest?.topics?.size)
        assertTrue(result.warnings.any { it.code == HelpManifestWarningCode.DUPLICATE_ID })
    }

    @Test
    fun targetMapperHandlesKnownAndUnknownValues() {
        assertEquals(HelpTourTarget.TRAFFIC_BUTTON, HelpTourTarget.fromWireName("traffic_button"))
        assertEquals(HelpTourTarget.ZONES_BUTTON, HelpTourTarget.fromWireName("zones_button"))
        assertNull(HelpTourTarget.fromWireName("unknown_button"))
    }

    @Test
    fun actionMapperHandlesKnownMissingAndUnknownValues() {
        assertEquals(HelpTourAction.OPEN_PROFILE, HelpTourAction.fromWireName("open_profile"))
        assertEquals(HelpTourAction.NONE, HelpTourAction.fromWireName(null))
        assertNull(HelpTourAction.fromWireName("open_code"))
    }
}

private fun helpManifestJson(
    schemaVersion: Int = 1,
    contentVersion: Int = 2,
    target: String = "map",
    action: String? = null
): String =
    action?.let { ""","action":"$it"""" }.orEmpty().let { actionJson ->
    """
        {
          "schemaVersion": $schemaVersion,
          "contentVersion": $contentVersion,
          "updatedAt": "2026-08-13",
          "onboardingVersion": 1,
          "onboarding": {
            "steps": [
              {"id":"map","target":"$target"$actionJson,"title":"Controlla","text":"Tocca la mappa","order":1}
            ]
          },
          "topics": [
            {
              "id": "weather",
              "title": "Meteo",
              "summary": "Sintesi",
              "order": 1,
              "blocks": [
                {"type":"paragraph","text":"Testo"},
                {"type":"bulletList","items":["Uno","Due"]},
                {"type":"note","text":"Nota"}
              ]
            }
          ]
        }
    """.trimIndent()
    }
