package net.sagberg.kartoffel.storage

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KartoffelDatabaseTest {
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
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun coverageCellUpsertPreservesTimeRangeAndCombinesEvidence() = runBlocking {
        val cellId = 0x08c09993866141ffL
        val dao = database.coverageCells()

        dao.upsert(
            cellId = cellId,
            firstSeenAtMillis = 2_000,
            lastSeenAtMillis = 3_000,
            evidenceMask = evidenceMaskOf(CoverageEvidenceSource.PASSIVE_TRACKING),
        )
        dao.upsert(
            cellId = cellId,
            firstSeenAtMillis = 1_000,
            lastSeenAtMillis = 4_000,
            evidenceMask = evidenceMaskOf(CoverageEvidenceSource.RECORDING_SESSION),
        )

        assertEquals(
            CoverageCellEntity(
                cellId = cellId,
                firstSeenAtMillis = 1_000,
                lastSeenAtMillis = 4_000,
                evidenceMask = evidenceMaskOf(
                    CoverageEvidenceSource.PASSIVE_TRACKING,
                    CoverageEvidenceSource.RECORDING_SESSION,
                ),
            ),
            dao.find(cellId),
        )
    }

    @Test
    fun coverageCellSetQueryReturnsOnlyRequestedStoredCells() = runBlocking {
        val dao = database.coverageCells()
        val firstCell = 0x08c09993866141ffL
        val secondCell = 0x08c2a1072b59b9ffL

        listOf(firstCell, secondCell).forEach { cellId ->
            dao.upsert(
                cellId = cellId,
                firstSeenAtMillis = 1_000,
                lastSeenAtMillis = 1_000,
                evidenceMask = evidenceMaskOf(CoverageEvidenceSource.RECORDING_SESSION),
            )
        }

        assertEquals(listOf(firstCell), dao.find(listOf(firstCell, 123L)).map { it.cellId })
    }

    @Test
    fun locationSamplesRoundTripAndQueryInCaptureOrder() = runBlocking {
        val dao = database.locationSamples()
        val laterId = dao.insert(sample(capturedAtMillis = 2_000, accepted = true))
        val earlierId = dao.insert(
            sample(
                capturedAtMillis = 1_000,
                accepted = false,
                rejectionReason = "accuracy_too_low",
            ),
        )

        assertEquals(2_000L, dao.find(laterId)?.capturedAtMillis)
        assertEquals(listOf(earlierId, laterId), dao.between(500, 2_500).map { it.id })
        assertNull(dao.find(Long.MAX_VALUE))
    }

    private fun sample(
        capturedAtMillis: Long,
        accepted: Boolean,
        rejectionReason: String? = null,
    ) = LocationSampleEntity(
        capturedAtMillis = capturedAtMillis,
        latitude = 59.9109,
        longitude = 10.7522,
        accuracyMeters = 7.5,
        source = "recording_session",
        trigger = "active_session",
        accepted = accepted,
        rejectionReason = rejectionReason,
    )
}
