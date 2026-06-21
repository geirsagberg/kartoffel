package net.sagberg.kartoffel.coverage

internal data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    init {
        require(south <= north) { "south must be less than or equal to north" }
        require(west <= east) { "west must be less than or equal to east" }
    }

    fun contains(coordinate: GeoCoordinate): Boolean =
        coordinate.latitude in south..north &&
            coordinate.longitude in west..east

    fun intersects(other: GeoBounds): Boolean =
        west <= other.east &&
            east >= other.west &&
            south <= other.north &&
            north >= other.south

    companion object {
        fun containing(coordinates: List<GeoCoordinate>): GeoBounds {
            require(coordinates.isNotEmpty()) { "coordinates must not be empty" }

            return GeoBounds(
                south = coordinates.minOf { it.latitude },
                west = coordinates.minOf { it.longitude },
                north = coordinates.maxOf { it.latitude },
                east = coordinates.maxOf { it.longitude },
            )
        }
    }
}
