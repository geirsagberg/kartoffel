package net.sagberg.kartoffel.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberTileOverlayState
import kotlinx.coroutines.launch
import net.sagberg.kartoffel.R
import net.sagberg.kartoffel.coverage.CoverageSnapshot
import net.sagberg.kartoffel.coverage.ForegroundCoverageRecorder
import net.sagberg.kartoffel.coverage.ForegroundLocationFix
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.PersistedCoverageLoader
import net.sagberg.kartoffel.storage.KartoffelDatabase

@Composable
@SuppressLint("MissingPermission")
internal fun CoverageMapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }
    val database = remember(context) { KartoffelDatabase.open(context) }
    val coverageRecorder = remember(database) { ForegroundCoverageRecorder(database) }
    val persistedCoverage = remember(database) {
        PersistedCoverageLoader(database.coverageCells())
    }
    var hasLocationPermission by remember {
        mutableStateOf(context.hasForegroundLocationPermission())
    }
    var firstFix by remember { mutableStateOf<MapCoordinate?>(null) }
    var acceptedForegroundFix by remember { mutableStateOf(false) }
    var recordingForegroundFix by remember { mutableStateOf(false) }
    var centeredOnFirstFix by rememberSaveable { mutableStateOf(false) }
    var coverageSnapshot by remember { mutableStateOf(CoverageSnapshot.Empty) }

    DisposableEffect(database) {
        onDispose { database.close() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasLocationPermission = granted
    }

    val fallbackCameraTarget = remember { LatLng(59.9139, 10.7522) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(fallbackCameraTarget, 12f)
    }
    val fogTileOverlayState = rememberTileOverlayState()
    val fogTileProvider = remember(coverageSnapshot.revision) {
        FogOfWarTileProvider(coverageSnapshot)
    }
    var hasObservedInitialFogRevision by remember { mutableStateOf(false) }

    LaunchedEffect(persistedCoverage) {
        coverageSnapshot = persistedCoverage.load(revision = coverageSnapshot.revision + 1)
    }

    LaunchedEffect(coverageSnapshot.revision) {
        if (hasObservedInitialFogRevision) {
            fogTileOverlayState.clearTileCache()
        }
        hasObservedInitialFogRevision = true
    }

    fun moveToCurrentLocation(zoom: Float) {
        if (!hasLocationPermission) return

        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient
            .getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token,
            )
            .addOnSuccessListener { location ->
                val target = location?.let {
                    LatLng(it.latitude, it.longitude)
                }
                    ?: return@addOnSuccessListener

                scope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(target, zoom),
                    )
                }
            }
    }

    DisposableEffect(hasLocationPermission, acceptedForegroundFix, context, fusedLocationClient) {
        if (!shouldRequestForegroundLocation(hasLocationPermission, acceptedForegroundFix)) {
            onDispose {}
        } else {
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                        ?: locationResult.locations.firstOrNull()
                        ?: return
                    if (recordingForegroundFix) return

                    if (firstFix == null) {
                        firstFix = MapCoordinate(location.latitude, location.longitude)
                    }
                    val callback = this
                    recordingForegroundFix = true
                    scope.launch {
                        val decision = coverageRecorder.record(
                            ForegroundLocationFix(
                                coordinate = GeoCoordinate(location.latitude, location.longitude),
                                capturedAtMillis = location.time,
                                accuracyMeters = if (location.hasAccuracy()) {
                                    location.accuracy.toDouble()
                                } else {
                                    Double.MAX_VALUE
                                },
                            ),
                        )
                        if (decision.accepted) {
                            acceptedForegroundFix = true
                            coverageSnapshot = persistedCoverage.load(
                                revision = coverageSnapshot.revision + 1,
                            )
                            fusedLocationClient.removeLocationUpdates(callback)
                        }
                        recordingForegroundFix = false
                    }
                }
            }
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1_000L,
            )
                .setMinUpdateIntervalMillis(500L)
                .setMaxUpdateAgeMillis(0L)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                context.mainLooper,
            )

            onDispose {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
    }

    LaunchedEffect(firstFix) {
        val request = firstFixCameraRequest(firstFix, centeredOnFirstFix)
            ?: return@LaunchedEffect

        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(request.target.latitude, request.target.longitude),
                request.zoom,
            )
        )
        centeredOnFirstFix = true
    }

    CoverageMapContent(
        hasLocationPermission = hasLocationPermission,
        onRequestLocationPermission = {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onCenterCurrentLocation = { moveToCurrentLocation(CURRENT_LOCATION_ZOOM) },
        map = {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission,
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false,
                )
            ) {
                TileOverlay(
                    tileProvider = fogTileProvider,
                    state = fogTileOverlayState,
                    fadeIn = false,
                    transparency = 0f,
                    visible = true,
                    zIndex = 10f,
                )
            }
        },
    )
}

@Composable
internal fun CoverageMapContent(
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onCenterCurrentLocation: () -> Unit,
    map: @Composable BoxScope.() -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CoverageMapTopAppBar()
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag("coverage_map_screen"),
        ) {
            map()

            if (hasLocationPermission) {
                FloatingActionButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = 24.dp)
                        .size(48.dp),
                    onClick = onCenterCurrentLocation,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 8.dp,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_my_location_24),
                        contentDescription = "Center on current location",
                    )
                }
            }

            if (!hasLocationPermission) {
                Button(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(24.dp),
                    onClick = onRequestLocationPermission,
                ) {
                    Text("Enable location")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverageMapTopAppBar() {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Text("Kartoffel")
                Text(
                    modifier = Modifier.testTag("passive_tracking_status"),
                    text = "Passive off",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            Button(
                modifier = Modifier.testTag("recording_session_control"),
                onClick = {},
            ) {
                Text("Start")
            }
            IconButton(
                modifier = Modifier.testTag("settings_diagnostics_menu"),
                onClick = { menuExpanded = true },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert_24),
                    contentDescription = "More options",
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = { menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text("Tracking diagnostics") },
                    onClick = { menuExpanded = false },
                )
            }
        },
    )
}

private fun Context.hasForegroundLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
