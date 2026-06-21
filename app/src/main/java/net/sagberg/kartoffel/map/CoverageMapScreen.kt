package net.sagberg.kartoffel.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import net.sagberg.kartoffel.R

@Composable
@SuppressLint("MissingPermission")
internal fun CoverageMapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }
    var hasLocationPermission by remember {
        mutableStateOf(context.hasForegroundLocationPermission())
    }
    var firstFix by remember { mutableStateOf<MapCoordinate?>(null) }
    var centeredOnFirstFix by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasLocationPermission = granted
    }

    val fallbackCameraTarget = remember { LatLng(59.9139, 10.7522) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(fallbackCameraTarget, 12f)
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

    DisposableEffect(hasLocationPermission, centeredOnFirstFix, context, fusedLocationClient) {
        if (!shouldRequestFirstLocationFix(hasLocationPermission, centeredOnFirstFix)) {
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
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                1_000L,
            )
                .setMinUpdateIntervalMillis(500L)
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
            )
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("coverage_map_screen"),
    ) {
        map()

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                modifier = Modifier.testTag("passive_tracking_status"),
                onClick = {},
            ) {
                Text("Passive off")
            }
            Button(
                modifier = Modifier.testTag("recording_session_control"),
                onClick = {},
            ) {
                Text("Start")
            }
            FilledTonalButton(
                modifier = Modifier.testTag("settings_diagnostics_menu"),
                onClick = {},
            ) {
                Text("Menu")
            }
        }

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

private fun Context.hasForegroundLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
