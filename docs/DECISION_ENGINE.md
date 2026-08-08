# Decision Engine

Questo documento descrive il motore che produce lo stato operativo `OPEN`, `LIMITED`, `NO_FLY` o equivalenti. L'obiettivo e' capire cosa puo restare lato backend per la nuova app Android.

Fonti principali: `functions/api/zoneCheckV2.js`, `functions/core/engine.js`, `functions/core/loader.js`, `functions/core/enr.js`, `functions/core/notam.js`, `functions/core/sup.js`, `public/js/geojson-pill.js`, `public/js/core/zoneDecisionSnapshot.js`, `docs/developer/schedule-engine.md`.

## Stato Attuale

La logica decisionale esiste sia frontend sia backend.

Backend:

- `zoneCheckV3` e' il contratto operativo previsto per Android;
- `zoneCheckV2` resta il motore legacy piu completo per i client esistenti;
- usa Turf per intersezioni e distanze;
- integra ENR, NOTAM e SUP;
- produce una risposta JSON utilizzabile da client esterni.

Frontend:

- `geojson-pill.js` calcola un verdetto immediato sui layer visibili;
- `zoneDecisionSnapshot.js` normalizza lo snapshot per pannelli e AI;
- `showZoneInfo.js` arricchisce con dettagli, autorizzazioni e UI.

Per Android, la logica da riusare e' il backend. Il client nativo dovrebbe limitarsi a rendering, stato UI, caching e chiamate API.

## Flusso `zoneCheckV2`

1. Valida `lat` e `lon`.
2. Applica `applySecurity`.
3. Carica le feature con `loadFeatures`.
4. Cerca feature intersecanti:
   - `Polygon` / `MultiPolygon` con `booleanPointInPolygon`;
   - `LineString` con distanza punto-linea entro soglia.
5. Per ogni feature:
   - inferisce AIP code dal nome;
   - valuta ENR con `analyzeEnrZone`;
   - se il nome sembra NOTAM, valuta NOTAM con `analyzeNotamZone`;
   - se `P_SUP`, valuta SUP con `analyzeSupZone`.
6. Calcola stato base con `getFlightStatusAt`.
7. Calcola `nearestOpen`.
8. Calcola `distanceToExitMeters` se il punto e' in zona a limite 0 e c'e un bearing.
9. Applica `resolveFinalStatus`.
10. Rimuove campi debug dalla risposta pubblica.

## Output Operativo

Campi principali:

- `status`: `OPEN`, `LIMITED`, `NO_FLY`;
- `maxAltitude`: quota massima in metri;
- `zebra`: indicatore NFZ/NOTAM restrittivo;
- `currentZone`: zona principale;
- `nearestOpen`: stima della zona volabile piu vicina;
- `distanceToExitMeters`: distanza stimata per uscire dalla NFZ lungo il bearing;
- `analysis.source`: origine della decisione;
- `analysis.blockers`: motivi bloccanti;
- `analysis.warnings`: warning non bloccanti;
- `zones`: dettagli per zona intersecata;
- `meta.engine`: `DSC`, `version`: `v2-production`.

## Calcolo Base

`functions/core/engine.js` estrae un limite da ogni feature:

- CTR (`ATM09_CTR`): ignora casi errati di PROHIBITED/NFZ e usa il limite reale arrotondato;
- corridoi: trattati come restrittivi;
- tactical: limite 45 m;
- NOTAM/NFZ: limite 0 quando attivi;
- ENR attivo: limite 0;
- ENR inattivo: limite 120;
- altrimenti legge `lowerLimitAGL`, `lowerLimit`, `maxHeight`, `maxAltitude`, `altitudeAGL`;
- arrotonda ai gradini 0, 25, 45, 60, 120.

Se nessuna feature contiene il punto:

```json
{ "limit": 120, "zebra": false, "where": "Nessuna restrizione rilevata" }
```

Priorita base: vince il limite piu basso. Se c'e zebra reale, forza limite 0.

## ENR

`functions/core/enr.js`:

- legge `functions/data/enr_enriched.json`;
- prova a caricare HTML ENR 5.5 locali o remoti;
- normalizza codice AIP;
- costruisce schedule candidate;
- valuta giorno, orari, H24, festivi, range settimanali, SR/SS/HJ con `SunCalc`;
- produce `activeNow: true | false | null`;
- costruisce `weekSchedule` e `daySchedule`.

Effetto in `resolveFinalStatus`:

- ENR attivo: `finalLimit = 0`, source `ENR`, blocker `ACTIVE_ENR`;
- ENR inattivo: warning `ENR_INACTIVE_NOW`, possibile override a 120;
- ENR non valutabile: warning `ENR_TEMPORAL_UNKNOWN`.

