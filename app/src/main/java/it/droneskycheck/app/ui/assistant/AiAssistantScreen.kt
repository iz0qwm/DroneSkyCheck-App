package it.droneskycheck.app.ui.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.ai.AiAssistantClient
import it.droneskycheck.app.data.ai.AiAssistantContext
import it.droneskycheck.app.data.ai.AiAssistantQuota
import it.droneskycheck.app.data.ai.AiAssistantRepository
import it.droneskycheck.app.data.ai.AiAssistantRequest
import it.droneskycheck.app.data.ai.AiAssistantRepositoryError
import it.droneskycheck.app.data.ai.AiAssistantSource
import it.droneskycheck.app.data.ai.AiAssistantSourceGroup
import it.droneskycheck.app.data.ai.AiAssistantUnavailableMessage
import it.droneskycheck.app.data.ai.DscAiInstallationIdRepository
import it.droneskycheck.app.data.ai.localAiAssistantResponseFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

@Composable
fun AiAssistantScreen(
    context: AiAssistantContext,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    client: AiAssistantClient? = null
) {
    BackHandler(onBack = onBack)

    val appContext = LocalContext.current.applicationContext
    val assistantClient = client ?: remember(appContext) {
        AiAssistantRepository(
            installationIdProvider = DscAiInstallationIdRepository(appContext)
        )
    }
    var draft by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var quota by remember { mutableStateOf<AiAssistantQuota?>(null) }
    var quotaStatusUnavailable by remember { mutableStateOf(false) }
    var quotaCountdown by remember { mutableStateOf<String?>(null) }
    var nextMessageId by remember { mutableLongStateOf(1L) }
    val messages = remember { mutableStateListOf<AiChatMessage>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val canSendWithQuota = quota?.let { it.unlimited || it.remaining > 0 } != false

    fun addMessage(author: AiChatAuthor, text: String, sources: List<AiAssistantSource> = emptyList()) {
        messages += AiChatMessage(
            id = nextMessageId++,
            author = author,
            text = text,
            sources = sources
        )
    }

    fun submitQuestion() {
        val query = draft.trim()
        if (query.isBlank() || isLoading) return
        if (!canSendWithQuota) {
            addMessage(AiChatAuthor.Assistant, exhaustedQuotaMessage(quota))
            return
        }

        draft = ""
        addMessage(AiChatAuthor.User, query)
        localAiAssistantResponseFor(query, context)?.let { response ->
            DscLogger.debug(
                AiAssistantUiLogTag,
                "UI append start kind=${response.kind} mappedTextSource=${response.mappedTextSource} " +
                    "textLength=${response.displayText.length} sources=${response.sources.size}"
            )
            addMessage(
                author = AiChatAuthor.Assistant,
                text = response.displayText,
                sources = response.sources
            )
            DscLogger.debug(
                AiAssistantUiLogTag,
                "UI state updated messages=${messages.size} loading=false error=false"
            )
            return
        }
        isLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                assistantClient.answer(
                    AiAssistantRequest(
                        query = query,
                        includeSources = true,
                        includeDiagnostics = false,
                        context = context
                    )
                )
            }
            result.fold(
                onSuccess = { response ->
                    response.quota?.let { quota = it }
                    DscLogger.debug(
                        AiAssistantUiLogTag,
                        "UI append start kind=${response.kind} mappedTextSource=${response.mappedTextSource} " +
                            "textLength=${response.displayText.length} sources=${response.sources.size}"
                    )
                    addMessage(
                        author = AiChatAuthor.Assistant,
                        text = response.displayText,
                        sources = response.sources
                    )
                },
                onFailure = { error ->
                    if (error is AiAssistantRepositoryError.QuotaExhausted) {
                        quota = error.quota
                    }
                    DscLogger.debug(
                        AiAssistantUiLogTag,
                        "UI append start kind=Error mappedTextSource=fallback textLength=${AiAssistantUnavailableMessage.length} sources=0"
                    )
                    addMessage(
                        AiChatAuthor.Assistant,
                        if (error is AiAssistantRepositoryError.QuotaExhausted) {
                            exhaustedQuotaMessage(error.quota)
                        } else {
                            AiAssistantUnavailableMessage
                        }
                    )
                }
            )
            isLoading = false
            DscLogger.debug(
                AiAssistantUiLogTag,
                "UI state updated messages=${messages.size} loading=false error=${result.isFailure}"
            )
        }
    }

    LaunchedEffect(messages.size) {
        DscLogger.debug(AiAssistantUiLogTag, "AiAssistantScreen messages=${messages.size}")
    }

    LaunchedEffect(assistantClient) {
        val result = withContext(Dispatchers.IO) {
            assistantClient.quota()
        }
        result.fold(
            onSuccess = {
                quota = it
                quotaStatusUnavailable = false
            },
            onFailure = {
                quotaStatusUnavailable = true
            }
        )
    }

    LaunchedEffect(quota?.nextCreditAt, quota?.remaining, quota?.unlimited) {
        quotaCountdown = quota?.nextCreditAt?.toQuotaCountdown()
        while (quota?.unlimited == false && quota?.nextCreditAt != null) {
            delay(1_000)
            val next = quota?.nextCreditAt
            quotaCountdown = next?.toQuotaCountdown()
            if (quotaCountdown == null) {
                quota = quota?.advanceAfterCountdown()
            }
        }
    }

    LaunchedEffect(messages.size, isLoading) {
        val lastIndex = messages.lastIndex
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
        ) {
            AiAssistantTopBar(
                onBack = onBack,
                quota = quota,
                quotaCountdown = quotaCountdown,
                quotaStatusUnavailable = quotaStatusUnavailable
            )
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AiAssistantIntro()
                }
                items(messages, key = { it.id }) { message ->
                    AiMessageBubble(message = message)
                }
                if (isLoading) {
                    item {
                        AiLoadingBubble()
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    placeholder = { Text("Scrivi una domanda...") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitQuestion() })
                )
                Button(
                    onClick = { submitQuestion() },
                    enabled = draft.isNotBlank() && !isLoading && canSendWithQuota,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Invia"
                    )
                }
            }
        }
    }
}

