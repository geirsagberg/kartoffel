package net.sagberg.kartoffel.map

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.CoverageSnapshot
import net.sagberg.kartoffel.coverage.H3CoverageCells
import net.sagberg.kartoffel.coverage.PersistedCoverageLoader
import net.sagberg.kartoffel.storage.CoverageEvidenceSource
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.evidenceMaskOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class DrawnRouteFogIntegrationTest {
    private lateinit var database: KartoffelDatabase

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            KartoffelDatabase::class.java,
        ).setDriver(AndroidSQLiteDriver()).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun confirmationPersistsExactPreviewAndRemovalPreservesOtherEvidence() = runBlocking {
        val h3 = H3CoverageCells()
        val preview = RouteDrawing(h3).recompute(
            listOf(
                GeoCoordinate(59.9109, 10.7522),
                GeoCoordinate(59.9139, 10.7612),
            ),
        )
        val routeCells = preview.cells.map { it.value }.toSet()
        val captured = routeCells.first()
        val overlapping = routeCells.last()
        val drawnOnly = routeCells.first { it != captured && it != overlapping }
        database.coverageCells().upsert(captured, 1_000, 1_000, evidenceMaskOf(CoverageEvidenceSource.AUTOMATIC_TRACKING))
        val overlapRoute = database.drawnRoutes().create(1_500, setOf(overlapping))
        val loader = PersistedCoverageLoader(database.coverageCells(), database.drawnRoutes())
        val drawnOnlyCenter = h3.shapeOf(CoverageCellId(drawnOnly)).boundary.center()
        assertTrue(fogAlpha(drawnOnlyCenter, loader.load()) > 0)

        val firstRoute = DrawnRouteRecorder(database.drawnRoutes()) { 2_000 }
            .confirm(preview) ?: error("Expected complete preview to confirm")

        assertTrue(preview.canConfirm)
        assertEquals(routeCells.sorted(), database.drawnRoutes().cellsForRoute(firstRoute))
        assertEquals(routeCells.sorted(), loader.load().cells.map { it.id.toLong() }.sorted())
        assertEquals(0, fogAlpha(drawnOnlyCenter, loader.load()))

        database.drawnRoutes().remove(firstRoute)

        val removedCoverage = loader.load()
        assertEquals(setOf(captured, overlapping), removedCoverage.cells.map { it.id.toLong() }.toSet())
        assertTrue(fogAlpha(drawnOnlyCenter, removedCoverage) > 0)
        assertEquals(0, fogAlpha(h3.shapeOf(CoverageCellId(captured)).boundary.center(), removedCoverage))
        assertEquals(0, fogAlpha(h3.shapeOf(CoverageCellId(overlapping)).boundary.center(), removedCoverage))
        assertEquals(listOf(overlapping), database.drawnRoutes().cellsForRoute(overlapRoute))
    }

    @Test
    fun failedPreviewCannotPersistPartialRoute() = runBlocking {
        val waypoint = GeoCoordinate(59.91, 10.75)
        val failed = DrawnRoutePreview(
            waypoints = listOf(waypoint, waypoint),
            cells = setOf(CoverageCellId(123)),
            failingSegmentIndex = 0,
        )

        val result = DrawnRouteRecorder(database.drawnRoutes()) { 2_000 }
            .confirm(failed)

        assertEquals(null, result)
        assertEquals(emptyList<Long>(), database.drawnRoutes().allCellIds())
        assertTrue(database.drawnRoutes().allRoutes().isEmpty())
    }

    private fun fogAlpha(coordinate: GeoCoordinate, coverage: CoverageSnapshot): Int {
        val tile = fogTileForCoordinate(coordinate, zoom = 20)
        val rendered = FogTileProvider(coverage).getTile(tile.x, tile.y, tile.zoom)
            ?: error("Expected Fog tile")
        val data = rendered.data ?: error("Expected Fog PNG bytes")
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        val pixel = tile.pixelForCoordinate(coordinate)
        return Color.alpha(
            bitmap.getPixel(
                pixel.x.roundToInt().coerceIn(0, FOG_TILE_SIZE - 1),
                pixel.y.roundToInt().coerceIn(0, FOG_TILE_SIZE - 1),
            ),
        )
    }
}

private fun List<GeoCoordinate>.center(): GeoCoordinate = GeoCoordinate(
    latitude = sumOf(GeoCoordinate::latitude) / size,
    longitude = sumOf(GeoCoordinate::longitude) / size,
)
