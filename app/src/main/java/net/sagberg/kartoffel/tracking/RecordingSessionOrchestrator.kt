package net.sagberg.kartoffel.tracking

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface RecordingSessionGateway {
    suspend fun activeSessionId(): Long? = null

    suspend fun start(startedAtMillis: Long): Long

    suspend fun record(
        sessionId: Long,
        fix: RecordingLocationFix,
    ): RecordingLocationDecision

    suspend fun stop(sessionId: Long, endedAtMillis: Long)
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

internal interface RecordingActivityUpdates {
    fun start(listener: suspend (RecordingActivity) -> Unit)

    fun stop()
}

internal const val DEFAULT_RECORDING_INTERVAL_MILLIS = 5_000L

internal class RecordingSessionOrchestrator(
    private val gateway: RecordingSessionGateway,
    private val locationUpdates: RecordingLocationUpdates,
    private val activityUpdates: RecordingActivityUpdates,
) {
    private val mutex = Mutex()
    private var activeSessionId: Long? = null
    private var locationUpdatesActive = false
    private var currentIntervalMillis: Long? = null

    val isRecording: Boolean
        get() = activeSessionId != null

    suspend fun start(startedAtMillis: Long) = mutex.withLock {
        if (activeSessionId != null) return@withLock
        val sessionId = gateway.activeSessionId() ?: gateway.start(startedAtMillis)
        activate(sessionId)
    }

    suspend fun resumeActiveSession(): Boolean = mutex.withLock {
        if (activeSessionId != null) return@withLock true
        val sessionId = gateway.activeSessionId() ?: return@withLock false
        activate(sessionId)
        true
    }

    suspend fun stop(endedAtMillis: Long) = mutex.withLock {
        val sessionId = activeSessionId ?: gateway.activeSessionId() ?: return@withLock
        activityUpdates.stop()
        stopLocationUpdates()
        activeSessionId = null
        gateway.stop(sessionId, endedAtMillis)
    }

    private suspend fun record(fix: RecordingLocationFix) = mutex.withLock {
        activeSessionId?.let { sessionId ->
            gateway.record(sessionId, fix)
            speedInterval(fix.speedMetersPerSecond)?.let(::useFasterInterval)
        }
    }

    private suspend fun onActivity(activity: RecordingActivity) = mutex.withLock {
        if (activeSessionId == null) return@withLock
        val intervalMillis = activity.intervalMillis
        if (intervalMillis == null) {
            stopLocationUpdates()
        } else if (!locationUpdatesActive) {
            startLocationUpdates(intervalMillis)
        } else if (intervalMillis != currentIntervalMillis) {
            locationUpdates.updateInterval(intervalMillis)
            currentIntervalMillis = intervalMillis
        }
    }

    private fun startLocationUpdates(intervalMillis: Long) {
        locationUpdates.start(intervalMillis, ::record)
        locationUpdatesActive = true
        currentIntervalMillis = intervalMillis
    }

    private fun activate(sessionId: Long) {
        activeSessionId = sessionId
        startLocationUpdates(DEFAULT_RECORDING_INTERVAL_MILLIS)
        runCatching { activityUpdates.start(::onActivity) }
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
