package net.sagberg.kartoffel.coverage

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class H3AndroidIntegrationTest {
    @Test
    fun loadsNativeH3AndCalculatesACoverageCellBoundary() {
        val coverageCells = H3CoverageCells()
        val coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522)
        val cell = coverageCells.cellAt(coordinate)

        assertEquals(626169207098265599, cell.value)
        assertTrue(coverageCells.boundaryOf(cell).size >= 5)
    }
}
