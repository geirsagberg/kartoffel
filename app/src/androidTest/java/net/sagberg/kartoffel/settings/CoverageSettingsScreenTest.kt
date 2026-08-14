package net.sagberg.kartoffel.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CoverageSettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun screenShowsCurrentCoverageSettings() {
        compose.setContent {
            MaterialTheme {
                CoverageSettingsScreen(
                    settings = CoverageSettings(
                        maximumAcceptedAccuracyMeters = 40,
                        maximumInterpolationGapSteps = 7,
                    ),
                    onMaximumAcceptedAccuracyChangeFinished = {},
                    onMaximumInterpolationGapChangeFinished = {},
                    onReset = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Maximum accepted accuracy").assertIsDisplayed()
        compose.onNodeWithText("40 m").assertIsDisplayed()
        compose.onNodeWithText("Maximum interpolation gap").assertIsDisplayed()
        compose.onNodeWithText("7 steps").assertIsDisplayed()
        compose.onNodeWithText("Reset to defaults").assertIsDisplayed()
    }

    @Test
    fun resetRestoresBothCoverageDefaults() {
        var resetCount = 0
        compose.setContent {
            MaterialTheme {
                CoverageSettingsScreen(
                    settings = CoverageSettings.Default,
                    onMaximumAcceptedAccuracyChangeFinished = {},
                    onMaximumInterpolationGapChangeFinished = {},
                    onReset = { resetCount += 1 },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Reset to defaults").performClick()

        compose.runOnIdle { assertEquals(1, resetCount) }
    }

    @Test
    fun slidersCommitDiscreteValuesWhenInteractionFinishes() {
        var acceptedAccuracy: Int? = null
        var interpolationGap: Int? = null
        compose.setContent {
            MaterialTheme {
                CoverageSettingsScreen(
                    settings = CoverageSettings.Default,
                    onMaximumAcceptedAccuracyChangeFinished = { acceptedAccuracy = it },
                    onMaximumInterpolationGapChangeFinished = { interpolationGap = it },
                    onReset = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("maximum_accuracy_slider").performTouchInput {
            swipe(
                start = Offset(width * 0.2f, center.y),
                end = Offset(width * 0.95f, center.y),
            )
        }
        compose.onNodeWithTag("maximum_interpolation_gap_slider")
            .performTouchInput {
                swipe(
                    start = Offset(width * 0.25f, center.y),
                    end = Offset(width * 0.95f, center.y),
                )
            }

        compose.runOnIdle {
            assertEquals(50, acceptedAccuracy)
            assertEquals(10, interpolationGap)
        }
    }
}
