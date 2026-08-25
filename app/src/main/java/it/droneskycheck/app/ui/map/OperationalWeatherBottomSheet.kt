package it.droneskycheck.app.ui.map

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.droneskycheck.app.data.weather.NearbyMetar
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastDay
import it.droneskycheck.app.data.weather.WeatherMetrics
import java.time.Instant
import kotlin.math.roundToInt

@Composable
internal fun OperationalWeatherBottomSheet(
    point: MapPoint?,
    isLoading: Boolean,
    forecast: WeatherForecast?,
    nearbyMetar: NearbyMetar?,
    error: String?,
    isWeatherMapLoading: Boolean = false,
    weatherMapError: String? = null,
    onRefresh: () -> Unit,
    onSelectedForecastTimeChanged: (Instant?) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val targetHeight = screenHeight * if (isLandscape) WeatherSheetLandscapeHeightFraction else WeatherSheetPortraitHeightFraction
    val maximumHeight = screenHeight * if (isLandscape) WeatherSheetLandscapeMaxHeightFraction else WeatherSheetPortraitMaxHeightFraction
    val minimumHeight = if (isLandscape) WeatherSheetLandscapeMinHeight else WeatherSheetPortraitMinHeight
    val sheetHeight = targetHeight
        .coerceAtLeast(minimumHeight)
        .coerceAtMost(maximumHeight)
    val listState = rememberLazyListState()
    val openedAt = remember(forecast) { Instant.now() }
    val days = remember(forecast, openedAt) {
        forecast?.let { buildOperationalWeatherDays(it, now = openedAt) }.orEmpty()
    }
    var selectedSlot by remember(forecast) { mutableStateOf<OperationalWeatherHourSlot?>(null) }

    LaunchedEffect(forecast, days) {
        selectedSlot = selectInitialOperationalWeatherSlot(days, now = openedAt)
    }
    LaunchedEffect(selectedSlot) {
        onSelectedForecastTimeChanged(selectedSlot?.forecastHour?.instant)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OperationalWeatherHeaderBar(
                    point = point,
                    isLoading = isLoading,
                    onRefresh = onRefresh,
                    onDismiss = onDismiss
                )
            }

            item {
                OperationalWeatherMapStatus(
                    isLoading = isWeatherMapLoading,
                    error = weatherMapError
                )
            }

            item {
                when {
                    isLoading -> OperationalWeatherLoading()
                    forecast != null && selectedSlot != null -> {
                        val slot = selectedSlot ?: return@item
                        val selectedDay = days.firstOrNull { it.date == slot.date } ?: days.firstOrNull()
                        OperationalWeatherContent(
                            forecast = forecast,
                            days = days,
                            selectedDay = selectedDay,
                            selectedSlot = slot,
                            nearbyMetar = nearbyMetar,
                            onDaySelected = { day ->
                                selectedSlot = selectOperationalWeatherSlotForDay(day, selectedSlot)
                            },
                            onSlotSelected = { selectedSlot = it }
                        )
                    }
                    error != null -> OperationalWeatherError(error = error, onRefresh = onRefresh)
                    else -> OperationalWeatherEmpty(point = point)
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun OperationalWeatherMapStatus(
    isLoading: Boolean,
    error: String?
) {
    when {
        isLoading -> Text(
            text = "Campo vento in caricamento",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        error != null -> Text(
            text = error,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OperationalWeatherHeaderBar(
    point: MapPoint?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Meteo Operativo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = point?.let { "Punto selezionato ${it.lat.formatCoordinate()}, ${it.lon.formatCoordinate()}" }
                    ?: "Seleziona un punto sulla mappa",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row {
            IconButton(onClick = onRefresh, enabled = point != null && !isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = "Aggiorna meteo operativo")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Chiudi meteo operativo")
            }
        }
    }
}

@Composable
private fun OperationalWeatherContent(
    forecast: WeatherForecast,
    days: List<OperationalWeatherDay>,
    selectedDay: OperationalWeatherDay?,
    selectedSlot: OperationalWeatherHourSlot,
    nearbyMetar: NearbyMetar?,
    onDaySelected: (OperationalWeatherDay) -> Unit,
    onSlotSelected: (OperationalWeatherHourSlot) -> Unit
) {
    val metrics = selectedSlot.forecastHour.metrics
    val condition = operationalWeatherCondition(
        weatherCode = metrics.weatherCode,
        isDaylight = isOperationalWeatherDaylight(selectedSlot)
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OperationalWeatherHero(
            metrics = metrics,
            condition = condition,
            selectedSlot = selectedSlot
        )

        OperationalWeatherDaySelector(
            days = days,
            selectedDay = selectedDay,
            onDaySelected = onDaySelected
        )

        selectedDay?.let {
            OperationalWeatherTimeline(
                day = it,
                selectedSlot = selectedSlot,
                onSlotSelected = onSlotSelected
            )
        }

        OperationalWeatherDetails(
            metrics = metrics,
            day = forecast.days.firstOrNull { it.date == selectedSlot.date }
        )

        nearbyMetar?.let {
            NearbyOperationalMetarSection(metar = it)
        }
    }
}

@Composable
private fun OperationalWeatherHero(
    metrics: WeatherMetrics,
    condition: OperationalWeatherCondition,
    selectedSlot: OperationalWeatherHourSlot
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeatherConditionIcon(
                    condition = condition,
                    modifier = Modifier.size(44.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = selectedSlot.timeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = metrics.temperatureText(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = condition.description,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WindHighlightCard(
                    title = "Vento",
                    value = metrics.windText(),
                    level = windLevel(metrics.windSpeedKmh),
                    modifier = Modifier.weight(1f)
                )
                WindHighlightCard(
                    title = "Raffiche",
                    value = metrics.gustText(),
                    level = windLevel(metrics.windGustsKmh),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactWeatherMetric(
                    icon = Icons.Default.Opacity,
                    label = "Pioggia",
                    value = metrics.precipitationText(),
                    modifier = Modifier.weight(1f)
                )
                CompactWeatherMetric(
                    icon = Icons.Default.Cloud,
                    label = "Nuvolosita",
                    value = metrics.cloudCoverText(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WindHighlightCard(
    title: String,
    value: String,
    level: OperationalWindLevel,
    modifier: Modifier = Modifier
) {
    val colors = windLevelColors(level)
    Surface(
        modifier = modifier.heightIn(min = 58.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.container,
        contentColor = colors.content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Air, contentDescription = null, modifier = Modifier.size(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CompactWeatherMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 46.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.labelSmall)
                Text(text = value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OperationalWeatherDaySelector(
    days: List<OperationalWeatherDay>,
    selectedDay: OperationalWeatherDay?,
    onDaySelected: (OperationalWeatherDay) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(days, key = { it.date.toString() }) { day ->
            FilterChip(
                selected = day.date == selectedDay?.date,
                onClick = { onDaySelected(day) },
                label = {
                    Text(
                        text = day.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun OperationalWeatherTimeline(
    day: OperationalWeatherDay,
    selectedSlot: OperationalWeatherHourSlot,
    onSlotSelected: (OperationalWeatherHourSlot) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Previsione oraria",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(day.slots, key = { it.key }) { slot ->
                OperationalWeatherHourCard(
                    slot = slot,
                    selected = slot.key == selectedSlot.key,
                    onClick = { onSlotSelected(slot) }
                )
            }
        }
    }
}

@Composable
private fun OperationalWeatherHourCard(
    slot: OperationalWeatherHourSlot,
    selected: Boolean,
    onClick: () -> Unit
) {
    val metrics = slot.forecastHour.metrics
    val condition = operationalWeatherCondition(metrics.weatherCode, isOperationalWeatherDaylight(slot))
    Card(
        modifier = Modifier
            .width(88.dp)
            .height(122.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(text = slot.timeLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            WeatherConditionIcon(condition = condition, modifier = Modifier.size(28.dp))
            Text(text = metrics.temperatureText(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = metrics.windText(),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OperationalWeatherDetails(
    metrics: WeatherMetrics,
    day: WeatherForecastDay?
) {
    val rows = listOfNotNull(
        metrics.windDirectionText()?.let { Triple(Icons.Default.Air, "Direzione vento", it) },
        metrics.precipitationProbabilityText()?.let { Triple(Icons.Default.Opacity, "Probabilita pioggia", it) },
        metrics.visibilityText()?.let { Triple(Icons.Default.Visibility, "Visibilita", it) },
        day?.sunriseLocalTimeText?.let { Triple(Icons.Default.WbTwilight, "Alba", it) },
        day?.sunsetLocalTimeText?.let { Triple(Icons.Default.WbTwilight, "Tramonto", it) }
    )
    if (rows.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Dettagli",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            rows.forEach { (icon, label, value) ->
                OperationalWeatherInfoRow(icon = icon, label = label, value = value)
            }
        }
    }
}

@Composable
private fun NearbyOperationalMetarSection(metar: NearbyMetar) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flight, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = "Dati aeronautici vicini",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OperationalWeatherInfoRow(
                icon = Icons.Default.Info,
                label = "METAR",
                value = "${metar.icao} · ${metar.distanceKm.formatOneDecimalLocal()} km"
            )
            metar.windSummaryLocal()?.let {
                OperationalWeatherInfoRow(Icons.Default.Air, "Vento METAR", it)
            }
            metar.temperatureC?.let {
                OperationalWeatherInfoRow(Icons.Default.Thermostat, "Temperatura METAR", "${it.roundToInt()} °C")
            }
            metar.visibilityMeters?.let {
                OperationalWeatherInfoRow(Icons.Default.Visibility, "Visibilita METAR", "${(it / 1_000.0).formatOneDecimalLocal()} km")
            }
            metar.flightCategory?.let {
                AssistChip(onClick = {}, label = { Text(it) })
            }
            metar.rawText?.let {
                HorizontalDivider()
                SelectionContainer {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationalWeatherInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 72.dp)
        )
    }
}

@Composable
private fun WeatherConditionIcon(
    condition: OperationalWeatherCondition,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = condition.icon.imageVector(),
            contentDescription = condition.contentDescription,
            modifier = Modifier.fillMaxWidth(),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun OperationalWeatherLoading() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Aggiornamento meteo operativo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Sto leggendo vento, raffiche e condizioni sul punto selezionato.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OperationalWeatherError(
    error: String,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Meteo non disponibile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(onClick = onRefresh) {
                Text("Riprova")
            }
        }
    }
}

@Composable
private fun OperationalWeatherEmpty(point: MapPoint?) {
    Text(
        text = if (point == null) {
            "Seleziona un punto sulla mappa per aprire il Meteo Operativo."
        } else {
            "Apri il meteo operativo per leggere la previsione del punto selezionato."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private data class WindColors(
    val container: Color,
    val content: Color
)

@Composable
private fun windLevelColors(level: OperationalWindLevel): WindColors =
    when (level) {
        OperationalWindLevel.Missing -> WindColors(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        OperationalWindLevel.Weak -> WindColors(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        OperationalWindLevel.Moderate -> WindColors(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        OperationalWindLevel.Sustained -> WindColors(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        OperationalWindLevel.Strong -> WindColors(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

private fun OperationalWindLevel.userText(): String =
    when (this) {
        OperationalWindLevel.Missing -> "Dato mancante"
        OperationalWindLevel.Weak -> "Debole"
        OperationalWindLevel.Moderate -> "Moderato"
        OperationalWindLevel.Sustained -> "Sostenuto"
        OperationalWindLevel.Strong -> "Forte"
    }

private fun OperationalWeatherIcon.imageVector(): ImageVector =
    when (this) {
        OperationalWeatherIcon.ClearDay -> Icons.Default.WbSunny
        OperationalWeatherIcon.ClearNight -> Icons.Default.NightsStay
        OperationalWeatherIcon.PartlyCloudyDay -> Icons.Default.WbTwilight
        OperationalWeatherIcon.PartlyCloudyNight -> Icons.Default.NightsStay
        OperationalWeatherIcon.Cloudy -> Icons.Default.Cloud
        OperationalWeatherIcon.Overcast -> Icons.Default.Cloud
        OperationalWeatherIcon.Fog -> Icons.Default.Visibility
        OperationalWeatherIcon.Drizzle -> Icons.Default.Opacity
        OperationalWeatherIcon.Rain -> Icons.Default.Opacity
        OperationalWeatherIcon.Showers -> Icons.Default.Opacity
        OperationalWeatherIcon.Snow -> Icons.Default.Cloud
        OperationalWeatherIcon.Thunderstorm -> Icons.Default.Speed
        OperationalWeatherIcon.Unknown -> Icons.Default.Info
    }

private fun NearbyMetar.windSummaryLocal(): String? {
    if (windDirectionDeg == null && windSpeedKt == null && windGustKt == null) return null
    return buildString {
        windDirectionDeg?.let { append("$it°") }
        windSpeedKt?.let {
            if (isNotEmpty()) append(" · ")
            append("$it kt")
        }
        windGustKt?.let {
            if (isNotEmpty()) append(" · ")
            append("raffiche $it kt")
        }
    }
}

private fun Double.formatOneDecimalLocal(): String =
    java.text.DecimalFormat("0.0").format(this)

private fun Double.formatCoordinate(): String =
    "%.5f".format(this)

private const val WeatherSheetPortraitHeightFraction = 0.50f
private const val WeatherSheetPortraitMaxHeightFraction = 0.58f
private const val WeatherSheetLandscapeHeightFraction = 0.58f
private const val WeatherSheetLandscapeMaxHeightFraction = 0.76f
private val WeatherSheetPortraitMinHeight: Dp = 320.dp
private val WeatherSheetLandscapeMinHeight: Dp = 260.dp
