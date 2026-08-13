# Guida Drone Sky Check - Manifest JSON, Tour Guidato e Immagini

## 1. File JSON principale

Il sistema Help utilizza un manifest JSON principale:

```text
app/src/main/res/raw/help_manifest.json
```

Lo stesso formato puo essere pubblicato anche online su KWOS:

```text
https://www.kwos.org/appoggio/droni/DroneSkyCheck/help/manifest.json
```

L'app usa il manifest incorporato nell'APK come base, poi controlla se esiste una versione remota piu recente.

## 2. Versioni del manifest

Nel JSON ci sono due versioni importanti:

```json
{
  "schemaVersion": 1,
  "contentVersion": 6,
  "onboardingVersion": 5
}
```

Usare cosi:

- `schemaVersion`: non cambiarlo salvo modifiche strutturali importanti.
- `contentVersion`: aumentarlo quando si modificano testi, sezioni guida, immagini o contenuti del manuale.
- `onboardingVersion`: aumentarlo solo quando cambia il Tour guidato.

Se aggiungi solo immagini o argomenti della guida, aumenta solo `contentVersion`.

## 3. Struttura generale del manifest

Il manifest contiene due sezioni principali:

```json
{
  "schemaVersion": 1,
  "contentVersion": 6,
  "updatedAt": "2026-08-13",
  "onboardingVersion": 5,
  "onboarding": {
    "steps": []
  },
  "topics": []
}
```

- `onboarding.steps`: contiene i passaggi del Tour guidato.
- `topics`: contiene le sezioni della Guida Drone Sky Check.

## 4. Tour guidato

Esempio di step del tour:

```json
{
  "id": "weather_flow",
  "target": "flight_opportunity_card",
  "action": "open_weather",
  "title": "Meteo e finestre di volo",
  "text": "Dal pannello del punto puoi aprire il report operativo per vedere meteo, finestre favorevoli e compatibilita con il drone.",
  "order": 3
}
```

Campi:

- `id`: identificativo univoco.
- `target`: elemento UI a cui si riferisce il passaggio.
- `action`: azione automatica del tour, opzionale.
- `title`: titolo mostrato nella finestra help.
- `text`: testo descrittivo.
- `order`: ordine numerico del passaggio.

Target supportati:

```text
map
zones_button
location_button
traffic_button
profile_button
selected_point_panel
weather_action
flight_opportunity_card
```

Azioni supportate:

```text
none
open_zones
open_profile
open_traffic
open_selected_point_details
open_weather
```

Non modificare target/action se non serve davvero cambiare il comportamento del tour.

## 5. Sezioni della guida

Ogni sezione della Guida Drone Sky Check sta dentro `topics`.

Esempio:

```json
{
  "id": "weather",
  "title": "Meteo per il volo",
  "summary": "Condizioni previste, vento, raffiche e finestre favorevoli.",
  "introduction": "Drone Sky Check analizza le condizioni meteorologiche previste nel punto selezionato.",
  "image": "weather_overview.webp",
  "imageAlt": "Schermata Meteo per il volo",
  "order": 3,
  "blocks": [
    {
      "type": "paragraph",
      "text": "La valutazione considera vento, raffiche, precipitazioni e compatibilita con il drone selezionato."
    },
    {
      "type": "image",
      "src": "weather_details.webp",
      "alt": "Dettaglio del report operativo meteo"
    },
    {
      "type": "note",
      "text": "La previsione e un supporto alla pianificazione e va confrontata con le condizioni osservate sul posto."
    }
  ]
}
```

Campi principali:

- `id`: identificativo univoco della sezione.
- `title`: titolo della scheda guida.
- `summary`: breve descrizione mostrata nell'elenco.
- `introduction`: testo iniziale opzionale.
- `image`: immagine principale opzionale.
- `imageAlt`: descrizione accessibile dell'immagine principale, opzionale.
- `order`: ordine della sezione.
- `blocks`: contenuto della pagina.

## 6. Tipi di blocchi supportati

### Paragrafo

```json
{
  "type": "paragraph",
  "text": "Tocca un punto della mappa per iniziare il controllo."
}
```

### Elenco puntato

Sono supportati sia `bullet_list` sia `bulletList`.

```json
{
  "type": "bullet_list",
  "items": [
    "Controlla la zona",
    "Verifica eventuali limitazioni",
    "Consulta meteo e traffico"
  ]
}
```

### Nota

```json
{
  "type": "note",
  "text": "Le informazioni sono un supporto operativo e non sostituiscono le fonti ufficiali."
}
```

### Immagine nel contenuto

```json
{
  "type": "image",
  "src": "controllo_zona.webp",
  "alt": "Pannello Controllo zona"
}
```

