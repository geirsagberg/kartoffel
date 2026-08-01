package net.sagberg.kartoffel.inspection

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells
import net.sagberg.kartoffel.storage.CoverageEvidenceSource
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.LocationSampleEntity
import net.sagberg.kartoffel.storage.RecordingSessionEntity
import net.sagberg.kartoffel.storage.inferredEvidenceMaskOf
import net.sagberg.kartoffel.tracking.RecordingActivity
import net.sagberg.kartoffel.tracking.RecordingLocationFix
import net.sagberg.kartoffel.tracking.RecordingSessionRecorder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingInspectionLoaderTest {
    private lateinit var database: KartoffelDatabase

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KartoffelDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun loaderAppliesScopeAndInclusiveStartExclusiveEndWithoutLosingSamples() = runBlocking {
        val coordinate = GeoCoordinate(59.9109, 10.7522)
        val sessionId = database.recordingSessions().insert(RecordingSessionEntity(1_000, 5_000))
        insertSample(1_000, coordinate, "passive_tracking", accepted = true)
        insertSample(2_000, coordinate, "passive_tracking", accepted = false)
        insertSample(3_000, coordinate, "recording_session", accepted = true, sessionId = sessionId)

        val snapshot = TrackingInspectionLoader(database).load(
            TrackingInspectionFilter(
                scope = TrackingInspectionScope.PassiveTracking,
                time = TrackingInspectionTime.Custom(startMillis = 1_000, endMillis = 3_000),
            ),
        )

        assertEquals(1, snapshot.cells.size)
        assertEquals(DiagnosticCellState.Observed, snapshot.cells.single().state)
        assertEquals(2, snapshot.cells.single().samples.size)
        assertEquals(listOf(2_000L, 1_000L), snapshot.cells.single().samples.map { it.capturedAtMillis })
        assertEquals(
            listOf(1_000L, 2_000L),
            snapshot.cells.single().samples(SampleSort.OldestFirst).map { it.capturedAtMillis },
        )
        assertEquals(1, snapshot.cells.single().acceptedCount)
        assertEquals(1, snapshot.cells.single().rejectedCount)
        assertEquals(listOf(sessionId), snapshot.availableRecordingSessions.map { it.id })

        val empty = TrackingInspectionLoader(database).load(
            TrackingInspectionFilter(
                TrackingInspectionScope.AllTracking,
                TrackingInspectionTime.Custom(10_000, 11_000),
            ),
        )
        assertTrue(empty.cells.isEmpty())

        val allRecording = TrackingInspectionLoader(database).load(
            TrackingInspectionFilter(
                TrackingInspectionScope.AllRecordingSessions,
                TrackingInspectionTime.AllTime,
            ),
        )
        val oneRecording = TrackingInspectionLoader(database).load(
            TrackingInspectionFilter(
                TrackingInspectionScope.RecordingSession(sessionId),
                TrackingInspectionTime.AllTime,
            ),
        )
        assertEquals(listOf(3_000L), allRecording.cells.single().samples.map { it.capturedAtMillis })
        assertEquals(listOf(3_000L), oneRecording.cells.single().samples.map { it.capturedAtMillis })
    }

    @Test
    fun inferredEvidenceOutranksRejectedSamplesWithoutFabricatingHistory() = runBlocking {
        val h3 = H3CoverageCells()
        val coordinate = GeoCoordinate(59.9109, 10.7522)
        val rejectedAndInferredCell = h3.cellAt(coordinate).value
        val inferredOnlyCell = h3.cellAt(GeoCoordinate(59.910527, 10.751046)).value
        val rejectedOnlyCoordinate = GeoCoordinate(59.9200, 10.7600)
        val rejectedOnlyCell = h3.cellAt(rejectedOnlyCoordinate).value
        insertSample(2_000, coordinate, "passive_tracking", accepted = false)
        insertSample(2_100, rejectedOnlyCoordinate, "passive_tracking", accepted = false)
        listOf(rejectedAndInferredCell, inferredOnlyCell).forEach { cellId ->
            database.coverageCells().upsert(
                cellId = cellId,
                firstSeenAtMillis = 1_500,
                lastSeenAtMillis = 2_500,
                evidenceMask = inferredEvidenceMaskOf(CoverageEvidenceSource.PASSIVE_TRACKING),
            )
        }

        val snapshot = TrackingInspectionLoader(database).load(TrackingInspectionFilter.Default)

        assertEquals(2, snapshot.cells.count { it.state == DiagnosticCellState.Inferred })
        assertEquals(DiagnosticCellState.RejectedOnly, snapshot.cells.single { it.cellId == rejectedOnlyCell }.state)
        assertTrue(snapshot.cells.single { it.cellId == inferredOnlyCell }.samples.isEmpty())
        assertEquals(1, snapshot.cells.single { it.cellId == rejectedAndInferredCell }.rejectedCount)
        assertEquals(null, snapshot.cells.single { it.cellId == rejectedOnlyCell }.evidenceFirstMillis)
        assertEquals(2_100L, snapshot.cells.single { it.cellId == rejectedOnlyCell }.sampleFirstMillis)
    }

    @Test
    fun sessionInferenceUsesPredecessorBeforeCustomWindow() = runBlocking {
        val recorder = RecordingSessionRecorder(database)
        val sessionId = recorder.start(1_000)
        recorder.record(
            sessionId,
            RecordingLocationFix(
                GeoCoordinate(59.9109, 10.7522),
                capturedAtMillis = 2_000,
                accuracyMeters = 8.0,
            ),
            RecordingActivity.WALKING,
        )
        recorder.record(
            sessionId,
            RecordingLocationFix(
                GeoCoordinate(59.910527, 10.751046),
                capturedAtMillis = 3_000,
                accuracyMeters = 8.0,
            ),
            RecordingActivity.WALKING,
        )

        val snapshot = TrackingInspectionLoader(database).load(
            TrackingInspectionFilter(
                TrackingInspectionScope.RecordingSession(sessionId),
                TrackingInspectionTime.Custom(2_500, 4_000),
            ),
        )

        assertEquals(1, snapshot.cells.count { it.state == DiagnosticCellState.Observed })
        assertEquals(2, snapshot.cells.count { it.state == DiagnosticCellState.Inferred })
        assertEquals(1, snapshot.cells.sumOf { it.samples.size })
        assertTrue(
            snapshot.cells.filter { it.state == DiagnosticCellState.Inferred }
                .all { it.evidenceFirstMillis == 3_000L && it.evidenceLastMillis == 3_000L },
        )
    }

    @Test
    fun slowLoadLogContainsOnlyCountsFilterKindsAndStageDurations() = runBlocking {
        val ticks = ArrayDeque(listOf(0L, 600L, 700L, 800L))
        val messages = mutableListOf<String>()

        TrackingInspectionLoader(
            database = database,
            clock = InspectionMonotonicClock { ticks.removeFirst() },
            logger = TrackingInspectionLogger(messages::add),
        ).load(TrackingInspectionFilter.Default)

        assertEquals(1, messages.size)
        assertTrue(messages.single().contains("db_ms=600"))
        assertTrue(messages.single().contains("samples=0 cells=0 scope=all time=all"))
        assertTrue(!messages.single().contains("latitude") && !messages.single().contains("longitude"))
    }

    private suspend fun insertSample(
        capturedAtMillis: Long,
        coordinate: GeoCoordinate,
        source: String,
        accepted: Boolean,
        sessionId: Long? = null,
    ) {
        database.locationSamples().insert(
            LocationSampleEntity(
                capturedAtMillis = capturedAtMillis,
                latitude = coordinate.latitude,
                longitude = coordinate.longitude,
                accuracyMeters = 8.0,
                source = source,
                trigger = "test",
                accepted = accepted,
                rejectionReason = if (accepted) null else "test_rejection",
                recordingSessionId = sessionId,
            ),
        )
    }
}
