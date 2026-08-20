package it.droneskycheck.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.droneskycheck.app.data.AppExternalLinks
import it.droneskycheck.app.data.AppLegalContent
import it.droneskycheck.app.data.AppReleaseNotes
import it.droneskycheck.app.data.AppThemeMode
import it.droneskycheck.app.data.ExternalLink
import it.droneskycheck.app.data.ExternalLinkIcon
import it.droneskycheck.app.data.PeriodicNoticeLinks
import it.droneskycheck.app.data.ReleaseNotes
import it.droneskycheck.app.data.help.HelpManifest
import it.droneskycheck.app.ui.help.HelpBottomSheet

private enum class SettingsPage {
    Home,
    BeginnerGuide,
    Display,
    DroneWorld,
    Legal,
    WhatsNew,
    LegalInfo,
    Terms,
    Disclaimer,
    OperationalRestrictions,
    Privacy,
    Licenses,
    CommunityLinks,
    Contributions
}

private val SettingsPageSaver = Saver<SettingsPage, String>(
    save = { page -> page.name },
    restore = { value -> runCatching { SettingsPage.valueOf(value) }.getOrDefault(SettingsPage.Home) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    helpManifest: HelpManifest,
    isHelpRefreshing: Boolean,
    helpRefreshMessage: String?,
    largeTextEnabled: Boolean,
    onLargeTextEnabledChanged: (Boolean) -> Unit,
    mapDarkeningEnabled: Boolean,
    onMapDarkeningEnabledChanged: (Boolean) -> Unit,
    enhancedZoneOutlinesEnabled: Boolean,
    onEnhancedZoneOutlinesEnabledChanged: (Boolean) -> Unit,
    appThemeMode: AppThemeMode,
    onAppThemeModeChanged: (AppThemeMode) -> Unit,
    beginnerStartupEnabled: Boolean,
    onBeginnerStartupEnabledChanged: (Boolean) -> Unit,
    onOpenBeginnerGuide: () -> Unit,
    onRefreshHelp: () -> Unit,
    onRepeatTour: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by rememberSaveable(stateSaver = SettingsPageSaver) { mutableStateOf(SettingsPage.Home) }
    var isHelpSheetVisible by rememberSaveable { mutableStateOf(false) }

    fun navigateBack() {
        page = when {
            page == SettingsPage.Home -> {
                onDismiss()
                SettingsPage.Home
            }
            page.isLegalDocument() -> SettingsPage.LegalInfo
            page == SettingsPage.WhatsNew ||
                page == SettingsPage.LegalInfo ||
                page == SettingsPage.CommunityLinks ||
                page == SettingsPage.Contributions -> SettingsPage.Legal
            else -> SettingsPage.Home
        }
    }

    BackHandler(onBack = ::navigateBack)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsTopBar(
                title = page.titleText(),
                isRoot = page == SettingsPage.Home,
                onClose = onDismiss,
                onBack = ::navigateBack
            )
            when (page) {
                SettingsPage.Home -> SettingsHome(
                    onOpenBeginnerGuide = { page = SettingsPage.BeginnerGuide },
                    onOpenDisplay = { page = SettingsPage.Display },
                    onOpenDroneWorld = { page = SettingsPage.DroneWorld },
                    onSupport = { onOpenUrl(PeriodicNoticeLinks.BuyMeACoffeeUrl) },
                    onOpenLegal = { page = SettingsPage.Legal }
                )

                SettingsPage.BeginnerGuide -> BeginnerGuideSettingsPage(
                    startupEnabled = beginnerStartupEnabled,
                    onStartupEnabledChanged = onBeginnerStartupEnabledChanged,
                    onOpenGuide = onOpenBeginnerGuide
                )

                SettingsPage.Display -> DisplaySettingsPage(
                    largeTextEnabled = largeTextEnabled,
                    onLargeTextEnabledChanged = onLargeTextEnabledChanged,
                    mapDarkeningEnabled = mapDarkeningEnabled,
                    onMapDarkeningEnabledChanged = onMapDarkeningEnabledChanged,
                    enhancedZoneOutlinesEnabled = enhancedZoneOutlinesEnabled,
                    onEnhancedZoneOutlinesEnabledChanged = onEnhancedZoneOutlinesEnabledChanged,
                    appThemeMode = appThemeMode,
                    onAppThemeModeChanged = onAppThemeModeChanged
                )

                SettingsPage.DroneWorld -> DroneWorldPage(onOpenUrl = onOpenUrl)

                SettingsPage.Legal -> LegalHomePage(
                    onOpenManual = { isHelpSheetVisible = true },
                    onRepeatTour = onRepeatTour,
                    onOpenWhatsNew = { page = SettingsPage.WhatsNew },
                    onOpenLegalInfo = { page = SettingsPage.LegalInfo },
                    onOpenCommunityLinks = { page = SettingsPage.CommunityLinks },
                    onOpenContributions = { page = SettingsPage.Contributions }
                )

                SettingsPage.WhatsNew -> WhatsNewPage(notes = AppReleaseNotes.Current)

                SettingsPage.LegalInfo -> LegalInfoPage(
                    onOpenTerms = { page = SettingsPage.Terms },
                    onOpenDisclaimer = { page = SettingsPage.Disclaimer },
                    onOpenRestrictions = { page = SettingsPage.OperationalRestrictions },
                    onOpenPrivacy = { page = SettingsPage.Privacy },
                    onOpenLicenses = { page = SettingsPage.Licenses }
                )

                SettingsPage.Terms -> TextInfoPage(body = AppLegalContent.TermsOfUseText)
                SettingsPage.Disclaimer -> TextInfoPage(body = AppLegalContent.DisclaimerText)
                SettingsPage.OperationalRestrictions -> TextInfoPage(
                    body = AppLegalContent.OperationalRestrictionsText
                )
                SettingsPage.Privacy -> TextInfoPage(body = AppLegalContent.PrivacyText)
                SettingsPage.Licenses -> LicensesPage()
                SettingsPage.CommunityLinks -> CommunityLinksPage(onOpenUrl = onOpenUrl)
                SettingsPage.Contributions -> ContributionsPage()
            }
        }
    }

    if (isHelpSheetVisible) {
        HelpBottomSheet(
            manifest = helpManifest,
            isRefreshInProgress = isHelpRefreshing,
            refreshMessage = helpRefreshMessage,
            onRefresh = onRefreshHelp,
            onDismiss = { isHelpSheetVisible = false }
        )
    }
}

