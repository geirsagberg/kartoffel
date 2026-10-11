package net.sagberg.kartoffel.map

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
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
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberTileOverlayState
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import net.sagberg.kartoffel.coverage.CoverageSnapshot
import net.sagberg.kartoffel.coverage.GeoBounds
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells
import net.sagberg.kartoffel.coverage.PersistedCoverageLoader
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnostics
import net.sagberg.kartoffel.inspection.SampleSort
import net.sagberg.kartoffel.inspection.TrackingInspectionFilter
import net.sagberg.kartoffel.inspection.TrackingInspectionLoader
import net.sagberg.kartoffel.inspection.TrackingInspectionSnapshot
import net.sagberg.kartoffel.inspection.color
import net.sagberg.kartoffel.settings.CoverageOutlineStyle
import net.sagberg.kartoffel.settings.color
import net.sagberg.kartoffel.storage.AutomaticTrackingPreference
import net.sagberg.kartoffel.storage.AutomaticTrackingPreferences
import net.sagberg.kartoffel.storage.DatabaseBackupManager
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.tracking.AutomaticTrackingManager
import net.sagberg.kartoffel.tracking.RecordingService
import net.sagberg.kartoffel.ui.theme.LocalDarkTheme

@Composable
internal fun MapScreen(
    coverageOutline: CoverageOutlineStyle,
    menuActions: MapMenuActions,
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
            menuActions = menuActions.copy(
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
            ),
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
    menuActions: MapMenuActions,
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
    var inspectionSort by remember { mutableStateOf(SampleSort.NewestFirst) }
    val drawnRouteH3 = remember { H3CoverageCells() }
    val drawnRouteEditor = remember(database, drawnRouteH3) {
        DrawnRouteEditor(database.drawnRoutes(), drawnRouteH3)
    }
    val drawnRouteState by drawnRouteEditor.state.collectAsState()
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
        FogTileProvider(CoverageSnapshot.Empty)
    }

    var coverageRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(database, persistedCoverage, fogTileProvider) {
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
                coverageRevision++
            }
    }

    LaunchedEffect(inspectionActive, inspectionFilter) {
        if (!inspectionActive) {
            inspectionSnapshot = null
            return@LaunchedEffect
        }
        inspectionSnapshot = inspectionLoader.load(inspectionFilter)
        selectedInspectionCellId = null
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
        menuActions = menuActions,
        hasLocationPermission = hasLocationPermission,
        isRecording = isRecording,
        coverageOutline = coverageOutline,
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
            inspectionSort = SampleSort.NewestFirst
        },
        onInspectionFilterChange = { inspectionFilter = it },
        onInspectionSortChange = { inspectionSort = it },
        onRemoveDrawnRoute = { routeId ->
            scope.launch {
                database.drawnRoutes().remove(routeId)
                inspectionSnapshot = inspectionLoader.load(inspectionFilter)
            }
        },
        manualDrawingActive = drawnRouteState.active,
        drawnRouteSaving = drawnRouteState.saving,
        drawnRoutePreview = drawnRouteState.preview,
        onEnterManualDrawing = drawnRouteEditor::enter,
        onUndoManualWaypoint = drawnRouteEditor::undo,
        onAddManualWaypoint = drawnRouteEditor::add,
        onMoveManualWaypoint = drawnRouteEditor::move,
        onCancelManualDrawing = drawnRouteEditor::cancel,
        onConfirmDrawnRoute = {
            scope.launch {
                drawnRouteEditor.confirm()?.onFailure {
                    Toast.makeText(context, "Could not save route. Try again.", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        },
        map = { drawnRouteMap, mapPadding ->
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = mapPadding,
                mapColorScheme = if (LocalDarkTheme.current) {
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
                onMapClick = if (drawnRouteMap.editable) {
                    { point ->
                        drawnRouteMap.onMapClick(
                            GeoCoordinate(point.latitude, point.longitude),
                        )
                    }
                } else if (inspectionActive) {
                    { selectedInspectionCellId = null }
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
                // The overlay must be attached before its cache can be cleared.
                LaunchedEffect(coverageOutlinePaint, coverageRevision) {
                    fogTileProvider.updateOutline(coverageOutlinePaint)
                    fogTileOverlayState.clearTileCache()
                }
                if (inspectionActive) {
                    visibleInspectionCells.forEach { cell ->
                        Polygon(
                            points = cell.boundary.map { LatLng(it.latitude, it.longitude) },
                            fillColor = cell.state.color.copy(alpha = 0.72f),
                            strokeColor = if (cell.cellId == selectedInspectionCellId) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
                            },
                            strokeWidth = if (cell.cellId == selectedInspectionCellId) 9f else 3f,
                            zIndex = if (cell.cellId == selectedInspectionCellId) 21f else 20f,
                            onClick = {
                                selectedInspectionCellId = cell.cellId.takeUnless {
                                    selectedInspectionCellId == it
                                }
                            },
                            clickable = true,
                        )
                    }
                    inspectionSnapshot?.drawnRoutes.orEmpty()
                        .flatMap { it.cells }
                        .distinctBy { it.id }
                        .forEach { cell ->
                            Polygon(
                                points = cell.boundary.map { LatLng(it.latitude, it.longitude) },
                                fillColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.62f),
                                strokeColor = if (cell.id == selectedInspectionCellId?.toString()) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                                strokeWidth = if (cell.id == selectedInspectionCellId?.toString()) {
                                    9f
                                } else {
                                    5f
                                },
                                zIndex = 22f,
                                clickable = true,
                                onClick = {
                                    val cellId = cell.id.toLong()
                                    selectedInspectionCellId = cellId.takeUnless {
                                        selectedInspectionCellId == it
                                    }
                                },
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
                            draggable = drawnRouteMap.editable,
                            onDragEnd = { moved -> drawnRouteMap.onWaypointDragEnd(index, moved) },
                        )
                    }
                }
            }
        },
    )

}

/** Outline colours follow Material roles in the current theme. */
@Composable
private fun CoverageOutlineStyle.paint(): CoverageOutlinePaint? {
    val outlineColor = color()
    return when (this) {
        CoverageOutlineStyle.None -> null
        CoverageOutlineStyle.Teal -> CoverageOutlinePaint(outlineColor.toArgb(), width = 2f)
        CoverageOutlineStyle.Graphite -> CoverageOutlinePaint(
            color = outlineColor.toArgb(),
            width = 1.75f,
        )
        CoverageOutlineStyle.Halo -> {
            CoverageOutlinePaint(
                color = outlineColor.toArgb(),
                width = 1.5f,
                halo = CoverageOutlinePaint.Halo(
                    color = outlineColor.copy(alpha = 0.33f).toArgb(),
                    width = 10f,
                    blurRadius = 5f,
                ),
            )
        }
    }
}

@Composable
private fun DraggableManualWaypoint(
    index: Int,
    waypoint: GeoCoordinate,
    draggable: Boolean,
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
        draggable = draggable,
        contentDescription = "Route waypoint ${index + 1}",
        zIndex = 23f,
    )
}

private fun Context.hasForegroundLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED