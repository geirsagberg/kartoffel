package net.sagberg.kartoffel.prototype

// PROTOTYPE (throwaway, issue #34): synthetic Oslo coverage and a fog tile provider that draws
// the production Fog colour plus a prototype-only outline around visited coverage.
// Never reads location history.

import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
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

// Same as the production FogTileRenderer default, used in both light and dark.
private val ProductionFog = Color.argb(185, 24, 29, 36)

internal enum class Outline(val key: String, val title: String) {
    None("none", "none"),
    Teal("teal", "teal line"),
    Graphite("graphite", "graphite line"),
    Halo("halo", "white halo"),
    Glow("glow", "teal + inner glow"),
}

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
    private val outline: Outline,
    private val dark: Boolean,
) : TileProvider {
    // Light and dark mirror KartoffelTheme primary.
    private val teal = if (dark) Color.rgb(0x72, 0xDB, 0xBC) else Color.rgb(0x00, 0x6B, 0x57)

    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        val tile = FogTileCoordinate(x, y, zoom)
        if (!tile.hasValidY()) return TileProvider.NO_TILE
        val b = tile.latLngBounds()
        // Padding keeps strokes and glows continuous across tile seams.
        val padLat = (b.north - b.south) * 0.25
        val padLng = (b.east - b.west) * 0.25
        val cells = coverage.cellsIntersecting(
            GeoBounds(b.south - padLat, b.west - padLng, b.north + padLat, b.east + padLng),
        )

        val bitmap = createBitmap(FOG_TILE_SIZE, FOG_TILE_SIZE)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ProductionFog)
        if (cells.isNotEmpty()) {
            val union = Path()
            cells.forEach { union.op(it.toPath(tile), Path.Op.UNION) }
            canvas.drawPath(
                union,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) },
            )
            drawOutline(canvas, union)
        }
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
        return Tile(FOG_TILE_SIZE, FOG_TILE_SIZE, bytes)
    }

    private fun drawOutline(canvas: Canvas, union: Path) {
        fun stroke(color: Int, width: Float, blur: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeJoin = Paint.Join.MITER
            if (blur > 0f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        val outside = Path(union).apply { fillType = Path.FillType.INVERSE_WINDING }
        when (outline) {
            Outline.None -> Unit
            Outline.Teal -> canvas.drawPath(union, stroke(teal, 2f))
            Outline.Graphite -> {
                // A neutral line that follows the mode: near-black on the light map, pale grey on dark.
                val ink = if (dark) Color.argb(230, 0xC8, 0xD0, 0xD6) else Color.argb(235, 0x1E, 0x24, 0x2A)
                canvas.drawPath(union, stroke(ink, if (dark) 1.5f else 1.75f))
            }
            Outline.Halo -> {
                // Soft light bleed into the fog, then a crisp white edge.
                canvas.withClip(outside) {
                    drawPath(union, stroke(Color.argb(if (dark) 90 else 80, 255, 255, 255), 10f, blur = 5f))
                }
                canvas.drawPath(union, stroke(Color.argb(240, 255, 255, 255), 1.5f))
            }
            Outline.Glow -> {
                // Teal light just inside the visited area, then a crisp teal edge.
                canvas.withClip(union) {
                    drawPath(union, stroke(Color.argb(if (dark) 60 else 70, Color.red(teal), Color.green(teal), Color.blue(teal)), 12f, blur = 5f))
                }
                canvas.drawPath(union, stroke(teal, 1.5f))
            }
        }
    }

    private fun CoverageCellShape.toPath(tile: FogTileCoordinate): Path = Path().apply {
        val first = tile.pixelForCoordinate(boundary.first())
        moveTo(first.x, first.y)
        boundary.drop(1).forEach { val p = tile.pixelForCoordinate(it); lineTo(p.x, p.y) }
        close()
    }
}
