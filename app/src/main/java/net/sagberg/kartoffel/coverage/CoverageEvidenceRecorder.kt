package net.sagberg.kartoffel.coverage

import androidx.room3.withWriteTransaction
import net.sagberg.kartoffel.storage.CoverageEvidenceSource
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.LocationSampleEntity
import net.sagberg.kartoffel.storage.PersistedActivityMode
import net.sagberg.kartoffel.storage.RecordingPointEntity
import net.sagberg.kartoffel.storage.evidenceMaskOf
import net.sagberg.kartoffel.storage.inferredEvidenceMaskOf

internal data class CoverageLocationFix(
    val coordinate: GeoCoordinate,
    val capturedAtMillis: Long,
    val accuracyMeters: Double,
    val speedMetersPerSecond: Double? = null,
) {
    init {
        require(capturedAtMillis >= 0)
        require(accuracyMeters >= 0.0)
        require(speedMetersPerSecond == null || speedMetersPerSecond >= 0.0)
    }
}

internal data class CoverageEvidenceRules(
    val source: CoverageEvidenceSource,
    val trigger: String?,
    val maximumAccuracyMeters: Double,
    val maximumInterpolationDistanceSteps: Int,
    val accuracyRejectionReason: String,
    val shortGapInterpolation: ShortGapInterpolationPolicy? = null,
)

internal data class ShortGapInterpolationPolicy(
    val maximumGapMillis: Long,
    val eligiblePreviousTriggers: Set<String>,
) {
    init {
        require(maximumGapMillis > 0)
        require(eligiblePreviousTriggers.isNotEmpty())
    }
}

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
        recordingId: Long? = null,
        activityMode: PersistedActivityMode = PersistedActivityMode.UNKNOWN,
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
            val previousAutomaticSample = if (
                recordingId == null &&
                decision.accepted &&
                rules.shortGapInterpolation != null
            ) {
                database.locationSamples().lastAcceptedBefore(
                    source = rules.source.persistedName,
                    capturedAtMillis = fix.capturedAtMillis,
                    eligibleTriggers = rules.shortGapInterpolation.eligiblePreviousTriggers,
                )
            } else {
                null
            }
            val sample = LocationSampleEntity(
                capturedAtMillis = fix.capturedAtMillis,
                latitude = fix.coordinate.latitude,
                longitude = fix.coordinate.longitude,
                accuracyMeters = fix.accuracyMeters,
                source = rules.source.persistedName,
                trigger = rules.trigger,
                accepted = decision.accepted,
                rejectionReason = decision.rejectionReason,
                recordingId = recordingId,
                activityMode = activityMode.persistedName,
            )
            val sampleId = database.locationSamples().insert(sample)
            cell?.let { acceptedCell ->
                if (recordingId != null) {
                    val previousPoint = database.recordingPoints()
                        .lastForRecording(recordingId)
                    previousPoint?.let { observedPoint ->
                        coverageCells.intermediateCellsForGap(
                            start = CoverageCellId(observedPoint.cellId),
                            destination = acceptedCell,
                            maximumGapSteps = rules.maximumInterpolationDistanceSteps,
                        ).forEach { inferredCell ->
                            database.coverageCells().upsert(
                                cellId = inferredCell.value,
                                firstSeenAtMillis = fix.capturedAtMillis,
                                lastSeenAtMillis = fix.capturedAtMillis,
                                evidenceMask = inferredEvidenceMaskOf(rules.source),
                            )
                        }
                    }
                    if (previousPoint?.cellId != acceptedCell.value) {
                        database.recordingPoints().insert(
                            RecordingPointEntity.fromAcceptedSample(
                                sample = sample.copy(id = sampleId),
                                cellId = acceptedCell.value,
                            ),
                        )
                    }
                } else if (
                    previousAutomaticSample != null &&
                    fix.capturedAtMillis - previousAutomaticSample.capturedAtMillis <=
                    checkNotNull(rules.shortGapInterpolation).maximumGapMillis
                ) {
                    coverageCells.intermediateCellsForGap(
                        start = coverageCells.cellAt(
                            GeoCoordinate(
                                latitude = previousAutomaticSample.latitude,
                                longitude = previousAutomaticSample.longitude,
                            ),
                        ),
                        destination = acceptedCell,
                        maximumGapSteps = rules.maximumInterpolationDistanceSteps,
                    ).forEach { inferredCell ->
                        database.coverageCells().upsert(
                            cellId = inferredCell.value,
                            firstSeenAtMillis = fix.capturedAtMillis,
                            lastSeenAtMillis = fix.capturedAtMillis,
                            evidenceMask = inferredEvidenceMaskOf(rules.source),
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
