package it.droneskycheck.app.data.ai

import it.droneskycheck.app.data.LocalDrone
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantRepositoryTest {
    @Test
    fun requestIncludesRequiredFlagsAndKnownContextOnly() {
        val request = AiAssistantRequest(
            query = "Posso volare qui adesso?",
            context = AiAssistantContext.from(
                location = AiAssistantLocation(lat = 41.9, lon = 12.5),
                drone = LocalDrone(
                    manufacturer = "DJI",
                    model = "Air 3S",
                    classLabel = "C1",
                    weight = 724.0,
                    cameras = ""
                )
            )
        ).toJson()

        assertEquals("Posso volare qui adesso?", request.getString("query"))
        assertTrue(request.getBoolean("includeSources"))
        assertFalse(request.getBoolean("includeDiagnostics"))

        val context = request.getJSONObject("context")
        assertEquals(41.9, context.getJSONObject("location").getDouble("lat"), 0.0)
        assertEquals(12.5, context.getJSONObject("location").getDouble("lon"), 0.0)
        assertEquals("DJI Air 3S", context.getString("aircraftModel"))
        assertEquals("C1", context.getString("classMark"))
        assertEquals(724.0, context.getDouble("massGrams"), 0.0)
        assertFalse(context.has("cameraPresent"))
        assertFalse(context.has("isToy"))
    }

    @Test
    fun parsesAnswerWithUserVisibleSources() {
        val response = parseAiAssistantResponse(
            JSONObject(
                """
                {
                  "type": "ANSWER",
                  "answer": "Puoi usare Scopri quando puoi volare dalla scheda della zona.",
                  "sources": [
                    {
                      "title": "Manuale Drone Sky Check",
                      "section": "Scopri quando puoi volare",
                      "chunkId": "P2"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(AiAssistantResponseKind.Answer, response.kind)
        assertEquals("Puoi usare Scopri quando puoi volare dalla scheda della zona.", response.displayText)
        assertEquals(1, response.sources.size)
        assertEquals("Manuale Drone Sky Check", response.sources.first().title)
        assertEquals("Scopri quando puoi volare", response.sources.first().section)
    }

    @Test
    fun parsesBothRouteWithoutDroppingNestedAnswersOrSources() {
        val response = parseAiAssistantResponse(
            JSONObject(
                """
                {
                  "status": "ok",
                  "route": "BOTH",
                  "answer": "Risposta sintetica di fallback che non deve essere usata.",
                  "regulatoryAnswer": {
                    "answer": "Registra l'operatore se richiesto e verifica categoria e assicurazione.",
                    "citations": [
                      {
                        "authority": "ENAC",
                        "document": "Regole UAS",
                        "section": "Operatori",
                        "chunkId": "R3"
                      }
                    ]
                  },
                  "productAnswer": {
                    "answer": "In Drone Sky Check salva il drone nel profilo e controlla la zona sulla mappa.",
                    "sources": [
                      {
                        "title": "Manuale Drone Sky Check",
                        "section": "Profilo pilota",
                        "sourceHash": "P2"
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(AiAssistantResponseKind.Both, response.kind)
        assertEquals("ok", response.status)
        assertEquals("BOTH", response.route)
        assertEquals("regulatoryAnswer.answer+productAnswer.answer", response.mappedTextSource)
        assertTrue(response.displayText.contains("Normativa"))
        assertTrue(response.displayText.contains("Registra l'operatore"))
        assertTrue(response.displayText.contains("Drone Sky Check"))
        assertTrue(response.displayText.contains("salva il drone nel profilo"))
        assertFalse(response.displayText.contains("fallback"))
        assertEquals(2, response.sources.size)
        assertEquals(AiAssistantSourceGroup.Regulatory, response.sources[0].group)
        assertEquals(AiAssistantSourceGroup.Product, response.sources[1].group)
    }

    @Test
    fun parsesOperationalAnswerFromOperationalSummary() {
        val response = parseAiAssistantResponse(
            JSONObject(
                """
                {
                  "type": "OPERATIONAL_ANSWER",
                  "operationalSummary": {
                    "headline": "Valutazione non immediatamente favorevole",
                    "summary": "Controlla le limitazioni attive.",
                    "details": ["Zona geografica UAS presente", "Verifica NOTAM"]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(AiAssistantResponseKind.OperationalAnswer, response.kind)
        assertEquals(
            "Valutazione non immediatamente favorevole\n\nControlla le limitazioni attive.\n\nZona geografica UAS presente\nVerifica NOTAM",
            response.displayText
        )
    }

    @Test
    fun parsesOperationalAnswerWhenRouteIsOperationalRequired() {
        val response = parseAiAssistantResponse(
            JSONObject(
                """
                {
                  "status": "OPERATIONAL_ANSWER",
                  "route": "DSC_OPERATIONAL_REQUIRED",
                  "operationalAnswer": {},
                  "operationalSummary": {
                    "headline": "A Roma servono verifiche operative specifiche",
                    "summary": "Il punto selezionato ricade in aree che richiedono attenzione prima del volo.",
                    "details": ["Controlla le zone UAS attive", "Non considerare questa risposta come autorizzazione"]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(AiAssistantResponseKind.OperationalAnswer, response.kind)
        assertEquals("OPERATIONAL_ANSWER", response.status)
        assertEquals("DSC_OPERATIONAL_REQUIRED", response.route)
        assertEquals("operationalSummary", response.mappedTextSource)
        assertTrue(response.displayText.contains("A Roma servono verifiche operative specifiche"))
        assertFalse(response.displayText.contains(AiAssistantUnavailableMessage))
    }

    @Test
    fun localContextGateAsksForMapPointWhenPlaceNameIsUsedWithoutCoordinates() {
        val response = localAiAssistantResponseFor(
            query = "Posso volare a Bologna?",
            context = AiAssistantContext(
                location = null,
                aircraftModel = "DJI Mini 4 Pro",
                classMark = "C0",
                massGrams = 249.0
            )
        )

        assertEquals(AiAssistantResponseKind.NeedsContext, response?.kind)
        assertEquals("NEEDS_CONTEXT", response?.status)
        assertEquals("LOCAL_CONTEXT_GATE", response?.route)
        assertEquals("local.context.location", response?.mappedTextSource)
        assertTrue(response!!.displayText.contains("Selezionalo sulla mappa"))
    }

    @Test
    fun localContextGateDoesNotInterceptHereWhenCoordinatesExist() {
        val response = localAiAssistantResponseFor(
            query = "Posso volare qui?",
            context = AiAssistantContext(
                location = AiAssistantLocation(lat = 41.9, lon = 12.5),
                aircraftModel = "DJI Mini 4 Pro",
                classMark = "C0",
                massGrams = 249.0
            )
        )

        assertNull(response)
    }

    @Test
    fun parsesOutOfScopeAsReformulationMessage() {
        val response = parseAiAssistantResponse(
            JSONObject(
                """
                {
                  "status": "OUT_OF_SCOPE",
                  "message": "internal fallback"
                }
                """.trimIndent()
            )
        )

        assertEquals(AiAssistantResponseKind.OutOfScope, response.kind)
        assertEquals("status.outOfScope", response.mappedTextSource)
        assertTrue(response.displayText.contains("riformularla in modo più specifico"))
        assertFalse(response.displayText.contains(AiAssistantUnavailableMessage))
    }

    @Test
    fun networkFailureRemainsTechnicalUnavailable() {
        val result = runBlocking {
            AiAssistantRepository(
                endpointUrl = "https://example.test/appDscAiAssistantAnswer",
                apiKey = "test-key",
                httpClient = FakeAiAssistantHttpClient(
                    error = AiAssistantRepositoryError.Network("offline")
                )
            ).answer(
                AiAssistantRequest(
                    query = "Domanda qualsiasi",
                    context = AiAssistantContext()
                )
            )
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AiAssistantRepositoryError.Network)
    }

    @Test
    fun parsesNeedsContextAsAssistantQuestion() {
        val response = parseAiAssistantResponse(
            JSONObject(
                """
                {
                  "type": "NEEDS_CONTEXT",
                  "nextQuestion": "Che drone hai? Se lo sai, indicami anche la marcatura di classe e il peso."
                }
                """.trimIndent()
            )
        )

        assertEquals(AiAssistantResponseKind.NeedsContext, response.kind)
        assertEquals(
            "Che drone hai? Se lo sai, indicami anche la marcatura di classe e il peso.",
            response.displayText
        )
    }
}

private class FakeAiAssistantHttpClient(
    private val response: AiAssistantHttpResponse? = null,
    private val error: AiAssistantRepositoryError? = null
) : AiAssistantHttpClient {
    override fun post(url: URL, apiKey: String, body: String): AiAssistantHttpResponse {
        error?.let { throw it }
        return response ?: AiAssistantHttpResponse(200, "{}")
    }
}
