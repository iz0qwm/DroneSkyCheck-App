package it.droneskycheck.app.ui.startup

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import it.droneskycheck.app.data.LocalAuthorizationRepository
import it.droneskycheck.app.ui.map.MapScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre

sealed interface StartupState {
    data object Initializing : StartupState
    data object LoadingMap : StartupState
    data object LoadingLocalData : StartupState
    data object Ready : StartupState
    data class Error(val message: String) : StartupState
}

@Composable
fun AppRoot() {
    val context = LocalContext.current.applicationContext
    var startupState by remember { mutableStateOf<StartupState>(StartupState.Initializing) }
    var retryToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(context, retryToken) {
        val startedAt = SystemClock.elapsedRealtime()
        val result = runStartupInitialization(context) { phase ->
            startupState = phase
        }
        if (result is StartupState.Ready) {
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            val remainingMs = MinimumStartupScreenMillis - elapsedMs
            if (remainingMs > 0) delay(remainingMs)
        }
        startupState = result
    }

    when (val state = startupState) {
        StartupState.Ready -> MapScreen()
        else -> StartupScreen(
            state = state,
            onRetry = { retryToken++ }
        )
    }
}

private const val MinimumStartupScreenMillis = 900L

private suspend fun runStartupInitialization(
    context: Context,
    onPhaseChanged: (StartupState) -> Unit
): StartupState =
    runCatching {
        onPhaseChanged(StartupState.LoadingMap)
        MapLibre.getInstance(context)

        onPhaseChanged(StartupState.LoadingLocalData)
        withContext(Dispatchers.IO) {
            LocalAuthorizationRepository(context).getActiveDraft()
        }
    }.fold(
        onSuccess = { StartupState.Ready },
        onFailure = { error ->
            StartupState.Error(error.message ?: "Impossibile inizializzare l'app")
        }
    )
