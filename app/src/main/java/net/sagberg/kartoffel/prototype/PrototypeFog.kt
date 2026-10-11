package net.sagberg.kartoffel.prototype

// PROTOTYPE (throwaway, issue #34): synthetic Oslo coverage and a fog tile provider with
// prototype-only colour, feather, tint and edge parameters. Never reads location history.

import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import java.io.ByteArrayOutputStream
import net.sagberg.kartoffel.coverage.CoverageCellShape
import net.sagberg.kartoffel.coverage.CoverageSnapshot
import net.sagberg.kartoffel.coverage.GeoBounds
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells
import net.sagberg.kartoffel.map.FOG_TILE_SIZE
import net.sagberg.kartoffel.map.FogTileCoordinate
import net.sagberg.kartoffel.map.hasValidY
import net.sagberg.kartoffel.map.latLngBounds
import net.sagberg.kartoffel.map.pixelForCoordinate

internal data class PrototypeFogStyle(
    val fog: Int,
    val featherPx: Float = 0f,
    val revealTint: Int? = null,
    val edgeColor: Int? = null,
    val edgeWidthPx: Float = 0f,
)

internal object SyntheticOsloCoverage {
    val here = GeoCoordinate(59.9136, 10.7400)

    private val walks = listOf(
        // Karl Johans gate, Oslo S to Slottet
        listOf(59.9111 to 10.7502, 59.9127 to 10.7445, 59.9136 to 10.7400, 59.9149 to 10.7350, 59.9163 to 10.7300, 59.9169 to 10.7274),
        // Slottsparken loop
        listOf(59.9169 to 10.7274, 59.9188 to 10.7240, 59.9205 to 10.7268, 59.9197 to 10.7318, 59.9172 to 10.7322),
        listOf(59.9180 to 10.7255, 59.9196 to 10.7295),
        // Bogstadveien
        listOf(59.9205 to 10.7268, 59.9232 to 10.7226, 59.9262 to 10.7182, 59.9290 to 10.7140),
        // Rådhuset, Aker Brygge, Tjuvholmen
        listOf(59.9149 to 10.7350, 59.9118 to 10.7336, 59.9102 to 10.7290, 59.9085 to 10.7240, 59.9068 to 10.7210),
        // Akersgata to Akershus
        listOf(59.9136 to 10.7400, 59.9105 to 10.7395, 59.9078 to 10.7370),
        // Torggata to Grünerløkka
        listOf(59.9136 to 10.7470, 59.9165 to 10.7495, 59.9198 to 10.7525, 59.9228 to 10.7580),
        // Operahuset
        listOf(59.9111 to 10.7502, 59.9076 to 10.7530),
    )

    val snapshot: CoverageSnapshot by lazy {
        val h3 = H3CoverageCells()
        val cells = walks.flatMap { walk ->
            walk.zipWithNext().flatMap { (from, to) ->
                h3.pathBetween(GeoCoordinate(from.first, from.second), GeoCoordinate(to.first, to.second))
            }
        }.distinct()
        CoverageSnapshot(cells.map(h3::shapeOf))
    }
}

internal class PrototypeFogTileProvider(
    private val coverage: CoverageSnapshot,
    private val fog: PrototypeFogStyle,
) : TileProvider {
    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        val tile = FogTileCoordinate(x, y, zoom)
        if (!tile.hasValidY()) return TileProvider.NO_TILE
        val b = tile.latLngBounds()
        val padLat = (b.north - b.south) * 0.25
        val padLng = (b.east - b.west) * 0.25
        val cells = coverage.cellsIntersecting(
            GeoBounds(b.south - padLat, b.west - padLng, b.north + padLat, b.east + padLng),
        )

        val bitmap = createBitmap(FOG_TILE_SIZE, FOG_TILE_SIZE)
        val canvas = Canvas(bitmap)
        canvas.drawColor(fog.fog)
        if (cells.isNotEmpty()) {
            val union = Path()
            cells.forEach { union.op(it.toPath(tile), Path.Op.UNION) }
            canvas.drawPath(
                union,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    if (fog.featherPx > 0f) {
                        maskFilter = BlurMaskFilter(fog.featherPx, BlurMaskFilter.Blur.NORMAL)
                    }
                },
            )
            fog.revealTint?.let { tint ->
                canvas.drawPath(
                    union,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = tint
                        if (fog.featherPx > 0f) {
                            maskFilter = BlurMaskFilter(fog.featherPx, BlurMaskFilter.Blur.NORMAL)
                        }
                    },
                )
            }
            fog.edgeColor?.let { edge ->
                canvas.drawPath(
                    union,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = edge
                        style = Paint.Style.STROKE
                        strokeWidth = fog.edgeWidthPx
                        strokeJoin = Paint.Join.ROUND
                    },
                )
            }
        }
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
        return Tile(FOG_TILE_SIZE, FOG_TILE_SIZE, bytes)
    }

    private fun CoverageCellShape.toPath(tile: FogTileCoordinate): Path = Path().apply {
        val first = tile.pixelForCoordinate(boundary.first())
        moveTo(first.x, first.y)
        boundary.drop(1).forEach { val p = tile.pixelForCoordinate(it); lineTo(p.x, p.y) }
        close()
    }
}
