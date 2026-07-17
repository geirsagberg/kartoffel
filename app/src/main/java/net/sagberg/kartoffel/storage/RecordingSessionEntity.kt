package net.sagberg.kartoffel.storage

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "recording_sessions")
internal data class RecordingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "started_at_ms")
    val startedAtMillis: Long,
    @ColumnInfo(name = "ended_at_ms")
    val endedAtMillis: Long? = null,
) {
    init {
        require(startedAtMillis >= 0)
        require(endedAtMillis == null || endedAtMillis >= startedAtMillis)
    }
}
