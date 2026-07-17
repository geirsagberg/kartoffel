package net.sagberg.kartoffel.storage

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface RecordingSessionDao {
    @Query(
        """
        SELECT * FROM recording_sessions
        WHERE ended_at_ms IS NULL
        ORDER BY started_at_ms DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeActive(): Flow<RecordingSessionEntity?>

    @Insert
    suspend fun insert(session: RecordingSessionEntity): Long

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    suspend fun find(id: Long): RecordingSessionEntity?

    @Query(
        """
        UPDATE recording_sessions
        SET ended_at_ms = :endedAtMillis
        WHERE id = :id AND ended_at_ms IS NULL
        """,
    )
    suspend fun stop(id: Long, endedAtMillis: Long): Int
}
