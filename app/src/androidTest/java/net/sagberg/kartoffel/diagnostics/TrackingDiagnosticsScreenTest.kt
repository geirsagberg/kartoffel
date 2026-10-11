package net.sagberg.kartoffel.diagnostics

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.sagberg.kartoffel.storage.PersistedActivityMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrackingDiagnosticsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun noRecordingIsPresentedAsANormalEmptyStateAndBackWorks() {
        var wentBack = false
        compose.setContent {
            MaterialTheme {
                TrackingDiagnosticsScreen(
                    history = null,
                    onBack = { wentBack = true },
                )
            }
        }

        compose.onNodeWithText("No recordings yet").assertIsDisplayed()
        compose.onNodeWithTag("tracking_diagnostics_back").performClick()
        compose.runOnIdle { assertTrue(wentBack) }
    }

    @Test
    fun recordingSummaryLabelsCountsAsFixesRatherThanTime() {
        compose.setContent {
            MaterialTheme {
                TrackingDiagnosticsScreen(
                    history = RecordingHistory(
                        isActive = true,
                        durationMillis = 125_000,
                        fixCounts = mapOf(
                            PersistedActivityMode.WALKING to 3,
                            PersistedActivityMode.UNKNOWN to 1,
                        ),
                    ),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Active recording").assertIsDisplayed()
        compose.onNodeWithText("Duration: 2m 5s").assertIsDisplayed()
        compose.onNodeWithText("Fix counts by Activity Mode").assertIsDisplayed()
        compose.onNodeWithText("Walking").assertIsDisplayed()
        compose.onNodeWithText("Unknown").assertIsDisplayed()
        compose.onNodeWithText("Counts are location fixes, not time spent.").assertIsDisplayed()
    }

    @Test
    fun recordingWithNoFixesIsPresentedAsANormalEmptyState() {
        compose.setContent {
            MaterialTheme {
                TrackingDiagnosticsScreen(
                    history = RecordingHistory(
                        isActive = false,
                        durationMillis = 1_000,
                        fixCounts = emptyMap(),
                    ),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("No fixes recorded yet").assertIsDisplayed()
    }
}
