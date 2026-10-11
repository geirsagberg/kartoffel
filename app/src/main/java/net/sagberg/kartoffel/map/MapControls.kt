package net.sagberg.kartoffel.map

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import net.sagberg.kartoffel.R
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.diagnostics.LatestFixDiagnostics
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnosticsState
import net.sagberg.kartoffel.diagnostics.LocationUpdateState
import net.sagberg.kartoffel.diagnostics.RequestedIntervalReason
import net.sagberg.kartoffel.inspection.SampleSort
import net.sagberg.kartoffel.inspection.TrackingInspectionControls
import net.sagberg.kartoffel.inspection.TrackingInspectionDetails
import net.sagberg.kartoffel.inspection.TrackingInspectionFilter
import net.sagberg.kartoffel.inspection.TrackingInspectionSnapshot
import net.sagberg.kartoffel.settings.CoverageOutlineStyle
import net.sagberg.kartoffel.tracking.RecordingActivity
import net.sagberg.kartoffel.ui.RecordingBorder

internal data class MapMenuActions(
    val onOpenSettings: () -> Unit = {},
    val onOpenAppearance: () -> Unit = {},
    val onOpenTrackingDiagnostics: () -> Unit = {},
    val onBackupDatabase: () -> Unit = {},
    val onRestoreDatabase: () -> Unit = {},
)