Nota: `activeNow: null` non blocca automaticamente, ma richiede prudenza.

## NOTAM

`functions/core/notam.js`:

- estrae serie/numero/anno dal nome zona;
- usa prima `notams_WE.json` via `fetchNotamUnified`;
- fallback a `notamProxy`;
- parse dei campi ICAO `Q`, `A`, `B`, `C`, `D`, `E`, `F`, `G`;
- B/C valutati in UTC;
- D valutato in UTC, inclusi giorni, range e overnight;
- classifica attivita: drone, military, parachute, firing, airshow, SAR, generic;
- classifica severita: `HARD`, `SOFT`, `INFO`;
- produce `blockingReason` solo se temporalmente attivo.

Effetto in `resolveFinalStatus`:

- NOTAM ICAO non attivo: se il base era zebra, puo sbloccare a 120 (`NOTAM_INACTIVE_OVERRIDE`);
- NOTAM attivo `HARD`: `finalLimit = 0`, source `NOTAM`, blocker `ACTIVE_HARD_NOTAM`;
- NOTAM attivo `SOFT`: warning `ACTIVE_SOFT_NOTAM` se il limite finale e' ancora maggiore di 0.

## SUP

`functions/core/sup.js`:

- legge `functions/data/sup_enriched.json`;
- match per nome normalizzato;
- valuta `dateFrom` e `dateTo`;
- restituisce `authorizationRequired`, `operationCategory` e periodo.

Effetto in `resolveFinalStatus`:

- SUP attiva con autorizzazione richiesta: aggiunge blocker `ACTIVE_SUP_AUTH_REQUIRED` e source `SUP`;
- SUP inattiva: warning `SUP_INACTIVE` e possibile override a 120.

Nota tecnica: la documentazione storica segnala una differenza frontend/backend sull'ultimo giorno SUP. Il frontend considera fine giornata, il backend costruisce `Date` dalla data e puo interpretarla come inizio giorno. Da correggere.

## Blocker E Warning

Blocker osservati:

- `ACTIVE_ENR`;
- `ACTIVE_HARD_NOTAM`;
- `ACTIVE_SUP_AUTH_REQUIRED`.

Warning osservati:

- `ACTIVE_SOFT_NOTAM`;
- `SUP_INACTIVE`;
- `ENR_INACTIVE_NOW`;
- `ENR_TEMPORAL_UNKNOWN`.

Lo stato finale viene derivato da `finalLimit`:

- `0` -> `NO_FLY`;
- `1..119` -> `LIMITED`;
- `120+` -> `OPEN`.

## Zone Sovrapposte

Backend:

- `zoneCheckV2` include tutte le `zones` intersecanti;
- la decisione passa da `resolveFinalStatus`.

Frontend:

- `getBestFeatureAt` sceglie una feature prioritaria per pannello;
- `getOverlappingZonesAtPoint` elenca le sovrapposte;
- il calcolo locale dipende dai layer visibili.

Android non dovrebbe decidere localmente quale zona prevale per il verdetto. Puo usare le feature cartografiche statiche per il rendering, ma il verdetto deve arrivare da backend `zoneCheckV3`.

## Distanza Dalla Zona

`distanceToExitNFZ`:

- richiede `bearing`;
- proietta una linea fino a 2 km;
- interseca la traiettoria con feature a limite 0;
- restituisce distanza minima in metri.

Questa e' una stima direzionale, non una distanza topologica completa al bordo piu vicino.

## Stato Di Maturita

Punti solidi:

- il motore backend esiste;
- integra geometrie, limiti, ENR, NOTAM, SUP;
- espone risposta JSON gia adatta a un client mobile;
- evita duplicazione completa lato client.

Punti da consolidare:

- sicurezza `debug=true` in `applySecurity`;
- contratti e test regressivi sui casi ENR/NOTAM/SUP;
- differenze frontend/backend su SUP;
- duplicazione di parsing temporale tra frontend e backend;
- logica SUP blocker: aggiunge blocker/source ma non forza chiaramente `finalLimit = 0` nel punto osservato;
- formato stabile per `zones[]`;
- performance su molti layer e bbox dense;
- gestione versioning dataset.

## Raccomandazione Android

Per il primo MVP:

- usare `zoneCheckV3` come unica fonte del verdetto Android;
- non implementare in Kotlin parsing ENR, NOTAM o SUP;
- non copiare `geojson-pill.js`;
- usare un modello locale solo per mappare `status`, `maxAltitude`, `blockers`, `warnings` in UI;
- introdurre test end-to-end backend prima di legare la UI Android a casi operativi sensibili.
