package net.sagberg.kartoffel.map

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.uber.h3core.H3Core
import java.io.File
import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.CoverageSnapshot
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells
import org.junit.Rule
import org.junit.Test

/** Throwaway: screenshots fog rendering variants on a real map for issue #36. */
class FogRenderingComparison {
    @get:Rule
    val compose = createComposeRule()

    private data class Variant(val name: String, val scale: Int, val union: Boolean)
    private data class View(val name: String, val zoom: Float)

    private val variants = listOf(
        Variant("a-current", 1, false),
        Variant("b-union", 1, true),
        Variant("c-union-2x", 2, true),
        Variant("d-union-3x", 3, true),
    )
    private val views = listOf(
        View("z17.0-close", 17f),
        View("z17.5-close-fractional", 17.5f),
        View("z15.5-medium-fractional", 15.5f),
        View("z14.0-out", 14f),
    )
    private val center = GeoCoordinate(59.9139, 10.7522)
    private val outline = CoverageOutlinePaint(color = 0xFF00696B.toInt(), width = 2f)

    private val snapshot: CoverageSnapshot by lazy {
        val h3 = H3Core.newSystemInstance()
        val cells = H3CoverageCells(h3)
        val ids = mutableSetOf<Long>()
        fun path(a: GeoCoordinate, b: GeoCoordinate) = cells.pathBetween(a, b).forEach { ids += it.value }
        path(GeoCoordinate(59.9100, 10.7300), GeoCoordinate(59.9180, 10.7700))
        path(GeoCoordinate(59.9200, 10.7400), GeoCoordinate(59.9080, 10.7600))
        path(GeoCoordinate(59.9139, 10.7300), GeoCoordinate(59.9139, 10.7800))
        // A solid patch with a hole, to show shared edges inside coverage.
        val patchCenter = cells.cellAt(GeoCoordinate(59.9160, 10.7560)).value
        ids += h3.gridDisk(patchCenter, 3)
        ids -= h3.gridDisk(patchCenter, 0).toSet()
        CoverageSnapshot(ids.map { cells.shapeOf(CoverageCellId(it)) })
    }

    @Test
    fun captureComparison() {
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "fog-comparison")
        out.deleteRecursively()
        out.mkdirs()
        val variant = mutableStateOf(variants.first())
        val view = mutableStateOf(views.first())
        compose.setContent {
            val camera = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(LatLng(center.latitude, center.longitude), view.value.zoom)
            }
            camera.position = CameraPosition.fromLatLngZoom(LatLng(center.latitude, center.longitude), view.value.zoom)
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                uiSettings = MapUiSettings(zoomControlsEnabled = false),
            ) {
                key(variant.value) {
                    val v = variant.value
                    val provider = remember {
                        FogTileProvider(
                            snapshot,
                            makeRenderer = { FogTileRenderer(outline = it, scale = v.scale, unionClear = v.union) },
                        ).apply { updateOutline(outline) }
                    }
                    TileOverlay(tileProvider = provider, fadeIn = false, zIndex = 10f)
                }
            }
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        views.forEach { v ->
            variants.forEach { variantValue ->
                compose.runOnIdle {
                    view.value = v
                    variant.value = variantValue
                }
                compose.waitForIdle()
                SystemClock.sleep(6_000)
                val shot = automation.takeScreenshot()
                File(out, "${v.name}_${variantValue.name}.png").outputStream().use {
                    shot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }

        // Tile cost: render every tile covering the patch area at zoom 17 and 15.
        val report = StringBuilder()
        variants.forEach { v ->
            val renderer = FogTileRenderer(outline = outline, scale = v.scale, unionClear = v.union)
            listOf(17, 15).forEach { zoom ->
                val origin = fogTileForCoordinate(center, zoom)
                val tiles = (-2..2).flatMap { dx -> (-2..2).map { dy -> FogTileCoordinate(origin.x + dx, origin.y + dy, zoom) } }
                repeat(2) { tiles.forEach { renderer.renderPng(it, snapshot.cellsIntersecting(it.latLngBounds(4.0))) } }
                val start = SystemClock.elapsedRealtimeNanos()
                var bytes = 0L
                tiles.forEach { bytes += renderer.renderPng(it, snapshot.cellsIntersecting(it.latLngBounds(4.0))).size }
                val ms = (SystemClock.elapsedRealtimeNanos() - start) / 1e6 / tiles.size
                report.append("${v.name} z$zoom: ${"%.1f".format(ms)} ms/tile, ${bytes / tiles.size / 1024} KiB/tile png, ${renderer.sizePixels}px = ${renderer.sizePixels * renderer.sizePixels * 4 / 1024} KiB decoded\n")
            }
        }
        File(out, "cost.txt").writeText(report.toString())
        println(report)
    }
}
