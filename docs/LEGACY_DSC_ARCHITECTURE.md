# Legacy DSC Architecture

Audit eseguito sul repository storico `C:\Users\raffa\DroneSkyCheck`, usato solo come riferimento. Il nuovo progetto Android resta `C:\Users\raffa\AndroidStudioProjects\DroneSkyCheck`.

## Sintesi

Drone Sky Check storico e' una piattaforma web/PWA con backend Firebase e servizi di supporto esterni. La nuova app Android non deve copiare la UI web, ma puo riusare dominio, dati e API gia presenti.

La parte piu utile per Android e' il blocco geografico:

- Cloud Functions in `functions/`;
- motore geografico in `functions/core/`;
- API pubbliche in `functions/api/`;
- dataset aeronautici in `functions/data/` e `public/data/`;
- mappa web Leaflet in `public/main.js` e `public/js/utility-layers.js`.

## Struttura Generale

| Area | Percorso storico | Ruolo rilevante per Android |
| --- | --- | --- |
| Web app | `public/` | PWA attuale, mappa Leaflet, pannelli, strumenti, Firebase SDK compat. |
| Backend Firebase | `functions/` | API, proxy, ingestione traffico, account, DSC+, AI, flight log. |
| Dataset frontend | `public/data/` | GeoJSON, NOTAM, ENR, SUP e metadati caricati dal browser. |
| Dataset backend | `functions/data/` | Copia usata dalle Cloud Functions per `zoneCheckV2` e `zones`. |
| Documentazione | `docs/`, `site/` | Documentazione gia generata su architettura, dataset e utenti. |
| Capacitor legacy | `capacitor/` | Wrapper Android della webapp. Solo riferimento, non base nativa. |
| Servizi esterni locali | `SERVER_DEM/`, `SERVER_OGN/`, `SERVER_OPENSKY_PROXY/`, `SERVER_UPLOAD_AUTH/`, `SERVER_DJI_LOG/` | Servizi Python/Hetzner usati da webapp/backend. |

Fonti principali: `functions/index.js`, `docs/developer/backend.md`, `docs/developer/data-sources.md`, `firebase.json`.

## Firebase Hosting

Hosting pubblica `public/` e applica rewrite verso alcune funzioni:

- `/proxy/notam` -> `notamProxy` in `europe-west8`;
- `/proxy/airplanes-live` -> `airplanesLiveProxy`;
- `/proxy/adsb-lol` -> `adsbLolProxy`.

I GeoJSON statici hanno header `Cache-Control: public, max-age=86400, immutable` e `Content-Type: application/geo+json`.

Fonte: `firebase.json`.

## Cloud Functions

`functions/index.js` e' l'entrypoint principale. Usa Firebase Functions v2 e runtime Node/ES modules.

Funzioni direttamente definite in `functions/index.js`:

- `zoneCheck`: endpoint storico puntuale, pubblico, senza la nuova sicurezza `x-api-key`;
- `notamProxy`: proxy HTML verso `deskaeronautico.it`;
- `metarProxy`: proxy JSON verso Aviation Weather;
- `airplanesLiveProxy` e `adsbLolProxy`: proxy per provider ADS-B point;
- export di moduli API, account, traffico, tracker, AI, USEPPO, fleet e manutenzione.

Funzioni piu importanti per Android:

- `zoneCheckV2` in `functions/api/zoneCheckV2.js`;
- `zones` in `functions/api/zones.js`;
- funzioni callable account in `functions/account/account-public-functions.js`;
- endpoint flight log in `functions/fleet/flight-log-sync.js`, utile solo per una fase DSC+/fleet futura;
- ingestion e letture realtime Firestore per droni/receiver/tracker, non necessarie per il primo MVP mappa.

## Firestore

Firestore viene usato per dati realtime, account e DSC+:

- traffico UAS: `air_traffic_objects`, subcollection `points`;
- receiver: `receivers`;
- tracker: `trackers`;
- traffico aereo live: `traffic_live`;
- aree operative temporanee: `doa_active`;
- presenza: `presence`;
- statistiche API: `api_usage`;
- account/membership: `memberships`, `dscCoreContexts`, `pilotPublicIds`, `uasOperators`, `userOperatorMemberships`, sotto-collezioni documentali e fleet.

