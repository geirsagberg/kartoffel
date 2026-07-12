package net.sagberg.kartoffel.coverage

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
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
class ForegroundCoverageRecorderTest {
    private lateinit var database: KartoffelDatabase
    private lateinit var recorder: ForegroundCoverageRecorder

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KartoffelDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        recorder = ForegroundCoverageRecorder(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun acceptedFixPersistsItsSampleAndCoverageCell() = runBlocking {
        val fix = fix(accuracyMeters = 8.0)

        val result = recorder.record(fix)

        val cellId = H3CoverageCells().cellAt(fix.coordinate).value
        val sample = database.locationSamples().between(0, 2_000).single()
        val cell = database.coverageCells().find(cellId)
        assertEquals(true, result.accepted)
        assertEquals(true, sample.accepted)
        assertNull(sample.rejectionReason)
        assertNotNull(cell)
        assertEquals(
            evidenceMaskOf(CoverageEvidenceSource.FOREGROUND_FIX),
            cell?.evidenceMask,
        )
        val snapshot = PersistedCoverageLoader(database.coverageCells()).load(revision = 1)
        assertEquals(1, snapshot.revision)
        assertEquals(cellId.toString(), snapshot.cells.single().id)
        assertEquals(H3CoverageCells().boundaryOf(CoverageCellId(cellId)), snapshot.cells.single().boundary)
    }

    @Test
    fun rejectedFixPersistsTheReasonWithoutCoverage() = runBlocking {
        val fix = fix(accuracyMeters = MAX_FOREGROUND_ACCURACY_METERS + 1.0)

        val result = recorder.record(fix)

        val sample = database.locationSamples().between(0, 2_000).single()
        assertEquals(false, result.accepted)
        assertEquals(FOREGROUND_ACCURACY_REJECTION, sample.rejectionReason)
        assertEquals(emptyList<Any>(), database.coverageCells().all())
    }

    private fun fix(accuracyMeters: Double) = ForegroundLocationFix(
        coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522),
        capturedAtMillis = 1_000,
        accuracyMeters = accuracyMeters,
    )
}
