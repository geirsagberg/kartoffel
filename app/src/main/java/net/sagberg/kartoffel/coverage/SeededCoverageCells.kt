package net.sagberg.kartoffel.coverage

internal object SeededCoverageCells {
    // Bump this when editing seed cells so Maps' tile cache refreshes during development.
    private const val REVISION = 1

    private val cells = listOf(
        demoHexCell(
            id = "oslo-sentrum-01",
            center = GeoCoordinate(latitude = 59.9139, longitude = 10.7522),
        ),
        demoHexCell(
            id = "oslo-sentrum-02",
            center = GeoCoordinate(latitude = 59.9126, longitude = 10.7461),
        ),
        demoHexCell(
            id = "oslo-sentrum-03",
            center = GeoCoordinate(latitude = 59.9163, longitude = 10.7484),
        ),
        demoHexCell(
            id = "oslo-sentrum-04",
            center = GeoCoordinate(latitude = 59.9104, longitude = 10.7562),
        ),
    )

    fun snapshot(): SeedCoverageSnapshot =
        SeedCoverageSnapshot(
            revision = REVISION,
            cells = cells,
        )

    private fun demoHexCell(
        id: String,
        center: GeoCoordinate,
    ): SeedCoverageCell {
        val latitudeRadius = 0.0012
        val longitudeRadius = 0.0021

        return SeedCoverageCell(
            id = id,
            boundary = listOf(
                GeoCoordinate(
                    latitude = center.latitude - latitudeRadius,
                    longitude = center.longitude,
                ),
                GeoCoordinate(
                    latitude = center.latitude - latitudeRadius * 0.5,
                    longitude = center.longitude + longitudeRadius,
                ),
                GeoCoordinate(
                    latitude = center.latitude + latitudeRadius * 0.5,
                    longitude = center.longitude + longitudeRadius,
                ),
                GeoCoordinate(
                    latitude = center.latitude + latitudeRadius,
                    longitude = center.longitude,
                ),
                GeoCoordinate(
                    latitude = center.latitude + latitudeRadius * 0.5,
                    longitude = center.longitude - longitudeRadius,
                ),
                GeoCoordinate(
                    latitude = center.latitude - latitudeRadius * 0.5,
                    longitude = center.longitude - longitudeRadius,
                ),
            ),
        )
    }
}
