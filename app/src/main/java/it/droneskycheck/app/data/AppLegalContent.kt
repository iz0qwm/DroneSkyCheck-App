package it.droneskycheck.app.data

object AppExternalLinks {
    const val OfficialWebsiteUrl = "https://www.droneskycheck.it/"
    const val WebMapUrl = "https://mappa.droneskycheck.it/"
    const val YouTubeChannelUrl = "https://www.youtube.com/@RaffaelloKWOS"
    const val TikTokChannelUrl = "https://www.tiktok.com/@raffaellokwos"
    const val DronePilotsTeamUrl = "https://www.dronepilotsteam.it/"
    const val TelegramCommunityUrl = "https://t.me/tuttosuidroni"
    const val TelegramUpdatesUrl = "https://t.me/DronePilotApp"
    const val SupportUrl = "https://buymeacoffee.com/tuttosuidroni"

    val CommunityLinks = listOf(
        ExternalLink(
            title = "Sito Web",
            subtitle = "www.droneskycheck.it",
            url = OfficialWebsiteUrl,
            icon = ExternalLinkIcon.Website
        ),
        ExternalLink(
            title = "Mappa Web",
            subtitle = "mappa.droneskycheck.it",
            url = WebMapUrl,
            icon = ExternalLinkIcon.Map
        ),
        ExternalLink(
            title = "Canale YouTube",
            subtitle = "Raffaello KWOS",
            url = YouTubeChannelUrl,
            icon = ExternalLinkIcon.Video
        ),
        ExternalLink(
            title = "Canale TikTok",
            subtitle = "@raffaellokwos",
            url = TikTokChannelUrl,
            icon = ExternalLinkIcon.Social
        ),
        ExternalLink(
            title = "Drone Pilots Team",
            subtitle = "www.dronepilotsteam.it",
            url = DronePilotsTeamUrl,
            icon = ExternalLinkIcon.Community
        ),
        ExternalLink(
            title = "Telegram Tutto sui Droni",
            subtitle = "Community generale e confronto sulle app",
            url = TelegramCommunityUrl,
            icon = ExternalLinkIcon.Community
        ),
        ExternalLink(
            title = "Telegram Drone Pilot App",
            subtitle = "Info di sistema e aggiornamenti app",
            url = TelegramUpdatesUrl,
            icon = ExternalLinkIcon.Social
        ),
        ExternalLink(
            title = "Sostieni Drone Sky Check",
            subtitle = "buymeacoffee.com/tuttosuidroni",
            url = SupportUrl,
            icon = ExternalLinkIcon.Support
        )
    )

    const val DroneZineUrl = "https://www.dronezine.it/"
    const val QuadricotteroNewsUrl = "https://www.youtube.com/@QuadricotteroN"
    const val DroneZineYouTubeUrl = "https://www.youtube.com/@DroneZine"

    val DroneWorldLinks = listOf(
        ExternalLink(
            title = "DroneZine",
            subtitle = "Notizie e approfondimenti",
            url = DroneZineUrl,
            icon = ExternalLinkIcon.Website
        ),
        ExternalLink(
            title = "Quadricottero News",
            subtitle = "Video, prove e novita",
            url = QuadricotteroNewsUrl,
            icon = ExternalLinkIcon.Video
        ),
        ExternalLink(
            title = "DroneZine YouTube",
            subtitle = "Video e approfondimenti",
            url = DroneZineYouTubeUrl,
            icon = ExternalLinkIcon.Video
        )
    )
}

object AppReleaseNotes {
    // When versionName changes, reset this section for the new version and collect only that version's changes.
    val Current = ReleaseNotes(
        versionName = "4.1",
        versionCode = 31,
        title = "Cosa c'è di nuovo in Drone Sky Check 4.1",
        intro = "Questa versione migliora DSC METEO integrando le informazioni di Vigilanza della Protezione Civile pubblicate per aree regionali.",
        highlights = listOf(
            "La Vigilanza meteorologica nazionale viene letta e associata alle regioni indicate nel bollettino.",
            "La fascia DSC METEO compare soltanto quando l'area visualizzata ricade in una regione interessata, senza trasformare la Vigilanza in un'allerta gialla, arancione o rossa.",
            "DSC Insights misura in forma anonima e aggregata l'apertura delle funzioni principali; le metriche si possono disattivare dalla pagina Privacy."
        )
    )
}

