package net.sagberg.kartoffel.coverage

import com.uber.h3core.H3Core
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class H3CoverageCellsTest {
    private val coverageCells = H3CoverageCells(H3Core.newInstance())

    @Test
    fun mapsRepresentativeCoordinatesToStableCoverageCells() {
        val osloCentralStation = GeoCoordinate(latitude = 59.9109, longitude = 10.7522)
        val statueOfLiberty = GeoCoordinate(latitude = 40.689167, longitude = -74.044444)

        assertEquals(626169207098265599, coverageCells.cellAt(osloCentralStation).value)
        assertEquals(626740350322065407, coverageCells.cellAt(statueOfLiberty).value)
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
    fun returnsEveryEquallyShortIntermediateCellForAOneCellGap() {
        val start = CoverageCellId(626169207098265599)
        val destination = CoverageCellId(626169207099809791)

        assertEquals(
            setOf(
                CoverageCellId(626169207099793407),
                CoverageCellId(626169207098388479),
            ),
            coverageCells.intermediateCellsForShortGap(start, destination),
        )
    }

    @Test
    fun doesNotInterpolateAdjacentCellsOrLargerGaps() {
        val start = CoverageCellId(626169207098265599)
        val adjacent = CoverageCellId(626169207098388479)
        val fartherAway = coverageCells.cellAt(
            GeoCoordinate(latitude = 59.915, longitude = 10.7522),
        )

        assertEquals(emptySet<CoverageCellId>(), coverageCells.intermediateCellsForShortGap(start, adjacent))
        assertEquals(emptySet<CoverageCellId>(), coverageCells.intermediateCellsForShortGap(start, fartherAway))
    }
}
