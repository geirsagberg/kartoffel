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
import net.sagberg.kartoffel.storage.RecordingEntity
import net.sagberg.kartoffel.storage.inferredEvidenceMaskOf
import net.sagberg.kartoffel.tracking.RecordingActivity
import net.sagberg.kartoffel.tracking.RecordingLocationFix
import net.sagberg.kartoffel.tracking.RecordingFixRecorder
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
        val recordingId = database.recordings().insert(RecordingEntity(1_000, 5_000))
        insertSample(1_000, coordinate, "automatic_tracking", accepted = true)
        insertSample(2_000, coordinate, "automatic_tracking", accepted = false)
        insertSample(3_000, coordinate, "recording", accepted = true, recordingId = recordingId)

        val snapshot = TrackingInspectionLoader(database).load(
            TrackingInspectionFilter(
                scope = TrackingInspectionScope.AutomaticTracking,
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
        assertEquals(listOf(recordingId), snapshot.availableRecordings.map { it.id })

        val empty = TrackingInspectionLoader(database).load(
            TrackingInspectionFilter(
                TrackingInspectionScope.AllTracking,
                TrackingInspectionTime.Custom(10_000, 11_000),
            ),
        )
        assertTrue(empty.cells.isEmpty())

        val allRecording = TrackingInspectionLoader(database).load(
            TrackingInspectionFilter(
                TrackingInspectionScope.AllRecordings,
                TrackingInspectionTime.AllTime,
            ),
        )
        val oneRecording = TrackingInspectionLoader(database).load(
            TrackingInspectionFilter(
                TrackingInspectionScope.Recording(recordingId),
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
        insertSample(2_000, coordinate, "automatic_tracking", accepted = false)
        insertSample(2_100, rejectedOnlyCoordinate, "automatic_tracking", accepted = false)
        listOf(rejectedAndInferredCell, inferredOnlyCell).forEach { cellId ->
            database.coverageCells().upsert(
                cellId = cellId,
                firstSeenAtMillis = 1_500,
                lastSeenAtMillis = 2_500,
                evidenceMask = inferredEvidenceMaskOf(CoverageEvidenceSource.AUTOMATIC_TRACKING),
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
    fun recordingInspectionDoesNotReconstructHistoricalInterpolation() = runBlocking {
        val recorder = RecordingFixRecorder(database)
        val recordingId = recorder.start(1_000)
        recorder.record(
            recordingId,
            RecordingLocationFix(
                GeoCoordinate(59.9109, 10.7522),
                capturedAtMillis = 2_000,
                accuracyMeters = 8.0,
            ),
            RecordingActivity.WALKING,
        )
        recorder.record(
            recordingId,
            RecordingLocationFix(
                GeoCoordinate(59.910527, 10.751046),
                capturedAtMillis = 3_000,
                accuracyMeters = 8.0,
            ),
            RecordingActivity.WALKING,
        )

        val snapshot = TrackingInspectionLoader(database).load(
            TrackingInspectionFilter(
                TrackingInspectionScope.Recording(recordingId),
                TrackingInspectionTime.Custom(2_500, 4_000),
            ),
        )

        assertEquals(1, snapshot.cells.count { it.state == DiagnosticCellState.Observed })
        assertEquals(0, snapshot.cells.count { it.state == DiagnosticCellState.Inferred })
        assertEquals(1, snapshot.cells.size)
        assertEquals(1, snapshot.cells.sumOf { it.samples.size })
        assertEquals(3_000L, snapshot.cells.single().evidenceFirstMillis)
        assertEquals(3_000L, snapshot.cells.single().evidenceLastMillis)
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

    @Test
    fun loaderListsDrawnRoutesByCreationTimeWithCellDerivedPreview() = runBlocking {
        val h3 = H3CoverageCells()
        val cells = setOf(
            h3.cellAt(GeoCoordinate(59.9109, 10.7522)).value,
            h3.cellAt(GeoCoordinate(59.9119, 10.7552)).value,
        )
        val routeId = database.drawnRoutes().create(4_000, cells)

        val snapshot = TrackingInspectionLoader(database).load(TrackingInspectionFilter.Default)

        val route = snapshot.drawnRoutes.single()
        assertEquals(routeId, route.id)
        assertEquals(4_000L, route.createdAtMillis)
        assertEquals(cells, route.cells.map { it.id.toLong() }.toSet())
        assertTrue(route.cells.all { it.boundary.size >= 3 })
        assertTrue(snapshot.availableRecordings.isEmpty())
    }

    private suspend fun insertSample(
        capturedAtMillis: Long,
        coordinate: GeoCoordinate,
        source: String,
        accepted: Boolean,
        recordingId: Long? = null,
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
                recordingId = recordingId,
            ),
        )
    }
}
