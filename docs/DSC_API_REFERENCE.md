# DSC API Reference For Android

Audit eseguito contro codice storico e documentazione pubblica API.

Fonti principali: `functions/index.js`, `functions/api/zoneCheckV2.js`, `functions/api/zones.js`, `functions/core/security.js`, `public/api/index.html`, `public/api/app.js`, `docs/developer/backend.md`.

## Classificazione Rapida

| API/funzione | Stato per Android | Nota |
| --- | --- | --- |
| `zoneCheckV2` | Riutilizzabile con modifiche di sicurezza | Contratto piu importante per il verdetto operativo. Oggi usa `x-api-key`. |
| `zones` | Riutilizzabile con modifiche di contratto | Gia supporta bbox. Mancano zoom/tiling/versioning piu robusti. |
| `zoneCheck` legacy | Non usare per nuova app | Endpoint precedente, logica piu vecchia e meno completa. |
| `notamProxy` | Non chiamare direttamente da app | Meglio passare da `zoneCheckV2`, salvo dettaglio NOTAM dedicato futuro. |
| `metarProxy` | Riutilizzabile, non core MVP | Utile per pannello meteo aeronautico. Pubblico. |
| `airplanesLiveProxy`, `adsbLolProxy` | Valutare dopo MVP | Proxy traffico aereo, non necessario per prima mappa UAS. |
| Firestore realtime public reads | Riutilizzabile dopo MVP | Droni, receiver, tracker, DOA. Richiede SDK Firebase e regole chiare. |
| Account callable functions | Riutilizzabili per DSC+ | Richiedono Firebase Auth. Non servono alla prima mappa anonima. |
| Flight Log HTTP APIs | Future DSC+ | Richiedono Firebase ID token e feature `flightLogImport`. |

## Sicurezza API Attuale

`zoneCheckV2`, `zones` e `stats` usano `applySecurity`:

- header richiesto: `x-api-key`;
- errore `401` se assente o invalido;
- rate limit standard: 40 richieste/minuto per key;
- varianti interne in `core/security.js` per path contenenti `lite` o `route`;
- logging su Firestore `api_usage`.

Problema Android: una API key statica hardcoded sarebbe estraibile dall'APK. Prima del rilascio mobile serve un meccanismo basato almeno su Firebase ID token, App Check/Play Integrity, oppure un token broker lato backend.

Nota: in `core/security.js` il parametro `debug=true` bypassa la API key. Questo va rimosso o limitato a chiave interna prima di considerare gli endpoint sicuri per mobile.

## `zoneCheckV2`

Endpoint documentato:

- metodo: `GET`;
- Cloud Run URL osservato in docs: `https://zonecheckv2-32dg4v266a-uc.a.run.app/zoneCheckV2`;
- autenticazione: header `x-api-key`;
- implementazione: `functions/api/zoneCheckV2.js`;
- moduli dipendenti: `functions/core/loader.js`, `functions/core/engine.js`, `functions/core/enr.js`, `functions/core/notam.js`, `functions/core/sup.js`, `functions/core/security.js`.

Parametri query:

| Parametro | Tipo | Obbligatorio | Uso |
| --- | --- | --- | --- |
| `lat` | number | Si | Latitudine decimale. |
| `lon` | number | Si | Longitudine decimale. |
| `bearing` | number | No | Direzione per stimare `distanceToExitMeters` in NFZ. |
| `debug` | boolean/string | No | Include dettagli aggiuntivi, ma oggi bypassa sicurezza se true. Da correggere. |

Risposta principale:

```json
{
  "position": { "lat": 41.9361, "lon": 12.4312 },
  "status": "NO_FLY",
  "maxAltitude": 0,
  "zebra": true,
  "currentZone": { "name": "NOTAM W0363/26", "where": "ENR" },
  "nearestOpen": {
    "distanceMeters": 1200,
    "zone": { "name": "Zona vicina", "limit": 120 }
  },
  "distanceToExitMeters": 320,
  "analysis": {
    "source": "NOTAM",
    "blockers": [],
    "warnings": []
  },
  "zones": [],
  "meta": { "engine": "DSC", "version": "v2-production" }
}
```

