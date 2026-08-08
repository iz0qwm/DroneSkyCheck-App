package it.droneskycheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.droneskycheck.app.ui.map.MapScreen
import it.droneskycheck.app.ui.theme.DroneSkyCheckTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        setContent {
            DroneSkyCheckTheme {
                MapScreen()
            }
        }
    }
}
