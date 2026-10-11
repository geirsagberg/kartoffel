package net.sagberg.kartoffel.tracking

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.sagberg.kartoffel.diagnostics.LatestFixDiagnostics
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnostics

internal interface RecordingGateway {
    suspend fun activeRecordingId(): Long? = null

    suspend fun start(startedAtMillis: Long): Long

    suspend fun record(
        recordingId: Long,
        fix: RecordingLocationFix,
        activity: RecordingActivity = RecordingActivity.UNKNOWN,
    ): RecordingLocationDecision

    suspend fun stop(recordingId: Long, endedAtMillis: Long)
}

internal interface RecordingLocationUpdates {
    fun start(
        intervalMillis: Long,
        listener: suspend (RecordingLocationFix) -> Unit,
    )

    fun updateInterval(intervalMillis: Long)

    fun stop()
}

internal enum class RecordingActivity {
    STILL,
    WALKING,
    RUNNING,
    ON_BICYCLE,
    IN_VEHICLE,
    UNKNOWN,
}

internal sealed interface RecordingActivityUpdate {
    val activity: RecordingActivity

    data class Transition(
        override val activity: RecordingActivity,
    ) : RecordingActivityUpdate

    data class BootstrapSample(
        override val activity: RecordingActivity,
        val confidence: Int,
    ) : RecordingActivityUpdate {
        init {
            require(confidence in 0..100)
        }
    }
}

internal interface RecordingActivityUpdates {
    fun start(listener: suspend (RecordingActivityUpdate) -> Unit)

    fun stopBootstrap()

    fun stop()
}

internal const val DEFAULT_RECORDING_INTERVAL_MILLIS = 5_000L
internal const val MINIMUM_BOOTSTRAP_ACTIVITY_CONFIDENCE = 75

