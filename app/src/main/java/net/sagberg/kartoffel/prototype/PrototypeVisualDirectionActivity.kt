package net.sagberg.kartoffel.prototype

// PROTOTYPE (throwaway, issue #34): three map-first visual directions for the Coverage Map,
// switchable with the yellow bar or:
//   adb shell am start -n net.sagberg.kartoffel/.prototype.PrototypeVisualDirectionActivity \
//     --es variant B --ez dark true
// Controls are no-ops. Coverage is synthetic central Oslo; nothing is read or persisted.

import android.graphics.Color.TRANSPARENT
import android.graphics.Color.argb
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import net.sagberg.kartoffel.R

private enum class Variant(val key: String, val title: String) {
    A("A", "Harbour"),
    B("B", "Survey"),
    C("C", "Lantern"),
}

// Keeps variant controls clear of the prototype switcher.
private val SwitcherClearance = 64.dp

class PrototypeVisualDirectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestedVariant = intent.getStringExtra("variant")?.uppercase()
        val requestedDark = if (intent.hasExtra("dark")) intent.getBooleanExtra("dark", false) else null
        setContent {
            val systemDark = isSystemInDarkTheme()
            var variant by rememberSaveable {
                mutableStateOf(Variant.entries.firstOrNull { it.key == requestedVariant } ?: Variant.A)
            }
            var dark by rememberSaveable { mutableStateOf(requestedDark ?: systemDark) }
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(TRANSPARENT) else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            val camera = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(LatLng(59.9150, 10.7385), 14.6f)
            }
            Box(Modifier.fillMaxSize()) {
                when (variant) {
                    Variant.A -> Harbour(dark, camera)
                    Variant.B -> Survey(dark, camera)
                    Variant.C -> Lantern(dark, camera)
                }
                PrototypeSwitcher(
                    label = "${variant.key} ${variant.title}",
                    dark = dark,
                    onPrevious = { variant = Variant.entries[(variant.ordinal + 2) % 3] },
                    onNext = { variant = Variant.entries[(variant.ordinal + 1) % 3] },
                    onToggleDark = { dark = !dark },
                )
            }
        }
    }
}

@Composable
private fun PrototypeMap(
    camera: CameraPositionState,
    mapStyle: String,
    fog: PrototypeFogStyle,
    land: Color,
    accent: Color,
    here: @Composable () -> Unit,
) {
    val provider = remember(fog) { PrototypeFogTileProvider(SyntheticOsloCoverage.snapshot, fog) }
    val context = LocalContext.current
    val hasMapsKey = remember {
        context.packageManager
            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY").isNullOrBlank().not()
    }
    if (!hasMapsKey) {
        StandInMap(provider, land, accent)
        return
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        properties = MapProperties(mapStyleOptions = MapStyleOptions(mapStyle)),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
        ),
    ) {
        key(fog) { TileOverlay(tileProvider = provider, fadeIn = false, zIndex = 10f) }
        MarkerComposable(
            keys = arrayOf(fog),
            state = rememberMarkerState(
                position = LatLng(SyntheticOsloCoverage.here.latitude, SyntheticOsloCoverage.here.longitude),
            ),
            anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
            zIndex = 30f,
        ) { here() }
    }
}