@Composable
internal fun MapContent(
    menuActions: MapMenuActions = MapMenuActions(),
    hasLocationPermission: Boolean,
    isRecording: Boolean,
    coverageOutline: CoverageOutlineStyle = CoverageOutlineStyle.Teal,
    isAutomaticTrackingEnabled: Boolean = false,
    liveTrackingDiagnostics: LiveTrackingDiagnosticsState = LiveTrackingDiagnosticsState(),
    diagnosticsNowMillis: Long = System.currentTimeMillis(),
    onRequestLocationPermission: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCenterCurrentLocation: () -> Unit,
    onEnableAutomaticTracking: () -> Unit = {},
    onDisableAutomaticTracking: () -> Unit = {},
    inspectionActive: Boolean = false,
    inspectionFilter: TrackingInspectionFilter = TrackingInspectionFilter.Default,
    inspectionSnapshot: TrackingInspectionSnapshot? = null,
    selectedInspectionCellId: Long? = null,
    inspectionSort: SampleSort = SampleSort.NewestFirst,
    onEnterTrackingInspection: () -> Unit = {},
    onExitTrackingInspection: () -> Unit = {},
    onInspectionFilterChange: (TrackingInspectionFilter) -> Unit = {},
    onInspectionSortChange: (SampleSort) -> Unit = {},
    onRemoveDrawnRoute: (Long) -> Unit = {},
    manualDrawingActive: Boolean = false,
    drawnRouteSaving: Boolean = false,
    drawnRoutePreview: DrawnRoutePreview = DrawnRoutePreview(),
    onEnterManualDrawing: () -> Unit = {},
    onUndoManualWaypoint: () -> Unit = {},
    onAddManualWaypoint: (GeoCoordinate) -> Unit = {},
    onMoveManualWaypoint: (Int, GeoCoordinate) -> Unit = { _, _ -> },
    onCancelManualDrawing: () -> Unit = {},
    onConfirmDrawnRoute: () -> Unit = {},
    map: @Composable BoxScope.(DrawnRouteMapInteractions, PaddingValues) -> Unit,
) {
    var showDebugInfo by rememberSaveable { mutableStateOf(false) }
    var topOverlayHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safePadding = WindowInsets.safeDrawing.asPaddingValues()
    val mapPadding = PaddingValues(
        start = safePadding.calculateStartPadding(layoutDirection),
        top = maxOf(safePadding.calculateTopPadding(), with(density) { topOverlayHeight.toDp() }),
        end = safePadding.calculateEndPadding(layoutDirection),
        bottom = safePadding.calculateBottomPadding(),
    )
    var confirmDiscard by remember { mutableStateOf(false) }
    BackHandler(enabled = manualDrawingActive) {
        if (drawnRouteSaving) return@BackHandler
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
                editable = manualDrawingActive && !drawnRouteSaving,
                preview = drawnRoutePreview,
                onMapClick = onAddManualWaypoint,
                onWaypointDragEnd = onMoveManualWaypoint,
            ),
            mapPadding,
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth()
                .onSizeChanged { topOverlayHeight = it.height }
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FloatingMapBar(
                menuActions = menuActions,
                showDebugInfo = showDebugInfo,
                onToggleDebugInfo = { showDebugInfo = !showDebugInfo },
                isRecording = isRecording,
                isAutomaticTrackingEnabled = isAutomaticTrackingEnabled,
                onEnableAutomaticTracking = onEnableAutomaticTracking,
                onDisableAutomaticTracking = onDisableAutomaticTracking,
                inspectionActive = inspectionActive,
                onEnterTrackingInspection = onEnterTrackingInspection,
                onExitTrackingInspection = onExitTrackingInspection,
                manualDrawingActive = manualDrawingActive,
                drawnRoutePreview = drawnRoutePreview,
                drawnRouteSaving = drawnRouteSaving,
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

            if (showDebugInfo && !inspectionActive && !manualDrawingActive &&
                liveTrackingDiagnostics.trackingActive
            ) {
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
                )
            }
        }

        if (inspectionActive) {
            selectedInspectionCellId?.let { cellId ->
                val cell = inspectionSnapshot?.cells?.firstOrNull { it.cellId == cellId }
                val routes = inspectionSnapshot?.drawnRoutes.orEmpty().filter { route ->
                    route.cells.any { it.id == cellId.toString() }
                }
                if (cell != null || routes.isNotEmpty()) {
                    TrackingInspectionDetails(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(12.dp)
                            .fillMaxWidth(),
                        cell = cell,
                        cellId = cellId,
                        drawnRoutes = routes,
                        onRemoveDrawnRoute = onRemoveDrawnRoute,
                        sort = inspectionSort,
                        onSortChange = onInspectionSortChange,
                    )
                }
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
        RecordingBorder(isRecording, coverageOutline)
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

/** A record dot or stop square using the theme’s error and onError colour pair. */
@Composable
private fun RecordingGlyph(isRecording: Boolean) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(MaterialTheme.colorScheme.onError, CircleShape)
            .testTag(if (isRecording) "stop_recording_icon" else "start_recording_icon"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (isRecording) 10.dp else 12.dp)
                .background(
                    MaterialTheme.colorScheme.error,
                    if (isRecording) RoundedCornerShape(2.dp) else CircleShape,
                ),
        )
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

/** Expands from the menu button when the Map needs contextual actions. */
@Composable
private fun FloatingMapBar(
    showDebugInfo: Boolean,
    onToggleDebugInfo: () -> Unit,
    menuActions: MapMenuActions,
    isRecording: Boolean,
    isAutomaticTrackingEnabled: Boolean,
    onEnableAutomaticTracking: () -> Unit,
    onDisableAutomaticTracking: () -> Unit,
    inspectionActive: Boolean,
    onEnterTrackingInspection: () -> Unit,
    onExitTrackingInspection: () -> Unit,
    manualDrawingActive: Boolean,
    drawnRoutePreview: DrawnRoutePreview,
    drawnRouteSaving: Boolean,
    onUndoManualWaypoint: () -> Unit,
    onCancelManualDrawing: () -> Unit,
    onConfirmDrawnRoute: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val expanded = inspectionActive || manualDrawingActive

    Surface(
        modifier = Modifier
            .animateContentSize(alignment = Alignment.TopEnd)
            .then(
                if (expanded) Modifier.widthIn(max = 560.dp).fillMaxWidth()
                else Modifier.width(56.dp),
            )
            .height(56.dp)
            .testTag("floating_map_bar"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = if (expanded) 24.dp else 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (expanded) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = if (inspectionActive) "Tracking Inspection" else "Draw route",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                inspectionActive -> TextButton(
                    modifier = Modifier.testTag("exit_tracking_inspection"),
                    onClick = onExitTrackingInspection,
                ) { Text("Exit") }
                manualDrawingActive -> {
                    TextButton(
                        onClick = onUndoManualWaypoint,
                        enabled = !drawnRouteSaving && drawnRoutePreview.waypoints.isNotEmpty(),
                    ) { Text("Undo") }
                    TextButton(onClick = onCancelManualDrawing, enabled = !drawnRouteSaving) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = onConfirmDrawnRoute,
                        enabled = !drawnRouteSaving && drawnRoutePreview.canConfirm,
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
                                menuActions.onOpenSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Appearance") },
                            onClick = {
                                menuExpanded = false
                                menuActions.onOpenAppearance()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Back up database") },
                            onClick = {
                                menuExpanded = false
                                menuActions.onBackupDatabase()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Restore database") },
                            onClick = {
                                menuExpanded = false
                                menuActions.onRestoreDatabase()
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
                            text = { Text(if (showDebugInfo) "Hide debug info" else "Show debug info") },
                            onClick = {
                                menuExpanded = false
                                onToggleDebugInfo()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Tracking diagnostics") },
                            onClick = {
                                menuExpanded = false
                                menuActions.onOpenTrackingDiagnostics()
                            },
                        )
                    }
                }
            }
        }
    }
}
