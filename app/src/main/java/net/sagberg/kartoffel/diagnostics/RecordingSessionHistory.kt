package net.sagberg.kartoffel.diagnostics

import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.PersistedActivityMode

internal data class RecordingSessionHistory(
    val isActive: Boolean,
    val durationMillis: Long,
    val fixCounts: Map<PersistedActivityMode, Int>,
) {
    init {
        require(durationMillis >= 0)
        require(fixCounts.values.all { it >= 0 })
    }
}

internal class RecordingSessionHistoryLoader(
    private val database: KartoffelDatabase,
) {
    suspend fun load(nowMillis: Long): RecordingSessionHistory? {
        val session = database.recordingSessions().active()
            ?: database.recordingSessions().latestCompleted()
            ?: return null
        val endMillis = session.endedAtMillis ?: nowMillis.coerceAtLeast(session.startedAtMillis)
        val counts = database.locationSamples().activityModeFixCounts(session.id)
            .groupingBy { PersistedActivityMode.fromPersistedName(it.activityMode) }
            .fold(0) { total, row -> total + row.fixCount }
        return RecordingSessionHistory(
            isActive = session.endedAtMillis == null,
            durationMillis = endMillis - session.startedAtMillis,
            fixCounts = counts,
        )
    }
}
