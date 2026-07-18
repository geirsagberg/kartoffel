package net.sagberg.kartoffel.tracking

import kotlinx.coroutines.runBlocking
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.storage.PassiveTrackingPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveTrackingOrchestratorTest {
    @Test
    fun explicitEnableRegistersPassiveMechanismsAndStartsBoundedInitialAcquisition() = runBlocking {
        val gateway = FakePassiveTrackingGateway()
        val actions = FakePassiveTrackingActions()
        val orchestrator = PassiveTrackingOrchestrator(gateway, actions)

        orchestrator.enable(wallTimeMillis = 10_000, elapsedRealtimeMillis = 2_000)

        assertEquals(10_000L, gateway.preference.passivePeriodStartedAtMillis)
        assertTrue(gateway.preference.enabled)
        assertEquals(1, actions.registerActivityCount)
        assertEquals(0, actions.registerOpportunisticCount)
        assertEquals(listOf(PASSIVE_FALLBACK_INTERVAL_MILLIS), actions.scheduledFallbacks)
        assertEquals(
            listOf(
                PassiveCaptureRequest(
                    trigger = PassiveFixTrigger.INITIAL_ENABLE,
                    activity = RecordingActivity.UNKNOWN,
                    intervalMillis = 5_000,
                    durationMillis = 30_000,
                    maximumFixes = 6,
                ),
            ),
            actions.captureRequests,
        )
    }

    @Test
    fun activityIsRetainedButDoesNotReplaceInitialAcquisition() = runBlocking {
        val gateway = FakePassiveTrackingGateway()
        val actions = FakePassiveTrackingActions()
        val orchestrator = PassiveTrackingOrchestrator(gateway, actions)
        orchestrator.enable(1_000, 1_000)

        orchestrator.onActivity(RecordingActivity.STILL, 2_000, 2_000)
        orchestrator.onActivity(RecordingActivity.WALKING, 3_000, 3_000)

        assertEquals(listOf(PassiveFixTrigger.INITIAL_ENABLE), actions.captureRequests.map { it.trigger })
        assertEquals(0, actions.stopCaptureCount)

        orchestrator.onFix(
            fix = RecordingLocationFix(
                coordinate = GeoCoordinate(59.9109, 10.7522),
                capturedAtMillis = 4_000,
                accuracyMeters = 8.0,
            ),
            capturedAtElapsedRealtimeMillis = 4_000,
            trigger = PassiveFixTrigger.INITIAL_ENABLE,
        )

        assertEquals(PassiveFixTrigger.MOVEMENT_WINDOW, actions.captureRequests.last().trigger)
        assertEquals(0, actions.registerOpportunisticCount)
    }

    @Test
    fun enableDuringRecordingPersistsPreferenceWithoutStartingPassiveWork() = runBlocking {
        val gateway = FakePassiveTrackingGateway(recordingSessionActive = true)
        val actions = FakePassiveTrackingActions()

        PassiveTrackingOrchestrator(gateway, actions).enable(1_000, 1_000)

        assertTrue(gateway.preference.enabled)
        assertEquals(0, actions.registerActivityCount)
        assertTrue(actions.scheduledFallbacks.isEmpty())
        assertTrue(actions.captureRequests.isEmpty())
    }

    @Test
    fun movementOpensModeSpecificBoundedWindowsAndStillStopsThem() = runBlocking {
        val gateway = FakePassiveTrackingGateway(
            preference = PassiveTrackingPreference(true, 1_000),
        )
        val actions = FakePassiveTrackingActions()
        val orchestrator = PassiveTrackingOrchestrator(gateway, actions)

        listOf(
            RecordingActivity.WALKING to 10_000L,
            RecordingActivity.RUNNING to 5_000L,
            RecordingActivity.ON_BICYCLE to 5_000L,
            RecordingActivity.IN_VEHICLE to 1_000L,
        ).forEachIndexed { index, (activity, interval) ->
            orchestrator.onActivity(
                activity = activity,
                observedAtElapsedRealtimeMillis = 2_000L + index,
                currentElapsedRealtimeMillis = 2_000L + index,
            )
            assertEquals(
                PassiveCaptureRequest(
                    trigger = PassiveFixTrigger.MOVEMENT_WINDOW,
                    activity = activity,
                    intervalMillis = interval,
                ),
                actions.captureRequests.last(),
            )
        }

        orchestrator.onActivity(
            activity = RecordingActivity.STILL,
            observedAtElapsedRealtimeMillis = 3_000,
            currentElapsedRealtimeMillis = 3_000,
        )

        assertEquals(1, actions.stopCaptureCount)
    }

    @Test
    fun freshStillSuppressesFallbackUntilActivityBecomesStale() = runBlocking {
        val gateway = FakePassiveTrackingGateway(
            preference = PassiveTrackingPreference(true, 1_000),
        )
        val actions = FakePassiveTrackingActions()
        val orchestrator = PassiveTrackingOrchestrator(gateway, actions)
        orchestrator.onActivity(RecordingActivity.STILL, 1_000, 1_000)
        actions.captureRequests.clear()

        orchestrator.onFallback(elapsedRealtimeMillis = 1_000 + 29 * 60 * 1_000L)

        assertTrue(actions.captureRequests.isEmpty())

        orchestrator.onFallback(elapsedRealtimeMillis = 1_000 + 31 * 60 * 1_000L)

        assertEquals(
            PassiveCaptureRequest(
                trigger = PassiveFixTrigger.FALLBACK_WINDOW,
                activity = RecordingActivity.UNKNOWN,
                intervalMillis = 10_000,
            ),
            actions.captureRequests.single(),
        )
        assertEquals(2, actions.scheduledFallbacks.size)
    }

    @Test
    fun staleMovementDoesNotOpenAWindowAndFreshMovementSuppressesFallback() = runBlocking {
        val gateway = FakePassiveTrackingGateway(
            preference = PassiveTrackingPreference(true, 1_000),
        )
        val actions = FakePassiveTrackingActions()
        val orchestrator = PassiveTrackingOrchestrator(gateway, actions)

        orchestrator.onActivity(
            activity = RecordingActivity.WALKING,
            observedAtElapsedRealtimeMillis = 1_000,
            currentElapsedRealtimeMillis = 1_000 + PASSIVE_ACTIVITY_FRESHNESS_MILLIS + 1,
        )
        orchestrator.onFallback(1_000 + PASSIVE_ACTIVITY_FRESHNESS_MILLIS + 2)
        assertEquals(PassiveFixTrigger.FALLBACK_WINDOW, actions.captureRequests.single().trigger)

        actions.captureRequests.clear()
        orchestrator.onActivity(RecordingActivity.RUNNING, 3_000_000, 3_000_000)
        actions.captureRequests.clear()
        orchestrator.onFallback(3_000_001)

        assertTrue(actions.captureRequests.isEmpty())
    }

    @Test
    fun fixesRequireTheCurrentPassivePeriodAndUseCaptureTimeAlignedActivity() = runBlocking {
        val gateway = FakePassiveTrackingGateway(
            preference = PassiveTrackingPreference(true, 10_000),
        )
        val actions = FakePassiveTrackingActions()
        val orchestrator = PassiveTrackingOrchestrator(gateway, actions)
        orchestrator.onActivity(RecordingActivity.WALKING, 1_000, 1_000)
        actions.captureRequests.clear()
        val coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522)

        orchestrator.onFix(
            fix = RecordingLocationFix(coordinate, 9_999, 8.0),
            capturedAtElapsedRealtimeMillis = 2_000,
            trigger = PassiveFixTrigger.OPPORTUNISTIC,
        )
        orchestrator.onFix(
            fix = RecordingLocationFix(coordinate, 11_000, 8.0),
            capturedAtElapsedRealtimeMillis = 2_000,
            trigger = PassiveFixTrigger.OPPORTUNISTIC,
        )
        orchestrator.onFix(
            fix = RecordingLocationFix(coordinate, 12_000, 8.0),
            capturedAtElapsedRealtimeMillis = 1_000 + PASSIVE_ACTIVITY_FRESHNESS_MILLIS + 1,
            trigger = PassiveFixTrigger.INITIAL_ENABLE,
        )

        assertEquals(
            listOf(RecordingActivity.WALKING, RecordingActivity.UNKNOWN),
            gateway.recorded.map { it.third },
        )
        assertEquals(1, actions.stopCaptureCount)
    }

    @Test
    fun sixthRejectedInitialDeliveryEndsAcquisitionAndRestoresOpportunisticListening() = runBlocking {
        val gateway = FakePassiveTrackingGateway(
            preference = PassiveTrackingPreference(true, 1_000),
            acceptedDecisions = false,
        )
        val actions = FakePassiveTrackingActions()
        val orchestrator = PassiveTrackingOrchestrator(gateway, actions)
        orchestrator.enable(1_000, 1_000)
        val coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522)

        repeat(PASSIVE_CAPTURE_MAXIMUM_FIXES) { index ->
            orchestrator.onFix(
                fix = RecordingLocationFix(coordinate, 2_000L + index, 30.0),
                capturedAtElapsedRealtimeMillis = 2_000L + index,
                trigger = PassiveFixTrigger.INITIAL_ENABLE,
            )
        }

        assertEquals(1, actions.stopCaptureCount)
        assertEquals(1, actions.registerOpportunisticCount)
    }

    @Test
    fun recordingTemporarilySuppressesPassiveWorkAndReturnsToFreshMovement() = runBlocking {
        val gateway = FakePassiveTrackingGateway(
            preference = PassiveTrackingPreference(true, 1_000),
        )
        val actions = FakePassiveTrackingActions()
        val orchestrator = PassiveTrackingOrchestrator(gateway, actions)
        orchestrator.onActivity(RecordingActivity.WALKING, 2_000, 2_000)
        actions.captureRequests.clear()
        val unregistersBeforePause = actions.unregisterOpportunisticCount

        orchestrator.pauseForRecordingSession()

        assertEquals(1, actions.stopCaptureCount)
        assertEquals(unregistersBeforePause + 1, actions.unregisterOpportunisticCount)
        assertEquals(1, actions.unregisterActivityCount)
        assertEquals(1, actions.cancelFallbackCount)

        orchestrator.resumeAfterRecordingSession(
            wallTimeMillis = 20_000,
            elapsedRealtimeMillis = 3_000,
        )

        assertEquals(20_000L, gateway.preference.passivePeriodStartedAtMillis)
        assertEquals(1, actions.registerActivityCount)
        assertEquals(1, actions.registerOpportunisticCount)
        assertEquals(
            PassiveFixTrigger.MOVEMENT_WINDOW,
            actions.captureRequests.single().trigger,
        )
    }

    @Test
    fun restoreRearmsWithoutInitialAcquisitionAndDisableRemovesAllWork() = runBlocking {
        val gateway = FakePassiveTrackingGateway(
            preference = PassiveTrackingPreference(true, 1_000),
        )
        val actions = FakePassiveTrackingActions()
        val orchestrator = PassiveTrackingOrchestrator(gateway, actions)

        orchestrator.restore()

        assertEquals(1, actions.registerActivityCount)
        assertEquals(1, actions.registerOpportunisticCount)
        assertTrue(actions.captureRequests.isEmpty())

        orchestrator.disable()

        assertEquals(false, gateway.preference.enabled)
        assertEquals(1, actions.unregisterActivityCount)
        assertEquals(1, actions.unregisterOpportunisticCount)
        assertEquals(2, actions.stopCaptureCount)
        assertEquals(1, actions.cancelFallbackCount)
    }

    @Test
    fun staleFallbackDoesNotRearmWorkOutsidePassiveMode() = runBlocking {
        val disabledActions = FakePassiveTrackingActions()
        PassiveTrackingOrchestrator(
            gateway = FakePassiveTrackingGateway(),
            actions = disabledActions,
        ).onFallback(elapsedRealtimeMillis = 2_000)

        val recordingActions = FakePassiveTrackingActions()
        PassiveTrackingOrchestrator(
            gateway = FakePassiveTrackingGateway(
                preference = PassiveTrackingPreference(true, 1_000),
                recordingSessionActive = true,
            ),
            actions = recordingActions,
        ).onFallback(elapsedRealtimeMillis = 2_000)

        assertTrue(disabledActions.scheduledFallbacks.isEmpty())
        assertTrue(recordingActions.scheduledFallbacks.isEmpty())
        assertTrue(disabledActions.captureRequests.isEmpty())
        assertTrue(recordingActions.captureRequests.isEmpty())
    }

    @Test
    fun staleCaptureEndDoesNotRestoreOpportunisticWorkOutsidePassiveMode() = runBlocking {
        val disabledActions = FakePassiveTrackingActions()
        PassiveTrackingOrchestrator(
            gateway = FakePassiveTrackingGateway(),
            actions = disabledActions,
        ).onCaptureEnded(elapsedRealtimeMillis = 2_000)

        val recordingActions = FakePassiveTrackingActions()
        PassiveTrackingOrchestrator(
            gateway = FakePassiveTrackingGateway(
                preference = PassiveTrackingPreference(true, 1_000),
                recordingSessionActive = true,
            ),
            actions = recordingActions,
        ).onCaptureEnded(elapsedRealtimeMillis = 2_000)

        assertEquals(0, disabledActions.registerOpportunisticCount)
        assertEquals(0, recordingActions.registerOpportunisticCount)
    }

    @Test
    fun restoreDuringRecordingDoesNotRegisterPassiveMechanisms() = runBlocking {
        val actions = FakePassiveTrackingActions()
        PassiveTrackingOrchestrator(
            gateway = FakePassiveTrackingGateway(
                preference = PassiveTrackingPreference(true, 1_000),
                recordingSessionActive = true,
            ),
            actions = actions,
        ).restore()

        assertEquals(0, actions.registerActivityCount)
        assertEquals(0, actions.registerOpportunisticCount)
        assertTrue(actions.scheduledFallbacks.isEmpty())
    }

    @Test
    fun restoreDuringActiveCaptureDoesNotEnableParallelOpportunisticDelivery() = runBlocking {
        val gateway = FakePassiveTrackingGateway(
            preference = PassiveTrackingPreference(true, 1_000),
        )
        val actions = FakePassiveTrackingActions()
        val orchestrator = PassiveTrackingOrchestrator(gateway, actions)
        orchestrator.onActivity(RecordingActivity.WALKING, 2_000, 2_000)

        orchestrator.restore()

        assertEquals(0, actions.registerOpportunisticCount)
        assertEquals(0, actions.stopCaptureCount)
    }

    private class FakePassiveTrackingGateway(
        var preference: PassiveTrackingPreference = PassiveTrackingPreference.Disabled,
        private val recordingSessionActive: Boolean = false,
        private val acceptedDecisions: Boolean = true,
    ) : PassiveTrackingGateway {
        val recorded = mutableListOf<Triple<RecordingLocationFix, PassiveFixTrigger, RecordingActivity>>()

        override suspend fun preference(): PassiveTrackingPreference = preference

        override suspend fun enable(passivePeriodStartedAtMillis: Long) {
            preference = PassiveTrackingPreference(
                enabled = true,
                passivePeriodStartedAtMillis = passivePeriodStartedAtMillis,
            )
        }

        override suspend fun disable() {
            preference = PassiveTrackingPreference.Disabled
        }

        override suspend fun recordingSessionActive(): Boolean = recordingSessionActive

        override suspend fun record(
            fix: RecordingLocationFix,
            trigger: PassiveFixTrigger,
            activity: RecordingActivity,
        ): RecordingLocationDecision {
            recorded += Triple(fix, trigger, activity)
            return if (acceptedDecisions) {
                RecordingLocationDecision(true, null)
            } else {
                RecordingLocationDecision(false, PASSIVE_ACCURACY_REJECTION)
            }
        }
    }

    private class FakePassiveTrackingActions : PassiveTrackingActions {
        var registerActivityCount = 0
        var unregisterActivityCount = 0
        var registerOpportunisticCount = 0
        var unregisterOpportunisticCount = 0
        val scheduledFallbacks = mutableListOf<Long>()
        val captureRequests = mutableListOf<PassiveCaptureRequest>()
        var stopCaptureCount = 0
        var cancelFallbackCount = 0

        override fun registerActivityTransitions() {
            registerActivityCount += 1
        }

        override fun unregisterActivityTransitions() {
            unregisterActivityCount += 1
        }

        override fun registerOpportunisticFixes() {
            registerOpportunisticCount += 1
        }

        override fun unregisterOpportunisticFixes() {
            unregisterOpportunisticCount += 1
        }

        override fun startCapture(request: PassiveCaptureRequest) {
            captureRequests += request
        }

        override fun stopCapture() {
            stopCaptureCount += 1
        }

        override fun scheduleFallback(delayMillis: Long) {
            scheduledFallbacks += delayMillis
        }

        override fun cancelFallback() {
            cancelFallbackCount += 1
        }
    }
}
