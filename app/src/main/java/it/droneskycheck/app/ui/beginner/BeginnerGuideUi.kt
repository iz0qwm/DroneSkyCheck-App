package it.droneskycheck.app.ui.beginner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import it.droneskycheck.app.data.beginner.BeginnerGuideClient
import it.droneskycheck.app.data.beginner.BeginnerGuideContent
import it.droneskycheck.app.data.beginner.BeginnerGuideLoadResult
import it.droneskycheck.app.data.beginner.BeginnerGuidePage
import it.droneskycheck.app.data.beginner.BeginnerGuidePreferences
import it.droneskycheck.app.data.beginner.BeginnerGuideReadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BeginnerGuideStartupIntroDialog(
    onOpenGuide: () -> Unit,
    onDisableStartup: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null
            )
        },
        title = { Text("Prima di volare") },
        text = {
            Text(
                text = "Hai appena iniziato con i droni?\nIn pochi minuti scopri le cose essenziali da sapere prima di decollare.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onOpenGuide) {
                Text("Apri la guida")
            }
        },
        dismissButton = {
            TextButton(onClick = onDisableStartup) {
                Text("Non mostrare piu all'avvio")
            }
        }
    )
}

@Composable
fun BeginnerGuideExperienceDialog(
    repository: BeginnerGuideClient,
    preferences: BeginnerGuidePreferences,
    startInBook: Boolean,
    onDismiss: () -> Unit
) {
    var mode by remember(startInBook) {
        mutableStateOf(if (startInBook) BeginnerGuideMode.Book else BeginnerGuideMode.Home)
    }
    var startPageIndex by remember(startInBook) { mutableIntStateOf(0) }
    var loadState by remember(repository) { mutableStateOf<BeginnerGuideUiLoadState>(BeginnerGuideUiLoadState.Loading) }
    var readingState by remember(preferences) { mutableStateOf(preferences.getReadingState()) }
    val coroutineScope = rememberCoroutineScope()

    fun reload(forceRefresh: Boolean) {
        loadState = BeginnerGuideUiLoadState.Loading
        coroutineScope.launch {
            val state = withContext(Dispatchers.IO) {
                when (val result = repository.loadGuide(forceRefresh = forceRefresh)) {
                    is BeginnerGuideLoadResult.Available -> {
                        preferences.setLocalContentVersion(result.content.manifest.contentVersion)
                        BeginnerGuideUiLoadState.Ready(result.content)
                    }
                    is BeginnerGuideLoadResult.Failed -> BeginnerGuideUiLoadState.Error(result.reason)
                }
            }
            readingState = withContext(Dispatchers.IO) { preferences.getReadingState() }
            loadState = state
        }
    }

    LaunchedEffect(repository) {
        reload(forceRefresh = false)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val state = loadState) {
                BeginnerGuideUiLoadState.Loading -> BeginnerGuideLoadingPage(onDismiss = onDismiss)
                is BeginnerGuideUiLoadState.Error -> BeginnerGuideErrorPage(
                    title = "Prima di volare",
                    message = "Contenuto non disponibile",
                    onRetry = { reload(forceRefresh = true) },
                    onDismiss = onDismiss
                )
                is BeginnerGuideUiLoadState.Ready -> {
                    when (mode) {
                        BeginnerGuideMode.Home -> BeginnerGuideHomePage(
                            content = state.content,
                            readingState = readingState,
                            onStart = {
                                val pageCount = state.content.manifest.pages.size
                                startPageIndex = readingState.startPageIndex(pageCount)
                                coroutineScope.launch(Dispatchers.IO) {
                                    preferences.setStarted(true)
                                }
                                readingState = readingState.copy(started = true)
                                mode = BeginnerGuideMode.Book
                            },
                            onRetry = { reload(forceRefresh = true) },
                            onDismiss = onDismiss
                        )
                        BeginnerGuideMode.Book -> BeginnerGuideBook(
                            content = state.content,
                            repository = repository,
                            initialPageIndex = startPageIndex,
                            onPageViewed = { index ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    preferences.setStarted(true)
                                    preferences.setLastPageIndex(index)
                                }
                                readingState = readingState.copy(started = true, lastPageIndex = index)
                            },
                            onCompleted = {
                                val lastIndex = state.content.manifest.pages.lastIndex
                                coroutineScope.launch(Dispatchers.IO) {
                                    preferences.setStarted(true)
                                    preferences.setCompleted(true)
                                    preferences.setLastPageIndex(lastIndex)
                                }
                                readingState = readingState.copy(
                                    started = true,
                                    completed = true,
                                    lastPageIndex = lastIndex
                                )
                                mode = BeginnerGuideMode.Home
                            },
                            zoomHintShown = readingState.zoomHintShown,
                            onZoomHintShown = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    preferences.setZoomHintShown(true)
                                }
                                readingState = readingState.copy(zoomHintShown = true)
                            },
                            onRetry = { reload(forceRefresh = true) },
                            onExit = { mode = BeginnerGuideMode.Home }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BeginnerGuideLoadingPage(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Carico la guida...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BeginnerGuideErrorPage(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Chiudi")
                }
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Riprova")
                }
            }
        }
    }
}

