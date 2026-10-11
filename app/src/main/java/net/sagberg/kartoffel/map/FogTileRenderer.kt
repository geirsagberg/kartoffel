package net.sagberg.kartoffel.map

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.io.ByteArrayOutputStream
import net.sagberg.kartoffel.coverage.CoverageCellShape
import net.sagberg.kartoffel.coverage.GeoCoordinate
import androidx.core.graphics.createBitmap

/** How the line along the edge of visited coverage is drawn over the Fog, in tile pixels. */
internal data class CoverageOutlinePaint(
    val color: Int,
    val width: Float,
    val glow: Glow? = null,
) {
    /** A soft band drawn on the Fog side of the line only. */
    data class Glow(
        val color: Int,
        val width: Float,
        val blurRadius: Float,
    )

    /** How far the outline can reach beyond a cell's edge into a neighbouring tile. */
    val reachPixels: Float get() = maxOf(width, glow?.let { it.width + it.blurRadius } ?: 0f)
}

internal class FogTileRenderer(
    private val fogColor: Int = Color.argb(185, 24, 29, 36),
    val outline: CoverageOutlinePaint? = null,
) {
    private val fullyFoggedPng by lazy {
        renderPngUncached(
            tile = FogTileCoordinate(x = 0, y = 0, zoom = 0),
            cellsToClear = emptyList(),
        )
    }

    fun renderPng(
        tile: FogTileCoordinate,
        cellsToClear: List<CoverageCellShape>,
    ): ByteArray =
        if (cellsToClear.isEmpty()) {
            fullyFoggedPng
        } else {
            renderPngUncached(tile, cellsToClear)
        }

    private fun renderPngUncached(
        tile: FogTileCoordinate,
        cellsToClear: List<CoverageCellShape>,
    ): ByteArray {
        val bitmap = createBitmap(FOG_TILE_SIZE, FOG_TILE_SIZE)
        val canvas = Canvas(bitmap)
        canvas.drawColor(fogColor)

        val outlined = outline?.let { paint -> paint to coverageOutline(cellsToClear).toTilePath(tile) }

        // Drawn before clearing so that the glow only remains on the Fog side of the edge.
        outlined?.let { (paint, path) ->
            paint.glow?.let { glow ->
                canvas.drawPath(
                    path,
                    outlineStroke(glow.color, glow.width).apply {
                        maskFilter = BlurMaskFilter(glow.blurRadius, BlurMaskFilter.Blur.NORMAL)
                    },
                )
            }
        }

        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        cellsToClear.forEach { cell ->
            canvas.drawPath(cell.boundary.toTilePolygon(tile), clearPaint)
        }

        outlined?.let { (paint, path) ->
            canvas.drawPath(path, outlineStroke(paint.color, paint.width))
        }

        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Fog tile PNG compression failed"
            }
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun outlineStroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        // Keeps the corners of hexagonal cells sharp.
        strokeJoin = Paint.Join.MITER
    }

    private fun List<List<GeoCoordinate>>.toTilePath(tile: FogTileCoordinate): Path =
        Path().apply { forEach { ring -> addPath(ring.toTilePolygon(tile)) } }

    private fun List<GeoCoordinate>.toTilePolygon(tile: FogTileCoordinate): Path {
        val first = tile.pixelForCoordinate(first())

        return Path().apply {
            moveTo(first.x, first.y)
            drop(1).forEach { coordinate ->
                val point = tile.pixelForCoordinate(coordinate)
                lineTo(point.x, point.y)
            }
            close()
        }
    }
}
