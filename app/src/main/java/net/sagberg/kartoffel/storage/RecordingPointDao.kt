package net.sagberg.kartoffel.storage

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
internal interface RecordingPointDao {
    @Insert
    suspend fun insert(point: RecordingPointEntity)

    @Query(
        """
        SELECT * FROM recording_points
        WHERE recording_id = :recordingId
        ORDER BY captured_at_ms, sample_id
        """,
    )
    suspend fun forRecording(recordingId: Long): List<RecordingPointEntity>

    @Query(
        """
        SELECT * FROM recording_points
        WHERE recording_id = :recordingId
        ORDER BY captured_at_ms DESC, sample_id DESC
        LIMIT 1
        """,
    )
    suspend fun lastForRecording(recordingId: Long): RecordingPointEntity?
}
