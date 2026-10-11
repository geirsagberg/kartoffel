package net.sagberg.kartoffel.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal enum class StorageByteMeasurement {
    /** Whole SQLite pages assigned to the table and its indexes, including unused space. */
    AllocatedPages,
    /** Modelled record sizes and page packing; see docs/storage-usage.md. */
    EstimatedPages,
}

internal data class TableStorageUsage(
    val rowCount: Long,
    val bytes: Long,
    val measurement: StorageByteMeasurement,
)

/** File lengths are separate observations, not an atomic total or filesystem block usage. */
internal data class StorageUsageSnapshot(
    val locationSamples: TableStorageUsage,
    val coverageCells: TableStorageUsage,
    val databaseFileBytes: Long,
    val walFileBytes: Long,
    val sharedMemoryFileBytes: Long,
    val rollbackJournalFileBytes: Long,
)

/** Reads an existing database without opening Room, creating a database, or running migrations. */
internal class StorageUsageLoader(private val databaseFile: File) {
    constructor(context: Context) : this(
        context.applicationContext.getDatabasePath(KartoffelDatabase.NAME),
    )

    suspend fun load(): StorageUsageSnapshot = withContext(Dispatchers.IO) {
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            StorageUsageSnapshot(
                locationSamples = database.storageUsage("location_samples"),
                coverageCells = database.storageUsage("coverage_cells"),
                databaseFileBytes = databaseFile.length(),
                walFileBytes = File("${databaseFile.path}-wal").length(),
                sharedMemoryFileBytes = File("${databaseFile.path}-shm").length(),
                rollbackJournalFileBytes = File("${databaseFile.path}-journal").length(),
            )
        }
    }
}

internal fun SQLiteDatabase.storageUsage(table: String): TableStorageUsage {
    require(table == "location_samples" || table == "coverage_cells")
    val rowCount = scalar("SELECT COUNT(*) FROM $table")
    val allocatedBytes = try {
        scalar(
            "SELECT COALESCE(SUM(pgsize), 0) FROM dbstat WHERE name IN " +
                "(SELECT name FROM sqlite_schema WHERE tbl_name = '$table' " +
                "AND type IN ('table', 'index'))",
        )
    } catch (failure: SQLiteException) {
        // Do not hide corrupt databases, query errors, or other measurement failures.
        if (!failure.message.orEmpty().contains("no such table: dbstat")) throw failure
        null
    }
    return TableStorageUsage(
        rowCount = rowCount,
        bytes = allocatedBytes ?: estimateStorageBytes(table),
        measurement = if (allocatedBytes != null) {
            StorageByteMeasurement.AllocatedPages
        } else {
            StorageByteMeasurement.EstimatedPages
        },
    )
}

internal fun SQLiteDatabase.estimateStorageBytes(table: String): Long {
    require(table == "location_samples" || table == "coverage_cells")
    val pageSize = scalar("PRAGMA page_size")
    // Budget 12 bytes per cell and two header bytes per value. Model 75% page occupancy.
    fun estimateTree(columns: List<String>): Long {
        val values = columns.joinToString(" + ") { column ->
            val quoted = "\"${column.replace("\"", "\"\"")}\""
            "CASE typeof($quoted) WHEN 'null' THEN 0 " +
                "WHEN 'integer' THEN 8 WHEN 'real' THEN 8 " +
                "ELSE length(CAST($quoted AS BLOB)) END"
        }
        val budget = scalar(
            "SELECT COALESCE(SUM(12 + ${columns.size * 2} + $values), 0) FROM $table",
        )
        val usableBytes = (pageSize - 12) * 3 / 4
        return maxOf(1L, (budget + usableBytes - 1) / usableBytes) * pageSize
    }

    val columns = rawQuery("PRAGMA table_info('$table')", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
    }
    val indexes = rawQuery("PRAGMA index_list('$table')", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
    }
    return estimateTree(columns) + indexes.sumOf { index ->
        val indexedColumns = rawQuery(
            "PRAGMA index_info('${index.replace("'", "''")}')",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    check(!cursor.isNull(2)) { "Cannot estimate an expression index" }
                    add(cursor.getString(2))
                }
                add("rowid")
            }
        }
        estimateTree(indexedColumns)
    }
}

private fun SQLiteDatabase.scalar(sql: String): Long = rawQuery(sql, null).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getLong(0)
}
