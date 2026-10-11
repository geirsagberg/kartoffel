package net.sagberg.kartoffel.tracking

import kotlinx.coroutines.flow.first
import net.sagberg.kartoffel.coverage.CoverageEvidenceRecorder
import net.sagberg.kartoffel.coverage.CoverageEvidenceRules
import net.sagberg.kartoffel.coverage.CoverageLocationDecision
import net.sagberg.kartoffel.coverage.CoverageLocationFix
import net.sagberg.kartoffel.coverage.H3CoverageCells
import net.sagberg.kartoffel.storage.CoverageEvidenceSource
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.PersistedActivityMode
import net.sagberg.kartoffel.storage.RecordingEntity

internal const val RECORDING_ACCURACY_REJECTION = "accuracy_exceeds_recording_limit"

private const val ACTIVE_RECORDING_TRIGGER = "active_recording"

internal typealias RecordingLocationFix = CoverageLocationFix
internal typealias RecordingLocationDecision = CoverageLocationDecision

internal class RecordingFixRecorder(
    private val database: KartoffelDatabase,
    coverageCells: H3CoverageCells = H3CoverageCells(),
) : RecordingGateway {
    private val evidenceRecorder = CoverageEvidenceRecorder(database, coverageCells)
    private val coverageSettings = database.coverageSettingsRepository

    override suspend fun activeRecordingId(): Long? =
        database.recordings().observeActive().first()?.id

    override suspend fun start(startedAtMillis: Long): Long =
        database.recordings().insert(
            RecordingEntity(startedAtMillis = startedAtMillis),
        )

    override suspend fun record(
        recordingId: Long,
        fix: RecordingLocationFix,
        activity: RecordingActivity,
    ): RecordingLocationDecision {
        val recording = requireNotNull(database.recordings().find(recordingId)) {
            "Recording $recordingId does not exist"
        }
        check(recording.endedAtMillis == null) { "Recording $recordingId has ended" }
        val settings = coverageSettings.current()
        return evidenceRecorder.record(
            fix = fix,
            rules = CoverageEvidenceRules(
                source = CoverageEvidenceSource.RECORDING,
                trigger = ACTIVE_RECORDING_TRIGGER,
                maximumAccuracyMeters = settings.maximumAcceptedAccuracyMeters.toDouble(),
                maximumInterpolationDistanceSteps = settings.maximumInterpolationDistanceSteps,
                accuracyRejectionReason = RECORDING_ACCURACY_REJECTION,
            ),
            recordingId = recordingId,
            activityMode = activity.persistedMode,
        )
    }

    override suspend fun stop(recordingId: Long, endedAtMillis: Long) {
        val recording = requireNotNull(database.recordings().find(recordingId)) {
            "Recording $recordingId does not exist"
        }
        require(endedAtMillis >= recording.startedAtMillis)
        database.recordings().stop(recordingId, endedAtMillis)
    }
}

internal val RecordingActivity.persistedMode: PersistedActivityMode
    get() = when (this) {
        RecordingActivity.STILL -> PersistedActivityMode.STILL
        RecordingActivity.WALKING -> PersistedActivityMode.WALKING
        RecordingActivity.RUNNING -> PersistedActivityMode.RUNNING
        RecordingActivity.ON_BICYCLE -> PersistedActivityMode.CYCLING
        RecordingActivity.IN_VEHICLE -> PersistedActivityMode.IN_VEHICLE
        RecordingActivity.UNKNOWN -> PersistedActivityMode.UNKNOWN
    }
