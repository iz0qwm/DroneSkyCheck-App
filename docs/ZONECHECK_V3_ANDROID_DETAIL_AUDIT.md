# zoneCheckV3 Android Detail Audit

Data: 2026-08-08.

Questo audit riguarda il workspace Android corrente. Il codice backend
`zoneCheckV3`, la webapp e `showZoneInfo()` non sono presenti in questo
repository, quindi i casi C e il confronto reale con coordinate/backend devono
essere completati nel repository server.

## Percorso dati verificato in Android

Flusso locale disponibile:

```text
zoneCheckV3 HTTP JSON
-> ZoneCheckV3Repository
-> ZoneCheckV3Models.kt
-> MapViewModel
-> ZoneBottomSheet
```

Stato trovato prima della patch:

- `ZoneCheckV3Repository` leggeva solo una parte minima di `identity`,
  `classification`, `uasLimit`, `info`, `authority`, `validity`.
- `ZoneCheckV3Models.kt` non modellava testo ufficiale, validita dettagliata,
  NOTAM, ENR, SUP, authorization strutturata, authority strutturata, enriched,
  o zona responsabile del verdetto.
- `ZoneBottomSheet` mostrava solo nome, famiglia/tipo, descrizione, limite,
  stato attivo, warning/blocker globali e pochi dettagli di verdetto.

## Matrice gap Android

| Informazione | Web showZoneInfo | zoneCheckV3 | Android model prima | Android UI prima | Android dopo patch |
| --- | --- | --- | --- | --- | --- |
| Nome zona | da verificare nel backend web | gia usato se in `identity.name` | si | si | si |
| Identificativo | da verificare | se in `identity.id/code` | no | no | si |
| Classificazione | da verificare | se in `classification` | parziale | parziale | si |
| Limite UAS | da verificare | se in `uasLimit` | si | si | si |
| Descrizione | da verificare | se in `info.description` | si | si | si |
| Testo ufficiale | da verificare | C se non prodotto | no | no | si se V3 lo invia |
| Informazioni AIP | da verificare | C se non prodotto | no | no | si se V3 le invia come official/ENR/SUP |
| ENR | da verificare | C se non prodotto | no | no | si se V3 lo invia |
| NOTAM | da verificare | C se non prodotto | no | no | si se V3 li invia |
| SUP | da verificare | C se non prodotto | no | no | si se V3 lo invia |
| Authority | da verificare | se in `authority` | solo boolean auth | no | si |
| Authorization requirement | da verificare | se in `authorization`/`authority` | boolean parziale | pill parziale | si |
| Operation mode/category | da verificare | C se non prodotto | no | no | si se V3 lo invia |
| Required license | da verificare | C se non prodotto | no | no | si se V3 lo invia |
| Validita | da verificare | se in `validity` | solo `activeNow` | solo attiva/non attiva | si |
| Schedule | da verificare | C se non prodotto | no | no | si se V3 lo invia |
| activeNow | da verificare | se in `validity.activeNow` | si | si | si |
| Warning | da verificare | se in `warnings[]` | globale | globale | globale e per zona |
| Blocker | da verificare | se in `blockers[]` | globale | globale | globale e per zona |
| Spiegazione operativa | da verificare | se in `info.operationalMeaning` | no | no | si |
| Enriched data | da verificare | C se non prodotto | no | no | si, key/value |
| Zona responsabile verdetto | da verificare | se in `responsibleZone` o marker equivalente | no | no | evidenziata senza ricalcolo quote |

Legenda:

- A: dato gia presente in V3 ma non deserializzato.
- B: dato deserializzato ma non visualizzato.
- C: dato non ancora prodotto da V3.

Con il solo repository Android disponibile, sono stati corretti i casi A/B
potenziali. I casi C richiedono modifica server.

## Contratto V3 Android-ready

Android ora accetta in modo backward-compatible:

- `responsibleZone`, `limitingZone`, `verdictZone`;
- `verdict.responsibleZoneId/responsibleZoneName`;
- per ogni zona: `identity`, `classification`, `uasLimit`, `verticalLimits`,
  `official`, `info`, `validity`, `authorization`, `authority`, `notams`,
  `enr`, `sup`, `warnings`, `blockers`, `enriched`;
- testi ufficiali come `sourceText`, `officialText`, `rawText`, `icaoText`,
  `text`, o campo NOTAM `E`;
- campi NOTAM ufficiali `Q`, `A`, `B`, `C`, `D`, `E`, `F`, `G`;
- validita come `activeNow`, `validFrom`, `validTo`, `schedule`,
  `interpretedSchedule`, `future`, `expired`.

Android non interpreta schedule, NOTAM, ENR o SUP: presenta solo i valori
strutturati ricevuti.

## UI Android aggiornata

La Bottom Sheet resta scrollabile e mostra sezioni solo quando hanno dati:

- Situazione operativa;
- Informazioni ufficiali, con testo lungo espandibile;
- Validita e orari;
- NOTAM;
- ENR;
- SUP;
- Autorizzazioni;
- Autorita / fonte;
- Dati enriched;
- blocker/warning globali e per zona.

La zona responsabile del verdetto viene evidenziata solo quando V3 espone una
relazione esplicita (`isVerdictSource`, `responsibleZone`, o campi equivalenti
nel verdetto).

## Backend ancora da completare

Da fare nel repository backend/webapp:

- confrontare realmente `showZoneInfo()` con `zoneCheckV3`;
- portare parser/helper NOTAM, ENR, SUP lato backend V3;
- preservare sempre testo ufficiale piu spiegazione DSC e significato
  operativo;
- usare gli enriched gia caricati senza duplicare dataset;
- aggiungere test backend su testo ufficiale, schedule, `activeNow`, severity,
  operational meaning ed enriched match;
- misurare RAM V3 dopo caricamento dataset e durante richieste significative;
- provare almeno 5 coordinate reali confrontando webapp, V3 e Android.
