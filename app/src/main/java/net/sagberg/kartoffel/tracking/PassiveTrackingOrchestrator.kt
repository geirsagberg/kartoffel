package net.sagberg.kartoffel.tracking

import net.sagberg.kartoffel.diagnostics.LatestFixDiagnostics
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnostics
import net.sagberg.kartoffel.diagnostics.RequestedIntervalReason
import net.sagberg.kartoffel.storage.PassiveTrackingPreference

internal const val PASSIVE_FALLBACK_INTERVAL_MILLIS = 60 * 60 * 1_000L
internal const val PASSIVE_ACTIVITY_FRESHNESS_MILLIS = 30 * 60 * 1_000L
internal const val PASSIVE_CAPTURE_DURATION_MILLIS = 90_000L
internal const val PASSIVE_CAPTURE_MAXIMUM_FIXES = 6

internal data class PassiveCaptureRequest(
    val trigger: PassiveFixTrigger,
    val activity: RecordingActivity,
    val intervalMillis: Long,
    val durationMillis: Long = PASSIVE_CAPTURE_DURATION_MILLIS,
    val maximumFixes: Int = PASSIVE_CAPTURE_MAXIMUM_FIXES,
) {
    init {
        require(intervalMillis > 0)
        require(durationMillis > 0)
        require(maximumFixes > 0)
    }
}

internal interface PassiveTrackingGateway {
    suspend fun preference(): PassiveTrackingPreference

    suspend fun enable(passivePeriodStartedAtMillis: Long)

    suspend fun disable()

    suspend fun recordingSessionActive(): Boolean

    suspend fun record(
        fix: RecordingLocationFix,
        trigger: PassiveFixTrigger,
        activity: RecordingActivity,
    ): RecordingLocationDecision
}

internal interface PassiveTrackingActions {
    fun registerActivityTransitions()

    fun unregisterActivityTransitions()

    fun registerOpportunisticFixes()

    fun unregisterOpportunisticFixes()

    fun startCapture(request: PassiveCaptureRequest)

    fun stopCapture()

    fun scheduleFallback(delayMillis: Long)

    fun cancelFallback()
}

internal class PassiveActivityState {
    private var activity = RecordingActivity.UNKNOWN
    private var observedAtElapsedRealtimeMillis: Long? = null

    @Synchronized
    fun observe(activity: RecordingActivity, observedAtElapsedRealtimeMillis: Long): Boolean {
        require(observedAtElapsedRealtimeMillis >= 0)
        if (
            this.observedAtElapsedRealtimeMillis?.let { observedAtElapsedRealtimeMillis < it } == true
        ) {
            return false
        }
        this.activity = activity
        this.observedAtElapsedRealtimeMillis = observedAtElapsedRealtimeMillis
        return true
    }

    @Synchronized
    fun activityAt(elapsedRealtimeMillis: Long): RecordingActivity {
        val observedAt = observedAtElapsedRealtimeMillis ?: return RecordingActivity.UNKNOWN
        return if (
            observedAt <= elapsedRealtimeMillis &&
            elapsedRealtimeMillis - observedAt <= PASSIVE_ACTIVITY_FRESHNESS_MILLIS
        ) {
            activity
        } else {
            RecordingActivity.UNKNOWN
        }
    }

    @Synchronized
    fun reset() {
        activity = RecordingActivity.UNKNOWN
        observedAtElapsedRealtimeMillis = null
    }
}

internal class PassiveCaptureState {
    private var trigger: PassiveFixTrigger? = null
    private var deliveredFixes = 0

    @Synchronized
    fun start(trigger: PassiveFixTrigger) {
        this.trigger = trigger
        deliveredFixes = 0
    }

    @Synchronized
    fun deliveryEndsCapture(trigger: PassiveFixTrigger): Boolean {
        if (this.trigger != trigger) return false
        deliveredFixes += 1
        return deliveredFixes >= PASSIVE_CAPTURE_MAXIMUM_FIXES
    }

    @Synchronized
    fun isActive(): Boolean = trigger != null

    @Synchronized
    fun activeTrigger(): PassiveFixTrigger? = trigger

    @Synchronized
    fun stop() {
        trigger = null
        deliveredFixes = 0
    }
}

