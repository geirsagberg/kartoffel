package net.sagberg.kartoffel.storage

import androidx.room3.Dao
import androidx.room3.ColumnInfo
import androidx.room3.Insert
import androidx.room3.Query

@Dao
internal interface LocationSampleDao {
    @Insert
    suspend fun insert(sample: LocationSampleEntity): Long

    @Query("SELECT * FROM location_samples WHERE id = :id")
    suspend fun find(id: Long): LocationSampleEntity?

    @Query(
        """
        SELECT * FROM location_samples
        WHERE captured_at_ms BETWEEN :fromMillis AND :toMillis
        ORDER BY captured_at_ms
        """,
    )
    suspend fun between(fromMillis: Long, toMillis: Long): List<LocationSampleEntity>

    @Query(
        """
        SELECT activity_mode, COUNT(*) AS fix_count
        FROM location_samples
        WHERE recording_session_id = :sessionId
        GROUP BY activity_mode
        """,
    )
    suspend fun activityModeFixCounts(sessionId: Long): List<ActivityModeFixCount>

    @Query(
        """
        SELECT * FROM location_samples
        WHERE source = :source
          AND accepted = 1
          AND trigger IN (:eligibleTriggers)
          AND captured_at_ms < :capturedAtMillis
        ORDER BY captured_at_ms DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun lastAcceptedBefore(
        source: String,
        capturedAtMillis: Long,
        eligibleTriggers: Set<String>,
    ): LocationSampleEntity?
}

internal data class ActivityModeFixCount(
    @ColumnInfo(name = "activity_mode")
    val activityMode: String,
    @ColumnInfo(name = "fix_count")
    val fixCount: Int,
)
