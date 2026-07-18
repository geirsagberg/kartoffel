package net.sagberg.kartoffel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import net.sagberg.kartoffel.diagnostics.TrackingDiagnosticsRoute
import net.sagberg.kartoffel.map.CoverageMapScreen
import net.sagberg.kartoffel.ui.theme.KartoffelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(0, 0),
            navigationBarStyle = SystemBarStyle.auto(0, 0),
        )
        setContent {
            KartoffelApp()
        }
    }
}

internal enum class KartoffelDestination { MAP, TRACKING_DIAGNOSTICS }

@Composable
internal fun KartoffelApp() {
    var destination by rememberSaveable { mutableStateOf(KartoffelDestination.MAP) }
    KartoffelTheme {
        when (destination) {
            KartoffelDestination.MAP -> CoverageMapScreen(
                onOpenTrackingDiagnostics = {
                    destination = KartoffelDestination.TRACKING_DIAGNOSTICS
                },
            )
            KartoffelDestination.TRACKING_DIAGNOSTICS -> {
                BackHandler { destination = KartoffelDestination.MAP }
                TrackingDiagnosticsRoute(onBack = { destination = KartoffelDestination.MAP })
            }
        }
    }
}
