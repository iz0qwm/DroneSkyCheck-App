package it.droneskycheck.app.ui.map

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import it.droneskycheck.app.data.UasDatasetUpdates
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AppBuildInfoPresentation(
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val platform: String
)

data class UasDatasetInfoPresentation(
    val availabilityLabel: String,
    val cacheLabel: String?,
    val datasetVersion: String?,
    val sourceUpdatedAt: String?,
    val cachedOnDeviceAt: String?,
    val metadataFallbackLabel: String?
)

data class AppInfoPresentation(
    val build: AppBuildInfoPresentation,
    val dataset: UasDatasetInfoPresentation,
    val textScale: TextScaleInfoPresentation? = null
)

data class TextScaleInfoPresentation(
    val systemFontScaleLabel: String,
    val largeTextEnabledLabel: String,
    val effectiveFontScaleLabel: String
)

fun buildAppBuildInfoPresentation(
    appName: String,
    versionName: String,
    versionCode: Long,
    androidRelease: String,
    sdkInt: Int
): AppBuildInfoPresentation =
    AppBuildInfoPresentation(
        appName = appName.ifBlank { "Drone Sky Check" },
        versionName = versionName.ifBlank { "sconosciuta" },
        versionCode = versionCode,
        platform = "Android $androidRelease (SDK $sdkInt)"
    )

fun appInfoPresentation(
    context: Context,
    mapStatusMessage: String?,
    updates: UasDatasetUpdates?,
    textScale: TextScaleInfoPresentation? = null
): AppInfoPresentation =
    AppInfoPresentation(
        build = context.readAppBuildInfoPresentation(),
        dataset = uasDatasetInfoPresentation(mapStatusMessage, updates),
        textScale = textScale
    )

fun textScaleInfoPresentation(
    systemFontScale: Float,
    largeTextEnabled: Boolean,
    effectiveFontScale: Float
): TextScaleInfoPresentation =
    TextScaleInfoPresentation(
        systemFontScaleLabel = systemFontScale.formatTextScale(),
        largeTextEnabledLabel = if (largeTextEnabled) "Attivo" else "Non attivo",
        effectiveFontScaleLabel = effectiveFontScale.formatTextScale()
    )

fun mapTitleAppInfoEnabled(
    statusMessage: String?,
    trafficAttention: TrafficAttentionPresentation?
): Boolean =
    statusMessage.isNullOrBlank() && trafficAttention == null

fun uasDatasetInfoPresentation(
    mapStatusMessage: String?,
    updates: UasDatasetUpdates? = null
): UasDatasetInfoPresentation {
    val degraded = !mapStatusMessage.isNullOrBlank()
    val sourceUpdatedAt = updates?.sourceUpdatedAt?.formatDatasetInstant()
    val cachedOnDeviceAt = updates?.cachedOnDeviceAt?.formatDatasetInstant()
    return UasDatasetInfoPresentation(
        availabilityLabel = if (degraded) {
            "Dati Zone UAS caricati dalla cache locale"
        } else {
            "Dati Zone UAS disponibili"
        },
        cacheLabel = when {
            degraded -> "Ultima copia disponibile"
            updates?.degraded == true -> "Metadata dataset caricati dalla cache locale"
            else -> null
        },
        datasetVersion = sourceUpdatedAt?.let { "Build $it" },
        sourceUpdatedAt = sourceUpdatedAt,
        cachedOnDeviceAt = cachedOnDeviceAt,
        metadataFallbackLabel = if (updates == null) {
            "Versione e date dataset non disponibili nell'app"
        } else {
            "Variazioni ultimo build: +${updates.addedCount} / -${updates.removedCount} / ~${updates.modifiedCount}"
        }
    )
}

fun appInfoDiagnosticText(info: AppInfoPresentation): String =
    buildString {
        appendLine(info.build.appName)
        appendLine("Versione app: ${info.build.versionName} (${info.build.versionCode})")
        appendLine("Piattaforma: ${info.build.platform}")
        info.textScale?.let { textScale ->
            appendLine("Scala testo Android: ${textScale.systemFontScaleLabel}")
            appendLine("Testo piu grande DSC: ${textScale.largeTextEnabledLabel}")
            appendLine("Scala testo effettiva DSC: ${textScale.effectiveFontScaleLabel}")
        }
        appendLine("Dati Zone UAS: ${info.dataset.availabilityLabel}")
        info.dataset.cacheLabel?.let { appendLine("Cache Zone UAS: $it") }
        info.dataset.datasetVersion?.let { appendLine("Versione dataset Zone UAS: $it") }
            ?: appendLine("Versione dataset Zone UAS: non disponibile")
        info.dataset.sourceUpdatedAt?.let { appendLine("Aggiornamento sorgente Zone UAS: $it") }
        info.dataset.cachedOnDeviceAt?.let { appendLine("Cache dispositivo Zone UAS: $it") }
        info.dataset.metadataFallbackLabel?.let { appendLine("Metadata Zone UAS: $it") }
    }.trimEnd()

private fun Context.readAppBuildInfoPresentation(): AppBuildInfoPresentation {
    val packageInfo = packageInfoCompat()
    val label = runCatching {
        packageManager.getApplicationLabel(applicationInfo).toString()
    }.getOrDefault("Drone Sky Check")
    return buildAppBuildInfoPresentation(
        appName = label,
        versionName = packageInfo?.versionName ?: "sconosciuta",
        versionCode = packageInfo?.versionCodeCompat() ?: 0L,
        androidRelease = Build.VERSION.RELEASE ?: "sconosciuto",
        sdkInt = Build.VERSION.SDK_INT
    )
}

private fun Context.packageInfoCompat(): PackageInfo? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }.getOrNull()

private fun PackageInfo.versionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }

private fun String.formatDatasetInstant(): String? =
    runCatching {
        DatasetInstantFormatter.format(Instant.parse(this))
    }.getOrNull()

private fun Float.formatTextScale(): String =
    String.format(Locale.US, "%.2f", this)

private val DatasetInstantFormatter: DateTimeFormatter =
    DateTimeFormatter
        .ofPattern("dd/MM/yyyy HH:mm 'UTC'", Locale.ITALY)
        .withZone(ZoneId.of("UTC"))