@Composable
private fun AiAssistantTopBar(
    onBack: () -> Unit,
    quota: AiAssistantQuota?,
    quotaCountdown: String?,
    quotaStatusUnavailable: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Indietro"
            )
        }
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 4.dp, end = 10.dp)
                .size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Assistente DSC",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    modifier = Modifier.padding(start = 8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = "Beta",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            AiQuotaIndicator(
                quota = quota,
                quotaCountdown = quotaCountdown,
                quotaStatusUnavailable = quotaStatusUnavailable
            )
        }
    }
}

@Composable
private fun AiQuotaIndicator(
    quota: AiAssistantQuota?,
    quotaCountdown: String?,
    quotaStatusUnavailable: Boolean
) {
    val text = when {
        quota == null && quotaStatusUnavailable -> "Crediti AI non disponibili"
        quota == null -> "Quote AI in verifica"
        quota.unlimited -> "Modalità test · utilizzo AI illimitato"
        quota.remaining <= 0 && quotaCountdown != null -> "Crediti 0/${quota.capacity} · prossimo tra $quotaCountdown"
        else -> "Crediti ${quota.remaining}/${quota.capacity}"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun AiAssistantIntro() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Assistente DSC",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Chiedimi informazioni sulle regole di volo, sull'uso di Drone Sky Check o sul punto selezionato sulla mappa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Funzione sperimentale: verifica sempre le fonti ufficiali prima di volare.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "L'Assistente DSC utilizza servizi di intelligenza artificiale. Per mantenere il servizio disponibile a tutti, il numero di richieste è limitato e si rigenera automaticamente nel tempo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AiMessageBubble(message: AiChatMessage) {
    val isUser = message.author == AiChatAuthor.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.96f),
            shape = RoundedCornerShape(
                topStart = 8.dp,
                topEnd = 8.dp,
                bottomStart = if (isUser) 8.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 8.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isUser) "Tu" else "Assistente DSC",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                SelectionContainer {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (!isUser && message.sources.isNotEmpty()) {
                    AiSourcesSection(sources = message.sources)
                }
            }
        }
    }
}

@Composable
private fun AiLoadingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Sto verificando...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AiSourcesSection(sources: List<AiAssistantSource>) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 2.dp)
        ) {
            Text("Fonti")
            Spacer(modifier = Modifier.size(6.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Chiudi fonti" else "Apri fonti"
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sources.forEach { source ->
                    Text(
                        text = source.toDisplayText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun AiAssistantSource.toDisplayText(): String =
    listOfNotNull(
        group?.toDisplayLabel()?.let { "$it:" },
        title,
        authority,
        document,
        section?.let { "Sezione $it" },
        page?.let { "pag. $it" }
    ).distinct().joinToString(" - ")

private fun AiAssistantSourceGroup.toDisplayLabel(): String =
    when (this) {
        AiAssistantSourceGroup.Regulatory -> "Normativa"
        AiAssistantSourceGroup.Product -> "Drone Sky Check"
    }

private fun exhaustedQuotaMessage(quota: AiAssistantQuota?): String {
    val countdown = quota?.nextCreditAt?.toQuotaCountdown()
    return if (countdown != null) {
        "Hai esaurito i crediti dell'Assistente DSC. Potrai inviare una nuova domanda tra $countdown."
    } else {
        "Hai esaurito i crediti dell'Assistente DSC. Riprova tra qualche minuto."
    }
}

private fun String.toQuotaCountdown(): String? {
    val seconds = runCatching {
        Duration.between(Instant.now(), Instant.parse(this)).seconds
    }.getOrNull() ?: return null
    if (seconds <= 0) return null
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) {
        "${minutes}m ${remainingSeconds}s"
    } else {
        "${remainingSeconds}s"
    }
}

private fun AiAssistantQuota.advanceAfterCountdown(): AiAssistantQuota {
    if (unlimited || remaining >= capacity) return this
    val nextRemaining = (remaining + 1).coerceAtMost(capacity)
    val secondsToRefill = refillSeconds
    val currentNextCreditAt = nextCreditAt
    val nextCredit = if (nextRemaining < capacity && secondsToRefill != null && currentNextCreditAt != null) {
        runCatching {
            Instant.parse(currentNextCreditAt).plusSeconds(secondsToRefill.toLong()).toString()
        }.getOrNull()
    } else {
        null
    }
    return copy(remaining = nextRemaining, nextCreditAt = nextCredit)
}

private data class AiChatMessage(
    val id: Long,
    val author: AiChatAuthor,
    val text: String,
    val sources: List<AiAssistantSource> = emptyList()
)

private enum class AiChatAuthor {
    User,
    Assistant
}

private const val AiAssistantUiLogTag = "DscAiAssistant"