// Without a Maps API key Google Maps renders nothing, not even tile overlays. This draws the
// same fog tiles on a flat land colour (no streets) so fog treatment can still be judged.
@Composable
private fun StandInMap(provider: PrototypeFogTileProvider, land: Color, accent: Color) {
    val zoom = 14
    val scale = 1 shl zoom
    fun world(lat: Double, lng: Double): Offset {
        val s = sin(lat * PI / 180)
        return Offset(
            ((lng + 180) / 360 * scale).toFloat(),
            ((0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * scale).toFloat(),
        )
    }
    val centre = world(59.9150, 10.7385)
    val tiles = remember(provider) {
        (-2..2).flatMap { dx ->
            (-4..4).map { dy ->
                val tx = centre.x.toInt() + dx
                val ty = centre.y.toInt() + dy
                val bytes = provider.getTile(tx, ty, zoom).data!!
                Triple(tx, ty, BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap())
            }
        }
    }
    Canvas(Modifier.fillMaxSize().background(land)) {
        val tilePx = 256.dp.toPx()
        fun screen(p: Offset) = Offset(
            size.width / 2 + (p.x - centre.x) * tilePx,
            size.height / 2 + (p.y - centre.y) * tilePx,
        )
        tiles.forEach { (tx, ty, image) ->
            val o = screen(Offset(tx.toFloat(), ty.toFloat()))
            drawImage(
                image,
                dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
                dstSize = IntSize(tilePx.roundToInt(), tilePx.roundToInt()),
            )
        }
        val here = screen(world(SyntheticOsloCoverage.here.latitude, SyntheticOsloCoverage.here.longitude))
        drawCircle(accent.copy(alpha = 0.25f), 18.dp.toPx(), here)
        drawCircle(Color.White, 8.dp.toPx(), here)
        drawCircle(accent, 5.dp.toPx(), here)
    }
}

@Composable
private fun HereDot(fill: Color, ring: Color, halo: Color) {
    Box(Modifier.size(36.dp).background(halo, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(16.dp).background(ring, CircleShape).padding(3.dp).background(fill, CircleShape))
    }
}

// A · Harbour: today's structure, refined. Material top bar, default map, flat slate fog.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Harbour(dark: Boolean, camera: CameraPositionState) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF7DD3B8), onPrimary = Color(0xFF00382B),
            surface = Color(0xFF161B1E), onSurface = Color(0xFFE1E5E3),
            surfaceContainerHigh = Color(0xFF242A2D), onSurfaceVariant = Color(0xFFA9B3AF),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1F6F5C), onPrimary = Color.White,
            surface = Color.White, onSurface = Color(0xFF1A1F1D),
            surfaceContainerHigh = Color.White, onSurfaceVariant = Color(0xFF55605B),
        )
    }
    val fog = if (dark) {
        PrototypeFogStyle(fog = argb(165, 0, 0, 0))
    } else {
        PrototypeFogStyle(fog = argb(130, 38, 50, 56))
    }
    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text("Kartoffel", fontWeight = FontWeight.Medium) },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(painterResource(R.drawable.ic_more_vert_24), "More options")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                PrototypeMap(
                    camera = camera,
                    mapStyle = if (dark) PrototypeMapStyles.harbourDark else PrototypeMapStyles.harbourLight,
                    fog = fog,
                    land = if (dark) Color(0xFF1D2329) else Color(0xFFF1EFEA),
                    accent = scheme.primary,
                ) { HereDot(scheme.primary, Color.White, scheme.primary.copy(alpha = 0.22f)) }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = SwitcherClearance),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = {},
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                    ) { Icon(painterResource(R.drawable.prototype_ic_draw_24), "Draw route", Modifier.size(20.dp)) }
                    SmallFloatingActionButton(
                        onClick = {},
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.primary,
                    ) { Icon(painterResource(R.drawable.ic_my_location_24), "Center on current location") }
                    ExtendedFloatingActionButton(
                        onClick = {},
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                        icon = { Icon(painterResource(R.drawable.ic_record_24), null) },
                        text = { Text("Record") },
                    )
                }
            }
        }
    }
}

// B · Survey: floating serif pill, monochrome map, dense pale fog with an ink coastline.

