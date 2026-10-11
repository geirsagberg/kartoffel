package net.sagberg.kartoffel.map

import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import java.util.concurrent.atomic.AtomicReference
import net.sagberg.kartoffel.coverage.CoverageSnapshot

internal class FogTileProvider(
    initialCoverage: CoverageSnapshot,
    initialOutline: CoverageOutlinePaint? = null,
) : TileProvider {
    private val coverage = AtomicReference(initialCoverage)
    private val renderer = AtomicReference(FogTileRenderer(outline = initialOutline))

    fun updateCoverage(snapshot: CoverageSnapshot) {
        coverage.set(snapshot)
    }

    /** Returns whether the outline changed, in which case cached tiles are stale. */
    fun updateOutline(outline: CoverageOutlinePaint?): Boolean {
        if (renderer.get().outline == outline) return false
        renderer.set(FogTileRenderer(outline = outline))
        return true
    }

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
                val renderer = renderer.get()
                val reach = renderer.outline?.reachPixels?.toDouble() ?: 0.0
                val visibleCells = coverage.get().cellsIntersecting(tile.latLngBounds(reach))
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
