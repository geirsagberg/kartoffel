package net.sagberg.kartoffel.storage

import androidx.room3.Dao
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
}
