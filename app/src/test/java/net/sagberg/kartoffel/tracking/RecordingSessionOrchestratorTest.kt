package net.sagberg.kartoffel.tracking

import kotlinx.coroutines.runBlocking
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.diagnostics.LatestFixDiagnostics
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnostics
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnosticsState
import net.sagberg.kartoffel.diagnostics.LocationUpdateState
import net.sagberg.kartoffel.diagnostics.RequestedIntervalReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSessionOrchestratorTest {
    @Test
    fun locationUpdatesAreRecordedOnlyDuringAnActiveRecordingSession() = runBlocking {
        val gateway = FakeRecordingSessionGateway()
        val locationUpdates = FakeRecordingLocationUpdates()
        val activityUpdates = FakeRecordingActivityUpdates()
        val orchestrator = RecordingSessionOrchestrator(gateway, locationUpdates, activityUpdates)
        val fix = RecordingLocationFix(
            coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522),
            capturedAtMillis = 2_000,
            accuracyMeters = 8.0,
        )

        orchestrator.start(startedAtMillis = 1_000)
        locationUpdates.emit(fix)
        orchestrator.stop(endedAtMillis = 3_000)

        assertEquals(listOf(5_000L), locationUpdates.startedIntervals)
        assertEquals(1, locationUpdates.stopCount)
        assertTrue(activityUpdates.started)
        assertTrue(activityUpdates.stopped)
        assertEquals(listOf(42L to fix), gateway.recorded)
        assertEquals(listOf(42L to 3_000L), gateway.stopped)
        assertFalse(orchestrator.isRecording)
    }

    @Test
    fun repeatedStartCommandKeepsOneRecordingSessionActive() = runBlocking {
        val gateway = FakeRecordingSessionGateway()
        val locationUpdates = FakeRecordingLocationUpdates()
        val activityUpdates = FakeRecordingActivityUpdates()
        val orchestrator = RecordingSessionOrchestrator(gateway, locationUpdates, activityUpdates)

        orchestrator.start(startedAtMillis = 1_000)
        orchestrator.start(startedAtMillis = 2_000)

        assertEquals(1, gateway.startCount)
        assertEquals(1, locationUpdates.startedIntervals.size)
        assertEquals(1, activityUpdates.startCount)
        assertTrue(orchestrator.isRecording)
    }

    @Test
    fun persistedRecordingSessionResumesAfterServiceRecreation() = runBlocking {
        val gateway = FakeRecordingSessionGateway(activeSessionId = 99L)
        val locationUpdates = FakeRecordingLocationUpdates()
        val activityUpdates = FakeRecordingActivityUpdates()
        val orchestrator = RecordingSessionOrchestrator(gateway, locationUpdates, activityUpdates)
        val fix = RecordingLocationFix(
            coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522),
            capturedAtMillis = 2_000,
            accuracyMeters = 8.0,
        )

        assertTrue(orchestrator.resumeActiveSession())
        locationUpdates.emit(fix)

        assertEquals(0, gateway.startCount)
        assertEquals(listOf(99L to fix), gateway.recorded)
    }

    @Test
    fun activityCallbackCannotCreateANewRecordingSession() = runBlocking {
        val gateway = FakeRecordingSessionGateway()
        val locationUpdates = FakeRecordingLocationUpdates()
        val orchestrator = RecordingSessionOrchestrator(
            gateway,
            locationUpdates,
            FakeRecordingActivityUpdates(),
        )

        assertFalse(orchestrator.resumeActiveSession())

        assertEquals(0, gateway.startCount)
        assertEquals(emptyList<Long>(), locationUpdates.startedIntervals)
    }

    @Test
    fun activityTransitionsAdaptTheLocationInterval() = runBlocking {
        val locationUpdates = FakeRecordingLocationUpdates()
        val activityUpdates = FakeRecordingActivityUpdates()
        val orchestrator = RecordingSessionOrchestrator(
            FakeRecordingSessionGateway(),
            locationUpdates,
            activityUpdates,
        )

        orchestrator.start(startedAtMillis = 1_000)
        activityUpdates.emit(RecordingActivity.WALKING)
        activityUpdates.emit(RecordingActivity.RUNNING)
        activityUpdates.emit(RecordingActivity.ON_BICYCLE)
        activityUpdates.emit(RecordingActivity.IN_VEHICLE)
        activityUpdates.emit(RecordingActivity.UNKNOWN)

        assertEquals(listOf(5_000L), locationUpdates.startedIntervals)
        assertEquals(listOf(10_000L, 5_000L, 1_000L, 5_000L), locationUpdates.updatedIntervals)
    }

    @Test
    fun liveDiagnosticsExplainTheCurrentLocationPolicy() = runBlocking {
        val locationUpdates = FakeRecordingLocationUpdates()
        val activityUpdates = FakeRecordingActivityUpdates()
        val diagnostics = LiveTrackingDiagnostics()
        val orchestrator = RecordingSessionOrchestrator(
            FakeRecordingSessionGateway(),
            locationUpdates,
            activityUpdates,
            diagnostics,
        )

        orchestrator.start(startedAtMillis = 1_000)

        assertEquals(
            LiveTrackingDiagnosticsState(
                trackingActive = true,
                activityMode = RecordingActivity.UNKNOWN,
                locationUpdateState = LocationUpdateState.ACTIVE,
                requestedLocationIntervalMillis = 5_000,
                intervalReason = RequestedIntervalReason.SESSION_START,
            ),
            diagnostics.state.value,
        )

        activityUpdates.emit(RecordingActivity.WALKING)

        assertEquals(RecordingActivity.WALKING, diagnostics.state.value.activityMode)
        assertEquals(10_000L, diagnostics.state.value.requestedLocationIntervalMillis)
        assertEquals(RequestedIntervalReason.ACTIVITY_MODE, diagnostics.state.value.intervalReason)

        activityUpdates.emit(RecordingActivity.STILL)

        assertEquals(LocationUpdateState.SUSPENDED, diagnostics.state.value.locationUpdateState)
        assertEquals(null, diagnostics.state.value.requestedLocationIntervalMillis)
        assertEquals(
            RequestedIntervalReason.SUSPENDED_WHILE_STILL,
            diagnostics.state.value.intervalReason,
        )

        orchestrator.stop(endedAtMillis = 2_000)

        assertFalse(diagnostics.state.value.trackingActive)
    }

    @Test
    fun stillStopsLocationUntilMovementResumesIt() = runBlocking {
        val locationUpdates = FakeRecordingLocationUpdates()
        val activityUpdates = FakeRecordingActivityUpdates()
        val orchestrator = RecordingSessionOrchestrator(
            FakeRecordingSessionGateway(),
            locationUpdates,
            activityUpdates,
        )

        orchestrator.start(startedAtMillis = 1_000)
        activityUpdates.emit(RecordingActivity.STILL)
        activityUpdates.emit(RecordingActivity.WALKING)

        assertEquals(listOf(5_000L, 10_000L), locationUpdates.startedIntervals)
        assertEquals(1, locationUpdates.stopCount)
        assertTrue(activityUpdates.started)
    }

    @Test
    fun unavailableActivityRecognitionKeepsTheFiveSecondFallback() = runBlocking {
        val locationUpdates = FakeRecordingLocationUpdates()
        val activityUpdates = FakeRecordingActivityUpdates(failOnStart = true)
        val orchestrator = RecordingSessionOrchestrator(
            FakeRecordingSessionGateway(),
            locationUpdates,
            activityUpdates,
        )

        orchestrator.start(startedAtMillis = 1_000)

        assertTrue(orchestrator.isRecording)
        assertEquals(listOf(5_000L), locationUpdates.startedIntervals)
    }

    @Test
    fun firstConfidentActivitySampleReplacesUnknownAndStopsBootstrapSampling() = runBlocking {
        val locationUpdates = FakeRecordingLocationUpdates()
        val activityUpdates = FakeRecordingActivityUpdates()
        val diagnostics = LiveTrackingDiagnostics()
        val orchestrator = RecordingSessionOrchestrator(
            FakeRecordingSessionGateway(),
            locationUpdates,
            activityUpdates,
            diagnostics,
        )

        orchestrator.start(startedAtMillis = 1_000)
        activityUpdates.emitBootstrap(RecordingActivity.WALKING, confidence = 74)

        assertEquals(RecordingActivity.UNKNOWN, diagnostics.state.value.activityMode)
        assertEquals(0, activityUpdates.stopBootstrapCount)

        activityUpdates.emitBootstrap(RecordingActivity.WALKING, confidence = 75)

        assertEquals(RecordingActivity.WALKING, diagnostics.state.value.activityMode)
        assertEquals(10_000L, diagnostics.state.value.requestedLocationIntervalMillis)
        assertEquals(1, activityUpdates.stopBootstrapCount)
    }

    @Test
    fun activityTransitionPreventsALaterBootstrapSampleFromReplacingTheMode() = runBlocking {
        val activityUpdates = FakeRecordingActivityUpdates()
        val diagnostics = LiveTrackingDiagnostics()
        val orchestrator = RecordingSessionOrchestrator(
            FakeRecordingSessionGateway(),
            FakeRecordingLocationUpdates(),
            activityUpdates,
            diagnostics,
        )

        orchestrator.start(startedAtMillis = 1_000)
        activityUpdates.emit(RecordingActivity.WALKING)
        activityUpdates.emitBootstrap(RecordingActivity.STILL, confidence = 90)

        assertEquals(RecordingActivity.WALKING, diagnostics.state.value.activityMode)
        assertEquals(1, activityUpdates.stopBootstrapCount)
    }

    @Test
    fun onlyTheFirstConfidentBootstrapSampleSeedsTheActivityMode() = runBlocking {
        val activityUpdates = FakeRecordingActivityUpdates()
        val diagnostics = LiveTrackingDiagnostics()
        val orchestrator = RecordingSessionOrchestrator(
            FakeRecordingSessionGateway(),
            FakeRecordingLocationUpdates(),
            activityUpdates,
            diagnostics,
        )

        orchestrator.start(startedAtMillis = 1_000)
        activityUpdates.emitBootstrap(RecordingActivity.WALKING, confidence = 80)
        activityUpdates.emitBootstrap(RecordingActivity.STILL, confidence = 90)

        assertEquals(RecordingActivity.WALKING, diagnostics.state.value.activityMode)
        assertEquals(1, activityUpdates.stopBootstrapCount)
    }

    @Test
    fun speedCanEscalateButNotRelaxTheCurrentInterval() = runBlocking {
        val locationUpdates = FakeRecordingLocationUpdates()
        val activityUpdates = FakeRecordingActivityUpdates()
        val diagnostics = LiveTrackingDiagnostics()
        val orchestrator = RecordingSessionOrchestrator(
            FakeRecordingSessionGateway(),
            locationUpdates,
            activityUpdates,
            diagnostics,
        )
        val coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522)

        orchestrator.start(startedAtMillis = 1_000)
        activityUpdates.emit(RecordingActivity.WALKING)
        locationUpdates.emit(
            RecordingLocationFix(coordinate, 2_000, 8.0, speedMetersPerSecond = 3.0),
        )
        locationUpdates.emit(
            RecordingLocationFix(coordinate, 3_000, 8.0, speedMetersPerSecond = 12.0),
        )
        locationUpdates.emit(
            RecordingLocationFix(coordinate, 4_000, 8.0, speedMetersPerSecond = 0.0),
        )

        assertEquals(listOf(10_000L, 5_000L, 1_000L), locationUpdates.updatedIntervals)
        assertEquals(1_000L, diagnostics.state.value.requestedLocationIntervalMillis)
        assertEquals(RequestedIntervalReason.SPEED_OVERRIDE, diagnostics.state.value.intervalReason)
        assertEquals(RecordingActivity.WALKING, diagnostics.state.value.activityMode)
    }

    @Test
    fun liveDiagnosticsExplainTheLatestRejectedFix() = runBlocking {
        val locationUpdates = FakeRecordingLocationUpdates()
        val diagnostics = LiveTrackingDiagnostics()
        val orchestrator = RecordingSessionOrchestrator(
            gateway = FakeRecordingSessionGateway(
                decision = RecordingLocationDecision(
                    accepted = false,
                    rejectionReason = "accuracy_exceeds_recording_limit",
                ),
            ),
            locationUpdates = locationUpdates,
            activityUpdates = FakeRecordingActivityUpdates(),
            diagnostics = diagnostics,
        )
        val fix = RecordingLocationFix(
            coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522),
            capturedAtMillis = 2_000,
            accuracyMeters = 34.0,
        )

        orchestrator.start(startedAtMillis = 1_000)
        locationUpdates.emit(fix)

        assertEquals(
            LatestFixDiagnostics(
                capturedAtMillis = 2_000,
                accuracyMeters = 34.0,
                accepted = false,
                rejectionReason = "accuracy_exceeds_recording_limit",
            ),
            diagnostics.state.value.latestFix,
        )
    }

    private class FakeRecordingSessionGateway(
        private val activeSessionId: Long? = null,
        private val decision: RecordingLocationDecision = RecordingLocationDecision(
            accepted = true,
            rejectionReason = null,
        ),
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
            return decision
        }

        override suspend fun stop(sessionId: Long, endedAtMillis: Long) {
            stopped += sessionId to endedAtMillis
        }
    }

    private class FakeRecordingLocationUpdates : RecordingLocationUpdates {
        val startedIntervals = mutableListOf<Long>()
        val updatedIntervals = mutableListOf<Long>()
        var stopCount = 0
        private var listener: (suspend (RecordingLocationFix) -> Unit)? = null

        override fun start(
            intervalMillis: Long,
            listener: suspend (RecordingLocationFix) -> Unit,
        ) {
            startedIntervals += intervalMillis
            this.listener = listener
        }

        override fun updateInterval(intervalMillis: Long) {
            updatedIntervals += intervalMillis
        }

        override fun stop() {
            stopCount += 1
            listener = null
        }

        suspend fun emit(fix: RecordingLocationFix) {
            checkNotNull(listener)(fix)
        }
    }

    private class FakeRecordingActivityUpdates(
        private val failOnStart: Boolean = false,
    ) : RecordingActivityUpdates {
        var startCount = 0
        var stopBootstrapCount = 0
        var started = false
        var stopped = false
        private var listener: (suspend (RecordingActivityUpdate) -> Unit)? = null

        override fun start(listener: suspend (RecordingActivityUpdate) -> Unit) {
            startCount += 1
            if (failOnStart) throw SecurityException("Activity recognition unavailable")
            started = true
            this.listener = listener
        }

        override fun stop() {
            stopped = true
            listener = null
        }

        override fun stopBootstrap() {
            stopBootstrapCount += 1
        }

        suspend fun emit(activity: RecordingActivity) {
            checkNotNull(listener)(RecordingActivityUpdate.Transition(activity))
        }

        suspend fun emitBootstrap(activity: RecordingActivity, confidence: Int) {
            checkNotNull(listener)(
                RecordingActivityUpdate.BootstrapSample(activity, confidence),
            )
        }
    }
}