@Composable
private fun BeginnerGuideHomePage(
    content: BeginnerGuideContent,
    readingState: BeginnerGuideReadingState,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.manifest.title.ifBlank { "Prima di volare" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = content.manifest.description.ifBlank {
                        "Le cose essenziali da sapere prima di usare un drone."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Chiudi Prima di volare")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Percorso essenziale",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${content.manifest.pages.size} pagine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                BeginnerGuideProgress(
                    readingState = readingState,
                    pageCount = content.manifest.pages.size
                )

                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(readingState.actionLabel(content.manifest.pages.size))
                }

                if (content.degraded) {
                    Text(
                        text = "Alcuni contenuti potrebbero non essere aggiornati. Puoi riprovare quando la connessione e migliore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Riprova download")
                    }
                }
            }
        }
    }
}

@Composable
private fun BeginnerGuideProgress(
    readingState: BeginnerGuideReadingState,
    pageCount: Int
) {
    val viewedPages = (readingState.lastPageIndex + 1).coerceIn(0, pageCount)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = {
                if (pageCount <= 0) 0f else viewedPages.toFloat() / pageCount.toFloat()
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = if (readingState.completed) {
                "Guida completata"
            } else if (viewedPages > 0) {
                "Letto fino alla pagina $viewedPages"
            } else {
                "Non ancora iniziata"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BeginnerGuideBook(
    content: BeginnerGuideContent,
    repository: BeginnerGuideClient,
    initialPageIndex: Int,
    onPageViewed: (Int) -> Unit,
    onCompleted: () -> Unit,
    zoomHintShown: Boolean,
    onZoomHintShown: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    val pages = content.manifest.pages
    if (pages.isEmpty()) {
        BeginnerGuideErrorPage(
            title = content.manifest.title.ifBlank { "Prima di volare" },
            message = "Contenuto non disponibile",
            onRetry = onRetry,
            onDismiss = onExit
        )
        return
    }

    var pageIndex by remember(content.manifest.contentVersion) {
        mutableIntStateOf(initialPageIndex.coerceIn(0, pages.lastIndex))
    }
    var scale by remember(pageIndex, content.manifest.contentVersion) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex, content.manifest.contentVersion) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var showZoomHint by remember(zoomHintShown) { mutableStateOf(!zoomHintShown) }

    fun resetZoom() {
        scale = 1f
        offset = Offset.Zero
    }

    fun previousPage() {
        if (pageIndex > 0) pageIndex-- else onExit()
    }

    fun nextPage() {
        if (pageIndex < pages.lastIndex) pageIndex++ else onCompleted()
    }

    BackHandler {
        if (scale.isZoomed()) resetZoom() else previousPage()
    }

    LaunchedEffect(pageIndex, content.manifest.contentVersion) {
        onPageViewed(pageIndex)
    }

    LaunchedEffect(showZoomHint, zoomHintShown) {
        if (!showZoomHint || zoomHintShown) return@LaunchedEffect
        onZoomHintShown()
        delay(ZoomHintMillis)
        showZoomHint = false
    }

    val page = pages[pageIndex]
    val imageFile = repository.cachedImageFile(content.manifest, page)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .clipToBounds()
            .onSizeChanged { size ->
                containerSize = size
                offset = offset.coerceInZoomBounds(scale, size)
            }
            .pointerInput(pageIndex, pages.size, containerSize) {
                var pageDragAmount = 0f
                awaitPointerEventScope {
                    while (true) {
                        val firstEvent = awaitPointerEvent()
                        if (firstEvent.changes.none { it.pressed }) continue
                        pageDragAmount = 0f
                        var gestureWasZoomed = scale.isZoomed()

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                val nextScale = (scale * event.calculateZoom()).coerceIn(1f, MaxZoomScale)
                                scale = nextScale
                                offset = if (nextScale.isZoomed()) {
                                    (offset + event.calculatePan()).coerceInZoomBounds(nextScale, containerSize)
                                } else {
                                    Offset.Zero
                                }
                                gestureWasZoomed = true
                                event.changes.forEach { it.consume() }
                            } else {
                                val change = pressed.first()
                                val delta = change.positionChange()
                                if (scale.isZoomed() || gestureWasZoomed) {
                                    offset = (offset + delta).coerceInZoomBounds(scale, containerSize)
                                    change.consume()
                                } else {
                                    pageDragAmount += delta.x
                                }
                            }
                        }

                        if (!gestureWasZoomed && !scale.isZoomed()) {
                            when {
                                pageDragAmount < -SwipeThresholdPx -> nextPage()
                                pageDragAmount > SwipeThresholdPx -> previousPage()
                            }
                        }
                    }
                }
            }
            .pointerInput(pageIndex) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale.isZoomed()) {
                            resetZoom()
                        } else {
                            scale = DoubleTapZoomScale
                            offset = Offset.Zero
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (imageFile != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageFile)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = page.accessibilityText,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .semantics {
                        contentDescription = page.accessibilityText
                    },
                contentScale = ContentScale.Fit,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                },
                error = {
                    BeginnerGuidePageFallback(
                        page = page,
                        onRetry = onRetry
                    )
                },
                success = {
                    SubcomposeAsyncImageContent(
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            )
        } else {
            BeginnerGuidePageFallback(
                page = page,
                onRetry = onRetry
            )
        }

        if (pageIndex > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.28f)
                    .clickable(
                        enabled = !scale.isZoomed(),
                        role = Role.Button,
                        onClickLabel = "Indietro",
                        onClick = ::previousPage
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = "Pagina precedente"
                    }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BottomActionHeight)
                .clickable(
                    enabled = !scale.isZoomed(),
                    role = Role.Button,
                    onClickLabel = if (pageIndex == pages.lastIndex) "Fine" else "Avanti",
                    onClick = ::nextPage
                )
                .semantics {
                    role = Role.Button
                    contentDescription = if (pageIndex == pages.lastIndex) {
                        "Fine, torna a Prima di volare"
                    } else {
                        "Pagina successiva"
                    }
                }
        )

        if (showZoomHint) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp, start = 20.dp, end = 20.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                tonalElevation = 3.dp
            ) {
                Text(
                    text = "Pizzica con due dita per ingrandire",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BeginnerGuidePageFallback(
    page: BeginnerGuidePage,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .aspectRatio(9f / 16f)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(20.dp)
        ) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Contenuto non disponibile",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Riprova")
            }
        }
    }
}

