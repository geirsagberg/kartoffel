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
class ManualRouteClaimFogIntegrationTest {
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
    fun confirmationPersistsExactPreviewAndWithdrawalPreservesOtherEvidence() = runBlocking {
        val h3 = H3CoverageCells()
        val preview = ManualRouteDrawing(h3).recompute(
            listOf(
                GeoCoordinate(59.9109, 10.7522),
                GeoCoordinate(59.9119, 10.7552),
            ),
        )
        val claimed = preview.cells.map { it.value }.toSet()
        val captured = claimed.first()
        val overlapping = claimed.last()
        val manualOnly = claimed.first { it != captured && it != overlapping }
        database.coverageCells().upsert(captured, 1_000, 1_000, evidenceMaskOf(CoverageEvidenceSource.PASSIVE_TRACKING))
        val overlapClaim = database.manualRouteClaims().create(1_500, setOf(overlapping))
        val loader = PersistedCoverageLoader(database.coverageCells(), database.manualRouteClaims())
        val manualOnlyCenter = h3.shapeOf(CoverageCellId(manualOnly)).boundary.center()
        assertTrue(fogAlpha(manualOnlyCenter, loader.load()) > 0)

        val firstClaim = ManualRouteClaimRecorder(database.manualRouteClaims()) { 2_000 }
            .confirm(preview) ?: error("Expected complete preview to confirm")

        assertTrue(preview.canConfirm)
        assertEquals(claimed.sorted(), database.manualRouteClaims().cellsForClaim(firstClaim))
        assertEquals(claimed.sorted(), loader.load().cells.map { it.id.toLong() }.sorted())
        assertEquals(0, fogAlpha(manualOnlyCenter, loader.load()))

        database.manualRouteClaims().withdraw(firstClaim)

        val withdrawnCoverage = loader.load()
        assertEquals(setOf(captured, overlapping), withdrawnCoverage.cells.map { it.id.toLong() }.toSet())
        assertTrue(fogAlpha(manualOnlyCenter, withdrawnCoverage) > 0)
        assertEquals(0, fogAlpha(h3.shapeOf(CoverageCellId(captured)).boundary.center(), withdrawnCoverage))
        assertEquals(0, fogAlpha(h3.shapeOf(CoverageCellId(overlapping)).boundary.center(), withdrawnCoverage))
        assertEquals(listOf(overlapping), database.manualRouteClaims().cellsForClaim(overlapClaim))
    }

    @Test
    fun failedPreviewCannotPersistPartialClaim() = runBlocking {
        val waypoint = GeoCoordinate(59.91, 10.75)
        val failed = ManualRoutePreview(
            waypoints = listOf(waypoint, waypoint),
            cells = setOf(CoverageCellId(123)),
            failingSegmentIndex = 0,
        )

        val result = ManualRouteClaimRecorder(database.manualRouteClaims()) { 2_000 }
            .confirm(failed)

        assertEquals(null, result)
        assertEquals(emptyList<Long>(), database.manualRouteClaims().allCellIds())
        assertTrue(database.manualRouteClaims().allClaims().isEmpty())
    }

    private fun fogAlpha(coordinate: GeoCoordinate, coverage: CoverageSnapshot): Int {
        val tile = fogTileForCoordinate(coordinate, zoom = 20)
        val rendered = FogOfWarTileProvider(coverage).getTile(tile.x, tile.y, tile.zoom)
            ?: error("Expected Fog of War tile")
        val data = rendered.data ?: error("Expected Fog of War PNG bytes")
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
