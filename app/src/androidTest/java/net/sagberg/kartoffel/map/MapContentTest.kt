package net.sagberg.kartoffel.map

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import net.sagberg.kartoffel.diagnostics.LatestFixDiagnostics
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnosticsState
import net.sagberg.kartoffel.diagnostics.LocationUpdateState
import net.sagberg.kartoffel.diagnostics.RequestedIntervalReason
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.CoverageCellShape
import net.sagberg.kartoffel.inspection.DiagnosticCellState
import net.sagberg.kartoffel.inspection.DiagnosticProvenance
import net.sagberg.kartoffel.inspection.DiagnosticSample
import net.sagberg.kartoffel.inspection.DiagnosticSampleCell
import net.sagberg.kartoffel.inspection.TrackingInspectionFilter
import net.sagberg.kartoffel.inspection.TrackingInspectionMeasurements
import net.sagberg.kartoffel.inspection.TrackingInspectionSnapshot
import net.sagberg.kartoffel.inspection.InspectionDrawnRoute
import net.sagberg.kartoffel.tracking.RecordingActivity
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun restoreStartsOnlyAfterDatabaseConsumersAreDisposed() {
        val restoreSource = mutableStateOf<Uri?>(null)
        var databaseConsumerActive = false
        var restoreStarted = false
        compose.setContent {
            DatabaseRestoreGate(
                restoreSource = restoreSource.value,
                onRestore = {
                    assertEquals(false, databaseConsumerActive)
                    restoreStarted = true
                },
            ) {
                DisposableEffect(Unit) {
                    databaseConsumerActive = true
                    onDispose { databaseConsumerActive = false }
                }
                Box(
                    modifier = Modifier
                        .testTag("confirm_restore")
                        .clickable { restoreSource.value = Uri.EMPTY },
                )
            }
        }

        compose.onNodeWithTag("confirm_restore").performClick()
        compose.waitUntil { restoreStarted }

        compose.runOnIdle { assertEquals(false, databaseConsumerActive) }
    }

    @Test
    fun permissionRequestIsShownInContextBeforePermissionIsGranted() {
        compose.setMapContent(hasLocationPermission = false)

        compose.onNodeWithTag("fake_google_map").assertIsDisplayed()
        compose.onNodeWithText("Enable location", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Center on current location").assertCountEquals(0)
        compose.onNodeWithContentDescription("Draw route").assertIsDisplayed()
        compose.onNodeWithTag("enable_location_control").assertIsDisplayed()
        compose.onAllNodesWithTag("recording_control").assertCountEquals(0)
        compose.onNodeWithTag("settings_diagnostics_menu").assertIsDisplayed()
        compose.onAllNodesWithTag("start_recording_icon", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Walking · 5 s").assertCountEquals(0)
    }

    @Test
    fun floatingBarShowsTheTitleWithoutRoutineTrackingStatus() {
        compose.setMapContent(hasLocationPermission = true, isAutomaticTrackingEnabled = true)

        compose.onNodeWithText("Kartoffel").assertIsDisplayed()
        compose.onNodeWithContentDescription("More options").assertIsDisplayed()
        compose.onAllNodesWithText("Automatic", substring = true).assertCountEquals(0)
    }

    @Test
    fun drawRouteAndLocationSitAboveTheRecordButton() {
        compose.setMapContent(hasLocationPermission = true)

        val draw = compose.onNodeWithContentDescription("Draw route").getUnclippedBoundsInRoot()
        val location = compose.onNodeWithContentDescription("Center on current location")
            .getUnclippedBoundsInRoot()
        val record = compose.onNodeWithTag("recording_control").getUnclippedBoundsInRoot()

        assertTrue(draw.bottom <= location.top)
        assertTrue(location.bottom <= record.top)
        assertTrue(record.height >= 64.dp)
        assertTrue(record.width > location.width)
        compose.onNodeWithText("Record", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun currentLocationControlIsAvailableAfterPermissionIsGranted() {
        compose.setMapContent(hasLocationPermission = true)

        compose.onAllNodesWithText("Enable location").assertCountEquals(0)
        compose.onNodeWithContentDescription("Center on current location").assertIsDisplayed()
    }

    @Test
    fun mapControlsHaveRoomBelowTheFloatingBar() {
        var mapTopPadding = 0.dp
        compose.setContent {
            MaterialTheme {
                MapContent(
                    hasLocationPermission = true,
                    isRecording = false,
                    onRequestLocationPermission = {},
                    onStartRecording = {},
                    onStopRecording = {},
                    onCenterCurrentLocation = {},
                    map = { _, padding -> mapTopPadding = padding.calculateTopPadding() },
                )
            }
        }

        val bar = compose.onNodeWithTag("floating_map_bar").getUnclippedBoundsInRoot()
        compose.runOnIdle { assertTrue(mapTopPadding >= bar.bottom + 8.dp) }
    }

    @Test
    fun secondaryActionsAreAvailableFromTheOverflowMenu() {
        var settingsOpened = false
        var diagnosticsOpened = false
        var inspectionOpened = false
        var backupOpened = false
        var restoreOpened = false
        var appearanceOpened = false
        compose.setMapContent(
            hasLocationPermission = true,
            onOpenSettings = { settingsOpened = true },
            onOpenTrackingDiagnostics = { diagnosticsOpened = true },
            onEnterTrackingInspection = { inspectionOpened = true },
            onBackupDatabase = { backupOpened = true },
            onRestoreDatabase = { restoreOpened = true },
            onOpenAppearance = { appearanceOpened = true },
        )

        compose.onNodeWithContentDescription("More options").performClick()

        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Back up database").assertIsDisplayed()
        compose.onNodeWithText("Restore database").assertIsDisplayed()
        compose.onNodeWithText("Tracking inspection").assertIsDisplayed()
        compose.onNodeWithText("Enable automatic tracking").assertIsDisplayed()
        compose.onNodeWithText("Tracking diagnostics").assertIsDisplayed()
        compose.onAllNodesWithText("Draw route").assertCountEquals(0)
        compose.onNodeWithText("Settings").performClick()
        compose.runOnIdle { assertEquals(true, settingsOpened) }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Tracking inspection").performClick()
        compose.runOnIdle { assertEquals(true, inspectionOpened) }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Tracking diagnostics").performClick()
        compose.runOnIdle { assertEquals(true, diagnosticsOpened) }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Back up database").performClick()
        compose.runOnIdle { assertEquals(true, backupOpened) }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Restore database").performClick()
        compose.runOnIdle { assertEquals(true, restoreOpened) }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Appearance").performClick()
        compose.runOnIdle { assertEquals(true, appearanceOpened) }
    }

    @Test
    fun drawingSupportsMapEditsUndoCancelBackConfirmAndActiveRecording() {
        val drawingActive = mutableStateOf(false)
        val preview = mutableStateOf(DrawnRoutePreview())
        val routeDrawing = RouteDrawing(pathBetween = { _, destination ->
            if (destination.latitude >= 3.0) {
                listOf(CoverageCellId(9), CoverageCellId(10))
            } else {
                listOf(CoverageCellId(1), CoverageCellId(2))
            }
        })
        var confirmed = false
        var stoppedRecording = false
        compose.setContent {
            MaterialTheme {
                MapContent(
                    hasLocationPermission = true,
                    isRecording = true,
                    onRequestLocationPermission = {},
                    onStartRecording = {},
                    onStopRecording = { stoppedRecording = true },
                    onCenterCurrentLocation = {},
                    manualDrawingActive = drawingActive.value,
                    drawnRoutePreview = preview.value,
                    onEnterManualDrawing = { drawingActive.value = true },
                    onUndoManualWaypoint = {
                        preview.value = routeDrawing.undo(preview.value)
                    },
                    onAddManualWaypoint = { preview.value = routeDrawing.add(preview.value, it) },
                    onMoveManualWaypoint = { index, waypoint ->
                        preview.value = routeDrawing.move(preview.value, index, waypoint)
                    },
                    onCancelManualDrawing = {
                        drawingActive.value = false
                        preview.value = DrawnRoutePreview()
                    },
                    onConfirmDrawnRoute = {
                        confirmed = true
                        drawingActive.value = false
                        preview.value = DrawnRoutePreview()
                    },
                    map = { interactions, _ ->
                        // Centred so that the floating bar over the map does not cover them.
                        Column(Modifier.align(Alignment.Center)) {
                            Box(
                                Modifier.size(48.dp).testTag("fake_map_tap").clickable {
                                    val next = interactions.preview.waypoints.size + 1.0
                                    interactions.onMapClick(GeoCoordinate(next, next))
                                },
                            )
                            Box(
                                Modifier.size(48.dp).testTag("fake_waypoint_drag_end").clickable {
                                    interactions.onWaypointDragEnd(1, GeoCoordinate(3.0, 3.0))
                                },
                            )
                            if (interactions.preview.cells.isNotEmpty()) {
                                Box(Modifier.size(1.dp).testTag("provisional_cell_preview"))
                            }
                            if (interactions.preview.waypoints.size >= 2) {
                                Box(Modifier.size(1.dp).testTag("route_polyline_preview"))
                            }
                        }
                    },
                )
            }
        }

        compose.onNodeWithContentDescription("Draw route").performClick()
        compose.onNodeWithText("Draw route").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Draw route").assertCountEquals(0)
        compose.onNodeWithText("Undo").assertIsNotEnabled()
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Stop", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithTag("fake_map_tap").performClick()
        compose.onNodeWithTag("fake_map_tap").performClick()
        compose.onNodeWithContentDescription("Drawn route preview: 2 waypoints, 2 cells")
            .assertIsDisplayed()
        compose.onNodeWithTag("provisional_cell_preview").assertIsDisplayed()
        compose.onNodeWithTag("route_polyline_preview").assertIsDisplayed()
        compose.onNodeWithTag("fake_waypoint_drag_end").performClick()
        compose.runOnIdle {
            assertEquals(listOf(9L, 10L), preview.value.cells.map { it.value })
        }
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Undo").assertIsNotEnabled()
        compose.onNodeWithText("Confirm").assertIsNotEnabled()

        compose.onNodeWithTag("fake_map_tap").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(DrawnRoutePreview(), preview.value) }

        compose.onNodeWithContentDescription("Draw route").performClick()
        pressBack()
        compose.runOnIdle { assertEquals(false, drawingActive.value) }
        compose.onAllNodesWithText("Discard route?").assertCountEquals(0)

        compose.onNodeWithContentDescription("Draw route").performClick()
        compose.onNodeWithTag("fake_map_tap").performClick()
        pressBack()
        compose.onNodeWithText("Discard route?").assertIsDisplayed()
        compose.onNodeWithText("Keep drawing").performClick()
        pressBack()
        compose.onNodeWithText("Discard").performClick()
        compose.runOnIdle { assertEquals(false, drawingActive.value) }

        compose.onNodeWithContentDescription("Draw route").performClick()
        compose.onNodeWithTag("fake_map_tap").performClick()
        compose.onNodeWithTag("fake_map_tap").performClick()
        compose.onNodeWithText("Stop", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(true, stoppedRecording) }
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle { assertEquals(true, confirmed) }
        compose.onAllNodesWithText("Undo").assertCountEquals(0)
        compose.runOnIdle { assertEquals(DrawnRoutePreview(), preview.value) }
    }

    @Test
    fun trackingInspectionHidesNormalMapActions() {
        var exited = false
        compose.setMapContent(
            hasLocationPermission = true,
            inspectionActive = true,
            onExitTrackingInspection = { exited = true },
        )

        compose.onNodeWithText("Tracking Inspection").assertIsDisplayed()
        compose.onNodeWithTag("inspection_filter_controls").assertIsDisplayed()
        compose.onNodeWithText("All tracking").assertIsDisplayed()
        compose.onNodeWithText("All time").assertIsDisplayed()
        compose.onAllNodesWithTag("recording_control").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Center on current location").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Draw route").assertCountEquals(0)
        compose.onAllNodesWithTag("live_diagnostics_panel").assertCountEquals(0)
        compose.onAllNodesWithTag("settings_diagnostics_menu").assertCountEquals(0)
        compose.onNodeWithTag("exit_tracking_inspection").performClick()
        compose.runOnIdle { assertEquals(true, exited) }
    }

    @Test
    fun trackingInspectionTogglesShowingAndConfirmsRemovalOfOneDrawnRoute() {
        val selected = mutableStateOf<Long?>(null)
        var removed: Long? = null
        val route = InspectionDrawnRoute(
            id = 7,
            createdAtMillis = 1_000,
            cells = listOf(
                CoverageCellShape(
                    id = "123",
                    boundary = listOf(
                        GeoCoordinate(59.91, 10.75),
                        GeoCoordinate(59.92, 10.75),
                        GeoCoordinate(59.91, 10.76),
                    ),
                ),
            ),
        )
        val snapshot = mutableStateOf(
            TrackingInspectionSnapshot(
                filter = TrackingInspectionFilter.Default,
                availableScopes = emptyList(),
                availableRecordings = emptyList(),
                cells = emptyList(),
                measurements = TrackingInspectionMeasurements(0, 0, 0),
                drawnRoutes = listOf(route),
            ),
        )
        compose.setContent {
            MaterialTheme {
                MapContent(
                    hasLocationPermission = true,
                    isRecording = false,
                    onRequestLocationPermission = {},
                    onStartRecording = {},
                    onStopRecording = {},
                    onCenterCurrentLocation = {},
                    inspectionActive = true,
                    inspectionSnapshot = snapshot.value,
                    selectedDrawnRouteId = selected.value,
                    onSelectDrawnRoute = { selected.value = it },
                    onRemoveDrawnRoute = {
                        removed = it
                        snapshot.value = snapshot.value.copy(drawnRoutes = emptyList())
                    },
                    map = { _, _ ->
                        Box(
                            Modifier
                                .size(1.dp)
                                .testTag(
                                    if (snapshot.value.drawnRoutes.isEmpty()) {
                                        "refreshed_effective_coverage"
                                    } else {
                                        "drawn_route_coverage_preview"
                                    },
                                ),
                        )
                    },
                )
            }
        }

        compose.onNodeWithText("Drawn route", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("remove_drawn_route_7").assertWidthIsAtLeast(64.dp)
        compose.onNodeWithText("Show").performClick()
        compose.runOnIdle { assertEquals(7L, selected.value) }
        compose.onNodeWithText("Hide").performClick()
        compose.runOnIdle { assertEquals(null, selected.value) }
        compose.onNodeWithText("Show").performClick()
        compose.runOnIdle { assertEquals(7L, selected.value) }
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Remove drawn route?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(null, removed) }
        compose.onNodeWithText("Remove").performClick()
        compose.onAllNodesWithText("Remove", useUnmergedTree = true)[1].performClick()
        compose.runOnIdle { assertEquals(7L, removed) }
        compose.onAllNodesWithTag("drawn_route_7").assertCountEquals(0)
        compose.onNodeWithTag("refreshed_effective_coverage").assertIsDisplayed()
    }

    @Test
    fun drawnRouteListIsHeightLimitedWithUsableActions() {
        val boundary = listOf(
            GeoCoordinate(59.91, 10.75),
            GeoCoordinate(59.92, 10.75),
            GeoCoordinate(59.91, 10.76),
        )
        val routes = (1L..6L).map { id ->
            InspectionDrawnRoute(
                id = id,
                createdAtMillis = id * 1_000,
                cells = listOf(CoverageCellShape(id.toString(), boundary)),
            )
        }
        val snapshot = TrackingInspectionSnapshot(
            filter = TrackingInspectionFilter.Default,
            availableScopes = emptyList(),
            availableRecordings = emptyList(),
            cells = emptyList(),
            measurements = TrackingInspectionMeasurements(0, 0, 0),
            drawnRoutes = routes,
        )
        compose.setMapContent(
            hasLocationPermission = true,
            inspectionActive = true,
            inspectionSnapshot = snapshot,
        )

        compose.onNodeWithTag("drawn_route_list").assertHeightIsEqualTo(220.dp)
        compose.onNodeWithTag("remove_drawn_route_1").assertWidthIsAtLeast(64.dp)
    }

    @Test
    fun selectedInspectionCellShowsSummaryAndExpandableLosslessHistory() {
        val cell = DiagnosticSampleCell(
            cellId = 123,
            boundary = listOf(
                GeoCoordinate(59.91, 10.75),
                GeoCoordinate(59.92, 10.75),
                GeoCoordinate(59.91, 10.76),
            ),
            state = DiagnosticCellState.Observed,
            provenance = setOf(
                DiagnosticProvenance.AutomaticObserved,
                DiagnosticProvenance.AutomaticRejected,
            ),
            acceptedCount = 1,
            rejectedCount = 1,
            evidenceFirstMillis = 1_000,
            evidenceLastMillis = 2_000,
            sampleFirstMillis = 1_000,
            sampleLastMillis = 2_000,
            samples = listOf(
                DiagnosticSample(2, 2_000, 12.0, "automatic_tracking", "window", null, "walking", false, "accuracy"),
                DiagnosticSample(1, 1_000, 8.0, "automatic_tracking", "window", null, "walking", true, null),
            ),
        )
        val snapshot = TrackingInspectionSnapshot(
            filter = TrackingInspectionFilter.Default,
            availableScopes = emptyList(),
            availableRecordings = emptyList(),
            cells = listOf(cell),
            measurements = TrackingInspectionMeasurements(1, 2, 3),
        )
        compose.setMapContent(
            hasLocationPermission = true,
            inspectionActive = true,
            inspectionSnapshot = snapshot,
            selectedInspectionCellId = cell.cellId,
        )

        compose.onAllNodesWithText("Observed").assertCountEquals(2)
        compose.onNodeWithText("2 total · 1 accepted · 1 rejected · Automatic").assertIsDisplayed()
        compose.onAllNodesWithText("Accepted", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Samples (2)").performClick()
        compose.onNodeWithText("Accepted · 8 m", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Rejected · 12 m", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Newest first").assertIsDisplayed()
    }

    @Test
    fun automaticTrackingCanBeEnabledAndDisabledFromTheOverflowMenu() {
        val automaticEnabled = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                MapContent(
                    hasLocationPermission = true,
                    isRecording = false,
                    isAutomaticTrackingEnabled = automaticEnabled.value,
                    onRequestLocationPermission = {},
                    onStartRecording = {},
                    onStopRecording = {},
                    onCenterCurrentLocation = {},
                    onEnableAutomaticTracking = { automaticEnabled.value = true },
                    onDisableAutomaticTracking = { automaticEnabled.value = false },
                    map = { _, _ -> },
                )
            }
        }

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Enable automatic tracking").performClick()
        compose.runOnIdle { assertEquals(true, automaticEnabled.value) }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Disable automatic tracking").performClick()
        compose.runOnIdle { assertEquals(false, automaticEnabled.value) }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Enable automatic tracking").assertIsDisplayed()
    }

    @Test
    fun automaticTrackingCannotBeEnabledDuringARecording() {
        compose.setMapContent(
            hasLocationPermission = true,
            isRecording = true,
            isAutomaticTrackingEnabled = false,
        )

        compose.onNodeWithContentDescription("More options").performClick()

        compose.onNodeWithText("Enable automatic tracking").assertIsNotEnabled()
        compose.onNodeWithText("Restore database").assertIsNotEnabled()
    }

    @Test
    fun recordingControlStartsAndStopsDeliberateCapture() {
        val requestedRecording = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                MapContent(
                    hasLocationPermission = true,
                    isRecording = requestedRecording.value,
                    onRequestLocationPermission = {},
                    onStartRecording = { requestedRecording.value = true },
                    onStopRecording = { requestedRecording.value = false },
                    onCenterCurrentLocation = {},
                    map = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("Record", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("start_recording_icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recording_control").performClick()
        compose.runOnIdle { assertEquals(true, requestedRecording.value) }
        compose.onNodeWithTag("stop_recording_icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Stop", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recording_control").performClick()
        compose.runOnIdle { assertEquals(false, requestedRecording.value) }
    }

    @Test
    fun liveDiagnosticsStayCompactUntilExpanded() {
        compose.setContent {
            MaterialTheme {
                MapContent(
                    hasLocationPermission = true,
                    isRecording = true,
                    liveTrackingDiagnostics = LiveTrackingDiagnosticsState(
                        trackingActive = true,
                        activityMode = RecordingActivity.WALKING,
                        locationUpdateState = LocationUpdateState.ACTIVE,
                        requestedLocationIntervalMillis = 5_000,
                        intervalReason = RequestedIntervalReason.SPEED_OVERRIDE,
                        latestFix = LatestFixDiagnostics(
                            capturedAtMillis = 2_000,
                            accuracyMeters = 34.0,
                            accepted = false,
                            rejectionReason = "accuracy_exceeds_recording_limit",
                        ),
                    ),
                    diagnosticsNowMillis = 12_000,
                    onRequestLocationPermission = {},
                    onStartRecording = {},
                    onStopRecording = {},
                    onCenterCurrentLocation = {},
                    map = { _, _ -> },
                )
            }
        }

        compose.onAllNodesWithTag("live_diagnostics_panel").assertCountEquals(0)
        compose.onNodeWithTag("settings_diagnostics_menu").performClick()
        compose.onNodeWithText("Show debug info").performClick()
        compose.onNodeWithText("Walking · 5 s").assertIsDisplayed()
        compose.onAllNodesWithText("Speed override").assertCountEquals(0)

        compose.onNodeWithTag("live_diagnostics_panel").performClick()

        compose.onNodeWithText("Location updates: Active").assertIsDisplayed()
        compose.onNodeWithText("Interval reason: Speed override").assertIsDisplayed()
        compose.onNodeWithText("Last fix: 10 s ago").assertIsDisplayed()
        compose.onNodeWithText(
            "Latest fix: Rejected · 34 m · Accuracy exceeds recording limit",
        ).assertIsDisplayed()
        compose.onAllNodesWithText("History").assertCountEquals(0)
        compose.onNodeWithTag("settings_diagnostics_menu").performClick()
        compose.onNodeWithText("Hide debug info").performClick()
        compose.onAllNodesWithTag("live_diagnostics_panel").assertCountEquals(0)
    }

    @Test
    fun liveDiagnosticsExplainTheSafeFallbackAndAcceptedFix() {
        compose.setContent {
            MaterialTheme {
                MapContent(
                    hasLocationPermission = true,
                    isRecording = true,
                    liveTrackingDiagnostics = LiveTrackingDiagnosticsState(
                        trackingActive = true,
                        activityMode = RecordingActivity.UNKNOWN,
                        locationUpdateState = LocationUpdateState.ACTIVE,
                        requestedLocationIntervalMillis = 5_000,
                        intervalReason = RequestedIntervalReason.SAFE_FALLBACK,
                        latestFix = LatestFixDiagnostics(
                            capturedAtMillis = 1_000,
                            accuracyMeters = 8.0,
                            accepted = true,
                            rejectionReason = null,
                        ),
                    ),
                    diagnosticsNowMillis = 3_000,
                    onRequestLocationPermission = {},
                    onStartRecording = {},
                    onStopRecording = {},
                    onCenterCurrentLocation = {},
                    map = { _, _ -> },
                )
            }
        }

        compose.onNodeWithTag("settings_diagnostics_menu").performClick()
        compose.onNodeWithText("Show debug info").performClick()
        compose.onNodeWithText("Unknown · 5 s fallback").assertIsDisplayed()
        compose.onNodeWithTag("live_diagnostics_panel").performClick()
        compose.onNodeWithText("Interval reason: Safe fallback").assertIsDisplayed()
        compose.onNodeWithText("Last fix: 2 s ago").assertIsDisplayed()
        compose.onNodeWithText("Latest fix: Accepted · 8 m").assertIsDisplayed()
    }

    @Test
    fun liveDiagnosticsExplainSuspensionWhileStill() {
        compose.setContent {
            MaterialTheme {
                MapContent(
                    hasLocationPermission = true,
                    isRecording = true,
                    liveTrackingDiagnostics = LiveTrackingDiagnosticsState(
                        trackingActive = true,
                        activityMode = RecordingActivity.STILL,
                        locationUpdateState = LocationUpdateState.SUSPENDED,
                        requestedLocationIntervalMillis = null,
                        intervalReason = RequestedIntervalReason.SUSPENDED_WHILE_STILL,
                    ),
                    diagnosticsNowMillis = 3_000,
                    onRequestLocationPermission = {},
                    onStartRecording = {},
                    onStopRecording = {},
                    onCenterCurrentLocation = {},
                    map = { _, _ -> },
                )
            }
        }

        compose.onNodeWithTag("settings_diagnostics_menu").performClick()
        compose.onNodeWithText("Show debug info").performClick()
        compose.onNodeWithText("Still · suspended").assertIsDisplayed()
        compose.onNodeWithTag("live_diagnostics_panel").performClick()
        compose.onNodeWithText("Location updates: Suspended").assertIsDisplayed()
        compose.onNodeWithText("Interval reason: Suspended while still").assertIsDisplayed()
        compose.onNodeWithText("Last fix: None").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setMapContent(
        hasLocationPermission: Boolean,
        isRecording: Boolean = false,
        isAutomaticTrackingEnabled: Boolean = false,
        onOpenSettings: () -> Unit = {},
        onOpenTrackingDiagnostics: () -> Unit = {},
        onBackupDatabase: () -> Unit = {},
        onRestoreDatabase: () -> Unit = {},
        onOpenAppearance: () -> Unit = {},
        inspectionActive: Boolean = false,
        onEnterTrackingInspection: () -> Unit = {},
        onExitTrackingInspection: () -> Unit = {},
        inspectionSnapshot: TrackingInspectionSnapshot? = null,
        selectedInspectionCellId: Long? = null,
    ) {
        setContent {
            MaterialTheme {
                MapContent(
                    hasLocationPermission = hasLocationPermission,
                    isRecording = isRecording,
                    isAutomaticTrackingEnabled = isAutomaticTrackingEnabled,
                    onRequestLocationPermission = {},
                    onStartRecording = {},
                    onStopRecording = {},
                    onCenterCurrentLocation = {},
                    menuActions = MapMenuActions(
                        onOpenSettings = onOpenSettings,
                        onOpenTrackingDiagnostics = onOpenTrackingDiagnostics,
                        onBackupDatabase = onBackupDatabase,
                        onRestoreDatabase = onRestoreDatabase,
                        onOpenAppearance = onOpenAppearance,
                    ),
                    inspectionActive = inspectionActive,
                    onEnterTrackingInspection = onEnterTrackingInspection,
                    onExitTrackingInspection = onExitTrackingInspection,
                    inspectionSnapshot = inspectionSnapshot,
                    selectedInspectionCellId = selectedInspectionCellId,
                    map = { _, _ ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("fake_google_map"),
                        )
                    },
                )
            }
        }
    }
}