`alt` e opzionale, ma consigliato.

## 7. Immagini remote

Le immagini della guida sono pensate principalmente per essere pubblicate su KWOS:

```text
https://www.kwos.org/appoggio/droni/DroneSkyCheck/help/images/
```

Nel manifest non serve scrivere l'URL completo. Basta scrivere:

```json
"image": "weather_overview.webp"
```

L'app lo risolve automaticamente come:

```text
https://www.kwos.org/appoggio/droni/DroneSkyCheck/help/images/weather_overview.webp
```

La base URL e definita nel codice in:

```text
DscApiConfig.HelpImagesBaseUrl
```

## 8. URL assoluti

Se serve, si puo usare anche un URL assoluto:

```json
"image": "https://example.com/manual/weather.webp"
```

In questo caso l'app usa direttamente quell'URL.

Sono accettati solo URL `https://`.

## 9. Formati immagine supportati

Formati previsti:

```text
.webp
.png
.jpg
.jpeg
```

Formato consigliato:

```text
.webp
```

Gli screenshot dovrebbero essere ridotti prima del caricamento, per evitare immagini troppo pesanti.

## 10. Sicurezza immagini

Il manifest remoto puo contenere immagini, quindi l'app filtra i percorsi.

Sono accettati:

```text
weather.webp
manual/weather.webp
https://example.com/weather.webp
```

Sono rifiutati:

```text
file://...
content://...
javascript:...
../../secret.png
weather.svg
```

Se un'immagine non e valida, non esiste, e offline o non si carica, la guida continua normalmente senza mostrare errori invasivi.

## 11. Cache immagini

Le immagini sono caricate solo quando si apre il topic della guida.

La cache e gestita dall'image loader dell'app, Coil:

- cache in memoria;
- cache disco;
- download asincrono;
- nessun blocco dello startup;
- nessun database dedicato alle immagini.

Il manifest JSON mantiene la propria cache separata. Le immagini non vengono salvate dentro la cache del manifest.

## 12. Fallback locale opzionale

Per immagini relative, l'app prova anche un fallback locale con lo stesso nome base.

Esempio:

```json
"image": "weather_overview.webp"
```

Se il download remoto fallisce, l'app puo cercare un drawable locale chiamato:

```text
res/drawable/weather_overview.webp
```

Questo fallback e opzionale. Se il drawable non esiste, semplicemente non viene mostrata nessuna immagine.

## 13. Come aggiungere una nuova sezione guida

1. Aggiungere un nuovo oggetto dentro `topics`.
2. Dare un `id` unico.
3. Impostare `title`, `summary`, `order`.
4. Aggiungere `introduction`, `image`, `imageAlt` se utili.
5. Aggiungere i blocchi in `blocks`.
6. Aumentare `contentVersion`.
7. Pubblicare il nuovo manifest remoto su KWOS.

Esempio minimo:

```json
{
  "id": "new_section",
  "title": "Nuova sezione",
  "summary": "Breve descrizione della nuova sezione.",
  "introduction": "Testo introduttivo della nuova sezione.",
  "image": "new_section.webp",
  "imageAlt": "Schermata della nuova sezione",
  "order": 8,
  "blocks": [
    {
      "type": "paragraph",
      "text": "Testo della guida."
    }
  ]
}
```

## 14. Come aggiungere immagini

1. Preparare screenshot piccoli, preferibilmente `.webp`.
2. Caricarli su:

```text
https://www.kwos.org/appoggio/droni/DroneSkyCheck/help/images/
```

3. Nel manifest usare solo il nome file:

```json
"image": "controllo_zona.webp"
```

oppure come blocco:

```json
{
  "type": "image",
  "src": "controllo_zona.webp",
  "alt": "Pannello Controllo zona"
}
```

4. Aumentare `contentVersion`.

## 15. Quando modificare il manifest embedded

Il file embedded:

```text
app/src/main/res/raw/help_manifest.json
```

serve come fallback offline e come base iniziale dell'app.

Va aggiornato quando:

- si vuole includere una nuova versione minima della guida nell'APK;
- si cambia il tour guidato;
- si vuole avere contenuto disponibile anche senza manifest remoto.

Per aggiornamenti frequenti del manuale, preferire il manifest remoto su KWOS.

## 16. Regola pratica

- Cambio solo testi guida: aumenta `contentVersion`.
- Aggiungo immagini guida: aumenta `contentVersion`.
- Aggiungo nuove sezioni guida: aumenta `contentVersion`.
- Cambio ordine o comportamento del tour guidato: aumenta `onboardingVersion`.
- Cambio struttura JSON in modo incompatibile: valutare aumento `schemaVersion`.