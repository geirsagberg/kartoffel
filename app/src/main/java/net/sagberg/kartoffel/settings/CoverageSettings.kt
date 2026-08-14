package net.sagberg.kartoffel.settings

import kotlinx.coroutines.flow.Flow

internal data class CoverageSettings(
    val maximumAcceptedAccuracyMeters: Int,
    val maximumInterpolationGapSteps: Int,
) {
    init {
        require(maximumAcceptedAccuracyMeters in ACCEPTED_ACCURACY_RANGE)
        require((maximumAcceptedAccuracyMeters - ACCEPTED_ACCURACY_RANGE.first) % 5 == 0)
        require(maximumInterpolationGapSteps in INTERPOLATION_GAP_RANGE)
    }

    companion object {
        val ACCEPTED_ACCURACY_RANGE = 20..50
        val INTERPOLATION_GAP_RANGE = 1..10

        val Default = CoverageSettings(
            maximumAcceptedAccuracyMeters = 25,
            maximumInterpolationGapSteps = 3,
        )
    }
}

internal interface CoverageSettingsRepository {
    fun observe(): Flow<CoverageSettings>

    suspend fun current(): CoverageSettings

    suspend fun setMaximumAcceptedAccuracyMeters(value: Int)

    suspend fun setMaximumInterpolationGapSteps(value: Int)

    suspend fun reset()
}
