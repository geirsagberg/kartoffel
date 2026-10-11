package net.sagberg.kartoffel.map

import android.Manifest
import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberTileOverlayState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.R
import net.sagberg.kartoffel.coverage.CoverageSnapshot
import net.sagberg.kartoffel.coverage.GeoBounds
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells
import net.sagberg.kartoffel.coverage.PersistedCoverageLoader
import net.sagberg.kartoffel.diagnostics.LatestFixDiagnostics
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnostics
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnosticsState
import net.sagberg.kartoffel.diagnostics.LocationUpdateState
import net.sagberg.kartoffel.diagnostics.RequestedIntervalReason
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.DatabaseBackupManager
import net.sagberg.kartoffel.storage.AutomaticTrackingPreference
import net.sagberg.kartoffel.storage.AutomaticTrackingPreferences
import net.sagberg.kartoffel.inspection.color
import net.sagberg.kartoffel.settings.CoverageOutlineStyle
import net.sagberg.kartoffel.inspection.SampleSort
import net.sagberg.kartoffel.inspection.TrackingInspectionControls
import net.sagberg.kartoffel.inspection.TrackingInspectionDetails
import net.sagberg.kartoffel.inspection.TrackingInspectionFilter
import net.sagberg.kartoffel.inspection.TrackingInspectionLoader
import net.sagberg.kartoffel.inspection.TrackingInspectionSnapshot
import net.sagberg.kartoffel.tracking.AutomaticTrackingManager
import net.sagberg.kartoffel.tracking.RecordingActivity
import net.sagberg.kartoffel.tracking.RecordingService
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun MapScreen(
    coverageOutline: CoverageOutlineStyle,
    darkTheme: Boolean,
    onOpenSettings: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenTrackingDiagnostics: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager = remember(context) { DatabaseBackupManager(context) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var restoreSource by remember { mutableStateOf<Uri?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.sqlite3"),
    ) { destination ->
        destination ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { backupManager.export(destination) }
                .onSuccess {
                    Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
                }
                .onFailure { failure ->
                    Toast.makeText(
                        context,
                        failure.message ?: "Backup failed",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { source -> pendingRestore = source }

    DatabaseRestoreGate(
        restoreSource = restoreSource,
        onRestore = { source ->
            try {
                AutomaticTrackingManager.prepareForDatabaseRestore(context)
                backupManager.restore(source)
                Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
            } catch (failure: Exception) {
                runCatching { AutomaticTrackingManager.restore(context) }
                Toast.makeText(
                    context,
                    failure.message ?: "Restore failed",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                // Reopen every Room consumer, including after a failed file swap.
                (context as? Activity)?.recreate()
            }
        },
    ) {
        MapRoute(
            coverageOutline = coverageOutline,
            darkTheme = darkTheme,
            onOpenSettings = onOpenSettings,
            onOpenAppearance = onOpenAppearance,
            onOpenTrackingDiagnostics = onOpenTrackingDiagnostics,
            onBackupDatabase = {
                backupLauncher.launch("kartoffel-backup-${System.currentTimeMillis()}.db")
            },
            onRestoreDatabase = {
                restoreLauncher.launch(
                    arrayOf(
                        "application/vnd.sqlite3",
                        "application/x-sqlite3",
                        "application/octet-stream",
                    ),
                )
            },
        )

        pendingRestore?.let { source ->
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text("Restore backup?") },
                text = {
                    Text(
                        "This replaces all current coverage and tracking history. " +
                            "This cannot be undone.",
                    )
                },
                dismissButton = {
                    TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingRestore = null
                            restoreSource = source
                        },
                    ) { Text("Restore") }
                },
            )
        }
    }
}

@Composable
internal fun DatabaseRestoreGate(
    restoreSource: Uri?,
    onRestore: suspend (Uri) -> Unit,
    content: @Composable () -> Unit,
) {
    if (restoreSource == null) {
        content()
        return
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("Restoring backup…")
    }
    LaunchedEffect(restoreSource) {
        onRestore(restoreSource)
    }
}

@Composable
@SuppressLint("MissingPermission")
private fun MapRoute(
    coverageOutline: CoverageOutlineStyle,
    darkTheme: Boolean,
    onOpenSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenTrackingDiagnostics: () -> Unit,
    onBackupDatabase: () -> Unit,
    onRestoreDatabase: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val fusedLocationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }
    val database = remember(context) { KartoffelDatabase.open(context) }
    val inspectionLoader = remember(database) { TrackingInspectionLoader(database) }
    var inspectionActive by remember { mutableStateOf(false) }
    var inspectionFilter by remember { mutableStateOf(TrackingInspectionFilter.Default) }
    var inspectionSnapshot by remember { mutableStateOf<TrackingInspectionSnapshot?>(null) }
    var selectedInspectionCellId by remember { mutableStateOf<Long?>(null) }
    var selectedDrawnRouteId by remember { mutableStateOf<Long?>(null) }
    var inspectionSort by remember { mutableStateOf(SampleSort.NewestFirst) }
    val drawnRouteH3 = remember { H3CoverageCells() }
    val routeDrawing = remember(drawnRouteH3) { RouteDrawing(drawnRouteH3) }
    val drawnRouteRecorder = remember(database) {
        DrawnRouteRecorder(database.drawnRoutes())
    }
    var manualDrawingActive by remember { mutableStateOf(false) }
    var drawnRoutePreview by remember { mutableStateOf(DrawnRoutePreview()) }
    var effectiveCoverageCellIds by remember { mutableStateOf(emptySet<Long>()) }
    val persistedCoverage = remember(database) {
        PersistedCoverageLoader(database.coverageCells(), database.drawnRoutes())
    }
    var hasLocationPermission by remember {
        mutableStateOf(context.hasForegroundLocationPermission())
    }
    var firstFix by remember { mutableStateOf<MapCoordinate?>(null) }
    val cameraPreferences = remember(context) { MapCameraPreferences(context) }
    val savedCameraPosition = remember(cameraPreferences) { cameraPreferences.load() }
    val activeRecording by database.recordings().observeActive().collectAsState(
        initial = null,
    )
    val isRecording = activeRecording != null
    val automaticTrackingPreferences = remember(database) {
        AutomaticTrackingPreferences(database.trackingSettings())
    }
    val automaticTrackingPreference by automaticTrackingPreferences.observe().collectAsState(
        initial = AutomaticTrackingPreference.Disabled,
    )
    var startupCameraResolved by rememberSaveable {
        mutableStateOf(savedCameraPosition != null)
    }
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
            RecordingService.start(context)
        }
    }
    val automaticBackgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        scope.launch { AutomaticTrackingManager.enable(context) }
    }
    val automaticForegroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = context.hasForegroundLocationPermission()
        if (hasLocationPermission) {
            automaticBackgroundPermissionLauncher.launch(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        }
    }
    LaunchedEffect(context) {
        AutomaticTrackingManager.restore(context)
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { AutomaticTrackingManager.reconcilePermissions(context) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = savedCameraPosition ?: CameraPosition.fromLatLngZoom(
            LatLng(59.9139, 10.7522),
            12f,
        )
    }
    val cameraIsMoving = cameraPositionState.isMoving
    val cameraMoveStartedReason = cameraPositionState.cameraMoveStartedReason
    LaunchedEffect(cameraIsMoving, cameraMoveStartedReason, startupCameraResolved) {
        val shouldPersistCamera = startupCameraResolved ||
            cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        if (!cameraIsMoving && shouldPersistCamera) {
            cameraPreferences.save(cameraPositionState.position)
        }
    }
    val visibleInspectionCells = inspectionSnapshot?.let { snapshot ->
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
        if (bounds == null || bounds.southwest.longitude > bounds.northeast.longitude) {
            snapshot.cells
        } else {
            snapshot.cellsIntersecting(
                GeoBounds(
                    south = bounds.southwest.latitude,
                    west = bounds.southwest.longitude,
                    north = bounds.northeast.latitude,
                    east = bounds.northeast.longitude,
                ),
            )
        }
    }.orEmpty()
    val fogTileOverlayState = rememberTileOverlayState()
    val coverageOutlinePaint = coverageOutline.paint()
    val fogTileProvider = remember {
        FogTileProvider(CoverageSnapshot.Empty, initialOutline = coverageOutlinePaint)
    }

    LaunchedEffect(coverageOutlinePaint, fogTileProvider, fogTileOverlayState) {
        if (fogTileProvider.updateOutline(coverageOutlinePaint)) {
            fogTileOverlayState.clearTileCache()
        }
    }

    LaunchedEffect(database, persistedCoverage, fogTileProvider, fogTileOverlayState) {
        var hasLoadedInitialCoverage = false
        combine(
            database.coverageCells().observeAll(),
            database.drawnRoutes().observeAllCellIds(),
        ) { captured, manual -> captured to manual }
            .distinctUntilChangedBy { (captured, manual) ->
                captured.map { it.cellId } to manual
            }
            .collect { (captured, manual) ->
                effectiveCoverageCellIds = (captured.map { it.cellId } + manual).toSet()
                fogTileProvider.updateCoverage(persistedCoverage.load(captured, manual))
                if (hasLoadedInitialCoverage) {
                    fogTileOverlayState.clearTileCache()
                }
                hasLoadedInitialCoverage = true
            }
    }

    LaunchedEffect(inspectionActive, inspectionFilter) {
        if (!inspectionActive) {
            inspectionSnapshot = null
            return@LaunchedEffect
        }
        inspectionSnapshot = inspectionLoader.load(inspectionFilter)
        selectedInspectionCellId = null
        selectedDrawnRouteId = null
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
        val shouldRequestLocation = shouldRequestForegroundLocation(
            hasLocationPermission = hasLocationPermission,
            observedFirstFix = firstFix != null,
            shouldCenterOnFirstFix = !startupCameraResolved,
        )
        if (!shouldRequestLocation) {
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
        val request = firstFixCameraRequest(firstFix, startupCameraResolved)
            ?: return@LaunchedEffect

        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(request.target.latitude, request.target.longitude),
                request.zoom,
            )
        )
        cameraPreferences.save(cameraPositionState.position)
        startupCameraResolved = true
    }

    MapContent(
        hasLocationPermission = hasLocationPermission,
        isRecording = isRecording,
        isAutomaticTrackingEnabled = automaticTrackingPreference.enabled,
        liveTrackingDiagnostics = liveTrackingDiagnostics,
        diagnosticsNowMillis = diagnosticsNowMillis,
        onRequestLocationPermission = {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onStartRecording = {
            recordingPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
        },
        onStopRecording = {
            RecordingService.stop(context)
        },
        onCenterCurrentLocation = { moveToCurrentLocation(CURRENT_LOCATION_ZOOM) },
        onEnableAutomaticTracking = {
            automaticForegroundPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                ),
            )
        },
        onDisableAutomaticTracking = {
            scope.launch { AutomaticTrackingManager.disable(context) }
        },
        onOpenSettings = onOpenSettings,
        onOpenAppearance = onOpenAppearance,
        onOpenTrackingDiagnostics = onOpenTrackingDiagnostics,
        onBackupDatabase = onBackupDatabase,
        onRestoreDatabase = onRestoreDatabase,
        inspectionActive = inspectionActive,
        inspectionFilter = inspectionFilter,
        inspectionSnapshot = inspectionSnapshot,
        selectedInspectionCellId = selectedInspectionCellId,
        inspectionSort = inspectionSort,
        onEnterTrackingInspection = { inspectionActive = true },
        onExitTrackingInspection = {
            inspectionActive = false
            inspectionFilter = TrackingInspectionFilter.Default
            selectedInspectionCellId = null
            selectedDrawnRouteId = null
            inspectionSort = SampleSort.NewestFirst
        },
        onInspectionFilterChange = { inspectionFilter = it },
        onInspectionSortChange = { inspectionSort = it },
        selectedDrawnRouteId = selectedDrawnRouteId,
        onSelectDrawnRoute = { selectedDrawnRouteId = it },
        onRemoveDrawnRoute = { routeId ->
            scope.launch {
                database.drawnRoutes().remove(routeId)
                selectedDrawnRouteId = null
                inspectionSnapshot = inspectionLoader.load(inspectionFilter)
            }
        },
        manualDrawingActive = manualDrawingActive,
        drawnRoutePreview = drawnRoutePreview,
        onEnterManualDrawing = {
            drawnRoutePreview = DrawnRoutePreview()
            manualDrawingActive = true
        },
        onUndoManualWaypoint = {
            drawnRoutePreview = routeDrawing.undo(drawnRoutePreview)
        },
        onAddManualWaypoint = { waypoint ->
            drawnRoutePreview = routeDrawing.add(drawnRoutePreview, waypoint)
        },
        onMoveManualWaypoint = { index, waypoint ->
            drawnRoutePreview = routeDrawing.move(drawnRoutePreview, index, waypoint)
        },
        onCancelManualDrawing = {
            drawnRoutePreview = DrawnRoutePreview()
            manualDrawingActive = false
        },
        onConfirmDrawnRoute = {
            if (drawnRoutePreview.canConfirm) {
                scope.launch {
                    if (drawnRouteRecorder.confirm(drawnRoutePreview) != null) {
                        drawnRoutePreview = DrawnRoutePreview()
                        manualDrawingActive = false
                    }
                }
            }
        },
        map = { drawnRouteMap ->
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
                mapColorScheme = if (darkTheme) {
                    ComposeMapColorScheme.DARK
                } else {
                    ComposeMapColorScheme.LIGHT
                },
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission,
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false,
                ),
                onMapClick = if (drawnRouteMap.active) {
                    { point ->
                        drawnRouteMap.onMapClick(
                            GeoCoordinate(point.latitude, point.longitude),
                        )
                    }
                } else {
                    null
                },
            ) {
                TileOverlay(
                    tileProvider = fogTileProvider,
                    state = fogTileOverlayState,
                    fadeIn = false,
                    transparency = 0f,
                    visible = true,
                    zIndex = 10f,
                )
                if (inspectionActive) {
                    visibleInspectionCells.forEach { cell ->
                        Polygon(
                            points = cell.boundary.map { LatLng(it.latitude, it.longitude) },
                            fillColor = cell.state.color.copy(alpha = 0.72f),
                            strokeColor = if (cell.cellId == selectedInspectionCellId) {
                                Color.White
                            } else {
                                Color.Black.copy(alpha = 0.6f)
                            },
                            strokeWidth = if (cell.cellId == selectedInspectionCellId) 9f else 3f,
                            zIndex = if (cell.cellId == selectedInspectionCellId) 21f else 20f,
                            onClick = { selectedInspectionCellId = cell.cellId },
                            clickable = true,
                        )
                    }
                    inspectionSnapshot?.drawnRoutes
                        ?.firstOrNull { it.id == selectedDrawnRouteId }
                        ?.cells
                        ?.forEach { cell ->
                            Polygon(
                                points = cell.boundary.map { LatLng(it.latitude, it.longitude) },
                                fillColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.62f),
                                strokeColor = MaterialTheme.colorScheme.tertiary,
                                strokeWidth = 5f,
                                zIndex = 22f,
                            )
                        }
                }
                if (drawnRouteMap.active) {
                    drawnRouteMap.preview.cells.forEach { cell ->
                        Polygon(
                            points = drawnRouteH3.boundaryOf(cell).map {
                                LatLng(it.latitude, it.longitude)
                            },
                            fillColor = if (cell.value in effectiveCoverageCellIds) {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.46f)
                            },
                            strokeColor = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2f,
                            zIndex = 20f,
                        )
                    }
                    if (drawnRouteMap.preview.waypoints.size >= 2) {
                        Polyline(
                            points = drawnRouteMap.preview.waypoints.map {
                                LatLng(it.latitude, it.longitude)
                            },
                            color = MaterialTheme.colorScheme.primary,
                            width = 8f,
                            zIndex = 22f,
                        )
                    }
                    drawnRouteMap.preview.waypoints.forEachIndexed { index, waypoint ->
                        DraggableManualWaypoint(
                            index = index,
                            waypoint = waypoint,
                            onDragEnd = { moved -> drawnRouteMap.onWaypointDragEnd(index, moved) },
                        )
                    }
                }
            }
        },
    )

}

