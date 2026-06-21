package net.sagberg.kartoffel.map

import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import net.sagberg.kartoffel.coverage.SeedCoverageSnapshot

internal class FogOfWarTileProvider(
    private val coverageSnapshot: SeedCoverageSnapshot,
    private val renderer: FogOfWarTileRenderer = FogOfWarTileRenderer(),
) : TileProvider {
    override fun getTile(
        x: Int,
        y: Int,
        zoom: Int,
    ): Tile? {
        return try {
            val tile = FogTileCoordinate(x = x, y = y, zoom = zoom)
            if (!tile.hasValidY()) {
                TileProvider.NO_TILE
            } else {
                val visibleCells = coverageSnapshot.cellsIntersecting(tile.latLngBounds())
                val bytes = renderer.renderPng(
                    tile = tile,
                    cellsToClear = visibleCells,
                )

                Tile(FOG_TILE_SIZE, FOG_TILE_SIZE, bytes)
            }
        } catch (failure: Exception) {
            when (fogTileFailureHandlingFor(failure)) {
                FogTileFailureHandling.NoTile -> TileProvider.NO_TILE
                FogTileFailureHandling.Retry -> null
            }
        }
    }
}

internal enum class FogTileFailureHandling {
    NoTile,
    Retry,
}

internal fun fogTileFailureHandlingFor(failure: Exception): FogTileFailureHandling =
    when (failure) {
        is IllegalArgumentException,
        is IndexOutOfBoundsException,
        is NoSuchElementException -> FogTileFailureHandling.NoTile
        else -> FogTileFailureHandling.Retry
    }
