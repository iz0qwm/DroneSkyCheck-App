# Android Backend Gaps

Nota 2026-08-08: questo audit e' storico. La direzione attuale implementata e'
`zoneCheckV3` per il verdetto operativo e mirror statico KWOS di `public/data`
per le geometrie Android. Le note sotto restano utili come contesto dei gap
precedenti, ma non rappresentano piu la scelta architetturale corrente.

Questo documento elenca cio che manca o va consolidato nel backend storico prima di sviluppare seriamente la mappa nativa Android.

Fonti principali: `functions/api/zoneCheckV2.js`, `functions/api/zones.js`, `functions/core/security.js`, `firestore.rules`, `storage.rules`, `public/main.js`, `public/js/utility-layers.js`, `docs/developer/backend.md`, `docs/developer/schedule-engine.md`.

## Priorita Alta

### 1. Sicurezza Mobile Per API Cartografiche

Stato attuale:

- `zoneCheckV2` e `zones` richiedono `x-api-key`;
- le chiavi sono hardcoded in `functions/core/security.js`;
- una app Android non puo proteggere una chiave statica nel binario;
- `debug=true` bypassa la verifica API key.

Gap:

- supportare Firebase ID token per utenti autenticati;
- valutare Firebase App Check con Play Integrity;
- rimuovere o limitare il bypass debug;
- spostare le API key fuori dal codice sorgente, ad esempio in config/secret controllato;
- definire rate limit per device/app/user, non solo per key in memoria.

### 2. Contratto Viewport Per La Mappa

Stato attuale:

- `zones` supporta `bbox`, `type`, `restriction`, `simplify`, `limit`;
- non gestisce `zoom`;
- non ha paginazione;
- non ha `dataVersion`;
- puo troncare con `truncated=true`.

Gap:

- parametro `zoom` o `detailLevel`;
- filtro layer multiplo, ad esempio `layers=P_NOTAM,ATM09_CTR`;
- risposta con `dataVersion`, `generatedAt`, `sourceVersion`;
- strategia per `truncated=true`;
- ETag o hash per cache locale;
- endpoint diff/updates per ridurre traffico;
- regole per geometrie molto grandi a zoom basso.

### 3. Consolidamento `zoneCheckV2`

Stato attuale:

- motore backend gia utile;
- integra ENR, NOTAM, SUP;
- ci sono differenze documentate tra frontend e backend.

Gap:

- test regressivi per casi reali OPEN/LIMITED/NO_FLY;
- test su NOTAM attivo, futuro, scaduto e schedule D);
- test su ENR H24, MON-FRI, HOL, SR/SS/HJ;
- test su SUP ultimo giorno;
- chiarire se `ACTIVE_SUP_AUTH_REQUIRED` deve sempre produrre `NO_FLY` o solo blocker informativo;
- stabilizzare schema `zones[]`.

## Priorita Media

### 4. Dettaglio Zona Come API JSON

Oggi molti dettagli sono ricostruiti nel frontend:

- `zone_details.json`;
- `zones_glossary.json`;
- `aviosuperfici_refs.json`;
- `uasgz_enriched.json`;
- pannelli ENR/NOTAM/SUP;
- contatti autorita;
- badge autorizzativi.

Gap:

- endpoint `zoneDetails` o estensione di `zoneCheckV2`;
- testo ufficiale e contatti in JSON;
- riferimenti a fonti;
- stato temporale gia interpretato;
- requisiti autorizzativi essenziali.

### 5. NOTAM JSON Dedicato

`notamProxy` restituisce HTML. Android non dovrebbe fare scraping HTML.

Gap:

- endpoint JSON per NOTAM per codice;
- raw ICAO opzionale;
- periodo, schedule, activeNow, severity;
- fonte dati e timestamp;
- gestione errori upstream.

### 6. Ricerca Localita

La webapp usa logica frontend. Per Android serve decidere se usare direttamente un provider o un proxy backend.

Gap:

- endpoint geocoding se si vuole controllare provider, rate limit e privacy;
- normalizzazione risultati;
- bounding su Italia o contesto UAS;
- cache.

### 7. Profilo Utente E Luoghi Salvati

Il modello account/DSC+ esiste, ma per Android serve un contratto semplice.

Gap:

- decidere se Android legge/scrive direttamente Firestore o passa da callable functions;
- endpoint o repository per luoghi salvati;
- modello offline/cache;
- migrazione da local web storage a cloud se necessario.

## Priorita Bassa Per MVP

### 8. Realtime Traffic

Firestore espone in lettura pubblica traffico, receiver e tracker.

Gap:

- filtri per viewport;
- retention e privacy mobile;
- limiti letture Firestore;
- UX e battery policy;
- eventuale Cloud Function aggregata per evitare listener troppo ampi.

### 9. Meteo

`metarProxy` e meteo runtime esistono, ma sono accessori.

Gap:

- endpoint meteo puntuale aggregato;
- cache coerente;
- policy provider/API key;
- scelta di quali dati mostrare nel primo MVP.

### 10. Autorizzazioni, USEPPO, Flight Log

Sono domini avanzati.

Gap:

- non portarli nel primo MVP mappa;
- quando serviranno, partire da API server-side e Firebase Auth;
- non copiare tool web.

## Debito Tecnico Rilevante

- Logica temporale duplicata tra frontend e backend.
- Correzioni dati applicate in frontend invece che nella pipeline dati.
- Layer caricati in blocco nella webapp.
- API key nel codice sorgente.
- Rate limit in memoria per istanza, non distribuito.
- Alcuni endpoint pubblici non hanno lo stesso modello di auth.
- `zoneCheck` legacy convive con `zoneCheckV2`.
- Proxy HTML NOTAM non e' un contratto mobile.
- Firestore realtime e' pubblico in lettura per molte collection.
- Alcune parti hanno caratteri/encoding legacy nei commenti e nelle stringhe.

## Decisioni Prima Della Schermata Mappa

1. La mappa MVP sara anonima o richiedera login Firebase?
2. Quale sicurezza API usare per Android: Firebase ID token, App Check/Play Integrity, API key dinamica o combinazione?
3. `zones` deve evolvere subito con `zoom`, `layers`, `dataVersion` e paginazione?
4. Il verdetto operativo Android usera solo `zoneCheckV2` o anche calcolo offline ridotto?
5. Quale SDK mappa testare per molti GeoJSON: MapLibre Native o Google Maps SDK/Maps Compose?
6. Quali layer sono obbligatori nel primo MVP?
7. Quanta cache locale e' accettabile per dati aeronautici variabili?
8. Come mostrare NOTAM/ENR/SUP senza sovraccaricare UI mobile?

## Raccomandazione MVP

Prima implementazione Android consigliata:

- mappa nativa;
- posizione utente;
- caricamento viewport via `zones`;
- verdetto punto utente via `zoneCheckV2`;
- tap su mappa con bottom sheet sintetica;
- cache breve dei viewport;
- nessun porting di pannelli web;
- nessuna logica decisionale duplicata;
- nessun DSC+ obbligatorio.

Prima di iniziare codice mappa, chiudere almeno il gap di sicurezza mobile e il contratto minimo di `zones`.
