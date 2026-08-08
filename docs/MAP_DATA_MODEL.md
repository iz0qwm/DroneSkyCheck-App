# Map Data Model

Questo documento descrive il modello dati geografico del sistema storico utile alla nuova app Android. Non e' una specifica UI.

Fonti principali: `public/main.js`, `public/js/utility-layers.js`, `public/js/geojson-pill.js`, `functions/api/zones.js`, `functions/core/loader.js`, `docs/developer/datasets/geozones.md`, `docs/user/map.md`.

## Sorgenti Dati

La mappa storica usa due copie dei dataset:

- `public/data/`: dati serviti direttamente alla webapp;
- `functions/data/`: dati usati dal backend Firebase.

Le due copie derivano dalla pipeline esterna `d-flight_JSON`, citata nella documentazione storica. Il repository storico consuma dataset gia generati, non effettua download D-Flight al runtime.

Dataset geografici principali:

- `dflight_geozones.json`;
- `dflight_geozones_clean.geojson`;
- `public/data/split/*.geojson`;
- `functions/data/split/*.geojson`;
- `notams_WE.json`;
- `enr_enriched.json`;
- `sup_enriched.json`;
- `zone_details.json`;
- `zones_glossary.json`;
- `aviosuperfici_refs.json`.

## Layer GeoJSON Principali

Definizione frontend: `public/main.js`.

| Key | File frontend | Significato |
| --- | --- | --- |
| `P_NOTAM` | `data/split/P_NOTAM.geojson` | NOTAM/NFZ temporanee. |
| `P_PARKS` | `data/split/P_PARKS.geojson` | Parchi e aree protette NFZ. |
| `P_PARKS_ENR` | `data/split/P_PARKS_ENR_561.geojson` | Parchi ENR 5.6.1. |
| `P_SUP` | `data/split/P_SUP.geojson` | SUP AIP temporanee. |
| `P_SECURITY` | `data/split/P_SECURITY.geojson` | UAS GZ SEC/PREF. |
| `ATM09_PARKS` | `data/split/ATM09_PARKS.geojson` | Altri parchi da D-Flight/ATM09. |
| `ATM09_CTR` | `data/split/ATM09_CTR.geojson` | CTR. |
| `ATM09_LIF` | `data/split/ATM09_LIF.geojson` | LIF/poligoni militari. |
| `ATM09_PRISON` | `data/split/ATM09_PRISON.geojson` | Zone proibite LI P / carceri. |
| `ATM09_RESTRICTED` | `data/split/ATM09_RESTRICTED.geojson` | Zone regolamentate LI R. |
| `ATM09_DANGER` | `data/split/ATM09_DANGER.geojson` | Zone pericolose LI D. |
| `ATM09_AVIOSUP` | `data/split/ATM09_AVIOSUP_REBUILT.geojson` | Avio/eli/idrosuperfici. |
| `ATM09_OTHER` | `data/split/ATM09_OTHER.geojson` | Aeroporti e altre aree. |
| `Other` | `data/split/Other.geojson` | ATZ, spesso 60 m. |
| `TACTICAL_ENR_5_2_2_6` | runtime HTML, no file statico | Aree tattiche ENR. |
| `CORRIDOR_ENR_5_2_2_5` | runtime HTML, no file statico | Zone e corridoi APR ENR. |

Backend: `functions/core/loader.js` carica una lista simile da `functions/data/split/` e aggiunge feature runtime per aree tattiche e corridoi ENR.

Nota verificata: `functions/core/loader.js` include `ATM09_AVIOSUP_REBUILT.geojson` nella lista `LAYERS`, ma nel codice letto la costante contiene anche riferimenti a layer che possono non esistere; gli errori vengono gestiti con warning e skip.

## Formato Feature

Il formato base e' GeoJSON:

- `FeatureCollection`;
- `Feature`;
- geometrie `Polygon`, `MultiPolygon`, `LineString`, `Point`;
- coordinate GeoJSON in ordine `[lon, lat]`.

Properties ricorrenti:

- `id` o `identifier`;
- `name`;
- `type` o `_splitType`;
- `restriction`;
- `otherReasonInfo`;
- `lowerLimit`, `lowerlimit`, `lowerLimitAGL`;
- `upperLimit`, `upperlimit`;
- `maxHeight`, `maxAltitude`, `altitudeAGL`;
- campi runtime `_temporal`, `_semantic`, `_inactive`, `_pane`, `_override`.

L'API `zones` normalizza e riduce la risposta a:

- `id`;
- `name`;
- `type`;
- `restriction`;
- `lowerLimit`;
- `upperLimit`;
- `geometry`.

## Quote E Colori

Frontend: `public/js/utility-layers.js` e `public/js/geojson-pill.js`.

Quote operative mappate:

- 0 m: rosso / NFZ;
- 25 m: arancione;
- 45 m: giallo;
- 60 m: celeste;
- 120 m: open/bianco/vuoto.

