package it.droneskycheck.app.ui.startup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.droneskycheck.app.R

private val StartupBackground = Color(0xFF01111F)
private val StartupContent = Color(0xFFFFFFFF)
private val StartupMutedContent = Color(0xFFC4D7E8)

@Composable
fun StartupScreen(
    state: StartupState,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StartupBackground)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_largo_no_sfondo),
                contentDescription = "Drone Sky Check",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = state.statusText(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state is StartupState.Error) StartupContent else StartupMutedContent,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (state is StartupState.Error) {
                Button(onClick = onRetry) {
                    Text("Riprova")
                }
            } else {
                CircularProgressIndicator(
                    color = StartupMutedContent,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

private fun StartupState.statusText(): String =
    when (this) {
        StartupState.Initializing,
        StartupState.LoadingMap,
        StartupState.LoadingLocalData,
        StartupState.Ready -> "Preparazione della mappa\u2026"
        is StartupState.Error -> "Impossibile inizializzare l'app"
    }
