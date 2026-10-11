package net.sagberg.kartoffel

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import net.sagberg.kartoffel.settings.Appearance
import net.sagberg.kartoffel.settings.AppearancePreferences
import net.sagberg.kartoffel.settings.CoverageOutlineStyle
import net.sagberg.kartoffel.settings.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class KartoffelAppTest {
    @get:Rule
    val compose = createComposeRule()

    private val appearancePreferences = AppearancePreferences(
        InstrumentationRegistry.getInstrumentation().targetContext,
    )
    private lateinit var originalAppearance: Appearance

    @Before
    fun rememberAppearance() {
        originalAppearance = appearancePreferences.load()
        appearancePreferences.save(Appearance.Default)
    }

    @After
    fun restoreAppearance() {
        appearancePreferences.save(originalAppearance)
    }

    @Test
    fun settingsOpensFromTheMapAndBackReturnsToIt() {
        compose.setContent { KartoffelApp() }

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Settings").performClick()

        compose.onNodeWithText("Maximum accepted accuracy").assertIsDisplayed()
        compose.onNodeWithTag("coverage_settings_back").performClick()
        compose.onNodeWithText("Kartoffel").assertIsDisplayed()
    }

    @Test
    fun appearanceChoicesApplyAndAreKeptOnTheDevice() {
        lateinit var activity: ComponentActivity
        compose.setContent {
            activity = LocalActivity.current as ComponentActivity
            KartoffelApp()
        }

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("System").assertIsSelected()
        compose.onNodeWithText("Teal").assertIsSelected()

        compose.onNodeWithText("Dark").performClick()
        compose.runOnIdle {
            val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertEquals(false, bars.isAppearanceLightStatusBars)
            assertEquals(false, bars.isAppearanceLightNavigationBars)
        }
        compose.onNodeWithText("Halo").performClick()
        compose.onNodeWithText("Done").performClick()

        compose.runOnIdle {
            assertEquals(
                Appearance(ThemeMode.Dark, CoverageOutlineStyle.Halo),
                appearancePreferences.load(),
            )
        }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("Dark").assertIsSelected()
        compose.onNodeWithText("Halo").assertIsSelected()
        compose.onNodeWithText("Light").performClick()
        compose.runOnIdle {
            val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertEquals(true, bars.isAppearanceLightStatusBars)
            assertEquals(true, bars.isAppearanceLightNavigationBars)
        }
    }
}
