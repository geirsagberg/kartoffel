package net.sagberg.kartoffel.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import net.sagberg.kartoffel.diagnostics.LatestFixDiagnostics
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnosticsState
import net.sagberg.kartoffel.diagnostics.LocationUpdateState
import net.sagberg.kartoffel.diagnostics.RequestedIntervalReason
import net.sagberg.kartoffel.tracking.RecordingActivity
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageMapContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun permissionRequestIsShownInContextBeforePermissionIsGranted() {
        compose.setCoverageMapContent(hasLocationPermission = false)

        compose.onNodeWithTag("fake_google_map").assertIsDisplayed()
        compose.onNodeWithText("Enable location", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Center on current location").assertCountEquals(0)
        compose.onNodeWithTag("passive_tracking_status").assertIsDisplayed()
        compose.onNodeWithTag("enable_location_control").assertIsDisplayed()
        compose.onAllNodesWithTag("recording_session_control").assertCountEquals(0)
        compose.onNodeWithTag("settings_diagnostics_menu").assertIsDisplayed()
        compose.onAllNodesWithTag("start_recording_icon", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Walking · 5 s").assertCountEquals(0)
    }

    @Test
    fun currentLocationControlIsAvailableAfterPermissionIsGranted() {
        compose.setCoverageMapContent(hasLocationPermission = true)

        compose.onAllNodesWithText("Enable location").assertCountEquals(0)
        compose.onNodeWithContentDescription("Center on current location").assertIsDisplayed()
    }

    @Test
    fun secondaryActionsAreAvailableFromTheOverflowMenu() {
        var diagnosticsOpened = false
        compose.setCoverageMapContent(
            hasLocationPermission = true,
            onOpenTrackingDiagnostics = { diagnosticsOpened = true },
        )

        compose.onNodeWithContentDescription("More options").performClick()

        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Enable Passive Tracking").assertIsDisplayed()
        compose.onNodeWithText("Tracking diagnostics").assertIsDisplayed()
        compose.onNodeWithText("Tracking diagnostics").performClick()
        compose.runOnIdle { assertEquals(true, diagnosticsOpened) }
    }

    @Test
    fun passiveTrackingCanBeEnabledAndDisabledFromTheOverflowMenu() {
        val passiveEnabled = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                CoverageMapContent(
                    hasLocationPermission = true,
                    isRecordingSession = false,
                    isPassiveTrackingEnabled = passiveEnabled.value,
                    onRequestLocationPermission = {},
                    onStartRecordingSession = {},
                    onStopRecordingSession = {},
                    onCenterCurrentLocation = {},
                    onEnablePassiveTracking = { passiveEnabled.value = true },
                    onDisablePassiveTracking = { passiveEnabled.value = false },
                    map = {},
                )
            }
        }

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Enable Passive Tracking").performClick()
        compose.onNodeWithText("Passive on").assertIsDisplayed()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Disable Passive Tracking").performClick()
        compose.onNodeWithText("Passive off").assertIsDisplayed()
    }

    @Test
    fun passiveTrackingCannotBeEnabledDuringARecordingSession() {
        compose.setCoverageMapContent(
            hasLocationPermission = true,
            isRecordingSession = true,
            isPassiveTrackingEnabled = false,
        )

        compose.onNodeWithContentDescription("More options").performClick()

        compose.onNodeWithText("Enable Passive Tracking").assertIsNotEnabled()
    }

    @Test
    fun recordingSessionControlStartsAndStopsDeliberateCapture() {
        val requestedRecording = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                CoverageMapContent(
                    hasLocationPermission = true,
                    isRecordingSession = requestedRecording.value,
                    onRequestLocationPermission = {},
                    onStartRecordingSession = { requestedRecording.value = true },
                    onStopRecordingSession = { requestedRecording.value = false },
                    onCenterCurrentLocation = {},
                    map = {},
                )
            }
        }

        compose.onNodeWithText("Start recording", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recording_session_control").performClick()
        compose.runOnIdle { assertEquals(true, requestedRecording.value) }
        compose.onNodeWithTag("stop_recording_icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Recording · Passive off").assertIsDisplayed()
        compose.onNodeWithText("Stop recording", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recording_session_control").performClick()
        compose.runOnIdle { assertEquals(false, requestedRecording.value) }
    }

    @Test
    fun liveDiagnosticsStayCompactUntilExpanded() {
        compose.setContent {
            MaterialTheme {
                CoverageMapContent(
                    hasLocationPermission = true,
                    isRecordingSession = true,
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
                    onStartRecordingSession = {},
                    onStopRecordingSession = {},
                    onCenterCurrentLocation = {},
                    map = {},
                )
            }
        }

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
    }

    @Test
    fun liveDiagnosticsExplainTheSafeFallbackAndAcceptedFix() {
        compose.setContent {
            MaterialTheme {
                CoverageMapContent(
                    hasLocationPermission = true,
                    isRecordingSession = true,
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
                    onStartRecordingSession = {},
                    onStopRecordingSession = {},
                    onCenterCurrentLocation = {},
                    map = {},
                )
            }
        }

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
                CoverageMapContent(
                    hasLocationPermission = true,
                    isRecordingSession = true,
                    liveTrackingDiagnostics = LiveTrackingDiagnosticsState(
                        trackingActive = true,
                        activityMode = RecordingActivity.STILL,
                        locationUpdateState = LocationUpdateState.SUSPENDED,
                        requestedLocationIntervalMillis = null,
                        intervalReason = RequestedIntervalReason.SUSPENDED_WHILE_STILL,
                    ),
                    diagnosticsNowMillis = 3_000,
                    onRequestLocationPermission = {},
                    onStartRecordingSession = {},
                    onStopRecordingSession = {},
                    onCenterCurrentLocation = {},
                    map = {},
                )
            }
        }

        compose.onNodeWithText("Still · suspended").assertIsDisplayed()
        compose.onNodeWithTag("live_diagnostics_panel").performClick()
        compose.onNodeWithText("Location updates: Suspended").assertIsDisplayed()
        compose.onNodeWithText("Interval reason: Suspended while still").assertIsDisplayed()
        compose.onNodeWithText("Last fix: None").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setCoverageMapContent(
        hasLocationPermission: Boolean,
        isRecordingSession: Boolean = false,
        isPassiveTrackingEnabled: Boolean = false,
        onOpenTrackingDiagnostics: () -> Unit = {},
    ) {
        setContent {
            MaterialTheme {
                CoverageMapContent(
                    hasLocationPermission = hasLocationPermission,
                    isRecordingSession = isRecordingSession,
                    isPassiveTrackingEnabled = isPassiveTrackingEnabled,
                    onRequestLocationPermission = {},
                    onStartRecordingSession = {},
                    onStopRecordingSession = {},
                    onCenterCurrentLocation = {},
                    onOpenTrackingDiagnostics = onOpenTrackingDiagnostics,
                    map = {
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
