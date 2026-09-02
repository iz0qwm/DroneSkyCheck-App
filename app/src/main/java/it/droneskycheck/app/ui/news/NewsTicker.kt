package it.droneskycheck.app.ui.news

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.droneskycheck.app.data.news.NewsItem
import it.droneskycheck.app.data.news.newsBadge
import kotlinx.coroutines.delay

@Composable
fun NewsTicker(
    items: List<NewsItem>,
    unseenCount: Int,
    onNewsLabelClick: () -> Unit,
    onHeadlineClick: (NewsItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val animationsEnabled = remember(context) { context.systemAnimationsEnabled() }
    var currentNewsId by remember { mutableStateOf<Long?>(null) }
    val currentIndex = items.indexOfFirst { it.id == currentNewsId }.takeIf { it >= 0 } ?: 0
    val current = items[currentIndex]

    LaunchedEffect(items, animationsEnabled) {
        if (items.none { it.id == currentNewsId }) currentNewsId = items.first().id
        while (items.size > 1) {
            delay(if (animationsEnabled) AnimatedHeadlineDurationMillis else StaticHeadlineDurationMillis)
            val index = items.indexOfFirst { it.id == currentNewsId }.takeIf { it >= 0 } ?: 0
            currentNewsId = items[(index + 1) % items.size].id
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(TickerHeight),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp
    ) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(LabelWidth)
                        .height(TickerHeight)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onNewsLabelClick)
                        .semantics {
                            contentDescription = if (unseenCount > 0) {
                                "DSC News, $unseenCount nuove notizie. Apri il giornale."
                            } else {
                                "DSC News. Apri il giornale."
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unseenCount > 0) "DSC NEWS · $unseenCount" else "DSC NEWS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(TickerHeight)
                        .clipToBounds()
                        .clickable { onHeadlineClick(current) }
                        .semantics {
                            contentDescription = "${newsBadge(current).label}. ${current.title}. Apri la notizia."
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "[${newsBadge(current).label}]  ${current.title}",
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .then(
                                if (animationsEnabled) {
                                    Modifier.basicMarquee(
                                        iterations = 1,
                                        initialDelayMillis = 1_400,
                                        velocity = 18.dp
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = if (animationsEnabled) TextOverflow.Clip else TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

internal fun Context.systemAnimationsEnabled(): Boolean =
    runCatching {
        Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }.getOrDefault(true)

private val TickerHeight = 32.dp
private val LabelWidth = 104.dp
private const val AnimatedHeadlineDurationMillis = 14_000L
private const val StaticHeadlineDurationMillis = 8_000L
