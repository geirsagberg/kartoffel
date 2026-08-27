package net.sagberg.kartoffel.coverage

import com.uber.h3core.H3Core
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class H3CoverageCellsTest {
    private val h3 = H3Core.newInstance()
    private val coverageCells = H3CoverageCells(h3)

    @Test
    fun mapsRepresentativeCoordinatesToStableCoverageCells() {
        val osloCentralStation = GeoCoordinate(latitude = 59.9109, longitude = 10.7522)
        val statueOfLiberty = GeoCoordinate(latitude = 40.689167, longitude = -74.044444)

        assertEquals(621665607470907391, coverageCells.cellAt(osloCentralStation).value)
        assertEquals(622236750694711295, coverageCells.cellAt(statueOfLiberty).value)
    }

    @Test
    fun returnsTheBoundaryForACoverageCell() {
        val coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522)
        val boundary = coverageCells.boundaryOf(coverageCells.cellAt(coordinate))

        assertTrue(boundary.size >= 5)
        assertTrue(boundary.all { it.latitude in -90.0..90.0 })
        assertTrue(boundary.all { it.longitude in -180.0..180.0 })
    }

    @Test
    fun returnsOneShortestPathForAnEligibleGap() {
        val start = coverageCells.cellAt(GeoCoordinate(59.9109, 10.7522))
        val destination = CoverageCellId(
            h3.gridDisk(start.value, 2).first { h3.gridDistance(start.value, it) == 2L },
        )

        val intermediateCells = coverageCells.intermediateCellsForGap(
            start = start,
            destination = destination,
            maximumGapSteps = 3,
        )

        assertEquals(1, intermediateCells.size)
        assertEquals(
            h3.gridPathCells(start.value, destination.value)
                .drop(1)
                .dropLast(1)
                .mapTo(linkedSetOf(), ::CoverageCellId),
            intermediateCells,
        )
    }

    @Test
    fun maximumGapIsInclusive() {
        val start = coverageCells.cellAt(GeoCoordinate(59.9109, 10.7522))
        val destination = CoverageCellId(
            h3.gridDisk(start.value, 3).first { h3.gridDistance(start.value, it) == 3L },
        )

        assertEquals(
            emptySet<CoverageCellId>(),
            coverageCells.intermediateCellsForGap(start, destination, maximumGapSteps = 2),
        )
        assertEquals(
            2,
            coverageCells.intermediateCellsForGap(start, destination, maximumGapSteps = 3).size,
        )
    }

    @Test
    fun doesNotInterpolateSameOrAdjacentCellsAndOneDisablesInterpolation() {
        val start = coverageCells.cellAt(GeoCoordinate(59.9109, 10.7522))
        val adjacent = CoverageCellId(h3.gridDisk(start.value, 1).first { it != start.value })
        val twoStepsAway = CoverageCellId(
            h3.gridDisk(start.value, 2).first { h3.gridDistance(start.value, it) == 2L },
        )

        assertEquals(
            emptySet<CoverageCellId>(),
            coverageCells.intermediateCellsForGap(start, start, maximumGapSteps = 3),
        )
        assertEquals(
            emptySet<CoverageCellId>(),
            coverageCells.intermediateCellsForGap(start, adjacent, maximumGapSteps = 3),
        )
        assertEquals(
            emptySet<CoverageCellId>(),
            coverageCells.intermediateCellsForGap(
                start,
                twoStepsAway,
                maximumGapSteps = 1,
            ),
        )
    }
}
