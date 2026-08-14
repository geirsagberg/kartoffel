package net.sagberg.kartoffel.coverage

import com.uber.h3core.H3Core

/**
 * Provisional for MVP. Changing this value invalidates persisted Coverage Cell IDs and may
 * require clearing local coverage data until a migration strategy is explicitly introduced.
 */
internal const val COVERAGE_CELL_RESOLUTION = 11

@JvmInline
internal value class CoverageCellId(val value: Long)

internal class H3CoverageCells(
    private val h3: H3Core = H3Core.newSystemInstance(),
) {
    fun cellAt(coordinate: GeoCoordinate): CoverageCellId =
        CoverageCellId(
            h3.latLngToCell(
                coordinate.latitude,
                coordinate.longitude,
                COVERAGE_CELL_RESOLUTION,
            ),
        )

    fun boundaryOf(cell: CoverageCellId): List<GeoCoordinate> =
        h3.cellToBoundary(cell.value).map { vertex ->
            GeoCoordinate(latitude = vertex.lat, longitude = vertex.lng)
        }

    fun pathBetween(start: GeoCoordinate, destination: GeoCoordinate): List<CoverageCellId> =
        h3.gridPathCells(cellAt(start).value, cellAt(destination).value).map(::CoverageCellId)

    fun shapeOf(cell: CoverageCellId): CoverageCellShape = CoverageCellShape(
        id = cell.value.toString(),
        boundary = boundaryOf(cell),
    )

    fun intermediateCellsForGap(
        start: CoverageCellId,
        destination: CoverageCellId,
        maximumGapSteps: Int,
    ): Set<CoverageCellId> {
        require(maximumGapSteps > 0)
        return runCatching {
            val distance = h3.gridDistance(start.value, destination.value)
            if (distance !in 2L..maximumGapSteps.toLong()) {
                return@runCatching emptySet()
            }

            h3.gridPathCells(start.value, destination.value)
                .drop(1)
                .dropLast(1)
                .mapTo(linkedSetOf(), ::CoverageCellId)
        }.getOrDefault(emptySet())
    }
}
