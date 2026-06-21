package net.sagberg.kartoffel.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import org.junit.Rule
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

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setCoverageMapContent(
        hasLocationPermission: Boolean,
    ) {
        setContent {
            MaterialTheme {
                CoverageMapContent(
                    hasLocationPermission = hasLocationPermission,
                    onRequestLocationPermission = {},
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