object AppLegalContent {
    val TermsOfUseText = """
        Drone Sky Check e uno strumento informativo destinato a facilitare la consultazione di dati utili alla pianificazione e alla valutazione delle operazioni con UAS.

        Utilizzando l'applicazione, l'utente accetta che le informazioni visualizzate possano provenire da fonti ufficiali, servizi esterni e sistemi di elaborazione di Drone Sky Check e che, per loro natura, possano essere soggette a ritardi, errori, indisponibilita o variazioni.

        Drone Sky Check non costituisce una fonte aeronautica ufficiale, non rilascia autorizzazioni al volo e non sostituisce i servizi e le pubblicazioni ufficiali messi a disposizione dalle autorita competenti.

        Prima di ogni operazione il pilota è tenuto a verificare le informazioni rilevanti sulle fonti ufficiali e ad assicurarsi che il volo possa essere svolto nel rispetto della normativa applicabile.

        Le informazioni relative a meteo, spazio aereo, NOTAM, traffico e altre condizioni operative sono fornite come supporto alla valutazione del pilota e non costituiscono garanzia della possibilita o della sicurezza del volo.

        Il servizio viene fornito nello stato in cui si trova e puo essere modificato, aggiornato o temporaneamente sospeso.

        Nei limiti consentiti dalla legge, lo sviluppatore non puo essere ritenuto responsabile per decisioni operative assunte esclusivamente sulla base delle informazioni mostrate dall'applicazione.

        L'utilizzo di Drone Sky Check implica l'accettazione delle presenti condizioni.
    """.trimIndent()

    val DisclaimerText = """
        Drone Sky Check raccoglie, interpreta e presenta in modo piu semplice dati provenienti da fonti ufficiali e da altri servizi utili alla pianificazione del volo.

        L'app facilita la consultazione, ma non sostituisce le fonti aeronautiche ufficiali ne gli strumenti messi a disposizione dagli enti competenti.

        Prima del volo verifica sempre le informazioni applicabili attraverso le fonti ufficiali indicate.

        Le informazioni mostrate non costituiscono autorizzazione al volo.
    """.trimIndent()

    val OperationalRestrictionsText = """
        Le informazioni operative mostrate nell'app devono essere verificate sulle fonti ufficiali prima di ogni volo.

        Condizioni locali, NOTAM, restrizioni temporanee, aggiornamenti cartografici e altre informazioni possono cambiare nel tempo.

        Il pilota resta responsabile della verifica delle condizioni operative, delle autorizzazioni necessarie e della conformita dell'operazione pianificata.
    """.trimIndent()

