package net.sagberg.kartoffel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class KartoffelAppTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsOpensFromTheMapAndBackReturnsToIt() {
        compose.setContent { KartoffelApp() }

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Settings").performClick()

        compose.onNodeWithText("Maximum accepted accuracy").assertIsDisplayed()
        compose.onNodeWithTag("coverage_settings_back").performClick()
        compose.onNodeWithText("Kartoffel").assertIsDisplayed()
    }
}
