package net.sagberg.kartoffel.map

import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.GeoCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualRouteDrawingTest {
    @Test
    fun previewDeduplicatesConnectedSegmentsAndUndoWalksDraftBack() {
        val drawing = ManualRouteDrawing(pathBetween = { start, _ ->
            if (start.latitude == 1.0) {
                listOf(CoverageCellId(1), CoverageCellId(2))
            } else {
                listOf(CoverageCellId(2), CoverageCellId(3))
            }
        })

        val preview = drawing.recompute(
            listOf(
                GeoCoordinate(1.0, 1.0),
                GeoCoordinate(2.0, 2.0),
                GeoCoordinate(3.0, 3.0),
            ),
        )

        assertEquals(listOf(1L, 2L, 3L), preview.cells.map { it.value })
        assertTrue(preview.canConfirm)
        assertEquals(2, drawing.undo(preview).waypoints.size)
        assertEquals(1, drawing.undo(drawing.undo(preview)).waypoints.size)
    }

    @Test
    fun conversionFailureKeepsWaypointsAndPartialPreviewButBlocksConfirmation() {
        val drawing = ManualRouteDrawing(pathBetween = { start, _ ->
            if (start.latitude == 2.0) error("cannot cross pentagon")
            listOf(CoverageCellId(1), CoverageCellId(2))
        })
        val waypoints = listOf(
            GeoCoordinate(1.0, 1.0),
            GeoCoordinate(2.0, 2.0),
            GeoCoordinate(3.0, 3.0),
        )

        val preview = drawing.recompute(waypoints)

        assertEquals(waypoints, preview.waypoints)
        assertEquals(setOf(CoverageCellId(1), CoverageCellId(2)), preview.cells)
        assertEquals(1, preview.failingSegmentIndex)
        assertFalse(preview.canConfirm)
    }
}
