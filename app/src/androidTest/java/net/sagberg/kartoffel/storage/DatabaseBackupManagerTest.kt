package net.sagberg.kartoffel.storage

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseBackupManagerTest {
    private lateinit var context: Context
    private lateinit var backupFile: File
    private lateinit var invalidFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        KartoffelDatabase.closeForRestore()
        context.deleteDatabase(KartoffelDatabase.NAME)
        backupFile = File(context.cacheDir, "database-backup-test.db")
        invalidFile = File(context.cacheDir, "invalid-database-backup-test.db")
        backupFile.delete()
        invalidFile.delete()
    }

    @After
    fun tearDown() {
        KartoffelDatabase.closeForRestore()
        context.deleteDatabase(KartoffelDatabase.NAME)
        backupFile.delete()
        invalidFile.delete()
    }

    @Test
    fun exportAndRestoreReplaceCurrentDatabaseWithSnapshot() = runBlocking {
        val firstCell = 0x08c09993866141ffL
        val laterCell = 0x08c2a1072b59b9ffL
        val database = KartoffelDatabase.open(context)
        database.coverageCells().upsert(firstCell, 1_000, 1_000, 1)
        val manager = DatabaseBackupManager(context)

        manager.export(Uri.fromFile(backupFile))
        database.coverageCells().upsert(laterCell, 2_000, 2_000, 1)
        manager.restore(Uri.fromFile(backupFile))

        val restored = KartoffelDatabase.open(context)
        assertNotNull(restored.coverageCells().find(firstCell))
        assertEquals(null, restored.coverageCells().find(laterCell))
    }

    @Test
    fun invalidRestoreLeavesCurrentDatabaseOpenAndUnchanged() = runBlocking {
        val cell = 0x08c09993866141ffL
        val database = KartoffelDatabase.open(context)
        database.coverageCells().upsert(cell, 1_000, 1_000, 1)
        invalidFile.writeText("not a database")

        val result = runCatching {
            DatabaseBackupManager(context).restore(Uri.fromFile(invalidFile))
        }

        assertTrue(result.isFailure)
        assertNotNull(database.coverageCells().find(cell))
    }

}
