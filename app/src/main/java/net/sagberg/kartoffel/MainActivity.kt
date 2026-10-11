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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import net.sagberg.kartoffel.diagnostics.TrackingDiagnosticsRoute
import net.sagberg.kartoffel.map.MapScreen
import net.sagberg.kartoffel.settings.AppearanceDialog
import net.sagberg.kartoffel.settings.AppearancePreferences
import net.sagberg.kartoffel.settings.CoverageSettingsRoute
import net.sagberg.kartoffel.settings.ThemeMode
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
        when (destination) {
            KartoffelDestination.MAP -> MapScreen(
                coverageOutline = appearance.coverageOutline,
                darkTheme = darkTheme,
                onOpenSettings = { destination = KartoffelDestination.SETTINGS },
                onOpenAppearance = { appearanceOpen = true },
                onOpenTrackingDiagnostics = {
                    destination = KartoffelDestination.TRACKING_DIAGNOSTICS
                },
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
    DisposableEffect(activity, darkTheme) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
}
