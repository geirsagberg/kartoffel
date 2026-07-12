package net.sagberg.kartoffel.map

import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.CoverageCellShape
import net.sagberg.kartoffel.coverage.CoverageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FogTileMathTest {
    @Test
    fun osloFallbackCoordinateMapsToStableZoom15TileAndPixel() {
        val oslo = GeoCoordinate(latitude = 59.9139, longitude = 10.7522)

        val tile = fogTileForCoordinate(oslo, zoom = 15)
        val pixel = tile.pixelForCoordinate(oslo)

        assertEquals(FogTileCoordinate(x = 17362, y = 9531, zoom = 15), tile)
        assertEquals(176.419, pixel.x.toDouble(), 0.001)
        assertEquals(120.090, pixel.y.toDouble(), 0.001)
    }

    @Test
    fun tileBoundsContainTheCoordinateThatProducedTheTile() {
        val oslo = GeoCoordinate(latitude = 59.9139, longitude = 10.7522)

        val bounds = fogTileForCoordinate(oslo, zoom = 15).latLngBounds()

        assertTrue(bounds.contains(oslo))
    }

    @Test
    fun seedSnapshotFiltersCoverageCellsToTheRequestedTileBounds() {
        val oslo = GeoCoordinate(latitude = 59.9139, longitude = 10.7522)
        val tileBounds = fogTileForCoordinate(oslo, zoom = 15).latLngBounds()
        val snapshot = CoverageSnapshot(
            revision = 1,
            cells = listOf(
                squareCell(
                    id = "near-oslo",
                    center = oslo,
                ),
                squareCell(
                    id = "far-from-oslo",
                    center = GeoCoordinate(latitude = 60.39299, longitude = 5.32415),
                ),
            ),
        )

        val visibleCellIds = snapshot
            .cellsIntersecting(tileBounds)
            .map { it.id }

        assertEquals(listOf("near-oslo"), visibleCellIds)
    }

    private fun squareCell(
        id: String,
        center: GeoCoordinate,
    ): CoverageCellShape {
        val delta = 0.0004

        return CoverageCellShape(
            id = id,
            boundary = listOf(
                GeoCoordinate(
                    latitude = center.latitude - delta,
                    longitude = center.longitude - delta,
                ),
                GeoCoordinate(
                    latitude = center.latitude - delta,
                    longitude = center.longitude + delta,
                ),
                GeoCoordinate(
                    latitude = center.latitude + delta,
                    longitude = center.longitude + delta,
                ),
                GeoCoordinate(
                    latitude = center.latitude + delta,
                    longitude = center.longitude - delta,
                ),
            ),
        )
    }
}
