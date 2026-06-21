package net.sagberg.kartoffel.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import net.sagberg.kartoffel.coverage.GeoBounds
import net.sagberg.kartoffel.coverage.GeoCoordinate

internal const val FOG_TILE_SIZE = 256

private const val MAX_WEB_MERCATOR_LATITUDE = 85.05112878

internal data class FogTileCoordinate(
    val x: Int,
    val y: Int,
    val zoom: Int,
) {
    init {
        require(zoom in 0..30) { "zoom must be in 0..30" }
    }
}

internal data class TilePixelPoint(
    val x: Float,
    val y: Float,
)

internal fun fogTileForCoordinate(
    coordinate: GeoCoordinate,
    zoom: Int,
): FogTileCoordinate {
    val tileCount = tileCountForZoom(zoom)
    val worldPoint = coordinate.toWorldPixelPoint(zoom)

    return FogTileCoordinate(
        x = floor(worldPoint.x / FOG_TILE_SIZE).toInt().coerceIn(0, tileCount - 1),
        y = floor(worldPoint.y / FOG_TILE_SIZE).toInt().coerceIn(0, tileCount - 1),
        zoom = zoom,
    )
}

internal fun FogTileCoordinate.hasValidY(): Boolean =
    y in 0 until tileCountForZoom(zoom)

internal fun FogTileCoordinate.latLngBounds(): GeoBounds {
    val normalizedX = normalizedX()
    val north = worldPixelYToLatitude(y * FOG_TILE_SIZE.toDouble(), zoom)
    val south = worldPixelYToLatitude((y + 1) * FOG_TILE_SIZE.toDouble(), zoom)
    val west = worldPixelXToLongitude(normalizedX * FOG_TILE_SIZE.toDouble(), zoom)
    val east = worldPixelXToLongitude((normalizedX + 1) * FOG_TILE_SIZE.toDouble(), zoom)

    return GeoBounds(
        south = south,
        west = west,
        north = north,
        east = east,
    )
}

internal fun FogTileCoordinate.pixelForCoordinate(
    coordinate: GeoCoordinate,
): TilePixelPoint {
    val worldPoint = coordinate.toWorldPixelPoint(zoom)
    val normalizedX = normalizedX()

    return TilePixelPoint(
        x = (worldPoint.x - normalizedX * FOG_TILE_SIZE).toFloat(),
        y = (worldPoint.y - y * FOG_TILE_SIZE).toFloat(),
    )
}

private data class WorldPixelPoint(
    val x: Double,
    val y: Double,
)

private fun GeoCoordinate.toWorldPixelPoint(zoom: Int): WorldPixelPoint {
    val scale = webMercatorScale(zoom)
    val latitude = latitude.coerceIn(
        -MAX_WEB_MERCATOR_LATITUDE,
        MAX_WEB_MERCATOR_LATITUDE,
    )
    val sinLatitude = sin(latitude.toRadians())

    return WorldPixelPoint(
        x = (longitude + 180.0) / 360.0 * scale,
        y = (0.5 - ln((1.0 + sinLatitude) / (1.0 - sinLatitude)) / (4.0 * PI)) * scale,
    )
}

private fun FogTileCoordinate.normalizedX(): Int {
    val tileCount = tileCountForZoom(zoom)
    return ((x % tileCount) + tileCount) % tileCount
}

private fun tileCountForZoom(zoom: Int): Int {
    require(zoom in 0..30) { "zoom must be in 0..30" }
    return 1 shl zoom
}

private fun webMercatorScale(zoom: Int): Double =
    FOG_TILE_SIZE * 2.0.pow(zoom)

private fun worldPixelXToLongitude(
    x: Double,
    zoom: Int,
): Double =
    x / webMercatorScale(zoom) * 360.0 - 180.0

private fun worldPixelYToLatitude(
    y: Double,
    zoom: Int,
): Double {
    val n = PI - 2.0 * PI * y / webMercatorScale(zoom)
    return (180.0 / PI) * atan(0.5 * (exp(n) - exp(-n)))
}

private fun Double.toRadians(): Double =
    this / 180.0 * PI
