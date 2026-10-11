package net.sagberg.kartoffel.prototype

// PROTOTYPE (throwaway, issue #34), round 2: the production Map (default Google Maps
// styling, production fog) with a floating top bar and a prominent Record button. The yellow
// switcher cycles three independent axes, also settable from a launch intent:
//   adb shell am start -n net.sagberg.kartoffel/.prototype.PrototypeVisualDirectionActivity \
//     --es outline 2 --es font schibsted --ez dark true
// outline: 0-4 or none|teal|graphite|halo|glow; font: 0-3 or roboto|schibsted|familjen|atkinson.
// Controls are no-ops. Coverage is synthetic central Oslo; nothing is read or persisted.

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import net.sagberg.kartoffel.R
import net.sagberg.kartoffel.ui.theme.KartoffelTheme

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int) = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

private enum class PrototypeFont(val key: String, val title: String, val family: FontFamily) {
    Roboto("roboto", "Roboto", FontFamily.Default),
    Schibsted("schibsted", "Schibsted", variableFamily(R.font.prototype_schibsted_grotesk)),
    Familjen("familjen", "Familjen", variableFamily(R.font.prototype_familjen_grotesk)),
    Atkinson("atkinson", "Atkinson", variableFamily(R.font.prototype_atkinson_hyperlegible_next)),
}

private fun <T : Enum<T>> List<T>.pick(value: String?, key: (T) -> String): T? =
    value?.lowercase()?.let { v -> v.toIntOrNull()?.let(::getOrNull) ?: firstOrNull { key(it) == v } }

private fun <T : Enum<T>> List<T>.after(current: T): T = this[(current.ordinal + 1) % size]

// Keeps the design's controls clear of the prototype switcher.
private val SwitcherClearance = 64.dp

class PrototypeVisualDirectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestedOutline = Outline.entries.pick(intent.getStringExtra("outline")) { it.key }
        val requestedFont = PrototypeFont.entries.pick(intent.getStringExtra("font")) { it.key }
        val requestedDark = if (intent.hasExtra("dark")) intent.getBooleanExtra("dark", false) else null
        setContent {
            val systemDark = isSystemInDarkTheme()
            var outline by rememberSaveable { mutableStateOf(requestedOutline ?: Outline.None) }
            var font by rememberSaveable { mutableStateOf(requestedFont ?: PrototypeFont.Roboto) }
            var dark by rememberSaveable { mutableStateOf(requestedDark ?: systemDark) }
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(TRANSPARENT) else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            val camera = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(LatLng(59.9150, 10.7385), 14.6f)
            }
            KartoffelTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize()) {
                    PrototypeMap(camera, outline, dark)
                    FloatingTopBar(font.family, Modifier.align(Alignment.TopCenter))
                    MapControls(font.family, Modifier.align(Alignment.BottomEnd))
                    PrototypeSwitcher(
                        segments = listOf(
                            "outline: ${outline.key}" to { outline = Outline.entries.after(outline) },
                            "font: ${font.title}" to { font = PrototypeFont.entries.after(font) },
                            (if (dark) "dark" else "light") to { dark = !dark },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrototypeMap(camera: CameraPositionState, outline: Outline, dark: Boolean) {
    val provider = remember(outline, dark) { PrototypeFogTileProvider(SyntheticOsloCoverage.snapshot, outline, dark) }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        // Production uses the default style and follows the system theme; this pins it to the toggle.
        mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
        ),
    ) {
        key(outline, dark) { TileOverlay(tileProvider = provider, fadeIn = false, zIndex = 10f) }
        // Stand-in for the platform my-location dot production shows.
        MarkerComposable(
            state = rememberMarkerState(
                position = LatLng(SyntheticOsloCoverage.here.latitude, SyntheticOsloCoverage.here.longitude),
            ),
            anchor = Offset(0.5f, 0.5f),
            zIndex = 30f,
        ) {
            Box(Modifier.size(36.dp).background(Color(0x334285F4), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(18.dp).background(Color.White, CircleShape).padding(3.dp).background(Color(0xFF4285F4), CircleShape))
            }
        }
    }
}

@Composable
private fun FloatingTopBar(family: FontFamily, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(56.dp)
            .shadow(4.dp, CircleShape)
            .background(scheme.surface, CircleShape)
            .padding(start = 24.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Kartoffel",
            style = TextStyle(fontFamily = family, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            color = scheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = {}) {
            Icon(painterResource(R.drawable.ic_more_vert_24), "More options", tint = scheme.onSurfaceVariant)
        }
    }
}

// Variant A's structure: two small buttons above one large, labelled Record button.
@Composable
private fun MapControls(family: FontFamily, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = SwitcherClearance + 8.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallMapButton(R.drawable.prototype_ic_draw_24, "Draw route", scheme.onSurface)
        SmallMapButton(R.drawable.ic_my_location_24, "Center on current location", scheme.primary)
        Spacer(Modifier.height(4.dp))
        RecordButton(family)
    }
}

@Composable
private fun SmallMapButton(icon: Int, description: String, tint: Color) {
    Surface(
        onClick = {},
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), description, Modifier.size(22.dp), tint = tint)
        }
    }
}

@Composable
private fun RecordButton(family: FontFamily) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = {},
        shape = RoundedCornerShape(22.dp),
        color = scheme.primary,
        contentColor = scheme.onPrimary,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.height(64.dp).padding(start = 18.dp, end = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A record glyph: a red dot set in a disc of the label colour.
            Box(Modifier.size(26.dp).background(scheme.onPrimary, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(12.dp).background(Color(0xFFE5484D), CircleShape))
            }
            Spacer(Modifier.width(14.dp))
            Text("Record", style = TextStyle(fontFamily = family, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

// Not part of the design: the prototype switcher. Tap a segment to cycle it.

@Composable
private fun BoxScope.PrototypeSwitcher(segments: List<Pair<String, () -> Unit>>) {
    val text = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
            .shadow(8.dp, CircleShape)
            .background(Color(0xFFFFE600), CircleShape)
            .border(2.dp, Color.Black, CircleShape)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, (label, onClick) ->
            if (index > 0) Box(Modifier.width(2.dp).height(44.dp).background(Color.Black))
            Text(label, style = text, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp))
        }
    }
}
