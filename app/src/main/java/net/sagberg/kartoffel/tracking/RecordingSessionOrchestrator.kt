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
    fun start(listener: suspend (RecordingLocationFix) -> Unit)

    fun stop()
}

internal class RecordingSessionOrchestrator(
    private val gateway: RecordingSessionGateway,
    private val locationUpdates: RecordingLocationUpdates,
) {
    private val mutex = Mutex()
    private var activeSessionId: Long? = null

    val isRecording: Boolean
        get() = activeSessionId != null

    suspend fun start(startedAtMillis: Long) = mutex.withLock {
        if (activeSessionId != null) return@withLock
        val sessionId = gateway.activeSessionId() ?: gateway.start(startedAtMillis)
        activeSessionId = sessionId
        locationUpdates.start(::record)
    }

    suspend fun stop(endedAtMillis: Long) = mutex.withLock {
        val sessionId = activeSessionId ?: gateway.activeSessionId() ?: return@withLock
        locationUpdates.stop()
        activeSessionId = null
        gateway.stop(sessionId, endedAtMillis)
    }

    private suspend fun record(fix: RecordingLocationFix) = mutex.withLock {
        activeSessionId?.let { sessionId ->
            gateway.record(sessionId, fix)
        }
    }
}
