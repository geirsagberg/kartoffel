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
    ): Tile {
        val tile = FogTileCoordinate(x = x, y = y, zoom = zoom)
        if (!tile.hasValidY()) return TileProvider.NO_TILE

        val visibleCells = coverageSnapshot.cellsIntersecting(tile.latLngBounds())
        val bytes = renderer.renderPng(
            tile = tile,
            cellsToClear = visibleCells,
        )

        return Tile(FOG_TILE_SIZE, FOG_TILE_SIZE, bytes)
    }
}
