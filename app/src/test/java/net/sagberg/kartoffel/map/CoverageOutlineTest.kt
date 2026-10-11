package net.sagberg.kartoffel.map

import com.uber.h3core.H3Core
import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageOutlineTest {
    private val h3 = H3Core.newInstance()
    private val coverageCells = H3CoverageCells(h3)
    private val center = coverageCells.cellAt(GeoCoordinate(latitude = 59.9109, longitude = 10.7522))

    @Test
    fun singleCellIsOutlinedByItsOwnBoundary() {
        val outline = coverageOutline(listOf(coverageCells.shapeOf(center)))

        assertEquals(1, outline.size)
        assertEquals(coverageCells.boundaryOf(center).toSet(), outline.single().toSet())
    }

    @Test
    fun neighbouringCellsShareOneOutlineWithoutTheirCommonEdge() {
        val neighbour = disk(1).first { it != center }

        val outline = coverageOutline(listOf(center, neighbour).map(coverageCells::shapeOf))

        assertEquals(1, outline.size)
        assertEquals(10, outline.single().size)
    }

    @Test
    fun fogEnclosedByCoverageIsOutlinedSeparately() {
        val ringAroundCenter = disk(1) - center

        val outline = coverageOutline(ringAroundCenter.map(coverageCells::shapeOf))

        assertEquals(listOf(6, 18), outline.map { it.size }.sorted())
        assertEquals(
            coverageCells.boundaryOf(center).toSet(),
            outline.first { it.size == 6 }.toSet(),
        )
    }

    @Test
    fun separateAreasGetSeparateOutlines() {
        val farCell = disk(3).first { h3.gridDistance(center.value, it.value) == 3L }

        val outline = coverageOutline(listOf(center, farCell).map(coverageCells::shapeOf))

        assertEquals(listOf(6, 6), outline.map { it.size })
    }

    private fun disk(radius: Int): List<CoverageCellId> =
        h3.gridDisk(center.value, radius).map(::CoverageCellId)
}
