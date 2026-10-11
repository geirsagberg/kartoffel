package net.sagberg.kartoffel.settings

import android.content.Context
import androidx.core.content.edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import net.sagberg.kartoffel.ui.theme.LocalDarkTheme

internal enum class ThemeMode(val storedValue: String, val label: String) {
    System("system", "System"),
    Light("light", "Light"),
    Dark("dark", "Dark"),
}

internal enum class CoverageOutlineStyle(val storedValue: String, val label: String) {
    None("none", "None"),
    Teal("teal", "Teal"),
    Graphite("graphite", "Graphite"),
    Halo("halo", "Halo"),
}

/** How sharply the Fog is drawn. Higher detail draws each map tile with more pixels, at more cost. */
internal enum class FogDetail(val storedValue: String, val label: String, val tileScale: Int) {
    Low("low", "Low", 1),
    Medium("medium", "Medium", 2),
    High("high", "High", 3),
}

internal data class Appearance(
    val themeMode: ThemeMode,
    val coverageOutline: CoverageOutlineStyle,
    val fogDetail: FogDetail,
) {
    companion object {
        val Default = Appearance(ThemeMode.System, CoverageOutlineStyle.Teal, FogDetail.Medium)

        fun fromStored(themeMode: String?, coverageOutline: String?, fogDetail: String?): Appearance =
            Appearance(
                themeMode = ThemeMode.entries.firstOrNull { it.storedValue == themeMode }
                    ?: Default.themeMode,
                coverageOutline = CoverageOutlineStyle.entries
                    .firstOrNull { it.storedValue == coverageOutline }
                    ?: Default.coverageOutline,
                fogDetail = FogDetail.entries.firstOrNull { it.storedValue == fogDetail }
                    ?: Default.fogDetail,
            )
    }
}

/** Device-local appearance choices, kept outside the database. */
internal class AppearancePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): Appearance = Appearance.fromStored(
        themeMode = preferences.getString(KEY_THEME_MODE, null),
        coverageOutline = preferences.getString(KEY_COVERAGE_OUTLINE, null),
        fogDetail = preferences.getString(KEY_FOG_DETAIL, null),
    )

    fun save(appearance: Appearance) {
        preferences.edit {
            putString(KEY_THEME_MODE, appearance.themeMode.storedValue)
            putString(KEY_COVERAGE_OUTLINE, appearance.coverageOutline.storedValue)
            putString(KEY_FOG_DETAIL, appearance.fogDetail.storedValue)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "appearance"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_COVERAGE_OUTLINE = "coverage_outline"
        const val KEY_FOG_DETAIL = "fog_detail"
    }
}

/** Shared by the coverage edge and the recording reminder. None uses the active-state color. */
@Composable
internal fun CoverageOutlineStyle.color(): Color {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        CoverageOutlineStyle.None, CoverageOutlineStyle.Teal -> colors.primary
        CoverageOutlineStyle.Graphite -> colors.onSurface.copy(alpha = 0.9f)
        CoverageOutlineStyle.Halo -> {
            val haloColor = if (LocalDarkTheme.current) colors.onSurface else colors.inverseOnSurface
            haloColor.copy(alpha = 0.94f)
        }
    }
}