internal class RecordingOrchestrator(
    private val gateway: RecordingGateway,
    private val locationUpdates: RecordingLocationUpdates,
    private val activityUpdates: RecordingActivityUpdates,
    private val diagnostics: LiveTrackingDiagnostics = LiveTrackingDiagnostics(),
    private val initialFixTimeout: RecordingInitialFixTimeout = NoOpRecordingInitialFixTimeout,
) {
    private val mutex = Mutex()
    private var activeRecordingId: Long? = null
    private var locationUpdatesActive = false
    private var currentIntervalMillis: Long? = null
    private var bootstrapCanSeedActivity = false
    private var awaitingInitialAcceptedFix = false
    private var latestActivity = RecordingActivity.UNKNOWN

    val isRecording: Boolean
        get() = activeRecordingId != null

    suspend fun start(startedAtMillis: Long) = mutex.withLock {
        if (activeRecordingId != null) return@withLock
        val recordingId = gateway.activeRecordingId() ?: gateway.start(startedAtMillis)
        activate(recordingId)
    }

    suspend fun resumeActiveRecording(): Boolean = mutex.withLock {
        if (activeRecordingId != null) return@withLock true
        val recordingId = gateway.activeRecordingId() ?: return@withLock false
        activate(recordingId)
        true
    }

    suspend fun stop(endedAtMillis: Long) = mutex.withLock {
        val recordingId = activeRecordingId ?: gateway.activeRecordingId() ?: return@withLock
        activityUpdates.stop()
        bootstrapCanSeedActivity = false
        initialFixTimeout.cancel()
        awaitingInitialAcceptedFix = false
        stopLocationUpdates()
        activeRecordingId = null
        gateway.stop(recordingId, endedAtMillis)
        diagnostics.recordingStopped()
    }

    private suspend fun record(fix: RecordingLocationFix) = mutex.withLock {
        activeRecordingId?.let { recordingId ->
            val decision = gateway.record(recordingId, fix, latestActivity)
            diagnostics.fixReceived(
                LatestFixDiagnostics(
                    capturedAtMillis = fix.capturedAtMillis,
                    accuracyMeters = fix.accuracyMeters,
                    accepted = decision.accepted,
                    rejectionReason = decision.rejectionReason,
                ),
            )
            if (decision.accepted && awaitingInitialAcceptedFix) {
                awaitingInitialAcceptedFix = false
                initialFixTimeout.cancel()
                applyLocationPolicy(latestActivity)
            }
            if (!awaitingInitialAcceptedFix) {
                speedInterval(fix.speedMetersPerSecond)?.let(::useFasterInterval)
            }
        }
    }

    private suspend fun onActivity(update: RecordingActivityUpdate) = mutex.withLock {
        if (activeRecordingId == null) return@withLock
        when (update) {
            is RecordingActivityUpdate.BootstrapSample -> {
                if (
                    !bootstrapCanSeedActivity ||
                    update.confidence < MINIMUM_BOOTSTRAP_ACTIVITY_CONFIDENCE
                ) {
                    return@withLock
                }
                bootstrapCanSeedActivity = false
                activityUpdates.stopBootstrap()
            }
            is RecordingActivityUpdate.Transition -> {
                if (bootstrapCanSeedActivity) {
                    bootstrapCanSeedActivity = false
                    activityUpdates.stopBootstrap()
                }
            }
        }
        val activity = update.activity
        latestActivity = activity
        if (awaitingInitialAcceptedFix) {
            diagnostics.activityObservedWhileAwaitingInitialFix(activity)
            return@withLock
        }
        applyLocationPolicy(activity)
    }

    private fun applyLocationPolicy(activity: RecordingActivity) {
        val intervalMillis = activity.intervalMillis
        if (intervalMillis == null) {
            stopLocationUpdates()
        } else if (!locationUpdatesActive) {
            startLocationUpdates(intervalMillis)
        } else if (intervalMillis != currentIntervalMillis) {
            locationUpdates.updateInterval(intervalMillis)
            currentIntervalMillis = intervalMillis
        }
        diagnostics.activityModeChanged(activity, intervalMillis)
    }

    private fun startLocationUpdates(intervalMillis: Long) {
        locationUpdates.start(intervalMillis, ::record)
        locationUpdatesActive = true
        currentIntervalMillis = intervalMillis
    }

    private fun activate(recordingId: Long) {
        activeRecordingId = recordingId
        bootstrapCanSeedActivity = true
        awaitingInitialAcceptedFix = true
        latestActivity = RecordingActivity.UNKNOWN
        startLocationUpdates(DEFAULT_RECORDING_INTERVAL_MILLIS)
        diagnostics.recordingStarted(DEFAULT_RECORDING_INTERVAL_MILLIS)
        initialFixTimeout.start(::onInitialFixTimeout)
        runCatching { activityUpdates.start(::onActivity) }
            .onFailure {
                bootstrapCanSeedActivity = false
                diagnostics.activityRecognitionUnavailable()
            }
    }

    private suspend fun onInitialFixTimeout() = mutex.withLock {
        if (activeRecordingId == null || !awaitingInitialAcceptedFix) return@withLock
        awaitingInitialAcceptedFix = false
        applyLocationPolicy(latestActivity)
    }

    private fun stopLocationUpdates() {
        if (locationUpdatesActive) {
            locationUpdates.stop()
        }
        locationUpdatesActive = false
        currentIntervalMillis = null
    }

    private fun useFasterInterval(intervalMillis: Long) {
        val current = currentIntervalMillis ?: return
        if (intervalMillis < current) {
            locationUpdates.updateInterval(intervalMillis)
            currentIntervalMillis = intervalMillis
            diagnostics.intervalAcceleratedBySpeed(intervalMillis)
        }
    }
}

private val RecordingActivity.intervalMillis: Long?
    get() = when (this) {
        RecordingActivity.STILL -> null
        RecordingActivity.WALKING -> 10_000L
        RecordingActivity.RUNNING,
        RecordingActivity.ON_BICYCLE,
        RecordingActivity.UNKNOWN,
        -> 5_000L
        RecordingActivity.IN_VEHICLE -> 1_000L
    }

private fun speedInterval(speedMetersPerSecond: Double?): Long? = when {
    speedMetersPerSecond == null -> null
    speedMetersPerSecond >= 10.0 -> 1_000L
    speedMetersPerSecond >= 2.5 -> 5_000L
    else -> null
}
