package it.droneskycheck.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import it.droneskycheck.app.data.help.HelpManifest
import it.droneskycheck.app.ui.help.HelpBottomSheet

private enum class SettingsPage {
    Home,
    Accessibility,
    Legal,
    Terms,
    Disclaimer,
    OperationalRestrictions,
    Privacy,
    Licenses,
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
            page.isLegalDetail() -> SettingsPage.Legal
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
                    onOpenAccessibility = { page = SettingsPage.Accessibility },
                    onOpenManual = { isHelpSheetVisible = true },
                    onRepeatTour = onRepeatTour,
                    onOpenWebsite = { onOpenUrl(AppExternalLinks.OfficialWebsiteUrl) },
                    onOpenWebMap = { onOpenUrl(AppExternalLinks.WebMapUrl) },
                    onOpenLegal = { page = SettingsPage.Legal },
                    onOpenYoutube = { onOpenUrl(AppExternalLinks.YouTubeChannelUrl) }
                )

                SettingsPage.Accessibility -> AccessibilitySettingsPage(
                    largeTextEnabled = largeTextEnabled,
                    onLargeTextEnabledChanged = onLargeTextEnabledChanged
                )

                SettingsPage.Legal -> LegalHomePage(
                    onOpenTerms = { page = SettingsPage.Terms },
                    onOpenDisclaimer = { page = SettingsPage.Disclaimer },
                    onOpenRestrictions = { page = SettingsPage.OperationalRestrictions },
                    onOpenPrivacy = { page = SettingsPage.Privacy },
                    onOpenLicenses = { page = SettingsPage.Licenses },
                    onOpenContributions = { page = SettingsPage.Contributions }
                )

                SettingsPage.Terms -> TextInfoPage(
                    body = "Il testo delle condizioni d'uso non risulta incluso nel progetto Android. Questa pagina e predisposta per visualizzarlo quando sara disponibile."
                )

                SettingsPage.Disclaimer -> TextInfoPage(body = AppLegalContent.DisclaimerText)

                SettingsPage.OperationalRestrictions -> TextInfoPage(
                    body = AppLegalContent.OperationalRestrictionsText
                )

                SettingsPage.Privacy -> TextInfoPage(
                    body = "Nel progetto Android non risulta configurato un URL o testo ufficiale di Privacy Policy. I dati del profilo FREE restano memorizzati localmente sul dispositivo e questa modifica non introduce upload, sincronizzazione cloud o telemetria."
                )

                SettingsPage.Licenses -> LicensesPage()

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
                contentDescription = if (isRoot) "Chiudi impostazioni" else "Torna alle impostazioni"
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsHome(
    onOpenAccessibility: () -> Unit,
    onOpenManual: () -> Unit,
    onRepeatTour: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenWebMap: () -> Unit,
    onOpenLegal: () -> Unit,
    onOpenYoutube: () -> Unit
) {
    SettingsSection {
        SettingsRow(Icons.Default.Visibility, "Accessibilita", "Testo piu grande e preferenze di leggibilita", onOpenAccessibility)
        SettingsRow(Icons.Default.Description, "Manuale Drone Sky Check", "Guida e spiegazioni operative", onOpenManual)
        SettingsRow(Icons.Default.PlayArrow, "Tour guidato", "Rilancia il tour iniziale", onRepeatTour)
        SettingsRow(Icons.Default.Public, "Sito web", AppExternalLinks.OfficialWebsiteUrl, onOpenWebsite)
        SettingsRow(Icons.Default.Public, "Mappa web", AppExternalLinks.WebMapUrl, onOpenWebMap)
    }
    SettingsSection(title = "Legale e informazioni") {
        SettingsRow(Icons.Default.Info, "Legale e informazioni", "Condizioni, disclaimer, privacy, licenze e contributi", onOpenLegal)
    }
    SettingsSection {
        SettingsRow(Icons.Default.PlayArrow, "Canale YouTube", "@RaffaelloKWOS", onOpenYoutube)
    }
}

@Composable
private fun AccessibilitySettingsPage(
    largeTextEnabled: Boolean,
    onLargeTextEnabledChanged: (Boolean) -> Unit
) {
    SettingsSection {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(Icons.Default.Visibility)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Testo piu grande", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Aumenta la leggibilita dei testi nell'app Drone Sky Check.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = largeTextEnabled, onCheckedChange = onLargeTextEnabledChanged)
        }
    }
}

@Composable
private fun LegalHomePage(
    onOpenTerms: () -> Unit,
    onOpenDisclaimer: () -> Unit,
    onOpenRestrictions: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenContributions: () -> Unit
) {
    SettingsSection {
        SettingsRow(Icons.Default.Description, "Condizioni d'uso", "Pagina predisposta", onOpenTerms)
        SettingsRow(Icons.Default.Info, "Disclaimer", "Fonti ufficiali e responsabilita del pilota", onOpenDisclaimer)
        SettingsRow(Icons.Default.Info, "Restrizioni operative", "Limiti di utilizzo dell'app", onOpenRestrictions)
        SettingsRow(Icons.Default.Description, "Privacy", "Dati FREE locali sul dispositivo", onOpenPrivacy)
        SettingsRow(Icons.Default.Description, "Licenze di terze parti", "Dipendenze open source principali", onOpenLicenses)
        SettingsRow(Icons.Default.Info, "Contribuzioni", "Community e contributor", onOpenContributions)
    }
}

@Composable
private fun TextInfoPage(body: String) {
    SettingsSection {
        Text(
            text = body,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
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
        AppLegalContent.DirectOpenSourceDependencies.forEach { dependency ->
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
            HorizontalDivider()
        }
    }
}

@Composable
private fun ContributionsPage() {
    SettingsSection {
        Text(
            text = AppLegalContent.ContributionsIntro,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        AppLegalContent.ContributorGroups.forEach { group ->
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                group.names.forEach { name ->
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
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
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
        SettingsPage.Accessibility -> "Accessibilita"
        SettingsPage.Legal -> "Legale e informazioni"
        SettingsPage.Terms -> "Condizioni d'uso"
        SettingsPage.Disclaimer -> "Disclaimer"
        SettingsPage.OperationalRestrictions -> "Restrizioni operative"
        SettingsPage.Privacy -> "Privacy"
        SettingsPage.Licenses -> "Licenze di terze parti"
        SettingsPage.Contributions -> "Contribuzioni"
    }

private fun SettingsPage.isLegalDetail(): Boolean =
    this in setOf(
        SettingsPage.Terms,
        SettingsPage.Disclaimer,
        SettingsPage.OperationalRestrictions,
        SettingsPage.Privacy,
        SettingsPage.Licenses,
        SettingsPage.Contributions
    )
