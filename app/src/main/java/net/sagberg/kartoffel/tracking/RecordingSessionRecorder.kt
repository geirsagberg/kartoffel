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
import net.sagberg.kartoffel.storage.RecordingSessionEntity

internal const val MAX_RECORDING_ACCURACY_METERS = 20.0
internal const val RECORDING_ACCURACY_REJECTION = "accuracy_exceeds_recording_limit"

private const val ACTIVE_SESSION_TRIGGER = "active_session"

internal typealias RecordingLocationFix = CoverageLocationFix
internal typealias RecordingLocationDecision = CoverageLocationDecision

internal class RecordingSessionRecorder(
    private val database: KartoffelDatabase,
    coverageCells: H3CoverageCells = H3CoverageCells(),
) : RecordingSessionGateway {
    private val evidenceRecorder = CoverageEvidenceRecorder(database, coverageCells)

    override suspend fun activeSessionId(): Long? =
        database.recordingSessions().observeActive().first()?.id

    override suspend fun start(startedAtMillis: Long): Long =
        database.recordingSessions().insert(
            RecordingSessionEntity(startedAtMillis = startedAtMillis),
        )

    override suspend fun record(
        sessionId: Long,
        fix: RecordingLocationFix,
        activity: RecordingActivity,
    ): RecordingLocationDecision {
        val session = requireNotNull(database.recordingSessions().find(sessionId)) {
            "Recording Session $sessionId does not exist"
        }
        check(session.endedAtMillis == null) { "Recording Session $sessionId has ended" }
        return evidenceRecorder.record(
            fix = fix,
            rules = CoverageEvidenceRules(
                source = CoverageEvidenceSource.RECORDING_SESSION,
                trigger = ACTIVE_SESSION_TRIGGER,
                maximumAccuracyMeters = MAX_RECORDING_ACCURACY_METERS,
                accuracyRejectionReason = RECORDING_ACCURACY_REJECTION,
            ),
            recordingSessionId = sessionId,
            activityMode = activity.persistedMode,
        )
    }

    override suspend fun stop(sessionId: Long, endedAtMillis: Long) {
        val session = requireNotNull(database.recordingSessions().find(sessionId)) {
            "Recording Session $sessionId does not exist"
        }
        require(endedAtMillis >= session.startedAtMillis)
        database.recordingSessions().stop(sessionId, endedAtMillis)
    }
}

private val RecordingActivity.persistedMode: PersistedActivityMode
    get() = when (this) {
        RecordingActivity.STILL -> PersistedActivityMode.STILL
        RecordingActivity.WALKING -> PersistedActivityMode.WALKING
        RecordingActivity.RUNNING -> PersistedActivityMode.RUNNING
        RecordingActivity.ON_BICYCLE -> PersistedActivityMode.CYCLING
        RecordingActivity.IN_VEHICLE -> PersistedActivityMode.IN_VEHICLE
        RecordingActivity.UNKNOWN -> PersistedActivityMode.UNKNOWN
    }
