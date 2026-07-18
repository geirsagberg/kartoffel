package net.sagberg.kartoffel.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import net.sagberg.kartoffel.R
import net.sagberg.kartoffel.coverage.CoverageSnapshot
import net.sagberg.kartoffel.coverage.PersistedCoverageLoader
import net.sagberg.kartoffel.diagnostics.LatestFixDiagnostics
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnostics
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnosticsState
import net.sagberg.kartoffel.diagnostics.LocationUpdateState
import net.sagberg.kartoffel.diagnostics.RequestedIntervalReason
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.tracking.RecordingActivity
import net.sagberg.kartoffel.tracking.RecordingSessionService
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@Composable
@SuppressLint("MissingPermission")
internal fun CoverageMapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }
    val database = remember(context) { KartoffelDatabase.open(context) }
    val persistedCoverage = remember(database) {
        PersistedCoverageLoader(database.coverageCells())
    }
    var hasLocationPermission by remember {
        mutableStateOf(context.hasForegroundLocationPermission())
    }
    var firstFix by remember { mutableStateOf<MapCoordinate?>(null) }
    var isRecordingSession by remember { mutableStateOf(false) }
    var centeredOnFirstFix by rememberSaveable { mutableStateOf(false) }
    val liveTrackingDiagnostics by LiveTrackingDiagnostics.processInstance.state.collectAsState()
    var diagnosticsNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(liveTrackingDiagnostics.trackingActive) {
        if (!liveTrackingDiagnostics.trackingActive) return@LaunchedEffect
        while (true) {
            diagnosticsNowMillis = System.currentTimeMillis()
            delay(1.seconds)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasLocationPermission = granted
    }
    val recordingPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = context.hasForegroundLocationPermission()
        if (hasLocationPermission) {
            RecordingSessionService.start(context)
            isRecordingSession = true
        }
    }

    val fallbackCameraTarget = remember { LatLng(59.9139, 10.7522) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(fallbackCameraTarget, 12f)
    }
    val fogTileOverlayState = rememberTileOverlayState()
    val fogTileProvider = remember { FogOfWarTileProvider(CoverageSnapshot.Empty) }

    LaunchedEffect(database, persistedCoverage, fogTileProvider, fogTileOverlayState) {
        var hasLoadedInitialCoverage = false
        database.coverageCells().observeAll()
            .distinctUntilChangedBy { cells -> cells.map { cell -> cell.cellId } }
            .collect { cells ->
                fogTileProvider.updateCoverage(persistedCoverage.load(cells))
                if (hasLoadedInitialCoverage) {
                    fogTileOverlayState.clearTileCache()
                }
                hasLoadedInitialCoverage = true
            }
    }

    LaunchedEffect(database) {
        database.recordingSessions().observeActive().collect { activeSession ->
            isRecordingSession = activeSession != null
        }
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

    DisposableEffect(hasLocationPermission, firstFix, context, fusedLocationClient) {
        if (!shouldRequestForegroundLocation(hasLocationPermission, firstFix != null)) {
            onDispose {}
        } else {
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                        ?: locationResult.locations.firstOrNull()
                        ?: return
                    firstFix = MapCoordinate(location.latitude, location.longitude)
                    fusedLocationClient.removeLocationUpdates(this)
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
        isRecordingSession = isRecordingSession,
        liveTrackingDiagnostics = liveTrackingDiagnostics,
        diagnosticsNowMillis = diagnosticsNowMillis,
        onRequestLocationPermission = {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onStartRecordingSession = {
            recordingPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
        },
        onStopRecordingSession = {
            RecordingSessionService.stop(context)
            isRecordingSession = false
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
    isRecordingSession: Boolean,
    liveTrackingDiagnostics: LiveTrackingDiagnosticsState = LiveTrackingDiagnosticsState(),
    diagnosticsNowMillis: Long = System.currentTimeMillis(),
    onRequestLocationPermission: () -> Unit,
    onStartRecordingSession: () -> Unit,
    onStopRecordingSession: () -> Unit,
    onCenterCurrentLocation: () -> Unit,
    map: @Composable BoxScope.() -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CoverageMapTopAppBar(
                isRecordingSession = isRecordingSession,
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag("coverage_map_screen"),
        ) {
            map()

            if (liveTrackingDiagnostics.trackingActive) {
                LiveTrackingDiagnosticsPanel(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
                    diagnostics = liveTrackingDiagnostics,
                    nowMillis = diagnosticsNowMillis,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (hasLocationPermission) {
                    FloatingActionButton(
                        modifier = Modifier.size(48.dp),
                        onClick = onCenterCurrentLocation,
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 4.dp,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_my_location_24),
                            contentDescription = "Center on current location",
                        )
                    }
                }

                ExtendedFloatingActionButton(
                    modifier = Modifier.testTag(
                        if (hasLocationPermission) {
                            "recording_session_control"
                        } else {
                            "enable_location_control"
                        },
                    ),
                    onClick = when {
                        !hasLocationPermission -> onRequestLocationPermission
                        isRecordingSession -> onStopRecordingSession
                        else -> onStartRecordingSession
                    },
                    icon = {
                        Icon(
                            modifier = if (hasLocationPermission) {
                                Modifier.testTag(
                                    if (isRecordingSession) {
                                        "stop_recording_icon"
                                    } else {
                                        "start_recording_icon"
                                    },
                                )
                            } else {
                                Modifier
                            },
                            painter = painterResource(
                                when {
                                    !hasLocationPermission -> R.drawable.ic_my_location_24
                                    isRecordingSession -> R.drawable.ic_stop_24
                                    else -> R.drawable.ic_record_24
                                },
                            ),
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(
                            when {
                                !hasLocationPermission -> "Enable location"
                                isRecordingSession -> "Stop recording"
                                else -> "Start recording"
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveTrackingDiagnosticsPanel(
    diagnostics: LiveTrackingDiagnosticsState,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .testTag("live_diagnostics_panel")
            .clickable { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = diagnostics.compactStatus,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (expanded) "Hide" else "Details",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (expanded) {
                Text("Location updates: ${diagnostics.locationUpdateState.displayName}")
                diagnostics.intervalReason?.let { reason ->
                    Text("Interval reason: ${reason.displayName}")
                }
                Text("Last fix: ${diagnostics.latestFix.lastFixAge(nowMillis)}")
                Text("Latest fix: ${diagnostics.latestFix.displayDecision()}")
            }
        }
    }
}

private val LiveTrackingDiagnosticsState.compactStatus: String
    get() {
        val suffix = if (intervalReason == RequestedIntervalReason.SAFE_FALLBACK) {
            " fallback"
        } else {
            ""
        }
        return "${activityMode.displayName} · " +
            "${requestedLocationIntervalMillis.displayInterval()}$suffix"
    }

private val RecordingActivity.displayName: String
    get() = when (this) {
        RecordingActivity.STILL -> "Still"
        RecordingActivity.WALKING -> "Walking"
        RecordingActivity.RUNNING -> "Running"
        RecordingActivity.ON_BICYCLE -> "Cycling"
        RecordingActivity.IN_VEHICLE -> "In vehicle"
        RecordingActivity.UNKNOWN -> "Unknown"
    }

private val LocationUpdateState.displayName: String
    get() = when (this) {
        LocationUpdateState.INACTIVE -> "Inactive"
        LocationUpdateState.ACTIVE -> "Active"
        LocationUpdateState.SUSPENDED -> "Suspended"
    }

private val RequestedIntervalReason.displayName: String
    get() = when (this) {
        RequestedIntervalReason.SESSION_START -> "Session start"
        RequestedIntervalReason.ACTIVITY_MODE -> "Activity mode"
        RequestedIntervalReason.SPEED_OVERRIDE -> "Speed override"
        RequestedIntervalReason.SAFE_FALLBACK -> "Safe fallback"
        RequestedIntervalReason.SUSPENDED_WHILE_STILL -> "Suspended while still"
    }

private fun Long?.displayInterval(): String = when (this) {
    null -> "suspended"
    else -> "${this / 1_000} s"
}

private fun LatestFixDiagnostics?.lastFixAge(nowMillis: Long): String {
    val fix = this ?: return "None"
    val ageSeconds = ((nowMillis - fix.capturedAtMillis).coerceAtLeast(0L) / 1_000)
    return if (ageSeconds < 60) {
        "$ageSeconds s ago"
    } else {
        "${ageSeconds / 60} min ago"
    }
}

private fun LatestFixDiagnostics?.displayDecision(): String {
    val fix = this ?: return "None"
    val result = if (fix.accepted) "Accepted" else "Rejected"
    val accuracy = "${fix.accuracyMeters.roundToInt()} m"
    val reason = fix.rejectionReason?.replace('_', ' ')?.replaceFirstChar(Char::uppercase)
    return listOfNotNull(result, accuracy, reason).joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverageMapTopAppBar(
    isRecordingSession: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Text("Kartoffel")
                Text(
                    modifier = Modifier.testTag("passive_tracking_status"),
                    text = if (isRecordingSession) {
                        "Recording · Passive off"
                    } else {
                        "Passive off"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRecordingSession) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        actions = {
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
