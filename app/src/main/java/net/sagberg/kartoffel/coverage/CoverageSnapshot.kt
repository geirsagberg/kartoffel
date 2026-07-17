package net.sagberg.kartoffel.coverage

internal data class CoverageCellShape(
    val id: String,
    val boundary: List<GeoCoordinate>,
) {
    init {
        require(boundary.size >= 3) { "a Coverage Cell boundary needs at least three points" }
    }

    val bounds: GeoBounds = GeoBounds.containing(boundary)
}

internal data class CoverageSnapshot(
    val cells: List<CoverageCellShape>,
) {
    fun cellsIntersecting(bounds: GeoBounds): List<CoverageCellShape> =
        cells.filter { it.bounds.intersects(bounds) }

    companion object {
        val Empty = CoverageSnapshot(cells = emptyList())
    }
}