@Composable
private fun SettingsTopBar(
    title: String,
    isRoot: Boolean,
    onClose: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = if (isRoot) onClose else onBack) {
            Icon(
                imageVector = if (isRoot) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (isRoot) "Chiudi impostazioni" else "Torna indietro"
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsHome(
    onOpenBeginnerGuide: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenDroneWorld: () -> Unit,
    onSupport: () -> Unit,
    onOpenLegal: () -> Unit
) {
    SettingsSection {
        SettingsRow(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = "Prima di volare",
            subtitle = "Guida essenziale per iniziare",
            onClick = onOpenBeginnerGuide
        )
        HorizontalDivider()
        SettingsRow(
            icon = Icons.Default.PhoneAndroid,
            title = "Schermo",
            subtitle = "Testo, tema e visualizzazione",
            onClick = onOpenDisplay
        )
        HorizontalDivider()
        SettingsRow(
            icon = Icons.Default.Public,
            title = "Mondo droni",
            subtitle = "News, video e approfondimenti",
            onClick = onOpenDroneWorld
        )
    }
    SettingsSection {
        SettingsRow(
            icon = Icons.Default.Info,
            title = "Legale e informazioni",
            subtitle = "Manuale, informazioni e community",
            onClick = onOpenLegal
        )
    }
    SupportDroneSkyCheckCard(onSupport = onSupport)
}

@Composable
private fun SupportDroneSkyCheckCard(onSupport: () -> Unit) {
    SettingsSection {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                SettingsIcon(Icons.Default.Favorite)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Sostieni Drone Sky Check",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Drone Sky Check è sviluppato e mantenuto indipendentemente. Se ti è utile, puoi contribuire con un caffè.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = onSupport) {
                Text("Offrimi un caffè")
            }
        }
    }
}

@Composable
private fun BeginnerGuideSettingsPage(
    startupEnabled: Boolean,
    onStartupEnabledChanged: (Boolean) -> Unit,
    onOpenGuide: () -> Unit
) {
    SettingsSection {
        SettingsRow(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = "Percorso essenziale",
            subtitle = "Apri la guida per principianti",
            onClick = onOpenGuide
        )
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(Icons.Default.PlayArrow)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Mostra \"Prima di volare\" all'avvio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Ripropone la presentazione iniziale senza limitare l'accesso manuale.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = startupEnabled,
                onCheckedChange = onStartupEnabledChanged
            )
        }
    }
}

