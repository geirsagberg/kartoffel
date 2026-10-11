package net.sagberg.kartoffel.map

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.GeoCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawnRouteEditorTest {
    private val first = GeoCoordinate(1.0, 1.0)
    private val second = GeoCoordinate(2.0, 2.0)
    private val third = GeoCoordinate(3.0, 3.0)

    @Test
    fun editingJourneyMovesWaypointsDeduplicatesCellsAndUndoesBeforeSaving() = runBlocking {
        var saved: Set<Long>? = null
        val editor = DrawnRouteEditor(
            pathBetween = { start, _ ->
                if (start == first) listOf(CoverageCellId(1), CoverageCellId(2))
                else listOf(CoverageCellId(2), CoverageCellId(3))
            },
            saveRoute = { saved = it; 7L },
        )
        editor.add(first)
        assertFalse(editor.state.value.active)
        assertNull(editor.confirm())
        editor.enter()
        editor.add(first)
        assertFalse(editor.state.value.preview.canConfirm)
        assertNull(editor.confirm())
        editor.add(second)
        editor.add(third)
        assertEquals(setOf(1L, 2L, 3L), editor.state.value.preview.cells.map { it.value }.toSet())
        editor.move(2, second)
        assertEquals(listOf(first, second, second), editor.state.value.preview.waypoints)
        editor.undo()
        assertEquals(listOf(first, second), editor.state.value.preview.waypoints)
        assertEquals(7L, editor.confirm()?.getOrThrow())
        assertEquals(setOf(1L, 2L), saved)
        assertEquals(DrawnRouteEditingState(), editor.state.value)
        assertNull(editor.confirm())
    }

    @Test
    fun cancelDiscardsOnlyTheUnconfirmedDraftAndAllowsAFreshStart() {
        val editor = DrawnRouteEditor(
            pathBetween = { _, _ -> listOf(CoverageCellId(1)) },
            saveRoute = { error("Cancel must not save") },
        )
        editor.enter()
        editor.add(first)
        editor.enter()
        assertEquals(listOf(first), editor.state.value.preview.waypoints)
        editor.cancel()
        assertEquals(DrawnRouteEditingState(), editor.state.value)
        editor.enter()
        editor.undo()
        assertTrue(editor.state.value.preview.waypoints.isEmpty())
    }

    @Test
    fun failedSegmentRetainsPartialPreviewAndBlocksSaveUntilEdited() = runBlocking {
        var saves = 0
        val editor = DrawnRouteEditor(
            pathBetween = { _, destination ->
                if (destination == third) error("Cannot connect segment")
                listOf(CoverageCellId(1), CoverageCellId(2))
            },
            saveRoute = { saves++; 1L },
        )
        editor.enter()
        editor.add(first)
        editor.add(second)
        editor.add(third)
        assertEquals(listOf(first, second, third), editor.state.value.preview.waypoints)
        assertEquals(setOf(1L, 2L), editor.state.value.preview.cells.map { it.value }.toSet())
        assertEquals(1, editor.state.value.preview.failingSegmentIndex)
        assertNull(editor.confirm())
        assertEquals(0, saves)
        editor.move(2, second)
        assertNull(editor.state.value.preview.failingSegmentIndex)
        assertEquals(1L, editor.confirm()?.getOrThrow())
        assertEquals(1, saves)
    }

    @Test
    fun savingFreezesTheSubmittedRouteAndIgnoresRepeatedConfirmation() = runBlocking {
        val finishSave = CompletableDeferred<Long>()
        var saves = 0
        var submitted: Set<Long>? = null
        val editor = DrawnRouteEditor(
            pathBetween = { _, _ -> listOf(CoverageCellId(1), CoverageCellId(2)) },
            saveRoute = { submitted = it; saves++; finishSave.await() },
        )
        editor.enter()
        editor.add(first)
        editor.add(second)
        val preview = editor.state.value.preview
        val saving = async(start = CoroutineStart.UNDISPATCHED) { editor.confirm() }
        assertTrue(editor.state.value.saving)
        editor.add(third)
        editor.move(0, third)
        editor.undo()
        editor.cancel()
        editor.enter()
        assertNull(editor.confirm())
        assertEquals(preview, editor.state.value.preview)
        assertEquals(1, saves)
        assertEquals(setOf(1L, 2L), submitted)
        finishSave.complete(8L)
        assertEquals(8L, saving.await()?.getOrThrow())
        assertEquals(DrawnRouteEditingState(), editor.state.value)
    }

    @Test
    fun failedSaveKeepsTheDraftForRetry() = runBlocking {
        var attempts = 0
        val failure = IllegalStateException("Disk full")
        val editor = DrawnRouteEditor(
            pathBetween = { _, _ -> listOf(CoverageCellId(1)) },
            saveRoute = { if (++attempts == 1) throw failure else 9L },
        )
        editor.enter()
        editor.add(first)
        editor.add(second)
        val before = editor.state.value
        assertEquals(failure, editor.confirm()?.exceptionOrNull())
        assertEquals(before, editor.state.value)
        assertEquals(9L, editor.confirm()?.getOrThrow())
        assertFalse(editor.state.value.active)
        assertEquals(2, attempts)
    }

    @Test
    fun coroutineCancellationIsPropagatedAndDoesNotLeaveTheEditorSaving() = runBlocking {
        val editor = DrawnRouteEditor(
            pathBetween = { _, _ -> listOf(CoverageCellId(1)) },
            saveRoute = { throw CancellationException("Map left") },
        )
        editor.enter()
        editor.add(first)
        editor.add(second)
        val before = editor.state.value
        var cancelled = false
        try {
            editor.confirm()
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(before, editor.state.value)
    }
}
