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
                TrackingInspectionScope.PassiveTracking ->
                    database.locationSamples().inspectionPassive(startMillis, endMillis)
                TrackingInspectionScope.AllRecordingSessions ->
                    database.locationSamples().inspectionRecordingSessions(startMillis, endMillis)
                is TrackingInspectionScope.RecordingSession ->
                    database.locationSamples().inspectionRecordingSession(
                        scope.id,
                        startMillis,
                        endMillis,
                    )
            }
            val sessions = database.recordingSessions().all()
            val durableEvidence = database.coverageCells().all()
            val manualRouteClaims = database.manualRouteClaims().allClaims().map { claim ->
                InspectionManualRouteClaim(
                    id = claim.id,
                    createdAtMillis = claim.createdAtMillis,
                    cells = database.manualRouteClaims().cellsForClaim(claim.id).map { cellId ->
                        h3.shapeOf(CoverageCellId(cellId))
                    },
                )
            }
            val sessionPoints = (filter.scope as? TrackingInspectionScope.RecordingSession)?.let {
                database.recordingSessionPoints().forSession(it.id)
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
                is TrackingInspectionScope.RecordingSession -> {
                    sessionPoints.filter { point ->
                        point.capturedAtMillis >= startMillis && point.capturedAtMillis < endMillis
                    }.forEach { point ->
                        aggregates.getOrPut(point.cellId) { MutableDiagnosticCell(point.cellId) }
                            .addSessionEvidence(point.capturedAtMillis, inferred = false)
                    }
                    sessionPoints.zipWithNext()
                        .filter { (_, destination) ->
                            destination.capturedAtMillis >= startMillis &&
                                destination.capturedAtMillis < endMillis
                        }
                        .forEach { (start, destination) ->
                        h3.intermediateCellsForShortGap(
                            CoverageCellId(start.cellId),
                            CoverageCellId(destination.cellId),
                        ).forEach { inferred ->
                            aggregates.getOrPut(inferred.value) { MutableDiagnosticCell(inferred.value) }
                                .addSessionEvidence(destination.capturedAtMillis, inferred = true)
                        }
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
                    TrackingInspectionScope.PassiveTracking,
                    TrackingInspectionScope.AllRecordingSessions,
                ) + sessions.map { TrackingInspectionScope.RecordingSession(it.id) },
                availableRecordingSessions = sessions.map {
                    InspectionRecordingSession(it.id, it.startedAtMillis, it.endedAtMillis)
                },
                cells = cells,
                measurements = measurements,
                manualRouteClaims = manualRouteClaims,
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
        var passiveObserved = false
        var recordingObserved = false
        var passiveInferred = false
        var recordingInferred = false
        var firstEvidenceMillis: Long? = null
        var lastEvidenceMillis: Long? = null

        fun addSessionEvidence(capturedAtMillis: Long, inferred: Boolean) {
            if (inferred) recordingInferred = true else recordingObserved = true
            firstEvidenceMillis = minOf(firstEvidenceMillis ?: Long.MAX_VALUE, capturedAtMillis)
            lastEvidenceMillis = maxOf(lastEvidenceMillis ?: Long.MIN_VALUE, capturedAtMillis)
        }

        fun addEvidence(entity: CoverageCellEntity, scope: TrackingInspectionScope) {
            val mask = entity.evidenceMask
            if (scope != TrackingInspectionScope.AllRecordingSessions) {
                passiveObserved = mask has CoverageEvidenceSource.PASSIVE_TRACKING.observedBit
                passiveInferred = mask has CoverageEvidenceSource.PASSIVE_TRACKING.inferredBit
            }
            if (scope != TrackingInspectionScope.PassiveTracking) {
                recordingObserved = mask has CoverageEvidenceSource.RECORDING_SESSION.observedBit
                recordingInferred = mask has CoverageEvidenceSource.RECORDING_SESSION.inferredBit
            }
            if (passiveObserved || recordingObserved || passiveInferred || recordingInferred) {
                firstEvidenceMillis = minOf(firstEvidenceMillis ?: Long.MAX_VALUE, entity.firstSeenAtMillis)
                lastEvidenceMillis = maxOf(lastEvidenceMillis ?: Long.MIN_VALUE, entity.lastSeenAtMillis)
            }
        }

        fun toModel(h3: H3CoverageCells): DiagnosticSampleCell {
            passiveObserved = passiveObserved || samples.any {
                it.accepted && it.source == CoverageEvidenceSource.PASSIVE_TRACKING.persistedName
            }
            recordingObserved = recordingObserved || samples.any {
                it.accepted && it.source == CoverageEvidenceSource.RECORDING_SESSION.persistedName
            }
            val observed = passiveObserved || recordingObserved
            val inferred = passiveInferred || recordingInferred
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
                    if (passiveObserved) add(DiagnosticProvenance.PassiveObserved)
                    if (recordingObserved) add(DiagnosticProvenance.RecordingObserved)
                    if (passiveInferred) add(DiagnosticProvenance.PassiveInferred)
                    if (recordingInferred) add(DiagnosticProvenance.RecordingInferred)
                    if (samples.any {
                            !it.accepted &&
                                it.source == CoverageEvidenceSource.PASSIVE_TRACKING.persistedName
                        }
                    ) {
                        add(DiagnosticProvenance.PassiveRejected)
                    }
                    if (samples.any {
                            !it.accepted &&
                                it.source == CoverageEvidenceSource.RECORDING_SESSION.persistedName
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
        TrackingInspectionScope.PassiveTracking -> "passive"
        TrackingInspectionScope.AllRecordingSessions -> "recording_all"
        is TrackingInspectionScope.RecordingSession -> "recording_one"
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
    recordingSessionId = recordingSessionId,
    activityMode = activityMode,
    accepted = accepted,
    rejectionReason = rejectionReason,
)