/** Teal uses the theme's primary colour and Graphite its text colour; Halo is a white edge. */
@Composable
private fun CoverageOutlineStyle.paint(): CoverageOutlinePaint? {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        CoverageOutlineStyle.None -> null
        CoverageOutlineStyle.Teal -> CoverageOutlinePaint(colors.primary.toArgb(), width = 2f)
        CoverageOutlineStyle.Graphite -> CoverageOutlinePaint(
            color = colors.onSurface.copy(alpha = 0.9f).toArgb(),
            width = 1.75f,
        )
        CoverageOutlineStyle.Halo -> CoverageOutlinePaint(
            color = Color.White.copy(alpha = 0.94f).toArgb(),
            width = 1.5f,
            glow = CoverageOutlinePaint.Glow(
                color = Color.White.copy(alpha = 0.33f).toArgb(),
                width = 10f,
                blurRadius = 5f,
            ),
        )
    }
}

@Composable
private fun DraggableManualWaypoint(
    index: Int,
    waypoint: GeoCoordinate,
    onDragEnd: (GeoCoordinate) -> Unit,
) {
    val markerState = remember(index) { MarkerState(LatLng(waypoint.latitude, waypoint.longitude)) }
    LaunchedEffect(waypoint) {
        if (!markerState.isDragging) {
            markerState.position = LatLng(waypoint.latitude, waypoint.longitude)
        }
    }
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(markerState.isDragging) {
        if (wasDragging && !markerState.isDragging) {
            onDragEnd(GeoCoordinate(markerState.position.latitude, markerState.position.longitude))
        }
        wasDragging = markerState.isDragging
    }
    Marker(
        state = markerState,
        draggable = true,
        contentDescription = "Route waypoint ${index + 1}",
        zIndex = 23f,
    )
}

