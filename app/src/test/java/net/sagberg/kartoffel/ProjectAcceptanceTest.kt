package net.sagberg.kartoffel

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectAcceptanceTest {
    private val root = findRepositoryRoot()

    @Test
    fun projectUsesSingleAndroidAppModule() {
        val settings = root.resolve("settings.gradle.kts").readText()

        assertTrue(settings.contains("rootProject.name = \"Kartoffel\""))
        assertTrue(settings.contains("include(\":app\")"))
        assertEquals(
            listOf("include(\":app\")"),
            Regex("""include\("[^"]+"\)""").findAll(settings).map { it.value }.toList(),
        )
    }

    @Test
    fun androidConfigurationKeepsTheMvpAndroid16OnlyFloor() {
        val buildFile = root.resolve("app/build.gradle.kts").readText()

        assertTrue(buildFile.contains("namespace = \"net.sagberg.kartoffel\""))
        assertTrue(buildFile.contains("applicationId = \"net.sagberg.kartoffel\""))
        assertEquals(37, buildFile.intAssignment("compileSdk"))
        assertEquals(36, buildFile.intAssignment("minSdk"))
        assertEquals(37, buildFile.intAssignment("targetSdk"))
        assertTrue(buildFile.contains("alias(libs.plugins.android.application)"))
        assertTrue(buildFile.contains("alias(libs.plugins.kotlin.compose)"))
    }

    @Test
    fun packageAreasExistForMvpBoundaries() {
        val packageRoot = root.resolve("app/src/main/java/net/sagberg/kartoffel")
        val expectedAreas = listOf(
            "tracking",
            "coverage",
            "map",
            "storage",
            "settings",
            "diagnostics",
        )

        expectedAreas.forEach { area ->
            assertTrue(
                "Expected package area '$area' to exist",
                packageRoot.resolve(area).exists(),
            )
        }
    }

    @Test
    fun mapKeyIsInjectedFromIgnoredLocalConfiguration() {
        val appBuild = root.resolve("app/build.gradle.kts").readText()
        val manifest = root.resolve("app/src/main/AndroidManifest.xml").readText()
        val gitignore = root.resolve(".gitignore").readText()

        assertTrue(root.resolve("local.properties.example").readText().contains("MAPS_API_KEY="))
        assertTrue(appBuild.contains("rootProject.file(\"local.properties\")"))
        assertTrue(appBuild.contains("manifestPlaceholders[\"MAPS_API_KEY\"]"))
        assertTrue(manifest.contains("android:name=\"com.google.android.geo.API_KEY\""))
        assertTrue(manifest.contains("android:value=\"${'$'}{MAPS_API_KEY}\""))
        assertTrue(gitignore.contains("local.properties"))
        assertTrue(gitignore.contains("secrets.properties"))
        assertTrue(gitignore.contains("api-keys.properties"))
    }

    @Test
    fun appLaunchesToGoogleBackedCoverageMapWithLocationPermissionWiring() {
        val mainActivity = root
            .resolve("app/src/main/java/net/sagberg/kartoffel/MainActivity.kt")
            .readText()
        val coverageMapScreen = root
            .resolve("app/src/main/java/net/sagberg/kartoffel/map/CoverageMapScreen.kt")
            .readText()
        val manifest = root.resolve("app/src/main/AndroidManifest.xml").readText()

        assertTrue(mainActivity.contains("KartoffelApp()"))
        assertTrue(mainActivity.contains("CoverageMapScreen("))
        assertTrue(mainActivity.contains("TrackingDiagnosticsRoute("))
        assertTrue(coverageMapScreen.contains("GoogleMap("))
        assertTrue(coverageMapScreen.contains("Modifier.fillMaxSize()"))
        assertTrue(coverageMapScreen.contains("MapProperties("))
        assertTrue(coverageMapScreen.contains("isMyLocationEnabled = hasLocationPermission"))
        assertTrue(coverageMapScreen.contains("ActivityResultContracts.RequestPermission()"))
        assertTrue(coverageMapScreen.contains("Manifest.permission.ACCESS_FINE_LOCATION"))
        assertTrue(manifest.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertTrue(manifest.contains("android.permission.ACCESS_COARSE_LOCATION"))
    }

    @Test
    fun recordingSessionsUseAnAndroidLocationForegroundService() {
        val manifest = root.resolve("app/src/main/AndroidManifest.xml").readText()
        val service = root
            .resolve(
                "app/src/main/java/net/sagberg/kartoffel/tracking/RecordingSessionService.kt",
            )

        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_LOCATION"))
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"location\""))
        assertTrue(manifest.contains(".tracking.RecordingSessionService"))
        assertTrue(service.exists())
        assertTrue(service.readText().contains("return START_STICKY"))
    }

    @Test
    fun trackedFilesDoNotContainGeneratedOutputsOrLocalSecrets() {
        val forbidden = trackedFiles().filter { path ->
            path == "local.properties" ||
                path == "secrets.properties" ||
                path == "maps.properties" ||
                path == "api-keys.properties" ||
                path.startsWith("build/") ||
                path.contains("/build/") ||
                path.endsWith(".apk") ||
                path.endsWith(".aab") ||
                path.endsWith(".jks") ||
                path.endsWith(".keystore") ||
                path.endsWith(".p12") ||
                path.endsWith(".pem")
        }

        assertTrue("Forbidden tracked files: $forbidden", forbidden.isEmpty())
    }

    private fun String.intAssignment(name: String): Int {
        val match = Regex("""$name\s*=\s*(\d+)""").find(this)
            ?: error("Missing integer assignment for $name")
        return match.groupValues[1].toInt()
    }

    private fun trackedFiles(): List<String> {
        val process = ProcessBuilder("git", "ls-files")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        return output.lineSequence().filter { it.isNotBlank() }.toList()
    }

    private fun findRepositoryRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        while (!current.resolve("settings.gradle.kts").exists()) {
            current = current.parent ?: error("Could not find repository root")
        }
        return current
    }
}
