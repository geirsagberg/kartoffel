package net.sagberg.kartoffel.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class DatabaseBackupManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val databaseFile: File
        get() = appContext.getDatabasePath(KartoffelDatabase.NAME)

    suspend fun export(destination: Uri) = withContext(Dispatchers.IO) {
        val snapshot = File.createTempFile("kartoffel-backup-", ".db", appContext.cacheDir)
        try {
            check(snapshot.delete()) { "Could not prepare the backup" }
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { database ->
                database.execSQL("VACUUM INTO '${snapshot.absolutePath.sqlQuoted()}'")
            }
            appContext.contentResolver.openOutputStream(destination, "w")?.use { output ->
                snapshot.inputStream().use { input -> input.copyTo(output) }
            } ?: error("The selected document cannot be opened for writing")
        } finally {
            snapshot.delete()
        }
    }

    suspend fun restore(source: Uri) = withContext(Dispatchers.IO) {
        val databaseDirectory = checkNotNull(databaseFile.parentFile)
        databaseDirectory.mkdirs()
        val staged = File(databaseDirectory, "${KartoffelDatabase.NAME}.restore")
        val previous = File(databaseDirectory, "${KartoffelDatabase.NAME}.previous")

        staged.delete()
        try {
            appContext.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(staged).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            } ?: error("The selected document cannot be opened")
            validate(staged)

            KartoffelDatabase.closeForRestore()
            deleteJournalFiles()
            previous.delete()
            if (databaseFile.exists()) {
                Files.move(
                    databaseFile.toPath(),
                    previous.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            try {
                Files.move(
                    staged.toPath(),
                    databaseFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (failure: Exception) {
                if (previous.exists()) {
                    Files.move(
                        previous.toPath(),
                        databaseFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
                throw failure
            }
            previous.delete()
        } finally {
            staged.delete()
        }
    }

    private fun validate(file: File) {
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                    "The selected backup is damaged"
                }
            }
            val version = database.version
            check(version in MIN_RESTORABLE_VERSION..KartoffelDatabase.VERSION) {
                if (version > KartoffelDatabase.VERSION) {
                    "This backup was created by a newer Kartoffel version"
                } else {
                    "This is not a supported Kartoffel backup"
                }
            }
            database.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master " +
                    "WHERE type = 'table' AND name IN (${REQUIRED_TABLES.joinToString { "?" }})",
                REQUIRED_TABLES.toTypedArray(),
            ).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == REQUIRED_TABLES.size) {
                    "This is not a Kartoffel backup"
                }
            }
        }
    }

    private fun deleteJournalFiles() {
        File("${databaseFile.path}-wal").delete()
        File("${databaseFile.path}-shm").delete()
        File("${databaseFile.path}-journal").delete()
    }

    private fun String.sqlQuoted(): String = replace("'", "''")

    private companion object {
        const val MIN_RESTORABLE_VERSION = 2
        val REQUIRED_TABLES = listOf("coverage_cells", "location_samples")
    }
}