`status` puo essere `OPEN`, `LIMITED`, `NO_FLY`. `analysis.source` puo essere `BASE`, `ENR`, `NOTAM`, `SUP`, `NOTAM_INACTIVE_OVERRIDE`, `SUP_INACTIVE_OVERRIDE`, `ENR_INACTIVE_OVERRIDE`.

Errori osservati:

- `400 Invalid coordinates`;
- `401 Invalid API key`;
- `429 Too many requests`;
- `500 Internal error`.

Uso attuale nella webapp:

- documentazione API e try-it: `public/api/index.html`, `public/api/app.js`;
- vista 3D documentata come consumatrice: `docs/user/airspace-3d.md`;
- la mappa web principale usa ancora molta logica locale e non dipende sempre da questa API.

Valutazione Android:

- da usare come sorgente primaria per il verdetto operativo;
- non duplicare il motore in Kotlin;
- prima del rilascio: sistemare auth mobile, debug bypass, test regressivi su casi ENR/NOTAM/SUP, latenza e timeout.

## `zones`

Endpoint documentato:

- metodo: `GET`;
- Cloud Run URL osservato in docs: `https://zones-32dg4v266a-uc.a.run.app/`;
- autenticazione: header `x-api-key`;
- implementazione: `functions/api/zones.js`;
- dipendenze: `functions/core/loader.js`, Turf, `functions/core/security.js`.

Parametri query:

| Parametro | Tipo | Obbligatorio | Uso |
| --- | --- | --- | --- |
| `bbox` | string | Si | `minLat,minLon,maxLat,maxLon`. Attenzione: non e' ordine GeoJSON. |
| `simplify` | boolean | No | Se `true`, usa `turf.simplify` con tolerance `0.001`. |
| `limit` | number | No | Default 200, massimo 1000. |
| `type` | string | No | Filtro su `properties.type`. |
| `restriction` | string | No | Filtro su `properties.restriction`. |

Risposta:

```json
{
  "type": "FeatureCollection",
  "count": 2,
  "truncated": false,
  "features": [
    {
      "type": "Feature",
      "properties": {
        "id": "ZONE_ID",
        "name": "Nome zona",
        "type": "ATM09_RESTRICTED",
        "restriction": "PROHIBITED",
        "lowerLimit": 0,
        "upperLimit": 120
      },
      "geometry": { "type": "Polygon", "coordinates": [] }
    }
  ]
}
```

Tipi documentati:

`P_NOTAM`, `P_PARKS`, `P_PARKS_ENR`, `P_SUP`, `ATM09_PARKS`, `ATM09_CTR`, `ATM09_LIF`, `ATM09_PRISON`, `ATM09_RESTRICTED`, `ATM09_DANGER`, `TACTICAL_ENR_5_2_2_6`, `CORRIDOR_ENR_5_2_2_5`, `ATM09_AVIOSUP`, `ATM09_OTHER`, `Other`.

Errori:

- `400 Missing bbox`;
- `400 Invalid bbox`;
- `400 Invalid bbox order`;
- `401 Invalid API key`;
- `429 Too many requests`;
- `500 Internal error`.

Caching:

- cache in memoria per chiave bbox/simplify/limit/type/restriction, TTL 30 secondi.

Valutazione Android:

- e' la base migliore per caricamento viewport;
- mancano ancora parametri espliciti `zoom`, `layers`, `minImportance`, `geometryDetail`, `dataVersion`;
- il limite massimo 1000 puo troncare viewport molto densi;
- serve un contratto stabile per aggiornamento incrementale e cache locale.

## `zoneCheck` Legacy

Implementazione: `functions/index.js`.

Endpoint storico con parametri `lat`, `lon`, `bearing`, `compact`. Carica tutte le feature locali e restituisce `currentZone`, `nearestOpenZone`, `summary`, oppure forma compatta.

Valutazione Android: non usarlo come base. E' utile per capire la prima versione del motore, ma `zoneCheckV2` contiene ENR/NOTAM/SUP e risposta piu adatta.

## Proxy NOTAM

Implementazione: `functions/index.js` (`notamProxy`).

Metodo: `GET`.

Parametri:

- `serie`: lettera A-Z;
- `numero`: 3-4 cifre;
- `anno`: 2 cifre.

