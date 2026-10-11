package net.sagberg.kartoffel.storage

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY started_at_ms DESC, id DESC")
    suspend fun all(): List<RecordingEntity>

    @Query(
        """
        SELECT * FROM recordings
        WHERE ended_at_ms IS NULL
        ORDER BY started_at_ms DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeActive(): Flow<RecordingEntity?>

    @Query(
        """
        SELECT * FROM recordings
        WHERE ended_at_ms IS NULL
        ORDER BY started_at_ms DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun active(): RecordingEntity?

    @Query(
        """
        SELECT * FROM recordings
        WHERE ended_at_ms IS NOT NULL
        ORDER BY ended_at_ms DESC, started_at_ms DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun latestCompleted(): RecordingEntity?

    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun find(id: Long): RecordingEntity?

    @Query(
        """
        UPDATE recordings
        SET ended_at_ms = :endedAtMillis
        WHERE id = :id AND ended_at_ms IS NULL
        """,
    )
    suspend fun stop(id: Long, endedAtMillis: Long): Int
}
