package net.sagberg.kartoffel.inspection

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells
import net.sagberg.kartoffel.storage.CoverageCellEntity
import net.sagberg.kartoffel.storage.CoverageEvidenceSource
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.LocationSampleEntity
import kotlin.coroutines.coroutineContext

internal fun interface InspectionMonotonicClock {
    fun elapsedRealtimeMillis(): Long
}

internal fun interface TrackingInspectionLogger {
    fun slowLoad(message: String)
}

internal class TrackingInspectionLoader(
    private val database: KartoffelDatabase,
    private val h3: H3CoverageCells = H3CoverageCells(),
    private val clock: InspectionMonotonicClock = InspectionMonotonicClock {
        SystemClock.elapsedRealtime()
    },
    private val logger: TrackingInspectionLogger = TrackingInspectionLogger { message ->
        Log.i("TrackingInspection", message)
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun load(filter: TrackingInspectionFilter): TrackingInspectionSnapshot =
        withContext(dispatcher) {
            val started = clock.elapsedRealtimeMillis()
            val (startMillis, endMillis) = filter.time.bounds()
            val samples = when (val scope = filter.scope) {
                TrackingInspectionScope.AllTracking ->
                    database.locationSamples().inspectionAll(startMillis, endMillis)
                TrackingInspectionScope.AutomaticTracking ->
                    database.locationSamples().inspectionAutomatic(startMillis, endMillis)
                TrackingInspectionScope.AllRecordings ->
                    database.locationSamples().inspectionRecordings(startMillis, endMillis)
                is TrackingInspectionScope.Recording ->
                    database.locationSamples().inspectionRecording(
                        scope.id,
                        startMillis,
                        endMillis,
                    )
            }
            val recordings = database.recordings().all()
            val durableEvidence = database.coverageCells().all()
            val drawnRoutes = database.drawnRoutes().allRoutes().map { route ->
                InspectionDrawnRoute(
                    id = route.id,
                    createdAtMillis = route.createdAtMillis,
                    cells = database.drawnRoutes().cellsForRoute(route.id).map { cellId ->
                        h3.shapeOf(CoverageCellId(cellId))
                    },
                )
            }
            val recordingPoints = (filter.scope as? TrackingInspectionScope.Recording)?.let {
                database.recordingPoints().forRecording(it.id)
            }.orEmpty()
            val queried = clock.elapsedRealtimeMillis()
            coroutineContext.ensureActive()

            val aggregates = linkedMapOf<Long, MutableDiagnosticCell>()
            samples.forEach { sample ->
                coroutineContext.ensureActive()
                val cellId = h3.cellAt(GeoCoordinate(sample.latitude, sample.longitude)).value
                aggregates.getOrPut(cellId) { MutableDiagnosticCell(cellId) }.samples += sample
            }
            when (filter.scope) {
                is TrackingInspectionScope.Recording -> {
                    recordingPoints.filter { point ->
                        point.capturedAtMillis >= startMillis && point.capturedAtMillis < endMillis
                    }.forEach { point ->
                        aggregates.getOrPut(point.cellId) { MutableDiagnosticCell(point.cellId) }
                            .addRecordingEvidence(point.capturedAtMillis)
                    }
                }
                else -> durableEvidence
                    .asSequence()
                    .filter { it.overlaps(startMillis, endMillis) }
                    .forEach { evidence ->
                        val aggregate = aggregates.getOrPut(evidence.cellId) {
                            MutableDiagnosticCell(evidence.cellId)
                        }
                        aggregate.addEvidence(evidence, filter.scope)
                    }
            }
            val aggregated = clock.elapsedRealtimeMillis()
            coroutineContext.ensureActive()

            val cells = aggregates.values.map { aggregate -> aggregate.toModel(h3) }
                .sortedBy { it.cellId }
            val prepared = clock.elapsedRealtimeMillis()
            val measurements = TrackingInspectionMeasurements(
                databaseMillis = queried - started,
                aggregationMillis = aggregated - queried,
                renderPreparationMillis = prepared - aggregated,
            )
            val snapshot = TrackingInspectionSnapshot(
                filter = filter,
                availableScopes = listOf(
                    TrackingInspectionScope.AllTracking,
                    TrackingInspectionScope.AutomaticTracking,
                    TrackingInspectionScope.AllRecordings,
                ) + recordings.map { TrackingInspectionScope.Recording(it.id) },
                availableRecordings = recordings.map {
                    InspectionRecording(it.id, it.startedAtMillis, it.endedAtMillis)
                },
                cells = cells,
                measurements = measurements,
                drawnRoutes = drawnRoutes,
            )
            val total = prepared - started
            if (total > SLOW_LOAD_MILLIS) {
                logger.slowLoad(
                    "slow load total_ms=$total db_ms=${measurements.databaseMillis} " +
                        "aggregate_ms=${measurements.aggregationMillis} " +
                        "render_ms=${measurements.renderPreparationMillis} " +
                        "samples=${samples.size} cells=${cells.size} " +
                        "scope=${filter.scope.kind} time=${filter.time.kind}",
                )
            }
            snapshot
        }

    private class MutableDiagnosticCell(val cellId: Long) {
        val samples = mutableListOf<LocationSampleEntity>()
        var automaticObserved = false
        var recordingObserved = false
        var automaticInferred = false
        var recordingInferred = false
        var firstEvidenceMillis: Long? = null
        var lastEvidenceMillis: Long? = null

        fun addRecordingEvidence(capturedAtMillis: Long) {
            recordingObserved = true
            firstEvidenceMillis = minOf(firstEvidenceMillis ?: Long.MAX_VALUE, capturedAtMillis)
            lastEvidenceMillis = maxOf(lastEvidenceMillis ?: Long.MIN_VALUE, capturedAtMillis)
        }

        fun addEvidence(entity: CoverageCellEntity, scope: TrackingInspectionScope) {
            val mask = entity.evidenceMask
            if (scope != TrackingInspectionScope.AllRecordings) {
                automaticObserved = mask has CoverageEvidenceSource.AUTOMATIC_TRACKING.observedBit
                automaticInferred = mask has CoverageEvidenceSource.AUTOMATIC_TRACKING.inferredBit
            }
            if (scope != TrackingInspectionScope.AutomaticTracking) {
                recordingObserved = mask has CoverageEvidenceSource.RECORDING.observedBit
                recordingInferred = mask has CoverageEvidenceSource.RECORDING.inferredBit
            }
            if (automaticObserved || recordingObserved || automaticInferred || recordingInferred) {
                firstEvidenceMillis = minOf(firstEvidenceMillis ?: Long.MAX_VALUE, entity.firstSeenAtMillis)
                lastEvidenceMillis = maxOf(lastEvidenceMillis ?: Long.MIN_VALUE, entity.lastSeenAtMillis)
            }
        }

        fun toModel(h3: H3CoverageCells): DiagnosticSampleCell {
            automaticObserved = automaticObserved || samples.any {
                it.accepted && it.source == CoverageEvidenceSource.AUTOMATIC_TRACKING.persistedName
            }
            recordingObserved = recordingObserved || samples.any {
                it.accepted && it.source == CoverageEvidenceSource.RECORDING.persistedName
            }
            val observed = automaticObserved || recordingObserved
            val inferred = automaticInferred || recordingInferred
            val accepted = samples.count(LocationSampleEntity::accepted)
            val sampleFirst = samples.minOfOrNull { it.capturedAtMillis }
            val sampleLast = samples.maxOfOrNull { it.capturedAtMillis }
            return DiagnosticSampleCell(
                cellId = cellId,
                boundary = h3.boundaryOf(CoverageCellId(cellId)),
                state = when {
                    observed || accepted > 0 -> DiagnosticCellState.Observed
                    inferred -> DiagnosticCellState.Inferred
                    else -> DiagnosticCellState.RejectedOnly
                },
                provenance = buildSet {
                    if (automaticObserved) add(DiagnosticProvenance.AutomaticObserved)
                    if (recordingObserved) add(DiagnosticProvenance.RecordingObserved)
                    if (automaticInferred) add(DiagnosticProvenance.AutomaticInferred)
                    if (recordingInferred) add(DiagnosticProvenance.RecordingInferred)
                    if (samples.any {
                            !it.accepted &&
                                it.source == CoverageEvidenceSource.AUTOMATIC_TRACKING.persistedName
                        }
                    ) {
                        add(DiagnosticProvenance.AutomaticRejected)
                    }
                    if (samples.any {
                            !it.accepted &&
                                it.source == CoverageEvidenceSource.RECORDING.persistedName
                        }
                    ) {
                        add(DiagnosticProvenance.RecordingRejected)
                    }
                },
                acceptedCount = accepted,
                rejectedCount = samples.size - accepted,
                evidenceFirstMillis = firstEvidenceMillis,
                evidenceLastMillis = lastEvidenceMillis,
                sampleFirstMillis = sampleFirst,
                sampleLastMillis = sampleLast,
                samples = samples.sortedWith(
                    compareByDescending<LocationSampleEntity> { it.capturedAtMillis }
                        .thenByDescending { it.id },
                ).map(LocationSampleEntity::toDiagnosticSample),
            )
        }
    }

    private companion object {
        const val SLOW_LOAD_MILLIS = 500L
    }
}

private infix fun Int.has(bit: Int): Boolean = this and bit != 0

private fun CoverageCellEntity.overlaps(startMillis: Long, endMillis: Long): Boolean =
    lastSeenAtMillis >= startMillis && firstSeenAtMillis < endMillis

private fun TrackingInspectionTime.bounds(): Pair<Long, Long> = when (this) {
    TrackingInspectionTime.AllTime -> 0L to Long.MAX_VALUE
    is TrackingInspectionTime.Last24Hours -> endMillis - 24 * 60 * 60 * 1_000L to endMillis
    is TrackingInspectionTime.Last7Days -> endMillis - 7 * 24 * 60 * 60 * 1_000L to endMillis
    is TrackingInspectionTime.Custom -> startMillis to endMillis
}

private val TrackingInspectionScope.kind: String
    get() = when (this) {
        TrackingInspectionScope.AllTracking -> "all"
        TrackingInspectionScope.AutomaticTracking -> "automatic"
        TrackingInspectionScope.AllRecordings -> "recording_all"
        is TrackingInspectionScope.Recording -> "recording_one"
    }

private val TrackingInspectionTime.kind: String
    get() = when (this) {
        TrackingInspectionTime.AllTime -> "all"
        is TrackingInspectionTime.Last24Hours -> "24_hours"
        is TrackingInspectionTime.Last7Days -> "7_days"
        is TrackingInspectionTime.Custom -> "custom"
    }

private fun LocationSampleEntity.toDiagnosticSample() = DiagnosticSample(
    id = id,
    capturedAtMillis = capturedAtMillis,
    accuracyMeters = accuracyMeters,
    source = source,
    trigger = trigger,
    recordingId = recordingId,
    activityMode = activityMode,
    accepted = accepted,
    rejectionReason = rejectionReason,
)
