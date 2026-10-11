package net.sagberg.kartoffel.diagnostics

import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.PersistedActivityMode

internal data class RecordingHistory(
    val isActive: Boolean,
    val durationMillis: Long,
    val fixCounts: Map<PersistedActivityMode, Int>,
) {
    init {
        require(durationMillis >= 0)
        require(fixCounts.values.all { it >= 0 })
    }
}

internal class RecordingHistoryLoader(
    private val database: KartoffelDatabase,
) {
    suspend fun load(nowMillis: Long): RecordingHistory? {
        val recording = database.recordings().active()
            ?: database.recordings().latestCompleted()
            ?: return null
        val endMillis = recording.endedAtMillis ?: nowMillis.coerceAtLeast(recording.startedAtMillis)
        val counts = database.locationSamples().activityModeFixCounts(recording.id)
            .groupingBy { PersistedActivityMode.fromPersistedName(it.activityMode) }
            .fold(0) { total, row -> total + row.fixCount }
        return RecordingHistory(
            isActive = recording.endedAtMillis == null,
            durationMillis = endMillis - recording.startedAtMillis,
            fixCounts = counts,
        )
    }
}
