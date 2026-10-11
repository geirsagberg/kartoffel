package net.sagberg.kartoffel.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceTest {
    @Test
    fun missingStoredValuesUseTheDefaults() {
        val appearance = Appearance.fromStored(themeMode = null, coverageOutline = null, fogDetail = null)

        assertEquals(Appearance(ThemeMode.System, CoverageOutlineStyle.Teal, FogDetail.Medium), appearance)
        assertEquals(Appearance.Default, appearance)
    }

    @Test
    fun everyChoiceSurvivesStorage() {
        ThemeMode.entries.forEach { themeMode ->
            CoverageOutlineStyle.entries.forEach { outline ->
                FogDetail.entries.forEach { fogDetail ->
                    val appearance = Appearance(themeMode, outline, fogDetail)

                    val restored = Appearance.fromStored(
                        themeMode = appearance.themeMode.storedValue,
                        coverageOutline = appearance.coverageOutline.storedValue,
                        fogDetail = appearance.fogDetail.storedValue,
                    )

                    assertEquals(appearance, restored)
                }
            }
        }
    }

    @Test
    fun storedValuesAreStableNames() {
        assertEquals(
            listOf("system", "light", "dark"),
            ThemeMode.entries.map { it.storedValue },
        )
        assertEquals(
            listOf("none", "teal", "graphite", "halo"),
            CoverageOutlineStyle.entries.map { it.storedValue },
        )
        assertEquals(
            listOf("low", "medium", "high"),
            FogDetail.entries.map { it.storedValue },
        )
    }

    @Test
    fun unknownStoredValuesFallBackToTheDefaults() {
        val appearance = Appearance.fromStored(themeMode = "sepia", coverageOutline = "glow", fogDetail = "ultra")

        assertEquals(Appearance.Default, appearance)
    }
}
