package it.droneskycheck.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneCheckV3RepositoryTest {
    @Test
    fun parserPreservesExtendedAeronauticalDetails() {
        val response = parseZoneCheckV3Response(
            JSONObject(
                """
                {
                  "position": { "lat": 44.01, "lon": 8.19 },
                  "verdict": {
                    "status": "NO_FLY",
                    "maxAltitudeMetersAgl": 0,
                    "source": "NOTAM",
                    "explanation": "Volo non consentito.",
                    "responsibleZoneId": "notam-zone-1"
                  },
                  "responsibleZone": {
                    "id": "notam-zone-1",
                    "name": "NOTAM W1234/26",
                    "reason": "Restrizione piu cautelativa"
                  },
                  "zones": [
                    {
                      "identity": {
                        "id": "notam-zone-1",
                        "name": "NOTAM W1234/26",
                        "code": "W1234/26"
                      },
                      "classification": {
                        "family": "NOTAM",
                        "type": "P_NOTAM",
                        "classification": "Temporary restriction"
                      },
                      "uasLimit": {
                        "metersAgl": 0,
                        "lower": "GND",
                        "upper": "1500 FT AMSL"
                      },
                      "info": {
                        "summary": "Area temporaneamente riservata",
                        "explanation": "L'area e attiva secondo il NOTAM.",
                        "operationalMeaning": "Non operare senza conferma e autorizzazione."
                      },
                      "official": {
                        "sourceReference": "AIP SUP/NOTAM",
                        "sourceText": "AREA ACT FOR SPECIAL AIR ACTIVITY.",
                        "Q": "LIXX/QRTCA/IV/BO/W/000/015/4401N00819E005",
                        "fields": {
                          "A": "LIXX",
                          "B": "202608080700",
                          "C": "202608081600",
                          "D": "0700-1600",
                          "E": "AREA ACT FOR SPECIAL AIR ACTIVITY."
                        }
                      },
                      "validity": {
                        "activeNow": true,
                        "validFrom": "2026-08-08T07:00:00Z",
                        "validTo": "2026-08-08T16:00:00Z",
                        "schedule": "0700-1600",
                        "nextActivation": "2026-08-09T07:00:00Z",
                        "explanation": "Attiva nella finestra pubblicata."
                      },
                      "authorization": {
                        "required": true,
                        "requiredLicense": "Specific",
                        "operationMode": "VLOS"
                      },
                      "authority": {
                        "name": "ATS",
                        "contact": "ATS unit"
                      },
                      "notams": [
                        {
                          "code": "W1234/26",
                          "fir": "LIXX",
                          "activityType": "Temporary reserved area",
                          "severity": "BLOCKER",
                          "summary": "Attivita speciale",
                          "explanation": "Il NOTAM rende la zona non disponibile.",
                          "operationalMeaning": "Verificare conferma ATS prima di pianificare.",
                          "official": {
                            "icaoText": "W1234/26 NOTAMN Q) LIXX/QRTCA/IV/BO/W/000/015",
                            "Q": "LIXX/QRTCA/IV/BO/W/000/015"
                          },
                          "validity": {
                            "activeNow": true,
                            "future": false,
                            "expired": false,
                            "validFrom": "2026-08-08T07:00:00Z",
                            "validTo": "2026-08-08T16:00:00Z",
                            "schedule": "0700-1600"
                          }
                        }
                      ],
                      "enr": {
                        "code": "ENR 5.2.2.6",
                        "activationType": "NOTAM",
                        "explanation": "Zona attivabile tramite NOTAM.",
                        "officialText": "ENR SOURCE TEXT",
                        "validity": {
                          "activeNow": true,
                          "schedule": "As notified by NOTAM"
                        },
                        "weekSchedule": [true, true, true, true, true, false, false],
                        "daySchedule": [false, false, false, false, false, false, false, true, true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false]
                      },
                      "sup": {
                        "reference": "SUP 12/26",
                        "title": "Temporary activity",
                        "description": "Supplement activity",
                        "officialText": "SUP SOURCE TEXT",
                        "validity": {
                          "activeNow": true,
                          "validTo": "2026-08-31"
                        }
                      },
                      "isVerdictSource": true
                    }
                  ],
                  "blockers": [
                    { "code": "ACTIVE_NOTAM", "zoneName": "NOTAM W1234/26" }
                  ],
                  "warnings": [],
                  "baseline": {
                    "maxAltitudeMetersAgl": 120,
                    "representedAsZone": false
                  },
                  "meta": { "engine": "DSC", "version": "v3" }
                }
                """.trimIndent()
            )
        )

        val zone = response.zones.single()
        val notam = zone.notams.single()

        assertEquals("notam-zone-1", response.responsibleZone?.id)
        assertEquals("notam-zone-1", response.verdict.responsibleZoneId)
        assertTrue(zone.isVerdictSource == true)
        assertEquals("AREA ACT FOR SPECIAL AIR ACTIVITY.", zone.official?.sourceText)
        assertEquals("LIXX/QRTCA/IV/BO/W/000/015/4401N00819E005", zone.official?.qLine)
        assertEquals("0700-1600", zone.validity?.schedule)
        assertEquals("2026-08-09T07:00:00Z", zone.validity?.nextActivation)
        assertTrue(zone.validity?.activeNow == true)
        assertEquals("Specific", zone.authorization?.requiredLicense)
        assertEquals("W1234/26", notam.code)
        assertEquals("W1234/26 NOTAMN Q) LIXX/QRTCA/IV/BO/W/000/015", notam.official?.sourceText)
        assertFalse(notam.validity?.future == true)
        assertFalse(notam.validity?.expired == true)
        assertEquals("ENR SOURCE TEXT", zone.enr?.official?.sourceText)
        assertEquals("As notified by NOTAM", zone.enr?.validity?.schedule)
        assertEquals(7, zone.enr?.weekSchedule?.size)
        assertEquals(true, zone.enr?.weekSchedule?.first()?.active)
        assertEquals(24, zone.enr?.daySchedule?.size)
        assertEquals(false, zone.enr?.daySchedule?.first())
        assertEquals(true, zone.enr?.daySchedule?.get(7))
        assertEquals("SUP SOURCE TEXT", zone.sup?.official?.sourceText)
    }

    @Test
    fun parserReadsNormalizedAppAuthorizationContract() {
        val response = parseZoneCheckV3Response(overlapJson(
            status = "NO_FLY",
            limit = 0,
            zones = """
              {
                "identity": { "id": "park", "name": "LIPROT123" },
                "classification": { "type": "ATM09_PARKS", "family": "PROTECTED_AREA" },
                "authority": {
                  "authorizationRequired": true,
                  "authority": {
                    "emails": ["parco@example.test"],
                    "note": "Ente Parco"
                  }
                },
                "authorization": {
                  "applicability": "WHEN_ACTIVE",
                  "resolutionStatus": "RESOLVED",
                  "procedures": [
                    {
                      "type": "ATM05",
                      "version": 1,
                      "label": "ATM05",
                      "reasonCode": "PROTECTED_AREA_ATM05"
                    }
                  ],
                  "additionalRequirements": [
                    {
                      "type": "ENTE_PARCO",
                      "label": "Ente Parco",
                      "reasonCode": "PROTECTED_AREA_ENTE_PARCO"
                    }
                  ],
                  "reasonCodes": [
                    "PROTECTED_AREA_ATM05",
                    "PROTECTED_AREA_ENTE_PARCO"
                  ],
                  "blockingReasons": [],
                  "resolverVersion": 1
                }
              }
            """.trimIndent()
        ))

        val zone = response.zones.single()
        val authorization = zone.authorization

        assertEquals(true, zone.authorizationRequired)
        assertEquals("WHEN_ACTIVE", authorization?.applicability)
        assertEquals("RESOLVED", authorization?.resolutionStatus)
        assertEquals("ATM05", authorization?.procedures?.single()?.type)
        assertEquals(1, authorization?.procedures?.single()?.version)
        assertEquals("ENTE_PARCO", authorization?.additionalRequirements?.single()?.type)
        assertEquals("PROTECTED_AREA_ATM05", authorization?.reasonCodes?.first())
        assertEquals(1, authorization?.resolverVersion)
        assertEquals(listOf("parco@example.test"), zone.authority?.emails)
        assertEquals("parco@example.test", zone.authority?.contact)
        assertEquals("Ente Parco", zone.authority?.name)
    }

    @Test
    fun parserPreservesEnrAuthorityEmails() {
        val response = parseZoneCheckV3Response(overlapJson(
            status = "NO_FLY",
            limit = 0,
            zones = """
              {
                "identity": { "id": "lip244", "name": "LI P244" },
                "classification": { "type": "ATM09_PRISON", "family": "PROHIBITED" },
                "authorization": {
                  "applicability": "WHEN_ACTIVE",
                  "resolutionStatus": "RESOLVED",
                  "procedures": [{ "type": "ATM05", "version": 1, "label": "ATM05" }]
                },
                "enr": {
                  "descrizione": "Proibito tutto il traffico aereo al di sotto di 2500 ft AMSL.",
                  "operationMode": "OPEN",
                  "operationCategory": "OPEN",
                  "authorizationRequired": true,
                  "authority": {
                    "emails": ["protocollo.prefrm@pec.interno.it"],
                    "note": "Prefettura di Roma"
                  }
                }
              }
            """.trimIndent()
        ))

        val authority = response.zones.single().enr?.authority

        assertEquals(listOf("protocollo.prefrm@pec.interno.it"), authority?.emails)
        assertEquals("protocollo.prefrm@pec.interno.it", authority?.contact)
        assertEquals("Prefettura di Roma", authority?.name)
        assertEquals("Proibito tutto il traffico aereo al di sotto di 2500 ft AMSL.", response.zones.single().enr?.description)
    }

    @Test
    fun parserPromotesEnrichedEnrDescriptionToZoneDetails() {
        val description = "Proibito tutto il traffico aereo al di sotto di 2500 ft AMSL."
        val response = parseZoneCheckV3Response(overlapJson(
            status = "NO_FLY",
            limit = 0,
            zones = """
              {
                "identity": { "id": "lip244", "name": "LI P244" },
                "classification": { "type": "ATM09_PRISON", "family": "PROHIBITED" },
                "authorization": {
                  "applicability": "WHEN_ACTIVE",
                  "resolutionStatus": "RESOLVED",
                  "procedures": [{ "type": "ATM05", "version": 1, "label": "ATM05" }]
                },
                "enriched": {
                  "aip": "LI P244",
                  "enrType": "5.1.1",
                  "descrizione": "$description",
                  "sourceFile": "ENR_5.1.1.html",
                  "operationMode": "OPEN",
                  "operationCategory": "OPEN",
                  "requiredLicense": ["A1/A3"],
                  "authorizationRequired": true,
                  "authority": {
                    "emails": ["protocollo.prefrm@pec.interno.it"],
                    "note": "Prefettura di Roma"
                  }
                }
              }
            """.trimIndent()
        ))

        val zone = response.zones.single()

        assertEquals(description, zone.enr?.description)
        assertEquals(description, zone.description)
        assertEquals(listOf("protocollo.prefrm@pec.interno.it"), zone.enr?.authority?.emails)
        assertEquals("Prefettura di Roma", zone.enr?.authority?.name)
    }

    @Test
    fun parserReadsNestedEnrEnrichmentDescriptionToZoneDetails() {
        val description = "1) Proibito tutto il traffico aereo al di sotto di 2500 ft AMSL."
        val response = parseZoneCheckV3Response(overlapJson(
            status = "NO_FLY",
            limit = 0,
            zones = """
              {
                "identity": { "id": "lip244", "name": "LI P244 - ROMA" },
                "classification": { "type": "ATM09_PRISON", "family": "PROHIBITED" },
                "authorization": {
                  "applicability": "WHEN_ACTIVE",
                  "resolutionStatus": "RESOLVED",
                  "procedures": [{ "type": "ATM05", "version": 1, "label": "ATM05" }]
                },
                "authority": {
                  "emails": ["protocollo.prefrm@pec.interno.it"],
                  "note": "Prefettura di Roma"
                },
                "info": {
                  "summary": "LIP244 LI P244 - ROMA"
                },
                "enr": {
                  "hasEnr": false,
                  "enrichment": {
                    "aip": "LI P244",
                    "enrType": "5.1.1",
                    "descrizione": "$description",
                    "sourceFile": "ENR_5.1.1.html",
                    "operationMode": "OPEN",
                    "operationCategory": "OPEN",
                    "requiredLicense": ["A1/A3"],
                    "authorizationRequired": true,
                    "authority": {
                      "emails": ["protocollo.prefrm@pec.interno.it"],
                      "note": "Prefettura di Roma"
                    }
                  }
                }
              }
            """.trimIndent()
        ))

        val zone = response.zones.single()

        assertEquals(description, zone.enr?.description)
        assertEquals(description, zone.description)
        assertEquals("ENR 5.1.1", zone.enr?.classification)
        assertEquals(listOf("protocollo.prefrm@pec.interno.it"), zone.enr?.authority?.emails)
    }

    @Test
    fun parserReadsUasGeographicalZoneEnrichedDescriptionAndAuthority() {
        val response = parseZoneCheckV3Response(overlapJson(
            status = "NO_FLY",
            limit = 0,
            zones = """
              {
                "identity": { "id": "uasgz-test", "name": "UAS GZ SEC018 - Settala" },
                "classification": { "type": "UAS_GZ", "family": "PROHIBITED" },
                "uasGeographicalZone": {
                  "id": "UAS GZ SEC018 - Settala",
                  "generalita": "Sono vietate le operazioni UAS.",
                  "description": "Consultare la lettera di istituzione della zona geografica.",
                  "operationMode": "OPEN_POSSIBLE",
                  "operationCategory": "OPEN_WITH_AUTH",
                  "requiredLicense": ["A1/A3"],
                  "authorizationRequired": true,
                  "authority": {
                    "emails": ["security@pec.snam.it"],
                    "note": "Centrali di stoccaggio SNAM"
                  }
                }
              }
            """.trimIndent()
        ))

        val zone = response.zones.single()

        assertEquals("Sono vietate le operazioni UAS.\n\nConsultare la lettera di istituzione della zona geografica.", zone.description)
        assertEquals(listOf("security@pec.snam.it"), zone.uasGeographicalZone?.authority?.emails)
        assertEquals("OPEN_WITH_AUTH", zone.uasGeographicalZone?.operationCategory)
    }

    @Test
    fun parserUnwrapsNestedAuthorityEmailObject() {
        val response = parseZoneCheckV3Response(overlapJson(
            status = "NO_FLY",
            limit = 0,
            zones = """
              {
                "identity": { "id": "sup-test", "name": "SUP fixture" },
                "classification": { "type": "P_SUP", "family": "SUP" },
                "sup": {
                  "authority": {
                    "authority": {
                      "emails": ["sup@example.test"],
                      "note": "Ente SUP"
                    }
                  }
                }
              }
            """.trimIndent()
        ))

        val authority = response.zones.single().sup?.authority

        assertEquals(listOf("sup@example.test"), authority?.emails)
        assertEquals("sup@example.test", authority?.contact)
        assertEquals("Ente SUP", authority?.name)
    }

    @Test
    fun parserKeepsGlobalNoFlyWhenInactiveEnrOverlapsActiveZeroMeterZone() {
        val response = parseZoneCheckV3Response(overlapJson(
            status = "NO_FLY",
            limit = 0,
            zones = """
              {
                "identity": { "id": "lire", "name": "LIRE PRATICA DI MARE" },
                "classification": { "type": "ATM09_OTHER" },
                "uasLimit": { "metersAgl": 0 },
                "operationalStatus": "ACTIVE",
                "validity": { "activeNow": true },
                "isVerdictSource": true
              },
              {
                "identity": { "id": "lispera", "name": "LISPERA SPERA" },
                "classification": { "type": "ATM09_RESTRICTED" },
                "uasLimit": { "metersAgl": 0 },
                "operationalStatus": "ENR_INACTIVE_NOW",
                "validity": { "activeNow": false }
              }
            """.trimIndent()
        ))

        assertEquals("NO_FLY", response.verdict.status)
        assertEquals(0, response.verdict.maxAltitudeMetersAgl)
        assertEquals("ACTIVE", response.zones[0].operationalStatus)
        assertEquals("ENR_INACTIVE_NOW", response.zones[1].operationalStatus)
    }

    @Test
    fun parserKeepsGlobalSixtyMetersWhenInactiveEnrOverlapsCtr() {
        val response = parseZoneCheckV3Response(overlapJson(
            status = "LIMITED",
            limit = 60,
            zones = """
              {
                "identity": { "id": "lispera", "name": "LISPERA SPERA" },
                "classification": { "type": "ATM09_RESTRICTED" },
                "uasLimit": { "metersAgl": 0 },
                "operationalStatus": "ENR_INACTIVE_NOW"
              },
              {
                "identity": { "id": "ctr", "name": "CTR 60" },
                "classification": { "type": "ATM09_CTR" },
                "uasLimit": { "metersAgl": 60 },
                "operationalStatus": "ACTIVE_LIMITED",
                "isVerdictSource": true
              }
            """.trimIndent()
        ))

        assertEquals("LIMITED", response.verdict.status)
        assertEquals(60, response.verdict.maxAltitudeMetersAgl)
        assertEquals("ENR_INACTIVE_NOW", response.zones[0].operationalStatus)
        assertEquals("ACTIVE_LIMITED", response.zones[1].operationalStatus)
    }

    @Test
    fun parserKeepsBaselineWhenOnlyInactiveEnrExists() {
        val response = parseZoneCheckV3Response(overlapJson(
            status = "OPEN",
            limit = 120,
            zones = """
              {
                "identity": { "id": "lispera", "name": "LISPERA SPERA" },
                "classification": { "type": "ATM09_RESTRICTED" },
                "uasLimit": { "metersAgl": 0 },
                "operationalStatus": "ENR_INACTIVE_NOW"
              }
            """.trimIndent()
        ))

        assertEquals("OPEN", response.verdict.status)
        assertEquals(120, response.verdict.maxAltitudeMetersAgl)
        assertEquals("ENR_INACTIVE_NOW", response.zones.single().operationalStatus)
    }

    @Test
    fun parserKeepsMostRestrictiveActiveLimit() {
        val response = parseZoneCheckV3Response(overlapJson(
            status = "LIMITED",
            limit = 25,
            zones = """
              {
                "identity": { "id": "z25", "name": "Zona 25" },
                "classification": { "type": "ATM09_OTHER" },
                "uasLimit": { "metersAgl": 25 },
                "operationalStatus": "ACTIVE_LIMITED",
                "isVerdictSource": true
              },
              {
                "identity": { "id": "z60", "name": "Zona 60" },
                "classification": { "type": "ATM09_CTR" },
                "uasLimit": { "metersAgl": 60 },
                "operationalStatus": "ACTIVE_LIMITED"
              }
            """.trimIndent()
        ))

        assertEquals("LIMITED", response.verdict.status)
        assertEquals(25, response.verdict.maxAltitudeMetersAgl)
        assertTrue(response.zones.first().isVerdictSource == true)
    }

    @Test
    fun parserKeepsZeroMeterVerdictWithInformationalNotam() {
        val response = parseZoneCheckV3Response(overlapJson(
            status = "NO_FLY",
            limit = 0,
            zones = """
              {
                "identity": { "id": "zero", "name": "Zona 0" },
                "classification": { "type": "ATM09_OTHER" },
                "uasLimit": { "metersAgl": 0 },
                "operationalStatus": "ACTIVE",
                "isVerdictSource": true
              },
              {
                "identity": { "id": "notam", "name": "NOTAM A1234/26" },
                "classification": { "type": "P_NOTAM" },
                "uasLimit": { "metersAgl": 120 },
                "operationalStatus": "NOTAM_ACTIVE",
                "notam": {
                  "code": "A1234/26",
                  "severity": "INFO",
                  "summary": "NOTAM informativo",
                  "official": { "sourceText": "A) LIRR E) TEMPORARY INFORMATION" }
                }
              }
            """.trimIndent()
        ))

        assertEquals("NO_FLY", response.verdict.status)
        assertEquals(0, response.verdict.maxAltitudeMetersAgl)
        assertEquals("INFO", response.zones[1].notams.single().severity)
    }

    @Test
    fun parserKeepsActiveRestrictionWithMultipleInactiveZones() {
        val response = parseZoneCheckV3Response(overlapJson(
            status = "LIMITED",
            limit = 25,
            zones = """
              {
                "identity": { "id": "inactive1", "name": "Inactive 1" },
                "uasLimit": { "metersAgl": 0 },
                "operationalStatus": "ENR_INACTIVE_NOW"
              },
              {
                "identity": { "id": "inactive2", "name": "Inactive 2" },
                "uasLimit": { "metersAgl": 0 },
                "operationalStatus": "SUP_INACTIVE_NOW"
              },
              {
                "identity": { "id": "active25", "name": "Active 25" },
                "uasLimit": { "metersAgl": 25 },
                "operationalStatus": "ACTIVE_LIMITED",
                "isVerdictSource": true
              }
            """.trimIndent()
        ))

        assertEquals("LIMITED", response.verdict.status)
        assertEquals(25, response.verdict.maxAltitudeMetersAgl)
        assertEquals(2, response.zones.count { it.operationalStatus?.contains("INACTIVE") == true })
    }

    private fun overlapJson(
        status: String,
        limit: Int,
        zones: String
    ): JSONObject =
        JSONObject(
            """
            {
              "position": { "lat": 41.65, "lon": 12.45 },
              "verdict": {
                "status": "$status",
                "maxAltitudeMetersAgl": $limit,
                "source": "TEST",
                "explanation": "Fixture"
              },
              "zones": [ $zones ],
              "blockers": [],
              "warnings": [],
              "baseline": {
                "maxAltitudeMetersAgl": 120,
                "representedAsZone": false
              },
              "meta": { "engine": "DSC", "version": "v3" }
            }
            """.trimIndent()
        )
}
