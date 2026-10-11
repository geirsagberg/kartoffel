package net.sagberg.kartoffel.tracking

import net.sagberg.kartoffel.diagnostics.LatestFixDiagnostics
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnostics
import net.sagberg.kartoffel.diagnostics.RequestedIntervalReason
import net.sagberg.kartoffel.storage.AutomaticTrackingPreference

internal const val AUTOMATIC_FALLBACK_INTERVAL_MILLIS = 60 * 60 * 1_000L
internal const val AUTOMATIC_ACTIVITY_FRESHNESS_MILLIS = 30 * 60 * 1_000L
internal const val AUTOMATIC_CAPTURE_DURATION_MILLIS = 90_000L
internal const val AUTOMATIC_CAPTURE_MAXIMUM_FIXES = 6

internal data class AutomaticCaptureRequest(
    val trigger: AutomaticFixTrigger,
    val activity: RecordingActivity,
    val intervalMillis: Long,
    val durationMillis: Long = AUTOMATIC_CAPTURE_DURATION_MILLIS,
    val maximumFixes: Int = AUTOMATIC_CAPTURE_MAXIMUM_FIXES,
) {
    init {
        require(intervalMillis > 0)
        require(durationMillis > 0)
        require(maximumFixes > 0)
    }
}

internal interface AutomaticTrackingGateway {
    suspend fun preference(): AutomaticTrackingPreference

    suspend fun enable(automaticPeriodStartedAtMillis: Long)

    suspend fun disable()

    suspend fun recordingActive(): Boolean

    suspend fun record(
        fix: RecordingLocationFix,
        trigger: AutomaticFixTrigger,
        activity: RecordingActivity,
    ): RecordingLocationDecision
}

internal interface AutomaticTrackingActions {
    fun registerActivityTransitions()

    fun unregisterActivityTransitions()

    fun registerOpportunisticFixes()

    fun unregisterOpportunisticFixes()

    fun startCapture(request: AutomaticCaptureRequest)

    fun stopCapture()

    fun scheduleFallback(delayMillis: Long)

    fun cancelFallback()
}

internal class AutomaticActivityState {
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
            elapsedRealtimeMillis - observedAt <= AUTOMATIC_ACTIVITY_FRESHNESS_MILLIS
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

internal class AutomaticCaptureState {
    private var trigger: AutomaticFixTrigger? = null
    private var deliveredFixes = 0

    @Synchronized
    fun start(trigger: AutomaticFixTrigger) {
        this.trigger = trigger
        deliveredFixes = 0
    }

    @Synchronized
    fun deliveryEndsCapture(trigger: AutomaticFixTrigger): Boolean {
        if (this.trigger != trigger) return false
        deliveredFixes += 1
        return deliveredFixes >= AUTOMATIC_CAPTURE_MAXIMUM_FIXES
    }

    @Synchronized
    fun isActive(): Boolean = trigger != null

    @Synchronized
    fun activeTrigger(): AutomaticFixTrigger? = trigger

