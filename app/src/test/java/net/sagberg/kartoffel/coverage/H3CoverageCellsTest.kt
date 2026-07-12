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

        assertEquals(0x08c09993866141ffL, coverageCells.cellAt(osloCentralStation).value)
        assertEquals(0x08c2a1072b59b9ffL, coverageCells.cellAt(statueOfLiberty).value)
    }

    @Test
    fun returnsTheBoundaryForACoverageCell() {
        val coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522)
        val boundary = coverageCells.boundaryOf(coverageCells.cellAt(coordinate))

        assertTrue(boundary.size >= 5)
        assertTrue(boundary.all { it.latitude in -90.0..90.0 })
        assertTrue(boundary.all { it.longitude in -180.0..180.0 })
    }
}
