package net.sagberg.kartoffel

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.ui.RecordingBorder
import net.sagberg.kartoffel.settings.CoverageOutlineStyle
import net.sagberg.kartoffel.diagnostics.TrackingDiagnosticsRoute
import net.sagberg.kartoffel.map.MapScreen
import net.sagberg.kartoffel.map.MapMenuActions
import net.sagberg.kartoffel.settings.AppearanceDialog
import net.sagberg.kartoffel.settings.AppearancePreferences
import net.sagberg.kartoffel.settings.CoverageSettingsRoute
import net.sagberg.kartoffel.settings.ThemeMode
import net.sagberg.kartoffel.ui.theme.KartoffelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KartoffelApp()
        }
    }
}

internal enum class KartoffelDestination { MAP, SETTINGS, TRACKING_DIAGNOSTICS }

@Composable
internal fun KartoffelApp() {
    var destination by rememberSaveable { mutableStateOf(KartoffelDestination.MAP) }
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    val settingsPersistenceScope = rememberCoroutineScope()
    val context = LocalContext.current
    val appearancePreferences = remember(context) { AppearancePreferences(context) }
    var appearance by remember(appearancePreferences) {
        mutableStateOf(appearancePreferences.load())
    }
    val darkTheme = when (appearance.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    SystemBarsFollowTheme(darkTheme)

    KartoffelTheme(darkTheme = darkTheme) {
        Box(Modifier.fillMaxSize()) {
            when (destination) {
                KartoffelDestination.MAP -> MapScreen(
                    coverageOutline = appearance.coverageOutline,
                    fogDetail = appearance.fogDetail,
                    menuActions = MapMenuActions(
                        onOpenSettings = { destination = KartoffelDestination.SETTINGS },
                        onOpenAppearance = { appearanceOpen = true },
                        onOpenTrackingDiagnostics = {
                            destination = KartoffelDestination.TRACKING_DIAGNOSTICS
                        },
                    ),
                )
                KartoffelDestination.SETTINGS -> {
                    BackHandler { destination = KartoffelDestination.MAP }
                    CoverageSettingsRoute(
                        persistenceScope = settingsPersistenceScope,
                        onBack = { destination = KartoffelDestination.MAP },
                    )
                }
                KartoffelDestination.TRACKING_DIAGNOSTICS -> {
                    BackHandler { destination = KartoffelDestination.MAP }
                    TrackingDiagnosticsRoute(onBack = { destination = KartoffelDestination.MAP })
                }
            }
            if (destination != KartoffelDestination.MAP) {
                SecondaryScreenRecordingBorder(appearance.coverageOutline)
            }
        }
        if (appearanceOpen) {
            AppearanceDialog(
                appearance = appearance,
                onAppearanceChange = {
                    appearance = it
                    appearancePreferences.save(it)
                },
                onDismiss = { appearanceOpen = false },
            )
        }
    }
}

/** Keeps status and navigation bar icons legible when the theme differs from the system's. */
@Composable
private fun SystemBarsFollowTheme(darkTheme: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    SideEffect {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

/** Only secondary screens observe here; the Map owns its subscription and restore lifecycle. */
@Composable
private fun SecondaryScreenRecordingBorder(coverageOutline: CoverageOutlineStyle) {
    val context = LocalContext.current
    val database = remember(context) { KartoffelDatabase.open(context) }
    val activeRecording by database.recordings().observeActive().collectAsState(initial = null)
    RecordingBorder(activeRecording != null, coverageOutline)
}
