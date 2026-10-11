package net.sagberg.kartoffel.map

import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import java.util.concurrent.atomic.AtomicReference
import net.sagberg.kartoffel.coverage.CoverageCellShape
import net.sagberg.kartoffel.coverage.CoverageSnapshot

internal class FogTileProvider(
    initialCoverage: CoverageSnapshot,
    private val renderPng: (FogTileRenderer, FogTileCoordinate, List<CoverageCellShape>) -> ByteArray =
        FogTileRenderer::renderPng,
    private val makeRenderer: (CoverageOutlinePaint?) -> FogTileRenderer = { FogTileRenderer(outline = it) },
) : TileProvider {
    private data class RenderState(
        val coverage: CoverageSnapshot,
        val renderer: FogTileRenderer,
    )

    private val state = AtomicReference(RenderState(initialCoverage, makeRenderer(null)))

    fun updateCoverage(snapshot: CoverageSnapshot) {
        state.updateAndGet { it.copy(coverage = snapshot) }
    }

    /** Returns whether the outline changed, in which case cached tiles are stale. */
    fun updateOutline(outline: CoverageOutlinePaint?): Boolean {
        val previous = state.getAndUpdate {
            if (it.renderer.outline == outline) {
                it
            } else {
                it.copy(renderer = makeRenderer(outline))
            }
        }
        return previous.renderer.outline != outline
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
                val renderingState = state.get()
                val renderer = renderingState.renderer
                val reach = renderer.outline?.reachPixels?.toDouble() ?: 0.0
                val visibleCells = renderingState.coverage.cellsIntersecting(tile.latLngBounds(reach))
                val bytes = renderPng(renderer, tile, visibleCells)

                // An in-flight tile may finish after the overlay's cache has been cleared.
                // Returning null asks Maps to retry instead of caching an obsolete tile.
                if (state.get() !== renderingState) null else Tile(renderer.sizePixels, renderer.sizePixels, bytes)
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