    val PrivacyText = """
        Drone Sky Check è progettato per ridurre al minimo la raccolta di dati personali.

        Profilo e dati del pilota

        I dati inseriti nell'applicazione, come informazioni del pilota, attestati, dati dell'operatore UAS, droni, foto profilo, preferenze e richieste salvate localmente, sono memorizzati localmente dall'app e non vengono sincronizzati con un account Drone Sky Check.

        Posizione geografica

        Se l'utente concede l'autorizzazione alla posizione, Drone Sky Check puo utilizzare la posizione del dispositivo per mostrare la posizione sulla mappa e fornire servizi basati sulla localizzazione.

        Le coordinate del punto selezionato o della posizione utilizzata dall'utente possono essere trasmesse ai servizi di Drone Sky Check per elaborare informazioni quali zone UAS, condizioni meteorologiche, informazioni operative e traffico nelle vicinanze.

        Queste informazioni non vengono utilizzate per creare un profilo personale dell'utente ne per finalita pubblicitarie.

        Servizi Internet

        Per alcune funzionalita l'app comunica con server Drone Sky Check e servizi esterni necessari alla fornitura delle informazioni richieste, inclusi servizi di mappe, dati operativi, meteo, traffico e contenuti del manuale.

        Durante una normale comunicazione Internet possono essere trattati dati tecnici, come indirizzo IP, data e ora della richiesta e informazioni necessarie al funzionamento e alla sicurezza del servizio.

        Collegamenti esterni

        Drone Sky Check puo contenere collegamenti verso siti e servizi di terze parti.

        Aprendo tali collegamenti si applicano le rispettive informative privacy.

        Dati memorizzati sul dispositivo

        I dati locali restano gestiti nello spazio dell'app fino alla loro cancellazione da parte dell'utente, alla cancellazione dei dati dell'applicazione o alla disinstallazione dell'app. In base alle impostazioni del dispositivo, Android puo includere i dati dell'app in backup o trasferimenti di sistema.

        Metriche di utilizzo

        DSC Insights puo inviare ai server Drone Sky Check metriche first-party relative all'apertura delle principali funzioni dell'app e alla piattaforma Android. Le metriche non includono coordinate, missioni, pratiche, droni, testi inseriti, prompt AI, email, nomi o identificativi personali.

        Le metriche di utilizzo possono essere disattivate in qualsiasi momento tramite l'interruttore presente in questa pagina. La preferenza resta memorizzata localmente sul dispositivo e la disattivazione interrompe l'invio di nuovi eventi.

        L'app non include Firebase Analytics, Crashlytics o SDK pubblicitari e non dispone di un sistema di account Drone Sky Check. Le metriche DSC Insights non vengono usate per pubblicita o profilazione individuale.

        Drone Sky Check non vende i dati personali degli utenti e non li utilizza per profilazione pubblicitaria.
    """.trimIndent()

    val ContributionsIntro = """
        Drone Sky Check e un progetto indipendente che cresce anche grazie al confronto con piloti, tester e appassionati che contribuiscono con idee, prove sul campo e segnalazioni.

        Un ringraziamento particolare va al Drone Pilots Team, realta di cui faccio parte attivamente, per il supporto, il confronto continuo e il contributo alla crescita del progetto.
    """.trimIndent()

    val ContributionsFooter =
        "Il loro contributo ai test e alle segnalazioni aiuta concretamente a rendere Drone Sky Check piu affidabile e utile per tutti i piloti.\n\nGrazie inoltre a tutti i beta tester e agli utenti che inviano feedback e segnalazioni."

    val ContributorGroups = listOf(
        ContributorGroup(
            title = "Contributor",
            names = listOf(
                "Andrea Fanelli",
                "Andrea Pinotti",
                "Danilo Scarato",
                "Emmanuel Fresco",
                "Francesco Romeo",
                "Silvio Gaetani",
                "Stefano Orsi"
            )
        )
    )

    val DirectOpenSourceDependencies = listOf(
        OpenSourceDependency("AndroidX Activity Compose", "AndroidX", "Apache License 2.0"),
        OpenSourceDependency("AndroidX Compose Material 3", "AndroidX", "Apache License 2.0"),
        OpenSourceDependency("AndroidX Compose UI", "AndroidX", "Apache License 2.0"),
        OpenSourceDependency("AndroidX Core KTX", "AndroidX", "Apache License 2.0"),
        OpenSourceDependency("AndroidX Core SplashScreen", "AndroidX", "Apache License 2.0"),
        OpenSourceDependency("AndroidX Lifecycle", "AndroidX", "Apache License 2.0"),
        OpenSourceDependency("AndroidX Room", "AndroidX", "Apache License 2.0"),
        OpenSourceDependency("Coil Compose", "Coil contributors", "Apache License 2.0"),
        OpenSourceDependency("MapLibre Android SDK", "MapLibre contributors", "BSD-style open source license"),
        OpenSourceDependency("PdfBox-Android", "Tom Roush / Apache PDFBox contributors", "Apache License 2.0")
    )
}

data class ContributorGroup(
    val title: String,
    val names: List<String>
)

data class ExternalLink(
    val title: String,
    val subtitle: String,
    val url: String,
    val icon: ExternalLinkIcon
)

enum class ExternalLinkIcon {
    Website,
    Map,
    Video,
    Social,
    Community,
    Support
}

data class ReleaseNotes(
    val versionName: String,
    val versionCode: Long,
    val title: String,
    val intro: String,
    val highlights: List<String>
)

data class OpenSourceDependency(
    val name: String,
    val owner: String,
    val license: String
)