internal class PassiveTrackingOrchestrator(
    private val gateway: PassiveTrackingGateway,
    private val actions: PassiveTrackingActions,
    private val activityState: PassiveActivityState = PassiveActivityState(),
    private val captureState: PassiveCaptureState = PassiveCaptureState(),
    private val diagnostics: LiveTrackingDiagnostics = LiveTrackingDiagnostics(),
) {
    suspend fun enable(
        wallTimeMillis: Long,
        elapsedRealtimeMillis: Long,
    ) {
        require(wallTimeMillis >= 0)
        require(elapsedRealtimeMillis >= 0)
        activityState.reset()
        gateway.enable(wallTimeMillis)
        if (gateway.recordingSessionActive()) return
        actions.registerActivityTransitions()
        actions.scheduleFallback(PASSIVE_FALLBACK_INTERVAL_MILLIS)
        startCapture(
            PassiveCaptureRequest(
                trigger = PassiveFixTrigger.INITIAL_ENABLE,
                activity = RecordingActivity.UNKNOWN,
                intervalMillis = DEFAULT_RECORDING_INTERVAL_MILLIS,
                durationMillis = 30_000,
                maximumFixes = PASSIVE_CAPTURE_MAXIMUM_FIXES,
            ),
        )
    }

    suspend fun onActivity(
        activity: RecordingActivity,
        observedAtElapsedRealtimeMillis: Long,
        currentElapsedRealtimeMillis: Long,
    ) {
        require(currentElapsedRealtimeMillis >= 0)
        if (!activityState.observe(activity, observedAtElapsedRealtimeMillis)) return
        if (!gateway.preference().enabled || gateway.recordingSessionActive()) return
        if (captureState.activeTrigger() == PassiveFixTrigger.INITIAL_ENABLE) return
        if (activityState.activityAt(currentElapsedRealtimeMillis) == RecordingActivity.UNKNOWN) {
            return
        }
        if (activity == RecordingActivity.STILL) {
            stopCapture()
            actions.registerOpportunisticFixes()
            diagnostics.passiveWaiting(activity)
            return
        }
        if (activity == RecordingActivity.UNKNOWN) return
        actions.unregisterOpportunisticFixes()
        startCapture(
            PassiveCaptureRequest(
                trigger = PassiveFixTrigger.MOVEMENT_WINDOW,
                activity = activity,
                intervalMillis = activity.passiveIntervalMillis,
            ),
        )
    }

    suspend fun onFallback(elapsedRealtimeMillis: Long) {
        require(elapsedRealtimeMillis >= 0)
        if (!gateway.preference().enabled || gateway.recordingSessionActive()) return
        actions.scheduleFallback(PASSIVE_FALLBACK_INTERVAL_MILLIS)
        val activity = activityState.activityAt(elapsedRealtimeMillis)
        if (activity != RecordingActivity.UNKNOWN) return
        actions.unregisterOpportunisticFixes()
        startCapture(
            PassiveCaptureRequest(
                trigger = PassiveFixTrigger.FALLBACK_WINDOW,
                activity = activity,
                intervalMillis = activity.passiveIntervalMillis,
            ),
        )
    }

    suspend fun onFix(
        fix: RecordingLocationFix,
        capturedAtElapsedRealtimeMillis: Long,
        trigger: PassiveFixTrigger,
    ): RecordingLocationDecision? {
        require(capturedAtElapsedRealtimeMillis >= 0)
        val preference = gateway.preference()
        val passivePeriodStart = preference.passivePeriodStartedAtMillis
        if (
            !preference.enabled ||
            passivePeriodStart == null ||
            fix.capturedAtMillis < passivePeriodStart ||
            gateway.recordingSessionActive()
        ) {
            return null
        }
        val decision = gateway.record(
            fix = fix,
            trigger = trigger,
            activity = activityState.activityAt(capturedAtElapsedRealtimeMillis),
        )
        diagnostics.fixReceived(
            LatestFixDiagnostics(
                capturedAtMillis = fix.capturedAtMillis,
                accuracyMeters = fix.accuracyMeters,
                accepted = decision.accepted,
                rejectionReason = decision.rejectionReason,
            ),
        )
        if (
            (trigger == PassiveFixTrigger.INITIAL_ENABLE && decision.accepted) ||
            captureState.deliveryEndsCapture(trigger)
        ) {
            completeCapture(trigger, capturedAtElapsedRealtimeMillis)
        }
        return decision
    }

    suspend fun pauseForRecordingSession() {
        if (!gateway.preference().enabled) return
        stopCapture()
        actions.unregisterOpportunisticFixes()
        actions.unregisterActivityTransitions()
        actions.cancelFallback()
    }

    suspend fun resumeAfterRecordingSession(
        wallTimeMillis: Long,
        elapsedRealtimeMillis: Long,
    ) {
        require(wallTimeMillis >= 0)
        require(elapsedRealtimeMillis >= 0)
        if (!gateway.preference().enabled) return
        gateway.enable(wallTimeMillis)
        actions.registerActivityTransitions()
        actions.registerOpportunisticFixes()
        actions.scheduleFallback(PASSIVE_FALLBACK_INTERVAL_MILLIS)
        val activity = activityState.activityAt(elapsedRealtimeMillis)
        if (activity != RecordingActivity.STILL && activity != RecordingActivity.UNKNOWN) {
            actions.unregisterOpportunisticFixes()
            startCapture(
                PassiveCaptureRequest(
                    trigger = PassiveFixTrigger.MOVEMENT_WINDOW,
                    activity = activity,
                    intervalMillis = activity.passiveIntervalMillis,
                ),
            )
        } else {
            diagnostics.passiveWaiting(activity)
        }
    }

    suspend fun restore() {
        if (!gateway.preference().enabled) return
        if (gateway.recordingSessionActive()) return
        actions.registerActivityTransitions()
        if (!captureState.isActive()) {
            actions.stopCapture()
            actions.registerOpportunisticFixes()
            diagnostics.passiveWaiting(RecordingActivity.UNKNOWN)
        }
        actions.scheduleFallback(PASSIVE_FALLBACK_INTERVAL_MILLIS)
    }

    suspend fun disable() {
        gateway.disable()
        activityState.reset()
        stopCapture()
        actions.unregisterOpportunisticFixes()
        actions.unregisterActivityTransitions()
        actions.cancelFallback()
        diagnostics.passiveStopped()
    }

    suspend fun onCaptureEnded(elapsedRealtimeMillis: Long) {
        if (!gateway.preference().enabled || gateway.recordingSessionActive()) return
        val trigger = captureState.activeTrigger()
        if (trigger == null) {
            actions.registerOpportunisticFixes()
            diagnostics.passiveWaiting(activityState.activityAt(elapsedRealtimeMillis))
            return
        }
        completeCapture(trigger, elapsedRealtimeMillis)
    }

    private fun completeCapture(
        trigger: PassiveFixTrigger,
        elapsedRealtimeMillis: Long,
    ) {
        stopCapture()
        val activity = activityState.activityAt(elapsedRealtimeMillis)
        if (
            trigger == PassiveFixTrigger.INITIAL_ENABLE &&
            activity != RecordingActivity.STILL &&
            activity != RecordingActivity.UNKNOWN
        ) {
            startCapture(
                PassiveCaptureRequest(
                    trigger = PassiveFixTrigger.MOVEMENT_WINDOW,
                    activity = activity,
                    intervalMillis = activity.passiveIntervalMillis,
                ),
            )
        } else {
            actions.registerOpportunisticFixes()
            diagnostics.passiveWaiting(activity)
        }
    }

    private fun startCapture(request: PassiveCaptureRequest) {
        captureState.start(request.trigger)
        actions.startCapture(request)
        diagnostics.passiveCaptureStarted(
            activityMode = request.activity,
            intervalMillis = request.intervalMillis,
            reason = when (request.trigger) {
                PassiveFixTrigger.INITIAL_ENABLE -> RequestedIntervalReason.PASSIVE_INITIAL
                PassiveFixTrigger.MOVEMENT_WINDOW -> RequestedIntervalReason.PASSIVE_WINDOW
                PassiveFixTrigger.FALLBACK_WINDOW -> RequestedIntervalReason.SAFE_FALLBACK
                PassiveFixTrigger.OPPORTUNISTIC -> error("Opportunistic fixes do not start capture")
            },
        )
    }

    private fun stopCapture() {
        captureState.stop()
        actions.stopCapture()
    }
}

private val RecordingActivity.passiveIntervalMillis: Long
    get() = when (this) {
        RecordingActivity.WALKING,
        RecordingActivity.UNKNOWN,
        -> 10_000L
        RecordingActivity.RUNNING,
        RecordingActivity.ON_BICYCLE,
        -> 5_000L
        RecordingActivity.IN_VEHICLE -> 1_000L
        RecordingActivity.STILL -> error("Still does not open a Passive Capture Window")
    }
