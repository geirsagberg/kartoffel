package net.sagberg.kartoffel.map

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.CoverageCellShape
import net.sagberg.kartoffel.inspection.TrackingInspectionFilter
import net.sagberg.kartoffel.inspection.TrackingInspectionMeasurements
import net.sagberg.kartoffel.inspection.TrackingInspectionSnapshot
import net.sagberg.kartoffel.inspection.InspectionDrawnRoute
import org.junit.Rule
import org.junit.Assert.assertEquals
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
    fun savingDisablesDrawingControlsAndBackUntilConfirmationFinishes() {
        val finishSave = CompletableDeferred<Long>()
        val editor = DrawnRouteEditor(
            pathBetween = { _, _ -> listOf(CoverageCellId(1)) },
            saveRoute = { finishSave.await() },
        )
        editor.enter()
        editor.add(GeoCoordinate(1.0, 1.0))
        editor.add(GeoCoordinate(2.0, 2.0))
        var editable = true
        compose.setContent {
            val state by editor.state.collectAsState()
            val scope = rememberCoroutineScope()
            MaterialTheme {
                MapContent(
                    hasLocationPermission = true,
                    isRecording = false,
                    onRequestLocationPermission = {},
                    onStartRecording = {},
                    onStopRecording = {},
                    onCenterCurrentLocation = {},
                    manualDrawingActive = state.active,
                    drawnRouteSaving = state.saving,
                    drawnRoutePreview = state.preview,
                    onUndoManualWaypoint = editor::undo,
                    onCancelManualDrawing = editor::cancel,
                    onConfirmDrawnRoute = { scope.launch { editor.confirm() } },
                    map = { interactions, _ -> editable = interactions.editable },
                )
            }
        }

        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Undo").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        pressBack()
        compose.onNodeWithText("Discard route?").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(false, editable)
            assertEquals(true, editor.state.value.saving)
            finishSave.complete(1L)
        }
        compose.waitUntil { !editor.state.value.active }
        compose.onNodeWithContentDescription("Draw route").assertIsDisplayed()
    }

    @Test
    fun drawingSupportsMapEditsUndoCancelBackConfirmAndActiveRecording() {
        var confirmed = false
        val editor = DrawnRouteEditor(
            pathBetween = { _, destination ->
                if (destination.latitude >= 3.0) {
                    listOf(CoverageCellId(9), CoverageCellId(10))
                } else {
                    listOf(CoverageCellId(1), CoverageCellId(2))
                }
            },
            saveRoute = { confirmed = true; 1L },
        )
        var stoppedRecording = false
        compose.setContent {
            val state by editor.state.collectAsState()
            val scope = rememberCoroutineScope()
            MaterialTheme {
                MapContent(
                    hasLocationPermission = true,
                    isRecording = true,
                    onRequestLocationPermission = {},
                    onStartRecording = {},
                    onStopRecording = { stoppedRecording = true },
                    onCenterCurrentLocation = {},
                    manualDrawingActive = state.active,
                    drawnRouteSaving = state.saving,
                    drawnRoutePreview = state.preview,
                    onEnterManualDrawing = editor::enter,
                    onUndoManualWaypoint = editor::undo,
                    onAddManualWaypoint = editor::add,
                    onMoveManualWaypoint = editor::move,
                    onCancelManualDrawing = editor::cancel,
                    onConfirmDrawnRoute = { scope.launch { editor.confirm() } },
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
                                Modifier.size(48.dp).testTag("fake_waypoint_move").clickable {
                                    interactions.onMoveWaypoint(1, GeoCoordinate(3.0, 3.0))
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
        compose.onNodeWithText("End trip", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithTag("fake_map_tap").performClick()
        compose.onNodeWithTag("fake_map_tap").performClick()
        compose.onNodeWithContentDescription("Drawn route preview: 2 waypoints, 2 cells")
            .assertIsDisplayed()
        compose.onNodeWithTag("provisional_cell_preview").assertIsDisplayed()
        compose.onNodeWithTag("route_polyline_preview").assertIsDisplayed()
        compose.onNodeWithTag("fake_waypoint_move").performClick()
        compose.runOnIdle {
            assertEquals(listOf(9L, 10L), editor.state.value.preview.cells.map { it.value })
        }
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Undo").assertIsNotEnabled()
        compose.onNodeWithText("Confirm").assertIsNotEnabled()

        compose.onNodeWithTag("fake_map_tap").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(DrawnRoutePreview(), editor.state.value.preview) }

        compose.onNodeWithContentDescription("Draw route").performClick()
        pressBack()
        compose.runOnIdle { assertEquals(false, editor.state.value.active) }
        compose.onAllNodesWithText("Discard route?").assertCountEquals(0)

        compose.onNodeWithContentDescription("Draw route").performClick()
        compose.onNodeWithTag("fake_map_tap").performClick()
        pressBack()
        compose.onNodeWithText("Discard route?").assertIsDisplayed()
        compose.onNodeWithText("Keep drawing").performClick()
        pressBack()
        compose.onNodeWithText("Discard").performClick()
        compose.runOnIdle { assertEquals(false, editor.state.value.active) }

        compose.onNodeWithContentDescription("Draw route").performClick()
        compose.onNodeWithTag("fake_map_tap").performClick()
        compose.onNodeWithTag("fake_map_tap").performClick()
        compose.onNodeWithText("End trip", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(true, stoppedRecording) }
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle { assertEquals(true, confirmed) }
        compose.onAllNodesWithText("Undo").assertCountEquals(0)
        compose.runOnIdle { assertEquals(DrawnRoutePreview(), editor.state.value.preview) }
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
        compose.onNodeWithText("Drawn routes").assertIsDisplayed()
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
    fun trackingInspectionListsOnlySelectedCellRoutesAndConfirmsRemoval() {
        val selectedCell = mutableStateOf<Long?>(null)
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
                cells = emptyList(),
                measurements = TrackingInspectionMeasurements(0, 0, 0),
                drawnRoutes = listOf(
                    route,
                    route.copy(id = 8),
                    route.copy(id = 9, cells = listOf(route.cells.single().copy(id = "456"))),
                ),
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
                    selectedInspectionCellId = selectedCell.value,
                    onRemoveDrawnRoute = {
                        removed = it
                        snapshot.value = snapshot.value.copy(
                            drawnRoutes = snapshot.value.drawnRoutes.filterNot { route -> route.id == it },
                        )
                    },
                    map = { _, _ -> },
                )
            }
        }

        compose.onAllNodesWithTag("drawn_route_list").assertCountEquals(0)
        compose.onAllNodesWithText("Routes", substring = true).assertCountEquals(0)
        compose.runOnIdle { selectedCell.value = 123 }
        compose.onAllNodesWithText("Samples", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Routes (2)").performClick()
        compose.onNodeWithTag("drawn_route_7").assertIsDisplayed()
        compose.onNodeWithTag("drawn_route_8").assertIsDisplayed()
        compose.onAllNodesWithTag("drawn_route_9").assertCountEquals(0)
        compose.onNodeWithTag("remove_drawn_route_7").performClick()
        compose.onNodeWithText("Remove drawn route?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, removed) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(null, removed) }
        compose.onNodeWithTag("remove_drawn_route_7").performClick()
        compose.onNodeWithTag("confirm_remove_drawn_route").performClick()
        compose.runOnIdle { assertEquals(7L, removed) }
        compose.onAllNodesWithTag("drawn_route_7").assertCountEquals(0)
        compose.onNodeWithTag("drawn_route_8").assertIsDisplayed()
        compose.onNodeWithText("Routes (1)").assertIsDisplayed()
        compose.onNodeWithTag("remove_drawn_route_8").performClick()
        compose.onNodeWithTag("confirm_remove_drawn_route").performClick()
        compose.runOnIdle { assertEquals(8L, removed) }
        compose.onAllNodesWithText("Routes", substring = true).assertCountEquals(0)
        compose.runOnIdle { selectedCell.value = 789 }
        compose.onAllNodesWithText("Routes", substring = true).assertCountEquals(0)
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

        compose.onNodeWithText("Track trip", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("start_recording_icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recording_control").performClick()
        compose.runOnIdle { assertEquals(true, requestedRecording.value) }
        compose.onNodeWithTag("stop_recording_icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("End trip", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recording_control").performClick()
        compose.runOnIdle { assertEquals(false, requestedRecording.value) }
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