private fun BeginnerGuideReadingState.actionLabel(pageCount: Int): String =
    when {
        completed -> "Rivedi la guida"
        !started -> "Inizia"
        else -> "Continua dalla pagina ${startPageIndex(pageCount) + 1}"
    }

private fun BeginnerGuideReadingState.startPageIndex(pageCount: Int): Int {
    if (pageCount <= 0) return 0
    if (completed || !started) return 0
    return (lastPageIndex + 1).coerceIn(0, pageCount - 1)
}

private sealed class BeginnerGuideUiLoadState {
    data object Loading : BeginnerGuideUiLoadState()
    data class Ready(val content: BeginnerGuideContent) : BeginnerGuideUiLoadState()
    data class Error(val reason: String) : BeginnerGuideUiLoadState()
}

private enum class BeginnerGuideMode {
    Home,
    Book
}

private fun Float.isZoomed(): Boolean =
    this > 1.01f

private fun Offset.coerceInZoomBounds(
    scale: Float,
    containerSize: IntSize
): Offset {
    if (!scale.isZoomed() || containerSize.width <= 0 || containerSize.height <= 0) return Offset.Zero
    val maxX = containerSize.width * (scale - 1f) / 2f
    val maxY = containerSize.height * (scale - 1f) / 2f
    return Offset(
        x = x.coerceIn(-maxX, maxX),
        y = y.coerceIn(-maxY, maxY)
    )
}

private val BottomActionHeight = 132.dp
private const val DoubleTapZoomScale = 2f
private const val MaxZoomScale = 4f
private const val ZoomHintMillis = 3_500L
private const val SwipeThresholdPx = 90f
