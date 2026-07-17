package net.sagberg.kartoffel.storage

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "recording_session_points",
    primaryKeys = ["sample_id"],
    foreignKeys = [
        ForeignKey(
            entity = RecordingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocationSampleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sample_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["recording_session_id", "captured_at_ms"])],
)
internal data class RecordingSessionPointEntity(
    @ColumnInfo(name = "sample_id")
    val sampleId: Long,
    @ColumnInfo(name = "recording_session_id")
    val recordingSessionId: Long,
    @ColumnInfo(name = "captured_at_ms")
    val capturedAtMillis: Long,
    @ColumnInfo(name = "cell_id")
    val cellId: Long,
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(capturedAtMillis >= 0)
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
    }

    companion object {
        fun fromAcceptedSample(
            sample: LocationSampleEntity,
            cellId: Long,
        ): RecordingSessionPointEntity {
            check(sample.accepted)
            val sessionId = checkNotNull(sample.recordingSessionId)
            return RecordingSessionPointEntity(
                sampleId = sample.id,
                recordingSessionId = sessionId,
                capturedAtMillis = sample.capturedAtMillis,
                cellId = cellId,
                latitude = sample.latitude,
                longitude = sample.longitude,
            )
        }
    }
}