@Composable
internal fun MapContent(
    hasLocationPermission: Boolean,
    isRecording: Boolean,
    isAutomaticTrackingEnabled: Boolean = false,
    liveTrackingDiagnostics: LiveTrackingDiagnosticsState = LiveTrackingDiagnosticsState(),
    diagnosticsNowMillis: Long = System.currentTimeMillis(),
    onRequestLocationPermission: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCenterCurrentLocation: () -> Unit,
    onEnableAutomaticTracking: () -> Unit = {},
    onDisableAutomaticTracking: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenTrackingDiagnostics: () -> Unit = {},
    onBackupDatabase: () -> Unit = {},
    onRestoreDatabase: () -> Unit = {},
    inspectionActive: Boolean = false,
    inspectionFilter: TrackingInspectionFilter = TrackingInspectionFilter.Default,
    inspectionSnapshot: TrackingInspectionSnapshot? = null,
    selectedInspectionCellId: Long? = null,
    inspectionSort: SampleSort = SampleSort.NewestFirst,
    onEnterTrackingInspection: () -> Unit = {},
    onExitTrackingInspection: () -> Unit = {},
    onInspectionFilterChange: (TrackingInspectionFilter) -> Unit = {},
    onInspectionSortChange: (SampleSort) -> Unit = {},
    selectedDrawnRouteId: Long? = null,
    onSelectDrawnRoute: (Long) -> Unit = {},
    onRemoveDrawnRoute: (Long) -> Unit = {},
    manualDrawingActive: Boolean = false,
    drawnRoutePreview: DrawnRoutePreview = DrawnRoutePreview(),
    onEnterManualDrawing: () -> Unit = {},
    onUndoManualWaypoint: () -> Unit = {},
    onAddManualWaypoint: (GeoCoordinate) -> Unit = {},
    onMoveManualWaypoint: (Int, GeoCoordinate) -> Unit = { _, _ -> },
    onCancelManualDrawing: () -> Unit = {},
    onConfirmDrawnRoute: () -> Unit = {},
    map: @Composable BoxScope.(DrawnRouteMapInteractions) -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    BackHandler(enabled = manualDrawingActive) {
        if (drawnRoutePreview.waypoints.isEmpty()) {
            onCancelManualDrawing()
        } else {
            confirmDiscard = true
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard route?") },
            text = { Text("Your unconfirmed route will be lost.") },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep drawing") }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onCancelManualDrawing()
                }) { Text("Discard") }
            },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("coverage_map_screen")
            .then(
                if (manualDrawingActive) {
                    Modifier.semantics {
                        contentDescription =
                            "Drawn route preview: ${drawnRoutePreview.waypoints.size} " +
                            "waypoints, ${drawnRoutePreview.cells.size} cells"
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        map(
            DrawnRouteMapInteractions(
                active = manualDrawingActive,
                preview = drawnRoutePreview,
                onMapClick = onAddManualWaypoint,
                onWaypointDragEnd = onMoveManualWaypoint,
            ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FloatingMapBar(
                isRecording = isRecording,
                isAutomaticTrackingEnabled = isAutomaticTrackingEnabled,
                onEnableAutomaticTracking = onEnableAutomaticTracking,
                onDisableAutomaticTracking = onDisableAutomaticTracking,
                onOpenSettings = onOpenSettings,
                onOpenAppearance = onOpenAppearance,
                onOpenTrackingDiagnostics = onOpenTrackingDiagnostics,
                onBackupDatabase = onBackupDatabase,
                onRestoreDatabase = onRestoreDatabase,
                inspectionActive = inspectionActive,
                onEnterTrackingInspection = onEnterTrackingInspection,
                onExitTrackingInspection = onExitTrackingInspection,
                manualDrawingActive = manualDrawingActive,
                drawnRoutePreview = drawnRoutePreview,
                onEnterManualDrawing = onEnterManualDrawing,
                onUndoManualWaypoint = onUndoManualWaypoint,
                onCancelManualDrawing = onCancelManualDrawing,
                onConfirmDrawnRoute = onConfirmDrawnRoute,
            )

            drawnRoutePreview.failingSegmentIndex?.let { segmentIndex ->
                Card(modifier = Modifier.testTag("drawn_route_error")) {
                    Text(
                        "Could not connect segment ${segmentIndex + 1}. Adjust its waypoints.",
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (!inspectionActive && !manualDrawingActive && liveTrackingDiagnostics.trackingActive) {
                LiveTrackingDiagnosticsPanel(
                    diagnostics = liveTrackingDiagnostics,
                    nowMillis = diagnosticsNowMillis,
                )
            }

            if (inspectionActive) {
                TrackingInspectionControls(
                    filter = inspectionFilter,
                    recordings = inspectionSnapshot?.availableRecordings.orEmpty(),
                    nowMillis = System.currentTimeMillis(),
                    loading = inspectionSnapshot == null,
                    empty = inspectionSnapshot?.let {
                        it.cells.isEmpty() && it.drawnRoutes.isEmpty()
                    } == true,
                    onFilterChange = onInspectionFilterChange,
                    drawnRoutes = inspectionSnapshot?.drawnRoutes.orEmpty(),
                    selectedDrawnRouteId = selectedDrawnRouteId,
                    onSelectDrawnRoute = onSelectDrawnRoute,
                    onRemoveDrawnRoute = onRemoveDrawnRoute,
                )
            }
        }

        if (inspectionActive) {
            inspectionSnapshot?.cells?.firstOrNull { it.cellId == selectedInspectionCellId }
                ?.let { cell ->
                    TrackingInspectionDetails(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(12.dp)
                            .fillMaxWidth(),
                        cell = cell,
                        sort = inspectionSort,
                        onSortChange = onInspectionSortChange,
                    )
                }
        } else {
            MapControls(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 24.dp),
                hasLocationPermission = hasLocationPermission,
                isRecording = isRecording,
                manualDrawingActive = manualDrawingActive,
                onEnterManualDrawing = onEnterManualDrawing,
                onCenterCurrentLocation = onCenterCurrentLocation,
                onRequestLocationPermission = onRequestLocationPermission,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
            )
        }
    }
}

/** Small Draw route and Location buttons above the large Record button, the primary action. */
@Composable
private fun MapControls(
    hasLocationPermission: Boolean,
    isRecording: Boolean,
    manualDrawingActive: Boolean,
    onEnterManualDrawing: () -> Unit,
    onCenterCurrentLocation: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!manualDrawingActive) {
            SmallMapButton(
                icon = R.drawable.ic_draw_24,
                contentDescription = "Draw route",
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onEnterManualDrawing,
            )
        }
        if (hasLocationPermission) {
            SmallMapButton(
                icon = R.drawable.ic_my_location_24,
                contentDescription = "Center on current location",
                tint = MaterialTheme.colorScheme.primary,
                onClick = onCenterCurrentLocation,
            )
        }
        RecordButton(
            modifier = Modifier.padding(top = 4.dp),
            hasLocationPermission = hasLocationPermission,
            isRecording = isRecording,
            onRequestLocationPermission = onRequestLocationPermission,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
        )
    }
}

@Composable
private fun SmallMapButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        modifier = Modifier.size(48.dp),
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = tint,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 3.dp,
            pressedElevation = 4.dp,
        ),
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            painter = painterResource(icon),
            contentDescription = contentDescription,
        )
    }
}

