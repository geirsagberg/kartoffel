package net.sagberg.kartoffel.storage

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
internal interface RecordingSessionPointDao {
    @Insert
    suspend fun insert(point: RecordingSessionPointEntity)

    @Query(
        """
        SELECT * FROM recording_session_points
        WHERE recording_session_id = :sessionId
        ORDER BY captured_at_ms, sample_id
        """,
    )
    suspend fun forSession(sessionId: Long): List<RecordingSessionPointEntity>

    @Query(
        """
        SELECT * FROM recording_session_points
        WHERE recording_session_id = :sessionId
        ORDER BY captured_at_ms DESC, sample_id DESC
        LIMIT 1
        """,
    )
    suspend fun lastForSession(sessionId: Long): RecordingSessionPointEntity?
}
