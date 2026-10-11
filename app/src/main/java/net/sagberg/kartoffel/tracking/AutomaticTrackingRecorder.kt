package net.sagberg.kartoffel.tracking

import net.sagberg.kartoffel.coverage.CoverageEvidenceRecorder
import net.sagberg.kartoffel.coverage.CoverageEvidenceRules
import net.sagberg.kartoffel.coverage.CoverageLocationDecision
import net.sagberg.kartoffel.coverage.ShortGapInterpolationPolicy
import net.sagberg.kartoffel.storage.CoverageEvidenceSource
import net.sagberg.kartoffel.storage.KartoffelDatabase

internal const val AUTOMATIC_ACCURACY_REJECTION = "accuracy_exceeds_automatic_limit"
internal const val MAXIMUM_AUTOMATIC_INTERPOLATION_GAP_MILLIS = 120_000L

internal enum class AutomaticFixTrigger(val persistedName: String) {
    INITIAL_ENABLE("initial_enable"),
    MOVEMENT_WINDOW("movement_window"),
    FALLBACK_WINDOW("fallback_window"),
    OPPORTUNISTIC("opportunistic"),
}

internal class AutomaticTrackingRecorder(
    database: KartoffelDatabase,
) {
    private val evidenceRecorder = CoverageEvidenceRecorder(database)
    private val coverageSettings = database.coverageSettingsRepository

    suspend fun record(
        fix: RecordingLocationFix,
        trigger: AutomaticFixTrigger,
        activity: RecordingActivity,
    ): CoverageLocationDecision {
        val settings = coverageSettings.current()
        return evidenceRecorder.record(
            fix = fix,
            rules = CoverageEvidenceRules(
                source = CoverageEvidenceSource.AUTOMATIC_TRACKING,
                trigger = trigger.persistedName,
                maximumAccuracyMeters = settings.maximumAcceptedAccuracyMeters.toDouble(),
                maximumInterpolationDistanceSteps = settings.maximumInterpolationDistanceSteps,
                accuracyRejectionReason = AUTOMATIC_ACCURACY_REJECTION,
                shortGapInterpolation = ShortGapInterpolationPolicy(
                    maximumGapMillis = MAXIMUM_AUTOMATIC_INTERPOLATION_GAP_MILLIS,
                    eligiblePreviousTriggers = AutomaticFixTrigger.entries
                        .mapTo(mutableSetOf(), AutomaticFixTrigger::persistedName),
                ),
            ),
            activityMode = activity.persistedMode,
        )
    }
}