Le regole permettono lettura pubblica per molti layer realtime (`air_traffic_objects`, `traffic_live`, `receivers`, `trackers`, `doa_active`) e bloccano le scritture client. Le scritture avvengono via backend. Le collection account richiedono `request.auth.uid` e regole piu strette.

Fonti: `firestore.rules`, `docs/developer/backend.md`, `docs/developer/datasets/realtime.md`.

## Firebase Authentication

La webapp usa Firebase Auth compat SDK. Login osservato:

- email/password con `signInWithEmailAndPassword`;
- Google con `GoogleAuthProvider` e `signInWithPopup`;
- callable functions con `request.auth` per funzioni account;
- endpoint HTTP per Flight Log con header `Authorization: Bearer <Firebase ID token>`.

Fonti: `public/js/core/firebase.js`, `public/js/core/account/account-service.js`, `public/sync-login.html`, `functions/account/account-security.js`, `functions/fleet/flight-log-sync.js`.

## Firebase Storage

Storage e' usato per documenti operatore DSC+. Il path principale consentito e':

`operators/{operatorId}/documents/{documentId}/versions/{versionId}/{storedFileName}`

Le regole controllano membership attiva, permesso `manageDocuments`, dimensione massima 25 MB e content type ammessi.

Fonte: `storage.rules`, `public/js/core/account/document-registry-service.js`.

## Servizi Esterni

| Servizio | Uso | File sorgente |
| --- | --- | --- |
| `deskaeronautico.it` | fallback NOTAM HTML | `functions/index.js`, `functions/api/zoneCheckV2.js` |
| Aviation Weather | METAR JSON | `functions/index.js`, `public/js/metar/metar-service.js` |
| `kwos.org` ENR | HTML ENR runtime per aree tattiche/corridoi e dettagli | `functions/core/loader.js`, `functions/core/enr.js`, `public/main.js` |
| Overpass API | POI per autorizzazioni | `functions/authorization/getAreaPOIs.js` |
| Open-Meteo/Meteoblue/RainViewer/OpenWeatherMap | meteo e layer weather | `docs/developer/datasets/metar-weather.md`, `public/js/tools-map.js`, `public/main.js` |
| `solarmonitor.kwos.org` | DEM, OGN, OpenSky proxy, DJI log parser | `docs/developer/backend.md`, `docs/developer/data-sources.md`, `functions/fleet/flight-log-sync.js` |
| Brevo | email transazionali DSC+ | `functions/notifications/` |
| Google Generative AI | spiegazioni AI | `functions/ai/` |

## Separazione Standard / DSC+

La parte standard della webapp include mappa, layer, ricerca, meteo, NOTAM/ENR/SUP, strumenti di consultazione e dati realtime pubblici.

DSC+ riguarda account cloud, membership, workspace sync, organization members, document registry, fleet registry, import Flight Log e feature flag.

Fonti: `docs/developer/membership-model.md`, `docs/developer/identity-model.md`, `functions/account/account-constants.js`, `functions/account/account-public-functions.js`.

Per il primo MVP Android mappa, DSC+ non e' un prerequisito funzionale. Serve pero decidere presto se la mappa nativa sara anonima, autenticata Firebase, o ibrida.

## Capacitor Legacy

Il wrapper Capacitor usa lo stesso `appId` della nuova app: `it.droneskycheck.app`.

Elementi osservati:

- carica la webapp remota `https://droneskycheck-d0136.web.app`;
- permessi `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`;
- `ACCESS_BACKGROUND_LOCATION` e' commentato;
- WebView autorizza geolocalizzazione web;
- gestisce file chooser;
- controlla `build-version.json` e pulisce cache WebView;
- mostra una finestra "what's new";
- dipendenza `@capacitor/geolocation`.

Fonti: `capacitor.config.ts`, `capacitor/android/app/src/main/AndroidManifest.xml`, `capacitor/android/app/src/main/java/it/droneskycheck/app/MainActivity.java`, `capacitor/package.json`.

Questa parte deve restare legacy. Non conviene riusarne architettura, UI o WebView per la nuova app nativa.

## Implicazione Per Android

La nuova app puo partire leggera se usa:

- `zoneCheckV2` per il verdetto puntuale;
- `zones` per geometrie viewport;
- Firebase Auth solo quando servono profilo, DSC+ o funzioni personali;
- Firestore realtime solo in una fase successiva per traffico/receiver/tracker.

La logica UAS non va duplicata nel client Android finche `zoneCheckV2` puo essere consolidata come contratto ufficiale.