@Composable
private fun RecordButton(
    hasLocationPermission: Boolean,
    isRecording: Boolean,
    onRequestLocationPermission: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        modifier = modifier
            .heightIn(min = 64.dp)
            .testTag(if (hasLocationPermission) "recording_control" else "enable_location_control"),
        onClick = when {
            !hasLocationPermission -> onRequestLocationPermission
            isRecording -> onStopRecording
            else -> onStartRecording
        },
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp,
        ),
        icon = {
            if (hasLocationPermission) {
                RecordingGlyph(isRecording)
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_my_location_24),
                    contentDescription = null,
                )
            }
        },
        text = {
            Text(
                text = when {
                    !hasLocationPermission -> "Enable location"
                    isRecording -> "Stop"
                    else -> "Record"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontWeight = FontWeight.SemiBold,
            )
        },
    )
}

/** A red record dot, or a red stop square while recording, set in a disc of the label colour. */
@Composable
private fun RecordingGlyph(isRecording: Boolean) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(LocalContentColor.current, CircleShape)
            .testTag(if (isRecording) "stop_recording_icon" else "start_recording_icon"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (isRecording) 10.dp else 12.dp)
                .background(
                    RecordingRed,
                    if (isRecording) RoundedCornerShape(2.dp) else CircleShape,
                ),
        )
    }
}

