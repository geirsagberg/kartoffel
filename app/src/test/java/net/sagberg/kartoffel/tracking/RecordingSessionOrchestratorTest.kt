package net.sagberg.kartoffel.tracking

import kotlinx.coroutines.runBlocking
import net.sagberg.kartoffel.coverage.GeoCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSessionOrchestratorTest {
    @Test
    fun locationUpdatesAreRecordedOnlyDuringAnActiveRecordingSession() = runBlocking {
        val gateway = FakeRecordingSessionGateway()
        val locationUpdates = FakeRecordingLocationUpdates()
        val orchestrator = RecordingSessionOrchestrator(gateway, locationUpdates)
        val fix = RecordingLocationFix(
            coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522),
            capturedAtMillis = 2_000,
            accuracyMeters = 8.0,
        )

        orchestrator.start(startedAtMillis = 1_000)
        locationUpdates.emit(fix)
        orchestrator.stop(endedAtMillis = 3_000)

        assertTrue(locationUpdates.started)
        assertTrue(locationUpdates.stopped)
        assertEquals(listOf(42L to fix), gateway.recorded)
        assertEquals(listOf(42L to 3_000L), gateway.stopped)
        assertFalse(orchestrator.isRecording)
    }

    @Test
    fun repeatedStartCommandKeepsOneRecordingSessionActive() = runBlocking {
        val gateway = FakeRecordingSessionGateway()
        val locationUpdates = FakeRecordingLocationUpdates()
        val orchestrator = RecordingSessionOrchestrator(gateway, locationUpdates)

        orchestrator.start(startedAtMillis = 1_000)
        orchestrator.start(startedAtMillis = 2_000)

        assertEquals(1, gateway.startCount)
        assertTrue(orchestrator.isRecording)
    }

    @Test
    fun persistedRecordingSessionResumesAfterServiceRecreation() = runBlocking {
        val gateway = FakeRecordingSessionGateway(activeSessionId = 99L)
        val locationUpdates = FakeRecordingLocationUpdates()
        val orchestrator = RecordingSessionOrchestrator(gateway, locationUpdates)
        val fix = RecordingLocationFix(
            coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522),
            capturedAtMillis = 2_000,
            accuracyMeters = 8.0,
        )

        orchestrator.start(startedAtMillis = 1_000)
        locationUpdates.emit(fix)

        assertEquals(0, gateway.startCount)
        assertEquals(listOf(99L to fix), gateway.recorded)
    }

    private class FakeRecordingSessionGateway(
        private val activeSessionId: Long? = null,
    ) : RecordingSessionGateway {
        var startCount = 0
        val recorded = mutableListOf<Pair<Long, RecordingLocationFix>>()
        val stopped = mutableListOf<Pair<Long, Long>>()

        override suspend fun activeSessionId(): Long? = activeSessionId

        override suspend fun start(startedAtMillis: Long): Long {
            startCount += 1
            return 42L
        }

        override suspend fun record(
            sessionId: Long,
            fix: RecordingLocationFix,
        ): RecordingLocationDecision {
            recorded += sessionId to fix
            return RecordingLocationDecision(accepted = true, rejectionReason = null)
        }

        override suspend fun stop(sessionId: Long, endedAtMillis: Long) {
            stopped += sessionId to endedAtMillis
        }
    }

    private class FakeRecordingLocationUpdates : RecordingLocationUpdates {
        var started = false
        var stopped = false
        private var listener: (suspend (RecordingLocationFix) -> Unit)? = null

        override fun start(listener: suspend (RecordingLocationFix) -> Unit) {
            started = true
            this.listener = listener
        }

        override fun stop() {
            stopped = true
            listener = null
        }

        suspend fun emit(fix: RecordingLocationFix) {
            checkNotNull(listener)(fix)
        }
    }
}
