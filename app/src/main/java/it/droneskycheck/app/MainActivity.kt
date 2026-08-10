package it.droneskycheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import it.droneskycheck.app.ui.startup.AppRoot
import it.droneskycheck.app.ui.theme.DroneSkyCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DroneSkyCheckTheme {
                AppRoot()
            }
        }
    }
}
