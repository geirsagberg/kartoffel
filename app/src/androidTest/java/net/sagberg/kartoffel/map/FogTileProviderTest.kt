package net.sagberg.kartoffel.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlin.math.hypot
import kotlin.math.roundToInt
import net.sagberg.kartoffel.coverage.CoverageSnapshot
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells
import net.sagberg.kartoffel.coverage.SeededCoverageCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FogTileProviderTest {
    @Test
    fun fullyFoggedTilesReusePreRenderedPngBytes() {
        val provider = FogTileProvider(CoverageSnapshot.Empty)

        val first = provider.getTile(x = 0, y = 0, zoom = 1)
            ?: error("Expected first fully fogged tile")
        val second = provider.getTile(x = 1, y = 1, zoom = 1)
            ?: error("Expected second fully fogged tile")

        assertSame(first.data, second.data)
    }

    @Test
    fun coverageCanChangeWithoutReplacingTheTileProvider() {
        val coveredSnapshot = SeededCoverageCells.snapshot()
        val visitedCell = coveredSnapshot.cells.first()
        val visitedCellCenter = GeoCoordinate(
            latitude = (visitedCell.bounds.south + visitedCell.bounds.north) / 2.0,
            longitude = (visitedCell.bounds.west + visitedCell.bounds.east) / 2.0,
        )
        val tile = fogTileForCoordinate(visitedCellCenter, zoom = 15)
        val provider = FogTileProvider(CoverageSnapshot.Empty)

        val foggedTile = provider.getTile(tile.x, tile.y, tile.zoom)
            ?: error("Expected Fog provider to return a tile")
        val foggedTileData = foggedTile.data ?: error("Expected PNG tile bytes")
        val foggedBitmap = BitmapFactory.decodeByteArray(
            foggedTileData,
            0,
            foggedTileData.size,
        )

        provider.updateCoverage(coveredSnapshot)

        val updatedTile = provider.getTile(tile.x, tile.y, tile.zoom)
            ?: error("Expected Fog provider to return an updated tile")
        val updatedTileData = updatedTile.data ?: error("Expected updated PNG tile bytes")
        val updatedBitmap = BitmapFactory.decodeByteArray(
            updatedTileData,
            0,
            updatedTileData.size,
        )
        val coveredPixel = tile.pixelForCoordinate(visitedCellCenter)
        val x = coveredPixel.x.roundToInt().coerceIn(0, FOG_TILE_SIZE - 1)
        val y = coveredPixel.y.roundToInt().coerceIn(0, FOG_TILE_SIZE - 1)

        assertTrue(Color.alpha(foggedBitmap.getPixel(x, y)) > 0)
        assertEquals(0, Color.alpha(updatedBitmap.getPixel(x, y)))
    }

    @Test
    fun generatedTileKeepsFogOpaqueAndClearsVisitedCellInterior() {
        val snapshot = SeededCoverageCells.snapshot()
        val visitedCell = snapshot.cells.first()
        val visitedCellCenter = GeoCoordinate(
            latitude = (visitedCell.bounds.south + visitedCell.bounds.north) / 2.0,
            longitude = (visitedCell.bounds.west + visitedCell.bounds.east) / 2.0,
        )
        val tile = fogTileForCoordinate(visitedCellCenter, zoom = 15)
        val renderedTile = FogTileProvider(snapshot).getTile(
            x = tile.x,
            y = tile.y,
            zoom = tile.zoom,
        )
            ?: error("Expected Fog provider to return a tile")
        val renderedTileData = renderedTile.data
            ?: error("Expected Fog provider to return PNG tile bytes")
        val bitmap = BitmapFactory.decodeByteArray(
            renderedTileData,
            0,
            renderedTileData.size,
        )
        val clearedPixel = tile.pixelForCoordinate(visitedCellCenter)
        val clearedX = clearedPixel.x.roundToInt().coerceIn(0, FOG_TILE_SIZE - 1)
        val clearedY = clearedPixel.y.roundToInt().coerceIn(0, FOG_TILE_SIZE - 1)

        assertTrue(Color.alpha(bitmap.getPixel(FOG_TILE_SIZE - 4, 4)) > 0)
        assertEquals(0, Color.alpha(bitmap.getPixel(clearedX, clearedY)))
    }

    @Test
    fun outlineIsDrawnAlongTheEdgeOfVisitedCoverage() {
        val edge = LoneCellEdge()
        val provider = FogTileProvider(edge.snapshot).apply {
            updateOutline(CoverageOutlinePaint(Color.RED, width = 4f))
        }

        val bitmap = provider.render(edge.tile)

        val onEdge = bitmap.pixelAt(edge.midpoint)
        assertEquals(255, Color.alpha(onEdge))
        assertTrue(Color.red(onEdge) > 200 && Color.green(onEdge) < 60)
        assertEquals(0, Color.alpha(bitmap.pixelAt(edge.inside(8f))))
        assertEquals(plainFog, bitmap.getPixel(2, 2))
    }

    @Test
    fun noOutlineLeavesTheEdgeAsPlainFogAndClearing() {
        val edge = LoneCellEdge()

        val bitmap = FogTileProvider(edge.snapshot).render(edge.tile)

        assertEquals(plainFog, bitmap.pixelAt(edge.outside(3f)))
        assertEquals(0, Color.alpha(bitmap.pixelAt(edge.inside(3f))))
    }

    @Test
    fun haloSpreadsIntoTheFogButNotIntoVisitedCoverage() {
        val edge = LoneCellEdge()
        val provider = FogTileProvider(edge.snapshot).apply {
            updateOutline(
                CoverageOutlinePaint(
                    color = Color.WHITE,
                    width = 1.5f,
                    halo = CoverageOutlinePaint.Halo(Color.WHITE, width = 10f, blurRadius = 5f),
                ),
            )
        }

        val bitmap = provider.render(edge.tile)

        assertTrue(Color.red(bitmap.pixelAt(edge.outside(4f))) > Color.red(plainFog))
        assertEquals(0, Color.alpha(bitmap.pixelAt(edge.inside(4f))))
        assertEquals(plainFog, bitmap.getPixel(2, 2))
    }

    @Test
    fun changingTheOutlineRendersNewTiles() {
        val edge = LoneCellEdge()
        val provider = FogTileProvider(edge.snapshot).apply {
            updateOutline(CoverageOutlinePaint(Color.RED, width = 4f))
        }

        assertFalse(provider.updateOutline(CoverageOutlinePaint(Color.RED, width = 4f)))
        assertTrue(provider.updateOutline(CoverageOutlinePaint(Color.BLUE, width = 4f)))

        val onEdge = provider.render(edge.tile).pixelAt(edge.midpoint)
        assertTrue(Color.blue(onEdge) > 200 && Color.red(onEdge) < 60)
    }

    @Test
    fun tileRenderedWithAnOldOutlineRequestsARetry() {
        val edge = LoneCellEdge()
        lateinit var provider: FogTileProvider
        var changeOutline = true
        provider = FogTileProvider(edge.snapshot, renderPng = { renderer, tile, cells ->
            renderer.renderPng(tile, cells).also {
                if (changeOutline) {
                    changeOutline = false
                    provider.updateOutline(CoverageOutlinePaint(Color.BLUE, width = 4f))
                }
            }
        })

        assertNull(provider.getTile(edge.tile.x, edge.tile.y, edge.tile.zoom))
        val onEdge = provider.render(edge.tile).pixelAt(edge.midpoint)
        assertTrue(Color.blue(onEdge) > 200 && Color.red(onEdge) < 60)
    }

    @Test
    fun tileRenderedWithOldCoverageRequestsARetry() {
        val edge = LoneCellEdge()
        lateinit var provider: FogTileProvider
        var changeCoverage = true
        provider = FogTileProvider(CoverageSnapshot.Empty, renderPng = { renderer, tile, cells ->
            renderer.renderPng(tile, cells).also {
                if (changeCoverage) {
                    changeCoverage = false
                    provider.updateCoverage(edge.snapshot)
                }
            }
        })

        assertNull(provider.getTile(edge.tile.x, edge.tile.y, edge.tile.zoom))
        assertEquals(0, Color.alpha(provider.render(edge.tile).pixelAt(edge.inside(8f))))
    }

    /** One H3 Coverage Cell in Oslo, large enough at zoom 17 to probe around one of its edges. */
    private class LoneCellEdge {
        private val cell = H3CoverageCells().let { cells ->
            cells.shapeOf(cells.cellAt(GeoCoordinate(latitude = 59.9139, longitude = 10.7522)))
        }
        val snapshot = CoverageSnapshot(listOf(cell))
        private val center = GeoCoordinate(
            latitude = cell.boundary.map { it.latitude }.average(),
            longitude = cell.boundary.map { it.longitude }.average(),
        )
        val tile = fogTileForCoordinate(center, zoom = 17)
        private val centerPixel = tile.pixelForCoordinate(center)
        // The edge whose midpoint is nearest the middle of the tile, so probes stay inside it.
        val midpoint = cell.boundary.indices
            .map { index ->
                val a = tile.pixelForCoordinate(cell.boundary[index])
                val b = tile.pixelForCoordinate(cell.boundary[(index + 1) % cell.boundary.size])
                TilePixelPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            }
            .minBy { hypot(it.x - FOG_TILE_SIZE / 2f, it.y - FOG_TILE_SIZE / 2f) }
        private val outward = hypot(midpoint.x - centerPixel.x, midpoint.y - centerPixel.y).let { length ->
            TilePixelPoint((midpoint.x - centerPixel.x) / length, (midpoint.y - centerPixel.y) / length)
        }

        fun outside(distance: Float) =
            TilePixelPoint(midpoint.x + outward.x * distance, midpoint.y + outward.y * distance)

        fun inside(distance: Float) = outside(-distance)
    }

    private fun FogTileProvider.render(tile: FogTileCoordinate): Bitmap {
        val data = getTile(tile.x, tile.y, tile.zoom)?.data ?: error("Expected PNG tile bytes")
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    private fun Bitmap.pixelAt(point: TilePixelPoint): Int {
        val x = point.x.roundToInt()
        val y = point.y.roundToInt()
        check(x in 0 until FOG_TILE_SIZE && y in 0 until FOG_TILE_SIZE) {
            "Probe ($x, $y) is outside the tile"
        }
        return getPixel(x, y)
    }

    private val plainFog: Int by lazy {
        FogTileProvider(CoverageSnapshot.Empty).render(FogTileCoordinate(0, 0, 0)).getPixel(0, 0)
    }
}
