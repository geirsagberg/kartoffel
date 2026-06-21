package net.sagberg.kartoffel.map

import android.graphics.BitmapFactory
import android.graphics.Color
import kotlin.math.roundToInt
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.SeededCoverageCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FogOfWarTileProviderTest {
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
