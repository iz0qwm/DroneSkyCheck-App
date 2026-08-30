package it.droneskycheck.app.ui.help

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.help.HelpContentBlock
import it.droneskycheck.app.data.help.HelpImageResolver
import it.droneskycheck.app.data.help.HelpManifest
import it.droneskycheck.app.data.help.HelpTopic

private data class HelpExpandedImage(
    val image: String,
    val contentDescription: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpBottomSheet(
    manifest: HelpManifest,
    isRefreshInProgress: Boolean = false,
    refreshMessage: String? = null,
    onRefresh: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTopicId by remember { mutableStateOf<String?>(null) }
    val selectedTopic = selectedTopicId?.let(manifest::topic)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        if (selectedTopic != null) {
            HelpTopicDetail(
                topic = selectedTopic,
                onBack = { selectedTopicId = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
            )
        } else {
            HelpTopicList(
                manifest = manifest,
                isRefreshInProgress = isRefreshInProgress,
                refreshMessage = refreshMessage,
                onRefresh = onRefresh,
                onTopicSelected = { selectedTopicId = it.id },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
            )
        }
    }
}

@Composable
fun HelpTopicDialog(
    topic: HelpTopic,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        },
        title = { Text(topic.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = HelpTopicDialogBodyMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HelpTopicBody(topic)
            }
        }
    )
}

@Composable
private fun HelpTopicList(
    manifest: HelpManifest,
    isRefreshInProgress: Boolean,
    refreshMessage: String?,
    onRefresh: (() -> Unit)?,
    onTopicSelected: (HelpTopic) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Guida Drone Sky Check",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Contenuti guida versione ${manifest.contentVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (onRefresh != null) {
                        OutlinedButton(
                            onClick = onRefresh,
                            enabled = !isRefreshInProgress
                        ) {
                            if (isRefreshInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(if (isRefreshInProgress) "Aggiorno" else "Aggiorna")
                        }
                    }
                }
                refreshMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(manifest.topics, key = { it.id }) { topic ->
            HelpTopicRow(
                topic = topic,
                onClick = { onTopicSelected(topic) }
            )
        }
    }
}

@Composable
private fun HelpTopicRow(
    topic: HelpTopic,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HelpIconChip(topicIcon(topic.id))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = topic.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HelpTopicDetail(
    topic: HelpTopic,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna alla guida")
                }
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            HelpTopicBody(topic)
        }
    }
}

@Composable
private fun HelpTopicBody(topic: HelpTopic) {
    var expandedImage by remember { mutableStateOf<HelpExpandedImage?>(null) }
    expandedImage?.let { image ->
        HelpImageZoomDialog(
            image = image.image,
            contentDescription = image.contentDescription,
            onDismiss = { expandedImage = null }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        topic.introduction?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        topic.image?.let { image ->
            val description = topic.imageAlt ?: "Schermata ${topic.title}"
            HelpGuideImage(
                image = image,
                contentDescription = description,
                onClick = {
                    expandedImage = HelpExpandedImage(
                        image = image,
                        contentDescription = description
                    )
                }
            )
        }
        topic.blocks.forEach { block ->
            when (block) {
                is HelpContentBlock.Paragraph -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is HelpContentBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.items.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "-",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                is HelpContentBlock.Image -> HelpGuideImage(
                    image = block.src,
                    contentDescription = block.alt,
                    onClick = {
                        expandedImage = HelpExpandedImage(
                            image = block.src,
                            contentDescription = block.alt
                        )
                    }
                )
                is HelpContentBlock.Note -> Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpGuideImage(
    image: String,
    contentDescription: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val resolved = remember(image) { HelpImageResolver.resolve(image) }
    LaunchedEffect(image, resolved?.url) {
        if (resolved == null) {
            DscLogger.warn(LogTag, "Help image ignored invalidSource=$image")
        }
    }
    if (resolved == null) return
    val localDrawableId = remember(resolved.localDrawableName) {
        resolved.localDrawableName?.let { drawableName ->
            context.resources.getIdentifier(drawableName, "drawable", context.packageName)
                .takeIf { it != 0 }
        }
    }
    LaunchedEffect(image, resolved.url, localDrawableId) {
        DscLogger.debug(
            LogTag,
            "Help image request source=$image resolved=${resolved.url} localFallback=${localDrawableId != null}"
        )
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(resolved.url)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxWidth(),
        loading = {},
        error = {
            if (localDrawableId != null) {
                LaunchedEffect(resolved.url, localDrawableId) {
                    DscLogger.warn(LogTag, "Help image remote failed, using local fallback url=${resolved.url}")
                }
                HelpGuideImageFrame(onClick = onClick) {
                    Image(
                        painter = painterResource(localDrawableId),
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = HelpImageMaxHeight),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                LaunchedEffect(resolved.url) {
                    DscLogger.warn(LogTag, "Help image unavailable url=${resolved.url}")
                }
            }
        },
        success = {
            LaunchedEffect(resolved.url) {
                DscLogger.debug(LogTag, "Help image loaded url=${resolved.url}")
            }
            HelpGuideImageFrame(onClick = onClick) {
                SubcomposeAsyncImageContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = HelpImageMaxHeight),
                    contentScale = ContentScale.Fit
                )
            }
        }
    )
}

@Composable
private fun HelpImageZoomDialog(
    image: String,
    contentDescription: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resolved = remember(image) { HelpImageResolver.resolve(image) } ?: return
    val localDrawableId = remember(resolved.localDrawableName) {
        resolved.localDrawableName?.let { drawableName ->
            context.resources.getIdentifier(drawableName, "drawable", context.packageName)
                .takeIf { it != 0 }
        }
    }
    LaunchedEffect(resolved.url) {
        DscLogger.debug(LogTag, "Help image zoom opened url=${resolved.url}")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolved.url)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = HelpImageZoomMaxHeight)
                    .clip(HelpImageShape),
                loading = {},
                error = {
                    if (localDrawableId != null) {
                        Image(
                            painter = painterResource(localDrawableId),
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = HelpImageZoomMaxHeight)
                                .clip(HelpImageShape),
                            contentScale = ContentScale.Fit
                        )
                    }
                },
                success = {
                    SubcomposeAsyncImageContent(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = HelpImageZoomMaxHeight)
                            .clip(HelpImageShape),
                        contentScale = ContentScale.Fit
                    )
                }
            )
        }
    }
}

@Composable
private fun HelpGuideImageFrame(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HelpImageShape)
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = HelpImageShape
            )
    ) {
        content()
    }
}

@Composable
private fun HelpIconChip(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun topicIcon(id: String): ImageVector =
    when (id) {
        "getting_started" -> Icons.Default.CheckCircle
        "check_area" -> Icons.Default.Map
        "weather" -> Icons.Default.Cloud
        "dsc_assistant" -> Icons.Default.AutoAwesome
        "traffic" -> Icons.Default.Flight
        "notam" -> Icons.Default.Description
        "authorizations" -> Icons.Default.Security
        "pilot_profile" -> Icons.Default.Person
        else -> Icons.Default.Timeline
    }

private val HelpImageShape = RoundedCornerShape(10.dp)
private val HelpTopicDialogBodyMaxHeight = 420.dp
private val HelpImageMaxHeight = 260.dp
private val HelpImageZoomMaxHeight = 760.dp
private const val LogTag = "HelpUi"
