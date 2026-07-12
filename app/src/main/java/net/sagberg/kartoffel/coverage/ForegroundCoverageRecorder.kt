package net.sagberg.kartoffel.coverage

import androidx.room3.withWriteTransaction
import net.sagberg.kartoffel.storage.CoverageEvidenceSource
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.LocationSampleEntity
import net.sagberg.kartoffel.storage.evidenceMaskOf

internal const val MAX_FOREGROUND_ACCURACY_METERS = 20.0
internal const val FOREGROUND_ACCURACY_REJECTION = "accuracy_exceeds_foreground_limit"

private const val FOREGROUND_SOURCE = "foreground_fix"
private const val APP_OPEN_TRIGGER = "app_open"

internal data class ForegroundLocationFix(
    val coordinate: GeoCoordinate,
    val capturedAtMillis: Long,
    val accuracyMeters: Double,
) {
    init {
        require(capturedAtMillis >= 0)
        require(accuracyMeters >= 0.0)
    }
}

internal data class ForegroundLocationDecision(
    val accepted: Boolean,
    val rejectionReason: String?,
) {
    init {
        require(accepted == (rejectionReason == null))
    }
}

internal fun decideForegroundLocation(
    fix: ForegroundLocationFix,
    maximumAccuracyMeters: Double = MAX_FOREGROUND_ACCURACY_METERS,
): ForegroundLocationDecision =
    if (fix.accuracyMeters <= maximumAccuracyMeters) {
        ForegroundLocationDecision(accepted = true, rejectionReason = null)
    } else {
        ForegroundLocationDecision(
            accepted = false,
            rejectionReason = FOREGROUND_ACCURACY_REJECTION,
        )
    }

internal class ForegroundCoverageRecorder(
    private val database: KartoffelDatabase,
    private val coverageCells: H3CoverageCells = H3CoverageCells(),
) {
    suspend fun record(fix: ForegroundLocationFix): ForegroundLocationDecision {
        val decision = decideForegroundLocation(fix)
        val cell = if (decision.accepted) coverageCells.cellAt(fix.coordinate) else null

        database.withWriteTransaction {
            database.locationSamples().insert(
                LocationSampleEntity(
                    capturedAtMillis = fix.capturedAtMillis,
                    latitude = fix.coordinate.latitude,
                    longitude = fix.coordinate.longitude,
                    accuracyMeters = fix.accuracyMeters,
                    source = FOREGROUND_SOURCE,
                    trigger = APP_OPEN_TRIGGER,
                    accepted = decision.accepted,
                    rejectionReason = decision.rejectionReason,
                ),
            )
            cell?.let {
                database.coverageCells().upsert(
                    cellId = it.value,
                    firstSeenAtMillis = fix.capturedAtMillis,
                    lastSeenAtMillis = fix.capturedAtMillis,
                    evidenceMask = evidenceMaskOf(CoverageEvidenceSource.FOREGROUND_FIX),
                )
            }
        }

        return decision
    }
}
