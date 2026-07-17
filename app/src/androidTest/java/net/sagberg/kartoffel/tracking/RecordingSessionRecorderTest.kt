package net.sagberg.kartoffel.tracking

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells
import net.sagberg.kartoffel.coverage.PersistedCoverageLoader
import net.sagberg.kartoffel.storage.CoverageEvidenceSource
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.evidenceMaskOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingSessionRecorderTest {
    private lateinit var database: KartoffelDatabase
    private lateinit var recorder: RecordingSessionRecorder

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KartoffelDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        recorder = RecordingSessionRecorder(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun recordingSessionPersistsAcceptedEvidenceAndReviewableGeometry() = runBlocking {
        val coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522)

        val sessionId = recorder.start(startedAtMillis = 1_000)
        val decision = recorder.record(
            sessionId,
            RecordingLocationFix(
                coordinate = coordinate,
                capturedAtMillis = 2_000,
                accuracyMeters = 8.0,
            ),
        )
        recorder.stop(sessionId, endedAtMillis = 3_000)

        val session = database.recordingSessions().find(sessionId)
        val sample = database.locationSamples().between(0, 4_000).single()
        val route = database.recordingSessionPoints().forSession(sessionId)
        val cellId = H3CoverageCells().cellAt(coordinate).value
        val cell = database.coverageCells().find(cellId)
        assertEquals(true, decision.accepted)
        assertEquals(1_000L, session?.startedAtMillis)
        assertEquals(3_000L, session?.endedAtMillis)
        assertEquals(sessionId, sample.recordingSessionId)
        assertEquals(true, sample.accepted)
        assertNull(sample.rejectionReason)
        assertEquals(1, route.size)
        assertEquals(sample.id, route.single().sampleId)
        assertEquals(coordinate.latitude, route.single().latitude, 0.0)
        assertEquals(coordinate.longitude, route.single().longitude, 0.0)
        assertNotNull(cell)
        assertEquals(
            evidenceMaskOf(CoverageEvidenceSource.RECORDING_SESSION),
            cell?.evidenceMask,
        )
        val snapshot = PersistedCoverageLoader(database.coverageCells()).load()
        assertEquals(
            H3CoverageCells().boundaryOf(CoverageCellId(cellId)),
            snapshot.cells.single().boundary,
        )
    }

    @Test
    fun recordingSessionRetainsRejectedEvidenceWithoutClearingCoverage() = runBlocking {
        val sessionId = recorder.start(startedAtMillis = 1_000)

        val decision = recorder.record(
            sessionId,
            RecordingLocationFix(
                coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522),
                capturedAtMillis = 2_000,
                accuracyMeters = MAX_RECORDING_ACCURACY_METERS + 1.0,
            ),
        )

        val sample = database.locationSamples().between(0, 3_000).single()
        assertEquals(false, decision.accepted)
        assertEquals(false, sample.accepted)
        assertEquals(RECORDING_ACCURACY_REJECTION, sample.rejectionReason)
        assertEquals(emptyList<Any>(), database.recordingSessionPoints().forSession(sessionId))
        assertEquals(emptyList<Any>(), database.coverageCells().all())
    }

    @Test
    fun reviewableGeometryKeepsOnlyCellTransitions() = runBlocking {
        val sessionId = recorder.start(startedAtMillis = 1_000)
        val coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522)

        recorder.record(
            sessionId,
            RecordingLocationFix(
                coordinate = coordinate,
                capturedAtMillis = 2_000,
                accuracyMeters = 8.0,
            ),
        )
        recorder.record(
            sessionId,
            RecordingLocationFix(
                coordinate = coordinate,
                capturedAtMillis = 2_500,
                accuracyMeters = 8.0,
            ),
        )

        assertEquals(2, database.locationSamples().between(0, 3_000).size)
        assertEquals(1, database.recordingSessionPoints().forSession(sessionId).size)
    }
}
