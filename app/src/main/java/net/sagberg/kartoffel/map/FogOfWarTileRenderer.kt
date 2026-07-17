package net.sagberg.kartoffel.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.io.ByteArrayOutputStream
import net.sagberg.kartoffel.coverage.CoverageCellShape
import androidx.core.graphics.createBitmap

internal class FogOfWarTileRenderer(
    private val fogColor: Int = Color.argb(185, 24, 29, 36),
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

        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        cellsToClear.forEach { cell ->
            canvas.drawPath(cell.toTilePath(tile), clearPaint)
        }

        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Fog of War tile PNG compression failed"
            }
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun CoverageCellShape.toTilePath(tile: FogTileCoordinate): Path {
        val first = tile.pixelForCoordinate(boundary.first())

        return Path().apply {
            moveTo(first.x, first.y)
            boundary.drop(1).forEach { coordinate ->
                val point = tile.pixelForCoordinate(coordinate)
                lineTo(point.x, point.y)
            }
            close()
        }
    }
}
