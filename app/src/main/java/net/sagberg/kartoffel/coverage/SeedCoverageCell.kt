package net.sagberg.kartoffel.coverage

internal data class SeedCoverageCell(
    val id: String,
    val boundary: List<GeoCoordinate>,
) {
    init {
        require(boundary.size >= 3) { "a Coverage Cell boundary needs at least three points" }
    }

    val bounds: GeoBounds = GeoBounds.containing(boundary)
}

internal data class SeedCoverageSnapshot(
    val revision: Int,
    val cells: List<SeedCoverageCell>,
) {
    fun cellsIntersecting(bounds: GeoBounds): List<SeedCoverageCell> =
        cells.filter { it.bounds.intersects(bounds) }
}
