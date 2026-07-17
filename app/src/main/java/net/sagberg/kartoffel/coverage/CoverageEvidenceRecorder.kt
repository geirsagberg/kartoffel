package net.sagberg.kartoffel.coverage

import androidx.room3.withWriteTransaction
import net.sagberg.kartoffel.storage.CoverageEvidenceSource
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.LocationSampleEntity
import net.sagberg.kartoffel.storage.RecordingSessionPointEntity
import net.sagberg.kartoffel.storage.evidenceMaskOf

internal data class CoverageLocationFix(
    val coordinate: GeoCoordinate,
    val capturedAtMillis: Long,
    val accuracyMeters: Double,
) {
    init {
        require(capturedAtMillis >= 0)
        require(accuracyMeters >= 0.0)
    }
}

internal data class CoverageEvidenceRules(
    val source: CoverageEvidenceSource,
    val trigger: String?,
    val maximumAccuracyMeters: Double,
    val accuracyRejectionReason: String,
)

internal data class CoverageLocationDecision(
    val accepted: Boolean,
    val rejectionReason: String?,
) {
    init {
        require(accepted == (rejectionReason == null))
    }
}

internal class CoverageEvidenceRecorder(
    private val database: KartoffelDatabase,
    private val coverageCells: H3CoverageCells = H3CoverageCells(),
) {
    suspend fun record(
        fix: CoverageLocationFix,
        rules: CoverageEvidenceRules,
        recordingSessionId: Long? = null,
    ): CoverageLocationDecision {
        val decision = if (fix.accuracyMeters <= rules.maximumAccuracyMeters) {
            CoverageLocationDecision(accepted = true, rejectionReason = null)
        } else {
            CoverageLocationDecision(
                accepted = false,
                rejectionReason = rules.accuracyRejectionReason,
            )
        }
        val cell = if (decision.accepted) coverageCells.cellAt(fix.coordinate) else null

        database.withWriteTransaction {
            val sample = LocationSampleEntity(
                capturedAtMillis = fix.capturedAtMillis,
                latitude = fix.coordinate.latitude,
                longitude = fix.coordinate.longitude,
                accuracyMeters = fix.accuracyMeters,
                source = rules.source.persistedName,
                trigger = rules.trigger,
                accepted = decision.accepted,
                rejectionReason = decision.rejectionReason,
                recordingSessionId = recordingSessionId,
            )
            val sampleId = database.locationSamples().insert(sample)
            cell?.let { acceptedCell ->
                if (recordingSessionId != null) {
                    val previousPoint = database.recordingSessionPoints()
                        .lastForSession(recordingSessionId)
                    if (previousPoint?.cellId != acceptedCell.value) {
                        database.recordingSessionPoints().insert(
                            RecordingSessionPointEntity.fromAcceptedSample(
                                sample = sample.copy(id = sampleId),
                                cellId = acceptedCell.value,
                            ),
                        )
                    }
                }
                database.coverageCells().upsert(
                    cellId = acceptedCell.value,
                    firstSeenAtMillis = fix.capturedAtMillis,
                    lastSeenAtMillis = fix.capturedAtMillis,
                    evidenceMask = evidenceMaskOf(rules.source),
                )
            }
        }

        return decision
    }
}
