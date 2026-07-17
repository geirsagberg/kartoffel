package net.sagberg.kartoffel.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        compose.onNodeWithText("Enable location").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Center on current location").assertCountEquals(0)
        compose.onNodeWithTag("passive_tracking_status").assertIsDisplayed()
        compose.onNodeWithTag("recording_session_control").assertIsDisplayed()
        compose.onNodeWithTag("settings_diagnostics_menu").assertIsDisplayed()
    }

    @Test
    fun currentLocationControlIsAvailableAfterPermissionIsGranted() {
        compose.setCoverageMapContent(hasLocationPermission = true)

        compose.onAllNodesWithText("Enable location").assertCountEquals(0)
        compose.onNodeWithContentDescription("Center on current location").assertIsDisplayed()
    }

    @Test
    fun secondaryActionsAreAvailableFromTheOverflowMenu() {
        compose.setCoverageMapContent(hasLocationPermission = true)

        compose.onNodeWithContentDescription("More options").performClick()

        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Tracking diagnostics").assertIsDisplayed()
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

        compose.onNodeWithText("Start").performClick()
        compose.runOnIdle { assertEquals(true, requestedRecording.value) }
        compose.onNodeWithText("Stop").performClick()
        compose.runOnIdle { assertEquals(false, requestedRecording.value) }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setCoverageMapContent(
        hasLocationPermission: Boolean,
    ) {
        setContent {
            MaterialTheme {
                CoverageMapContent(
                    hasLocationPermission = hasLocationPermission,
                    isRecordingSession = false,
                    onRequestLocationPermission = {},
                    onStartRecordingSession = {},
                    onStopRecordingSession = {},
                    onCenterCurrentLocation = {},
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