@Composable
private fun Survey(dark: Boolean, camera: CameraPositionState) {
    val ink = if (dark) Color(0xFF9DB8E0) else Color(0xFF2B4C7E)
    val onInk = if (dark) Color(0xFF0E1A2C) else Color.White
    val surface = if (dark) Color(0xFF1E2125) else Color.White
    val onSurface = if (dark) Color(0xFFE4E6E9) else Color(0xFF1C1F23)
    val muted = if (dark) Color(0xFF9AA1A8) else Color(0xFF5E656C)
    val record = if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
    val fog = if (dark) {
        PrototypeFogStyle(fog = argb(225, 21, 23, 26), edgeColor = 0xFF9DB8E0.toInt(), edgeWidthPx = 2.5f)
    } else {
        PrototypeFogStyle(fog = argb(222, 233, 235, 238), edgeColor = 0xFF2B4C7E.toInt(), edgeWidthPx = 2.5f)
    }
    Box(Modifier.fillMaxSize()) {
        PrototypeMap(
            camera = camera,
            mapStyle = if (dark) PrototypeMapStyles.surveyDark else PrototypeMapStyles.surveyLight,
            fog = fog,
            land = if (dark) Color(0xFF202326) else Color(0xFFF3F4F5),
            accent = ink,
        ) { HereDot(ink, surface, ink.copy(alpha = 0.18f)) }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(52.dp)
                .shadow(6.dp, RoundedCornerShape(26.dp))
                .background(surface, RoundedCornerShape(26.dp))
                .padding(start = 22.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Kartoffel",
                style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
                color = onSurface,
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable {}.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).background(record, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("Record", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = muted)
            }
            IconButton(onClick = {}) {
                Icon(painterResource(R.drawable.ic_more_vert_24), "More options", tint = muted)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = SwitcherClearance),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = {},
                shape = RoundedCornerShape(16.dp),
                containerColor = ink,
                contentColor = onInk,
                icon = { Icon(painterResource(R.drawable.prototype_ic_draw_24), null, Modifier.size(20.dp)) },
                text = { Text("Draw route") },
            )
            SmallFloatingActionButton(
                onClick = {},
                shape = RoundedCornerShape(12.dp),
                containerColor = surface,
                contentColor = ink,
                elevation = FloatingActionButtonDefaults.elevation(2.dp, 4.dp),
            ) { Icon(painterResource(R.drawable.ic_my_location_24), "Center on current location") }
        }
    }
}

// C · Lantern: no chrome. Violet feathered fog, potato-gold glow, one bottom dock.

private val Condensed = FontFamily(Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), 600, false))

@Composable
private fun Lantern(dark: Boolean, camera: CameraPositionState) {
    val gold = Color(0xFFE8B84A)
    val onGold = Color(0xFF2A1E00)
    val dock = if (dark) Color(0xFF221C2E) else Color.White
    val icon = if (dark) Color(0xFFD6D0E4) else Color(0xFF3C3550)
    val fog = if (dark) {
        PrototypeFogStyle(fog = argb(205, 14, 10, 24), featherPx = 10f, revealTint = argb(70, 232, 184, 74))
    } else {
        PrototypeFogStyle(fog = argb(150, 74, 62, 110), featherPx = 10f, revealTint = argb(40, 232, 184, 74))
    }
    Box(Modifier.fillMaxSize()) {
        PrototypeMap(
            camera = camera,
            mapStyle = if (dark) PrototypeMapStyles.lanternDark else PrototypeMapStyles.lanternLight,
            fog = fog,
            land = if (dark) Color(0xFF17141F) else Color(0xFFECEAF0),
            accent = gold,
        ) { HereDot(gold, onGold, gold.copy(alpha = 0.28f)) }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = SwitcherClearance)
                .shadow(10.dp, RoundedCornerShape(32.dp))
                .background(dock, RoundedCornerShape(32.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {}) {
                Icon(painterResource(R.drawable.prototype_ic_draw_24), "Draw route", Modifier.size(20.dp), tint = icon)
            }
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .background(gold, CircleShape)
                    .clickable {}
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(onGold, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text("Record", style = TextStyle(fontFamily = Condensed, fontSize = 17.sp, letterSpacing = 0.3.sp), color = onGold)
            }
            IconButton(onClick = {}) {
                Icon(painterResource(R.drawable.ic_my_location_24), "Center on current location", tint = icon)
            }
            IconButton(onClick = {}) {
                Icon(painterResource(R.drawable.ic_more_vert_24), "More options", tint = icon)
            }
        }
    }
}

// Not part of any design: the prototype switcher.

@Composable
private fun BoxScope.PrototypeSwitcher(
    label: String,
    dark: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleDark: () -> Unit,
) {
    val text = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
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
        Text("◀", style = text, modifier = Modifier.clickable(onClick = onPrevious).padding(horizontal = 16.dp, vertical = 10.dp))
        Text(label, style = text)
        Text("▶", style = text, modifier = Modifier.clickable(onClick = onNext).padding(horizontal = 16.dp, vertical = 10.dp))
        Box(Modifier.width(2.dp).height(44.dp).background(Color.Black))
        Text(
            if (dark) "dark" else "light",
            style = text,
            modifier = Modifier.clickable(onClick = onToggleDark).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
