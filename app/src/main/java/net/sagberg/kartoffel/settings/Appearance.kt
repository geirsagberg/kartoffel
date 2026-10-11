package net.sagberg.kartoffel.settings

import android.content.Context
import androidx.core.content.edit

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

internal data class Appearance(
    val themeMode: ThemeMode,
    val coverageOutline: CoverageOutlineStyle,
) {
    companion object {
        val Default = Appearance(ThemeMode.System, CoverageOutlineStyle.Teal)

        fun fromStored(themeMode: String?, coverageOutline: String?): Appearance =
            Appearance(
                themeMode = ThemeMode.entries.firstOrNull { it.storedValue == themeMode }
                    ?: Default.themeMode,
                coverageOutline = CoverageOutlineStyle.entries
                    .firstOrNull { it.storedValue == coverageOutline }
                    ?: Default.coverageOutline,
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
    )

    fun save(appearance: Appearance) {
        preferences.edit {
            putString(KEY_THEME_MODE, appearance.themeMode.storedValue)
            putString(KEY_COVERAGE_OUTLINE, appearance.coverageOutline.storedValue)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "appearance"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_COVERAGE_OUTLINE = "coverage_outline"
    }
}