private val RecordingRed = Color(0xFFE5484D)

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
        RequestedIntervalReason.RECORDING_START -> "Recording start"
        RequestedIntervalReason.ACTIVITY_MODE -> "Activity mode"
        RequestedIntervalReason.SPEED_OVERRIDE -> "Speed override"
        RequestedIntervalReason.SAFE_FALLBACK -> "Safe fallback"
        RequestedIntervalReason.SUSPENDED_WHILE_STILL -> "Suspended while still"
        RequestedIntervalReason.AUTOMATIC_INITIAL -> "Automatic initial fix"
        RequestedIntervalReason.AUTOMATIC_WINDOW -> "Automatic capture window"
        RequestedIntervalReason.OPPORTUNISTIC -> "Opportunistic fixes"
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

/** The floating, rounded bar over the Map holding its title and actions. */
@Composable
private fun FloatingMapBar(
    isRecording: Boolean,
    isAutomaticTrackingEnabled: Boolean,
    onEnableAutomaticTracking: () -> Unit,
    onDisableAutomaticTracking: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenTrackingDiagnostics: () -> Unit,
    onBackupDatabase: () -> Unit,
    onRestoreDatabase: () -> Unit,
    inspectionActive: Boolean,
    onEnterTrackingInspection: () -> Unit,
    onExitTrackingInspection: () -> Unit,
    manualDrawingActive: Boolean,
    drawnRoutePreview: DrawnRoutePreview,
    onEnterManualDrawing: () -> Unit,
    onUndoManualWaypoint: () -> Unit,
    onCancelManualDrawing: () -> Unit,
    onConfirmDrawnRoute: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .height(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 24.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = when {
                    inspectionActive -> "Tracking Inspection"
                    manualDrawingActive -> "Draw route"
                    else -> "Kartoffel"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                inspectionActive -> TextButton(
                    modifier = Modifier.testTag("exit_tracking_inspection"),
                    onClick = onExitTrackingInspection,
                ) { Text("Exit") }
                manualDrawingActive -> {
                    TextButton(
                        onClick = onUndoManualWaypoint,
                        enabled = drawnRoutePreview.waypoints.isNotEmpty(),
                    ) { Text("Undo") }
                    TextButton(onClick = onCancelManualDrawing) { Text("Cancel") }
                    TextButton(
                        onClick = onConfirmDrawnRoute,
                        enabled = drawnRoutePreview.canConfirm,
                    ) { Text("Confirm") }
                }
                else -> Box {
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
                            text = { Text("Draw route") },
                            onClick = {
                                menuExpanded = false
                                onEnterManualDrawing()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Tracking inspection") },
                            onClick = {
                                menuExpanded = false
                                onEnterTrackingInspection()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Appearance") },
                            onClick = {
                                menuExpanded = false
                                onOpenAppearance()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Back up database") },
                            onClick = {
                                menuExpanded = false
                                onBackupDatabase()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Restore database") },
                            onClick = {
                                menuExpanded = false
                                onRestoreDatabase()
                            },
                            enabled = !isRecording,
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isAutomaticTrackingEnabled) {
                                        "Disable automatic tracking"
                                    } else {
                                        "Enable automatic tracking"
                                    },
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                if (isAutomaticTrackingEnabled) {
                                    onDisableAutomaticTracking()
                                } else {
                                    onEnableAutomaticTracking()
                                }
                            },
                            enabled = !isRecording || isAutomaticTrackingEnabled,
                        )
                        DropdownMenuItem(
                            text = { Text("Tracking diagnostics") },
                            onClick = {
                                menuExpanded = false
                                onOpenTrackingDiagnostics()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun Context.hasForegroundLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
