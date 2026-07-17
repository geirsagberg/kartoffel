package net.sagberg.kartoffel.map

import android.graphics.BitmapFactory
import android.graphics.Color
import kotlin.math.roundToInt
import net.sagberg.kartoffel.coverage.CoverageSnapshot
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.SeededCoverageCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FogOfWarTileProviderTest {
    @Test
    fun fullyFoggedTilesReusePreRenderedPngBytes() {
        val provider = FogOfWarTileProvider(CoverageSnapshot.Empty)

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
        val provider = FogOfWarTileProvider(CoverageSnapshot.Empty)

        val foggedTile = provider.getTile(tile.x, tile.y, tile.zoom)
            ?: error("Expected Fog of War provider to return a tile")
        val foggedTileData = foggedTile.data ?: error("Expected PNG tile bytes")
        val foggedBitmap = BitmapFactory.decodeByteArray(
            foggedTileData,
            0,
            foggedTileData.size,
        )

        provider.updateCoverage(coveredSnapshot)

        val updatedTile = provider.getTile(tile.x, tile.y, tile.zoom)
            ?: error("Expected Fog of War provider to return an updated tile")
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
        val renderedTile = FogOfWarTileProvider(snapshot).getTile(
            x = tile.x,
            y = tile.y,
            zoom = tile.zoom,
        )
            ?: error("Expected Fog of War provider to return a tile")
        val renderedTileData = renderedTile.data
            ?: error("Expected Fog of War provider to return PNG tile bytes")
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
}
