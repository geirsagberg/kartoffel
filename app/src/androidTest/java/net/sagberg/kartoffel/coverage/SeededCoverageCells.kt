package net.sagberg.kartoffel.coverage

internal object SeededCoverageCells {
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

    fun snapshot(): CoverageSnapshot = CoverageSnapshot(revision = 1, cells = cells)

    private fun demoHexCell(
        id: String,
        center: GeoCoordinate,
    ): CoverageCellShape {
        val latitudeRadius = 0.0012
        val longitudeRadius = 0.0021

        return CoverageCellShape(
            id = id,
            boundary = listOf(
                GeoCoordinate(center.latitude - latitudeRadius, center.longitude),
                GeoCoordinate(
                    center.latitude - latitudeRadius * 0.5,
                    center.longitude + longitudeRadius,
                ),
                GeoCoordinate(
                    center.latitude + latitudeRadius * 0.5,
                    center.longitude + longitudeRadius,
                ),
                GeoCoordinate(center.latitude + latitudeRadius, center.longitude),
                GeoCoordinate(
                    center.latitude + latitudeRadius * 0.5,
                    center.longitude - longitudeRadius,
                ),
                GeoCoordinate(
                    center.latitude - latitudeRadius * 0.5,
                    center.longitude - longitudeRadius,
                ),
            ),
        )
    }
}