`styleByAltitude` usa `_splitType` per differenziare opacita e stile. I NOTAM `P_NOTAM` sono zebrati salvo casi con `ATS_ACTIVATION_REQUIRED`.

Questa logica e' UI-specifica. Android deve riusare il significato delle quote, non copiare colori o layout.

## Priorita E Sovrapposizioni

La webapp mantiene `window._allFeatures` e usa Turf per point-in-polygon.

Regole osservate:

- `getBestFeatureAt` ignora layer non visibili;
- ignora feature con `_inactive`;
- `P_NOTAM` e `P_SUP` hanno priorita alta nella selezione;
- poi vince la feature con limite piu restrittivo;
- `getOverlappingZonesAtPoint` raccoglie zone sovrapposte e le ordina per `lowerLimit`;
- `zoneCheckV2` invece valuta tutte le feature backend che intersecano il punto e risolve con `resolveFinalStatus`.

Per Android il verdetto operativo deve arrivare da `zoneCheckV3`. Le geometrie
cartografiche non devono arrivare da `/zones`, ma dal mirror statico KWOS di
`public/data`.

## Caricamento Attuale Della Webapp

All'avvio `public/main.js`:

- crea `window._allFeatures`;
- definisce `LAYERS`;
- crea `layerGroups`;
- aggiunge quasi tutti i layer alla mappa Leaflet;
- carica i GeoJSON con `loadLayerOnce`;
- carica anche i layer parchi nascosti tramite toggle virtuale;
- registra ogni feature in `_allFeatures`;
- esegue styling, override e arricchimenti temporali.

Questo significa che la webapp tende a scaricare molti GeoJSON statici all'apertura. Per Android questo non e' ideale.

## Caricamento Per Bounding Box

L'endpoint `zones` consente gia di richiedere geometrie per bounding box:

`bbox=minLat,minLon,maxLat,maxLon`

Il backend:

- carica feature con `loadFeatures`;
- costruisce un `bboxPolygon`;
- usa `turf.booleanIntersects`;
- applica filtri `type` e `restriction`;
- semplifica se richiesto;
- tronca a `limit` max 1000;
- cachea la risposta per 30 secondi.

`/zones` resta documentata per compatibilita e altri utilizzatori, ma non e' la
sorgente prevista per la prima app Android nativa.

Gaps per viewport Android:

- nessun parametro `zoom`;
- nessun elenco layer multiplo esplicito, solo `type`;
- nessun `dataVersion`;
- nessun ETag/app-level diff;
- nessuna paginazione/cursor quando `truncated=true`;
- nessuna geometria vettoriale tile-based;
- nessuna separazione detail/preview per zoom basso;
- nessuna strategia ufficiale per zone molto grandi.

## Runtime ENR: Aree Tattiche E Corridoi

`functions/core/loader.js` e `public/main.js` generano feature runtime da HTML ENR remoto:

- `ENR_5.2.2.6.html` per aree tattiche;
- `ENR_5.2.2.5.html` per corridoi APR.

I corridoi vengono convertiti in buffer poligonale a partire da `LineString` e larghezza in NM. Le aree tattiche vengono convertite in poligoni.

Implicazione Android: non ricostruire parsing HTML nel client. Se queste geometrie sono necessarie in mappa, devono essere esportate nella pipeline statica/mirror KWOS oppure rimanere calcolate dal backend operativo.

## Override E Correzioni Locali

La webapp contiene correzioni manuali:

- override coordinate aviosuperfici in `public/main.js`;
- rimozione aviosuperfici sospese da `aviosuperfici_refs.json`;
- correzione temporanea geometria ATZ Albenga in `public/js/utility-layers.js`;
- forcing di alcune zone `ATM09_OTHER` in `ATM09_RESTRICTED`.

Queste correzioni sono dominio/dato, non UI. Se servono ad Android, dovrebbero stare nella pipeline dataset o nel backend, non nel client.

## Informazioni Caricate Subito

All'apertura web vengono caricati o preparati:

- layer GeoJSON principali;
- `zones_glossary.json`;
- `aviosuperfici_refs.json`;
- `zone_details.json`;
- `uasgz_enriched.json`;
- `enr_enriched.json`;
- `sup_enriched.json`;
- `build-version.json`;
- listener Firestore per contatori/traffico quando attivati.

Android dovrebbe invece caricare inizialmente:

- posizione o viewport iniziale;
- geometrie essenziali dal mirror KWOS;
- verdetto puntuale via `zoneCheckV3`;
- dettagli solo su tap o apertura bottom sheet.

## Raccomandazione Per MVP Android

Per la prima mappa nativa:

- usare i GeoJSON statici da `https://www.kwos.org/appoggio/droni/DroneSkyCheck/`;
- non usare `/zones` per le geometrie Android;
- usare `zoneCheckV3` sul punto utente e sul punto selezionato;
- non duplicare parser ENR/NOTAM/SUP in Kotlin;
- lasciare la logica aeronautica al backend;
- introdurre cache locale e versionamento dataset in una fase successiva.
