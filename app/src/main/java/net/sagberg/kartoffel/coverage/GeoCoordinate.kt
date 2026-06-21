package net.sagberg.kartoffel.coverage

internal data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) {
            "latitude must be between -90.0 and 90.0 degrees inclusive; was $latitude"
        }
        require(longitude in -180.0..180.0) {
            "longitude must be between -180.0 and 180.0 degrees inclusive; was $longitude"
        }
    }
}
