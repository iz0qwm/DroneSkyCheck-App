package it.droneskycheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import it.droneskycheck.app.data.MapPreferencesRepository
import it.droneskycheck.app.ui.startup.AppRoot
import it.droneskycheck.app.ui.theme.DroneSkyCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val mapPreferences = MapPreferencesRepository(applicationContext)
        setContent {
            var appThemeMode by remember { mutableStateOf(mapPreferences.getAppThemeMode()) }
            DroneSkyCheckTheme(appThemeMode = appThemeMode) {
                AppRoot(
                    appThemeMode = appThemeMode,
                    onAppThemeModeChanged = { mode ->
                        mapPreferences.setAppThemeMode(mode)
                        appThemeMode = mode
                    }
                )
            }
        }
    }
}
