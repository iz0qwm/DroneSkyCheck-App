package it.droneskycheck.app.data

object AppExternalLinks {
    const val OfficialWebsiteUrl = "https://www.droneskycheck.it/"
    const val WebMapUrl = "https://mappa.droneskycheck.it/"
    const val YouTubeChannelUrl = "https://www.youtube.com/@RaffaelloKWOS"
}

object AppLegalContent {
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

    val ContributionsIntro =
        "Drone Sky Check cresce anche grazie ai test, alle segnalazioni e ai contributi della community."

    val ContributorGroups = listOf(
        ContributorGroup(
            title = "Drone Pilots Team",
            names = listOf(
                "Andrea Fanelli",
                "Andrea Pinotti",
                "Danilo Scarato",
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

data class OpenSourceDependency(
    val name: String,
    val owner: String,
    val license: String
)