Risposta: HTML da `deskaeronautico.it`, `Content-Type: text/html`, cache 5 minuti.

Valutazione Android:

- non chiamare direttamente nel primo MVP;
- usare `zoneCheckV2` per avere NOTAM gia interpretati;
- se in futuro serve dettaglio NOTAM ufficiale, creare endpoint JSON backend dedicato.

## `metarProxy`

Implementazione: `functions/index.js`.

Metodo: `GET`.

Parametri:

- `icao`: codice ICAO.

Risposta: primo oggetto JSON restituito da Aviation Weather. Cache HTTP 120 secondi.

Usato da: `public/js/metar/metar-service.js`.

Valutazione Android: utile per una futura bottom sheet meteo, non blocca il MVP mappa.

## Proxy Traffico Aereo

Implementazioni:

- `airplanesLiveProxy` in `functions/index.js`;
- `adsbLolProxy` in `functions/index.js`.

Parametri:

- `lat`, `lon`, `radius`;
- `radius` intero tra 1 e 250.

Risposta: JSON pass-through da provider. Cache HTTP 5 secondi.

Valutazione Android: non prioritaria. Il traffico aereo in app nativa richiede UX e consumo batteria dedicati.

## Firestore Realtime

Letture pubbliche osservate:

- `air_traffic_objects`;
- `air_traffic_objects/{id}/points`;
- `receivers`;
- `trackers`;
- `traffic_live`;
- `doa_active`.

Scritture client bloccate per queste collection. Ingestione via Cloud Functions:

- `ingestTrafficObject` in `functions/traffic/ingestTraffic.js`;
- `ingestTrackerHeartbeat` in `functions/trackers/ingestTrackerHeartbeat.js`;
- `ingestAircraftLive` in `functions/traffic/ingestAircraftLive.js`.

Valutazione Android: usare dopo MVP, con listener mirati e filtri di viewport se disponibili. Oggi le regole sono molto aperte in lettura.

## Account Callable Functions

Implementazione: `functions/account/account-public-functions.js`.

Le funzioni callable principali richiedono `request.auth`:

- `ensurePilotPublicId`;
- `ensureOperatorOwnerMembership`;
- `lookupPilotForOperatorInvitation`;
- `createOperatorInvitation`;
- `listOperatorMembers`;
- `listOperatorInvitations`;
- `listReceivedOperatorInvitations`;
- `listLinkedOperators`;
- `acceptOperatorInvitation`;
- `declineOperatorInvitation`;
- `cancelOperatorInvitation`;
- `revokeOperatorMember`;
- `updateOperatorMemberDocumentPermission`;
- `leaveOperator`;
- `deleteMyCloudData`;
- `deleteMyAccount`.

Valutazione Android: riutilizzabili quando l'app introdurra account/DSC+. Per il primo MVP mappa, si possono lasciare fuori.

## Flight Log HTTP APIs

Implementazione: `functions/fleet/flight-log-sync.js`.

Endpoint esportati:

- `checkDjiFlightLogImport`;
- `checkDjiFlightLogImports`;
- `importDjiFlightLog`.

Autenticazione: `Authorization: Bearer <Firebase ID token>`.

Requisiti:

- membership attiva;
- feature `flightLogImport`;
- rate limit server-side;
- per upload: `multipart/form-data`, campo `logfile`, max 50 MB;
- secret `DJI_FLIGHT_LOG_API_SECRET` resta lato backend.

Valutazione Android: modello di sicurezza buono per endpoint mobile autenticati. Non necessario per la prima schermata mappa.

## Funzionalita Mancanti Come API Mobile

- endpoint JSON per dettagli zona selezionata, con testo ufficiale, contatti e riferimenti senza dipendere dal frontend;
- endpoint NOTAM JSON dedicato per dettaglio raw/interpretato;
- endpoint viewport con `zoom`, lista layer e versioning dataset;
- endpoint per aggiornamenti/diff dei layer;
- endpoint profilo/luoghi salvati mobile-friendly, se non si vuole esporre direttamente Firestore;
- endpoint per ricerca localita normalizzato lato backend;
- endpoint mappa che combini `zones` + verdetto sintetico per centro viewport o punto utente.
