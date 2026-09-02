package it.droneskycheck.app.ui.news

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.droneskycheck.app.data.news.NewsBadge
import it.droneskycheck.app.data.news.NewsBadgeKind
import it.droneskycheck.app.data.news.NewsFeedResponse
import it.droneskycheck.app.data.news.NewsFilter
import it.droneskycheck.app.data.news.NewsItem
import it.droneskycheck.app.data.news.NewsLoadResult
import it.droneskycheck.app.data.news.NewsRepository
import it.droneskycheck.app.data.news.formatNewsDate
import it.droneskycheck.app.data.news.latestNewsId
import it.droneskycheck.app.data.news.newsBadge
import it.droneskycheck.app.data.news.toRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NewsScreen(
    repository: NewsRepository,
    initialItems: List<NewsItem>,
    initialDataStale: Boolean,
    selectedNewsId: Long?,
    onBack: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onItemsSeen: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var selectedFilter by remember { mutableStateOf(NewsFilter.ALL) }
    var loadedFilter by remember { mutableStateOf(NewsFilter.ALL) }
    var feed by remember {
        mutableStateOf(
            NewsFeedResponse(
                items = initialItems,
                limit = PageSize,
                offset = 0,
                total = initialItems.size
            )
        )
    }
    var loading by remember { mutableStateOf(initialItems.isEmpty()) }
    var loadingMore by remember { mutableStateOf(false) }
    var stale by remember { mutableStateOf(initialDataStale) }
    var unavailable by remember { mutableStateOf(false) }
    var focusedNewsId by remember(selectedNewsId) { mutableStateOf(selectedNewsId) }

    fun loadFirstPage(filter: NewsFilter) {
        scope.launch {
            loading = true
            unavailable = false
            val result = withContext(Dispatchers.IO) {
                repository.getNews(filter.toRequest(PageSize))
            }
            when (result) {
                is NewsLoadResult.Available -> {
                    feed = result.feed
                    loadedFilter = filter
                    stale = result.fromCache
                    latestNewsId(result.feed.items)?.let(onItemsSeen)
                    unavailable = false
                }
                NewsLoadResult.Unavailable -> {
                    if (feed.items.isEmpty() || filter != NewsFilter.ALL) {
                        unavailable = true
                    } else {
                        stale = true
                    }
                }
            }
            loading = false
        }
    }

    LaunchedEffect(selectedFilter) {
        if (selectedFilter != loadedFilter) {
            feed = NewsFeedResponse(emptyList(), PageSize, 0, 0)
            stale = false
            unavailable = false
        }
        loadFirstPage(selectedFilter)
    }

    LaunchedEffect(feed.items, focusedNewsId) {
        val targetId = focusedNewsId ?: return@LaunchedEffect
        val index = feed.items.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            listState.scrollToItem(index)
            focusedNewsId = null
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            NewsTopBar(onBack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "Le notizie dal mondo dei droni",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NewsFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = filter == selectedFilter,
                            onClick = {
                                if (filter != selectedFilter) {
                                    selectedFilter = filter
                                    focusedNewsId = null
                                }
                            },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                loading && feed.items.isEmpty() -> NewsLoadingState()
                unavailable && feed.items.isEmpty() -> NewsUnavailableState(
                    onRetry = { loadFirstPage(selectedFilter) }
                )
                feed.items.isEmpty() -> NewsEmptyState()
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (stale) {
                        item(key = "stale") {
                            Text(
                                text = "Dati non aggiornati · visualizzazione offline",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(feed.items, key = NewsItem::id) { item ->
                        NewsCard(
                            item = item,
                            highlighted = item.id == selectedNewsId,
                            onOpenExternalUrl = onOpenExternalUrl
                        )
                    }
                    if (feed.items.size < feed.total) {
                        item(key = "load-more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(
                                    enabled = !loadingMore,
                                    onClick = {
                                        scope.launch {
                                            loadingMore = true
                                            val result = withContext(Dispatchers.IO) {
                                                repository.getNews(
                                                    selectedFilter.toRequest(
                                                        limit = PageSize,
                                                        offset = feed.items.size
                                                    )
                                                )
                                            }
                                            if (result is NewsLoadResult.Available) {
                                                val combined = (feed.items + result.feed.items)
                                                    .distinctBy(NewsItem::id)
                                                feed = result.feed.copy(
                                                    items = combined,
                                                    offset = 0
                                                )
                                                stale = stale || result.fromCache
                                                latestNewsId(combined)?.let(onItemsSeen)
                                            }
                                            loadingMore = false
                                        }
                                    }
                                ) {
                                    if (loadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Carica altre")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna alla mappa")
        }
        Text(
            text = "DSC NEWS",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NewsCard(
    item: NewsItem,
    highlighted: Boolean,
    onOpenExternalUrl: (String) -> Unit
) {
    val featured = item.featured
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    if (featured) append("In evidenza. ")
                    append(newsBadge(item).label).append(". ")
                    append(item.title)
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                highlighted -> MaterialTheme.colorScheme.primaryContainer
                featured -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = if (highlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (featured) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (featured) {
                Text(
                    text = "IN EVIDENZA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            NewsBadgeChip(newsBadge(item))
            Text(
                text = item.title,
                style = if (featured) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (item.summary.isNotBlank()) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = listOf(item.sourceName.ifBlank { item.source }, formatNewsDate(item.publishedAt))
                    .filter(String::isNotBlank)
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { onOpenExternalUrl(item.originalUrl) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Leggi la notizia originale")
                Spacer(Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun NewsBadgeChip(badge: NewsBadge) {
    val background = when (badge.kind) {
        NewsBadgeKind.Geographic -> MaterialTheme.colorScheme.primaryContainer
        NewsBadgeKind.Regulation -> MaterialTheme.colorScheme.tertiaryContainer
        NewsBadgeKind.Safety, NewsBadgeKind.Defence -> MaterialTheme.colorScheme.errorContainer
        NewsBadgeKind.Fpv, NewsBadgeKind.Technology -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val foreground = when (badge.kind) {
        NewsBadgeKind.Geographic -> MaterialTheme.colorScheme.onPrimaryContainer
        NewsBadgeKind.Regulation -> MaterialTheme.colorScheme.onTertiaryContainer
        NewsBadgeKind.Safety, NewsBadgeKind.Defence -> MaterialTheme.colorScheme.onErrorContainer
        NewsBadgeKind.Fpv, NewsBadgeKind.Technology -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = background, contentColor = foreground) {
        Text(
            text = badge.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NewsLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = "Caricamento notizie" }
        )
    }
}

@Composable
private fun NewsUnavailableState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Le notizie non sono disponibili al momento.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) { Text("Riprova") }
    }
}

@Composable
private fun NewsEmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Nessuna notizia per questo filtro.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val PageSize = 20
