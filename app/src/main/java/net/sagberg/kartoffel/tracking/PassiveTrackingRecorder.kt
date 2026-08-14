package net.sagberg.kartoffel.tracking

import net.sagberg.kartoffel.coverage.CoverageEvidenceRecorder
import net.sagberg.kartoffel.coverage.CoverageEvidenceRules
import net.sagberg.kartoffel.coverage.CoverageLocationDecision
import net.sagberg.kartoffel.coverage.ShortGapInterpolationPolicy
import net.sagberg.kartoffel.storage.CoverageEvidenceSource
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.RoomCoverageSettings

internal const val PASSIVE_ACCURACY_REJECTION = "accuracy_exceeds_passive_limit"
internal const val MAXIMUM_PASSIVE_INTERPOLATION_GAP_MILLIS = 120_000L

internal enum class PassiveFixTrigger(val persistedName: String) {
    INITIAL_ENABLE("initial_enable"),
    MOVEMENT_WINDOW("movement_window"),
    FALLBACK_WINDOW("fallback_window"),
    OPPORTUNISTIC("opportunistic"),
}

internal class PassiveTrackingRecorder(
    database: KartoffelDatabase,
) {
    private val evidenceRecorder = CoverageEvidenceRecorder(database)
    private val coverageSettings = RoomCoverageSettings(database.coverageSettings())

    suspend fun record(
        fix: RecordingLocationFix,
        trigger: PassiveFixTrigger,
        activity: RecordingActivity,
    ): CoverageLocationDecision {
        val settings = coverageSettings.current()
        return evidenceRecorder.record(
            fix = fix,
            rules = CoverageEvidenceRules(
                source = CoverageEvidenceSource.PASSIVE_TRACKING,
                trigger = trigger.persistedName,
                maximumAccuracyMeters = settings.maximumAcceptedAccuracyMeters.toDouble(),
                maximumInterpolationGapSteps = settings.maximumInterpolationGapSteps,
                accuracyRejectionReason = PASSIVE_ACCURACY_REJECTION,
                shortGapInterpolation = if (trigger == PassiveFixTrigger.OPPORTUNISTIC) {
                    null
                } else {
                    ShortGapInterpolationPolicy(
                        maximumGapMillis = MAXIMUM_PASSIVE_INTERPOLATION_GAP_MILLIS,
                        eligiblePreviousTriggers = PassiveFixTrigger.entries
                            .filterNot { it == PassiveFixTrigger.OPPORTUNISTIC }
                            .mapTo(mutableSetOf(), PassiveFixTrigger::persistedName),
                    )
                },
            ),
            activityMode = activity.persistedMode,
        )
    }
}
