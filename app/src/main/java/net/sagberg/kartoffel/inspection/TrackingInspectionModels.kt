package net.sagberg.kartoffel.inspection

import net.sagberg.kartoffel.coverage.GeoBounds
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.CoverageCellShape

internal sealed interface TrackingInspectionScope {
    data object AllTracking : TrackingInspectionScope
    data object PassiveTracking : TrackingInspectionScope
    data object AllRecordingSessions : TrackingInspectionScope
    data class RecordingSession(val id: Long) : TrackingInspectionScope
}

internal sealed interface TrackingInspectionTime {
    data object AllTime : TrackingInspectionTime
    data class Last24Hours(val endMillis: Long) : TrackingInspectionTime
    data class Last7Days(val endMillis: Long) : TrackingInspectionTime
    data class Custom(val startMillis: Long, val endMillis: Long) : TrackingInspectionTime {
        init {
            require(startMillis < endMillis)
        }
    }
}

internal data class TrackingInspectionFilter(
    val scope: TrackingInspectionScope,
    val time: TrackingInspectionTime,
) {
    companion object {
        val Default = TrackingInspectionFilter(
            scope = TrackingInspectionScope.AllTracking,
            time = TrackingInspectionTime.AllTime,
        )
    }
}

internal enum class DiagnosticCellState { Observed, Inferred, RejectedOnly }

internal enum class DiagnosticProvenance {
    PassiveObserved,
    RecordingObserved,
    PassiveInferred,
    RecordingInferred,
    PassiveRejected,
    RecordingRejected,
}

internal enum class SampleSort { NewestFirst, OldestFirst }

internal data class DiagnosticSample(
    val id: Long,
    val capturedAtMillis: Long,
    val accuracyMeters: Double,
    val source: String,
    val trigger: String?,
    val recordingSessionId: Long?,
    val activityMode: String,
    val accepted: Boolean,
    val rejectionReason: String?,
)

internal data class DiagnosticSampleCell(
    val cellId: Long,
    val boundary: List<GeoCoordinate>,
    val state: DiagnosticCellState,
    val provenance: Set<DiagnosticProvenance>,
    val acceptedCount: Int,
    val rejectedCount: Int,
    val evidenceFirstMillis: Long?,
    val evidenceLastMillis: Long?,
    val sampleFirstMillis: Long?,
    val sampleLastMillis: Long?,
    val samples: List<DiagnosticSample>,
) {
    fun samples(sort: SampleSort): List<DiagnosticSample> = when (sort) {
        SampleSort.NewestFirst -> samples
        SampleSort.OldestFirst -> samples.asReversed()
    }
}

internal data class InspectionRecordingSession(
    val id: Long,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
)

internal data class InspectionManualRouteClaim(
    val id: Long,
    val createdAtMillis: Long,
    val cells: List<CoverageCellShape>,
)

internal data class TrackingInspectionMeasurements(
    val databaseMillis: Long,
    val aggregationMillis: Long,
    val renderPreparationMillis: Long,
)

internal data class TrackingInspectionSnapshot(
    val filter: TrackingInspectionFilter,
    val availableScopes: List<TrackingInspectionScope>,
    val availableRecordingSessions: List<InspectionRecordingSession>,
    val cells: List<DiagnosticSampleCell>,
    val measurements: TrackingInspectionMeasurements,
    val manualRouteClaims: List<InspectionManualRouteClaim> = emptyList(),
) {
    fun cellsIntersecting(viewport: GeoBounds): List<DiagnosticSampleCell> = cells.filter { cell ->
        GeoBounds.containing(cell.boundary).intersects(viewport)
    }
}