    @Synchronized
    fun stop() {
        trigger = null
        deliveredFixes = 0
    }
}

internal class AutomaticTrackingOrchestrator(
    private val gateway: AutomaticTrackingGateway,
    private val actions: AutomaticTrackingActions,
    private val activityState: AutomaticActivityState = AutomaticActivityState(),
    private val captureState: AutomaticCaptureState = AutomaticCaptureState(),
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
        if (gateway.recordingActive()) return
        actions.registerActivityTransitions()
        actions.scheduleFallback(AUTOMATIC_FALLBACK_INTERVAL_MILLIS)
        startCapture(
            AutomaticCaptureRequest(
                trigger = AutomaticFixTrigger.INITIAL_ENABLE,
                activity = RecordingActivity.UNKNOWN,
                intervalMillis = DEFAULT_RECORDING_INTERVAL_MILLIS,
                durationMillis = 30_000,
                maximumFixes = AUTOMATIC_CAPTURE_MAXIMUM_FIXES,
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
        if (!gateway.preference().enabled || gateway.recordingActive()) return
        if (captureState.activeTrigger() == AutomaticFixTrigger.INITIAL_ENABLE) return
        if (activityState.activityAt(currentElapsedRealtimeMillis) == RecordingActivity.UNKNOWN) {
            return
        }
        if (activity == RecordingActivity.STILL) {
            stopCapture()
            actions.registerOpportunisticFixes()
            diagnostics.automaticWaiting(activity)
            return
        }
        if (activity == RecordingActivity.UNKNOWN) return
        actions.unregisterOpportunisticFixes()
        startCapture(
            AutomaticCaptureRequest(
                trigger = AutomaticFixTrigger.MOVEMENT_WINDOW,
                activity = activity,
                intervalMillis = activity.automaticIntervalMillis,
            ),
        )
    }

    suspend fun onFallback(elapsedRealtimeMillis: Long) {
        require(elapsedRealtimeMillis >= 0)
        if (!gateway.preference().enabled || gateway.recordingActive()) return
        actions.scheduleFallback(AUTOMATIC_FALLBACK_INTERVAL_MILLIS)
        val activity = activityState.activityAt(elapsedRealtimeMillis)
        if (activity != RecordingActivity.UNKNOWN) return
        actions.unregisterOpportunisticFixes()
        startCapture(
            AutomaticCaptureRequest(
                trigger = AutomaticFixTrigger.FALLBACK_WINDOW,
                activity = activity,
                intervalMillis = activity.automaticIntervalMillis,
            ),
        )
    }

    suspend fun onFix(
        fix: RecordingLocationFix,
        capturedAtElapsedRealtimeMillis: Long,
        trigger: AutomaticFixTrigger,
    ): RecordingLocationDecision? {
        require(capturedAtElapsedRealtimeMillis >= 0)
        val preference = gateway.preference()
        val automaticPeriodStart = preference.automaticPeriodStartedAtMillis
        if (
            !preference.enabled ||
            automaticPeriodStart == null ||
            fix.capturedAtMillis < automaticPeriodStart ||
            gateway.recordingActive()
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
            (trigger == AutomaticFixTrigger.INITIAL_ENABLE && decision.accepted) ||
            captureState.deliveryEndsCapture(trigger)
        ) {
            completeCapture(trigger, capturedAtElapsedRealtimeMillis)
        }
        return decision
    }

    suspend fun pauseForRecording() {
        if (!gateway.preference().enabled) return
        stopCapture()
        actions.unregisterOpportunisticFixes()
        actions.unregisterActivityTransitions()
        actions.cancelFallback()
    }

    suspend fun resumeAfterRecording(
        wallTimeMillis: Long,
        elapsedRealtimeMillis: Long,
    ) {
        require(wallTimeMillis >= 0)
        require(elapsedRealtimeMillis >= 0)
        if (!gateway.preference().enabled) return
        gateway.enable(wallTimeMillis)
        actions.registerActivityTransitions()
        actions.registerOpportunisticFixes()
        actions.scheduleFallback(AUTOMATIC_FALLBACK_INTERVAL_MILLIS)
        val activity = activityState.activityAt(elapsedRealtimeMillis)
        if (activity != RecordingActivity.STILL && activity != RecordingActivity.UNKNOWN) {
            actions.unregisterOpportunisticFixes()
            startCapture(
                AutomaticCaptureRequest(
                    trigger = AutomaticFixTrigger.MOVEMENT_WINDOW,
                    activity = activity,
                    intervalMillis = activity.automaticIntervalMillis,
                ),
            )
        } else {
            diagnostics.automaticWaiting(activity)
        }
    }

    suspend fun restore() {
        if (!gateway.preference().enabled) return
        if (gateway.recordingActive()) return
        actions.registerActivityTransitions()
        if (!captureState.isActive()) {
            actions.stopCapture()
            actions.registerOpportunisticFixes()
            diagnostics.automaticWaiting(RecordingActivity.UNKNOWN)
        }
        actions.scheduleFallback(AUTOMATIC_FALLBACK_INTERVAL_MILLIS)
    }

    suspend fun disable() {
        gateway.disable()
        activityState.reset()
        stopCapture()
        actions.unregisterOpportunisticFixes()
        actions.unregisterActivityTransitions()
        actions.cancelFallback()
        diagnostics.automaticStopped()
    }

    suspend fun onCaptureEnded(elapsedRealtimeMillis: Long) {
        if (!gateway.preference().enabled || gateway.recordingActive()) return
        val trigger = captureState.activeTrigger()
        if (trigger == null) {
            actions.registerOpportunisticFixes()
            diagnostics.automaticWaiting(activityState.activityAt(elapsedRealtimeMillis))
            return
        }
        completeCapture(trigger, elapsedRealtimeMillis)
    }

    private fun completeCapture(
        trigger: AutomaticFixTrigger,
        elapsedRealtimeMillis: Long,
    ) {
        stopCapture()
        val activity = activityState.activityAt(elapsedRealtimeMillis)
        if (
            trigger == AutomaticFixTrigger.INITIAL_ENABLE &&
            activity != RecordingActivity.STILL &&
            activity != RecordingActivity.UNKNOWN
        ) {
            startCapture(
                AutomaticCaptureRequest(
                    trigger = AutomaticFixTrigger.MOVEMENT_WINDOW,
                    activity = activity,
                    intervalMillis = activity.automaticIntervalMillis,
                ),
            )
        } else {
            actions.registerOpportunisticFixes()
            diagnostics.automaticWaiting(activity)
        }
    }

    private fun startCapture(request: AutomaticCaptureRequest) {
        captureState.start(request.trigger)
        actions.startCapture(request)
        diagnostics.automaticCaptureStarted(
            activityMode = request.activity,
            intervalMillis = request.intervalMillis,
            reason = when (request.trigger) {
                AutomaticFixTrigger.INITIAL_ENABLE -> RequestedIntervalReason.AUTOMATIC_INITIAL
                AutomaticFixTrigger.MOVEMENT_WINDOW -> RequestedIntervalReason.AUTOMATIC_WINDOW
                AutomaticFixTrigger.FALLBACK_WINDOW -> RequestedIntervalReason.SAFE_FALLBACK
                AutomaticFixTrigger.OPPORTUNISTIC -> error("Opportunistic fixes do not start capture")
            },
        )
    }

    private fun stopCapture() {
        captureState.stop()
        actions.stopCapture()
    }
}

private val RecordingActivity.automaticIntervalMillis: Long
    get() = when (this) {
        RecordingActivity.WALKING,
        RecordingActivity.UNKNOWN,
        -> 10_000L
        RecordingActivity.RUNNING,
        RecordingActivity.ON_BICYCLE,
        -> 5_000L
        RecordingActivity.IN_VEHICLE -> 1_000L
        RecordingActivity.STILL -> error("Still does not open a Capture Window")
    }