@Composable
private fun DisplaySettingsPage(
    largeTextEnabled: Boolean,
    onLargeTextEnabledChanged: (Boolean) -> Unit,
    mapDarkeningEnabled: Boolean,
    onMapDarkeningEnabledChanged: (Boolean) -> Unit,
    enhancedZoneOutlinesEnabled: Boolean,
    onEnhancedZoneOutlinesEnabledChanged: (Boolean) -> Unit,
    appThemeMode: AppThemeMode,
    onAppThemeModeChanged: (AppThemeMode) -> Unit
) {
    SettingsSection(title = "Dimensione testo") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(Icons.Default.FormatSize)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Testo piu grande",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Aumenta la leggibilita dei testi nell'app Drone Sky Check.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = largeTextEnabled,
                onCheckedChange = onLargeTextEnabledChanged
            )
        }
    }

    SettingsSection(title = "Mappa") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(Icons.Default.Map)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Mappa piu scura",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Riduce la luminosita della base cartografica per far risaltare traffico e quote.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = mapDarkeningEnabled,
                onCheckedChange = onMapDarkeningEnabledChanged
            )
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(Icons.Default.Visibility)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Contorni zone piu visibili",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Aumenta il contrasto dei confini delle zone sulla mappa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enhancedZoneOutlinesEnabled,
                onCheckedChange = onEnhancedZoneOutlinesEnabledChanged
            )
        }
    }

    SettingsSection(title = "Tema") {
        AppThemeMode.entries.forEachIndexed { index, mode ->
            ThemeModeRow(
                mode = mode,
                selected = mode == appThemeMode,
                onClick = { onAppThemeModeChanged(mode) }
            )
            if (index < AppThemeMode.entries.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun ThemeModeRow(
    mode: AppThemeMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(mode.themeIcon())
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(mode.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    mode.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = selected,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun LegalHomePage(
    onOpenManual: () -> Unit,
    onRepeatTour: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onOpenLegalInfo: () -> Unit,
    onOpenCommunityLinks: () -> Unit,
    onOpenContributions: () -> Unit
) {
    SettingsSection {
        SettingsRow(Icons.Default.Description, "Manuale Drone Sky Check", "Guida e spiegazioni operative", onOpenManual)
        HorizontalDivider()
        SettingsRow(Icons.Default.PlayArrow, "Tour guidato", "Rilancia il tour iniziale", onRepeatTour)
        HorizontalDivider()
        SettingsRow(Icons.Default.NewReleases, "Cosa c'e di nuovo", "Novita della versione ${AppReleaseNotes.Current.versionName}", onOpenWhatsNew)
    }
    SettingsSection {
        SettingsRow(Icons.Default.Info, "Informazioni legali", "Condizioni, disclaimer, privacy e licenze", onOpenLegalInfo)
        HorizontalDivider()
        SettingsRow(Icons.Default.Link, "Link e Community", "Sito, mappa web e canali ufficiali", onOpenCommunityLinks)
        HorizontalDivider()
        SettingsRow(Icons.Default.Groups, "Contribuzioni", "Community, tester e segnalazioni", onOpenContributions)
    }
}

@Composable
private fun DroneWorldPage(onOpenUrl: (String) -> Unit) {
    SettingsSection {
        AppExternalLinks.DroneWorldLinks.forEachIndexed { index, link ->
            ExternalLinkRow(
                link = link,
                onClick = { onOpenUrl(link.url) }
            )
            if (index < AppExternalLinks.DroneWorldLinks.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun WhatsNewPage(notes: ReleaseNotes) {
    SettingsSection {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Versione ${notes.versionName} (${notes.versionCode})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = notes.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = notes.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            notes.highlights.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalInfoPage(
    onOpenTerms: () -> Unit,
    onOpenDisclaimer: () -> Unit,
    onOpenRestrictions: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit
) {
    SettingsSection {
        SettingsRow(Icons.Default.Description, "Condizioni d'uso", "Uso dell'app e responsabilita operative", onOpenTerms)
        HorizontalDivider()
        SettingsRow(Icons.Default.Info, "Disclaimer", "Fonti ufficiali e responsabilita del pilota", onOpenDisclaimer)
        HorizontalDivider()
        SettingsRow(Icons.Default.Visibility, "Restrizioni operative", "Limiti di utilizzo dell'app", onOpenRestrictions)
        HorizontalDivider()
        SettingsRow(Icons.Default.PrivacyTip, "Privacy", "Dati locali, posizione e servizi Internet", onOpenPrivacy)
        HorizontalDivider()
        SettingsRow(Icons.AutoMirrored.Filled.ListAlt, "Licenze di terze parti", "Dipendenze open source principali", onOpenLicenses)
    }
}

@Composable
private fun TextInfoPage(body: String) {
    SettingsSection {
        SelectionContainer {
            Text(
                text = body,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LicensesPage() {
    SettingsSection {
        Text(
            text = "Elenco basato sulle dipendenze dirette dichiarate nel progetto Gradle Android.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider()
        AppLegalContent.DirectOpenSourceDependencies.forEachIndexed { index, dependency ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(dependency.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(dependency.owner, style = MaterialTheme.typography.bodyMedium)
                Text(
                    dependency.license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (index < AppLegalContent.DirectOpenSourceDependencies.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun CommunityLinksPage(onOpenUrl: (String) -> Unit) {
    SettingsSection {
        AppExternalLinks.CommunityLinks.forEachIndexed { index, link ->
            ExternalLinkRow(
                link = link,
                onClick = { onOpenUrl(link.url) }
            )
            if (index < AppExternalLinks.CommunityLinks.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun ExternalLinkRow(
    link: ExternalLink,
    onClick: () -> Unit
) {
    SettingsRow(
        icon = link.icon.toImageVector(),
        title = link.title,
        subtitle = link.subtitle,
        onClick = onClick
    )
}

@Composable
private fun ContributionsPage() {
    SettingsSection {
        Text(
            text = AppLegalContent.ContributionsIntro,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Drone Pilots Team",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Supporto e confronto con la community",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppLegalContent.ContributorGroups.forEach { group ->
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                group.names.forEach { name ->
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        HorizontalDivider()
        Text(
            text = AppLegalContent.ContributionsFooter,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsSection(
    title: String? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Apri $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(10.dp)
                .size(24.dp)
        )
    }
}

private fun SettingsPage.titleText(): String =
    when (this) {
        SettingsPage.Home -> "Impostazioni"
        SettingsPage.BeginnerGuide -> "Prima di volare"
        SettingsPage.Display -> "Schermo"
        SettingsPage.DroneWorld -> "Mondo droni"
        SettingsPage.Legal -> "Legale e informazioni"
        SettingsPage.WhatsNew -> "Cosa c'e di nuovo"
        SettingsPage.LegalInfo -> "Informazioni legali"
        SettingsPage.Terms -> "Condizioni d'uso"
        SettingsPage.Disclaimer -> "Disclaimer"
        SettingsPage.OperationalRestrictions -> "Restrizioni operative"
        SettingsPage.Privacy -> "Privacy"
        SettingsPage.Licenses -> "Licenze di terze parti"
        SettingsPage.CommunityLinks -> "Link e Community"
        SettingsPage.Contributions -> "Contribuzioni"
    }

private fun SettingsPage.isLegalDocument(): Boolean =
    this in setOf(
        SettingsPage.Terms,
        SettingsPage.Disclaimer,
        SettingsPage.OperationalRestrictions,
        SettingsPage.Privacy,
        SettingsPage.Licenses
    )

private fun AppThemeMode.themeIcon(): ImageVector =
    when (this) {
        AppThemeMode.System -> Icons.Default.SettingsBrightness
        AppThemeMode.Light -> Icons.Default.LightMode
        AppThemeMode.Dark -> Icons.Default.DarkMode
    }

private fun ExternalLinkIcon.toImageVector(): ImageVector =
    when (this) {
        ExternalLinkIcon.Website -> Icons.Default.Public
        ExternalLinkIcon.Map -> Icons.Default.Map
        ExternalLinkIcon.Video -> Icons.Default.VideoLibrary
        ExternalLinkIcon.Social -> Icons.Default.Link
        ExternalLinkIcon.Community -> Icons.Default.Groups
        ExternalLinkIcon.Support -> Icons.Default.Favorite
    }
