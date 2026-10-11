package net.sagberg.kartoffel.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class StorageUsageLoaderTest {
    private lateinit var context: Context
    private lateinit var database: KartoffelDatabase
    private lateinit var file: File
    private lateinit var loader: StorageUsageLoader

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(NAME)
        file = context.getDatabasePath(NAME)
        database = Room.databaseBuilder(context, KartoffelDatabase::class.java, NAME)
            .setDriver(AndroidSQLiteDriver())
            .build()
        // Create the schema before measuring; the loader itself must never create it.
        database.coverageCells().all()
        loader = StorageUsageLoader(file)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(NAME)
    }

    @Test
    fun emptyStorageIncludesRootPagesAndReportsFileLengths() = runBlocking {
        val usage = loader.load()
        assertEquals(0L, usage.locationSamples.rowCount)
        assertEquals(0L, usage.coverageCells.rowCount)
        assertTrue(usage.locationSamples.bytes > usage.coverageCells.bytes)
        assertTrue(usage.coverageCells.bytes > 0)
        assertEquals(file.length(), usage.databaseFileBytes)
        assertEquals(File("${file.path}-wal").length(), usage.walFileBytes)
        assertEquals(File("${file.path}-shm").length(), usage.sharedMemoryFileBytes)
        assertEquals(File("${file.path}-journal").length(), usage.rollbackJournalFileBytes)
    }

    @Test
    fun populatedStorageIsAttributedSeparatelyAndPreservesEvidence() = runBlocking {
        val empty = loader.load()
        repeat(300) { index ->
            database.locationSamples().insert(
                LocationSampleEntity(
                    capturedAtMillis = 1_000L + index,
                    latitude = 0.0,
                    longitude = 0.0,
                    accuracyMeters = 5.0,
                    source = "automatic_tracking",
                    trigger = "synthetic",
                    accepted = true,
                    rejectionReason = null,
                ),
            )
        }
        val samplesOnly = loader.load()
        assertEquals(300L, samplesOnly.locationSamples.rowCount)
        assertTrue(samplesOnly.locationSamples.bytes > empty.locationSamples.bytes)
        assertEquals(empty.coverageCells, samplesOnly.coverageCells)
        repeat(300) { index ->
            database.coverageCells().upsert(1_000L + index, 1_000, 2_000, 5)
        }
        val tracking = TrackingSettingsEntity(
            automaticEnabled = true,
            automaticPeriodStartedAtMillis = 1_000,
        )
        database.trackingSettings().upsert(tracking)
        val cellsBefore = database.coverageCells().all()
        val samplesBefore = database.locationSamples().inspectionAll(0, Long.MAX_VALUE)
        val databaseBefore = file.digest()
        val walBefore = File("${file.path}-wal").digest()
        val populated = loader.load()
        assertEquals(300L, populated.coverageCells.rowCount)
        assertTrue(populated.coverageCells.bytes > empty.coverageCells.bytes)
        assertEquals(samplesOnly.locationSamples, populated.locationSamples)
        assertEquals(populated, loader.load())
        assertEquals(cellsBefore, database.coverageCells().all())
        assertEquals(samplesBefore, database.locationSamples().inspectionAll(0, Long.MAX_VALUE))
        assertEquals(tracking, database.trackingSettings().current())
        assertEquals(databaseBefore, file.digest())
        assertEquals(walBefore, File("${file.path}-wal").digest())
    }

    @Test
    fun estimateIncludesIndexesAndRespondsToTextBytes() {
        SQLiteDatabase.create(null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE location_samples(id INTEGER PRIMARY KEY, source TEXT)")
            sqlite.execSQL("CREATE TABLE coverage_cells(cell_id INTEGER PRIMARY KEY)")
            val pageSize = sqlite.rawQuery("PRAGMA page_size", null).use {
                it.moveToFirst()
                it.getLong(0)
            }
            assertEquals(pageSize, sqlite.estimateStorageBytes("location_samples"))
            sqlite.execSQL("CREATE INDEX sample_source ON location_samples(source)")
            assertEquals(pageSize * 2, sqlite.estimateStorageBytes("location_samples"))
            sqlite.execSQL(
                "INSERT INTO location_samples VALUES (1, ?)",
                arrayOf("é".repeat(4_000)),
            )
            assertTrue(sqlite.estimateStorageBytes("location_samples") > pageSize * 2)
            assertEquals(pageSize, sqlite.estimateStorageBytes("coverage_cells"))
            // Most Android builds omit dbstat; when absent the loader labels the estimate.
            val supportsDbstat = runCatching {
                sqlite.rawQuery("SELECT * FROM dbstat LIMIT 1", null).use { it.moveToFirst() }
            }.isSuccess
            val usage = sqlite.storageUsage("location_samples")
            assertEquals(1L, usage.rowCount)
            assertEquals(
                if (supportsDbstat) StorageByteMeasurement.AllocatedPages
                else StorageByteMeasurement.EstimatedPages,
                usage.measurement,
            )
            if (!supportsDbstat) {
                assertEquals(sqlite.estimateStorageBytes("location_samples"), usage.bytes)
            }
        }
    }

    @Test
    fun allocatedPageAccountingIncludesOnlyTheTableAndItsIndexes() {
        SQLiteDatabase.create(null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE location_samples(id INTEGER PRIMARY KEY, source TEXT)")
            sqlite.execSQL("CREATE INDEX sample_source ON location_samples(source)")
            sqlite.execSQL("CREATE TABLE coverage_cells(cell_id INTEGER PRIMARY KEY)")
            // A synthetic dbstat result also exercises this path on builds without the module.
            sqlite.execSQL("CREATE TABLE dbstat(name TEXT, pgsize INTEGER)")
            sqlite.execSQL(
                "INSERT INTO dbstat VALUES ('location_samples', 4096), " +
                    "('sample_source', 4096), ('sample_source', 4096), " +
                    "('coverage_cells', 4096), ('sqlite_schema', 4096)",
            )
            assertEquals(
                TableStorageUsage(0, 12_288, StorageByteMeasurement.AllocatedPages),
                sqlite.storageUsage("location_samples"),
            )
            assertEquals(
                TableStorageUsage(0, 4_096, StorageByteMeasurement.AllocatedPages),
                sqlite.storageUsage("coverage_cells"),
            )
        }
    }

    @Test
    fun missingDatabaseFailsWithoutCreatingIt() = runBlocking {
        val missing = File(context.cacheDir, "missing-storage-usage.db")
        missing.delete()
        assertTrue(runCatching { StorageUsageLoader(missing).load() }.isFailure)
        assertTrue(!missing.exists())
    }

    private companion object {
        const val NAME = "storage-usage-test.db"
    }
}

private fun File.digest(): List<Byte>? = if (exists()) {
    MessageDigest.getInstance("SHA-256").digest(readBytes()).toList()
} else {
    null
}
