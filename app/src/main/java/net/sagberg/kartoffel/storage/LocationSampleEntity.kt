package net.sagberg.kartoffel.storage

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "location_samples",
    indices = [Index(value = ["captured_at_ms"])],
)
internal data class LocationSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "captured_at_ms")
    val capturedAtMillis: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_meters")
    val accuracyMeters: Double,
    val source: String,
    val trigger: String?,
    val accepted: Boolean,
    @ColumnInfo(name = "rejection_reason")
    val rejectionReason: String?,
    @ColumnInfo(name = "recording_session_id")
    val recordingSessionId: Long? = null,
) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
        require(accuracyMeters >= 0.0)
        require(accepted == (rejectionReason == null))
    }
}
