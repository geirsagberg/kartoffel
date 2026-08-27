package net.sagberg.kartoffel.storage

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.execSQL
import androidx.sqlite.driver.AndroidSQLiteDriver

internal val MIGRATION_2_3 = Migration(2, 3) { connection ->
    connection.execSQL(
        "ALTER TABLE location_samples " +
            "ADD COLUMN activity_mode TEXT NOT NULL DEFAULT 'unknown'",
    )
}

internal val MIGRATION_3_4 = Migration(3, 4) { connection ->
    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS tracking_settings (
            id INTEGER NOT NULL,
            passive_enabled INTEGER NOT NULL,
            passive_period_started_at_ms INTEGER,
            PRIMARY KEY(id)
        )
        """.trimIndent(),
    )
}

internal val MIGRATION_4_5 = Migration(4, 5) { connection ->
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS index_location_samples_source_captured_at_ms " +
            "ON location_samples(source, captured_at_ms)",
    )
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS " +
            "index_location_samples_recording_session_id_captured_at_ms " +
            "ON location_samples(recording_session_id, captured_at_ms)",
    )
}

internal val MIGRATION_5_6 = Migration(5, 6) { connection ->
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS manual_route_claims (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, created_at_ms INTEGER NOT NULL)",
    )
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS manual_route_claim_cells (" +
            "claim_id INTEGER NOT NULL, cell_id INTEGER NOT NULL, " +
            "PRIMARY KEY(claim_id, cell_id), FOREIGN KEY(claim_id) " +
            "REFERENCES manual_route_claims(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
    )
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS index_manual_route_claim_cells_claim_id " +
            "ON manual_route_claim_cells(claim_id)",
    )
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS index_manual_route_claim_cells_cell_id " +
            "ON manual_route_claim_cells(cell_id)",
    )
}

internal val MIGRATION_6_7 = Migration(6, 7) { connection ->
    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS coverage_settings (
            id INTEGER NOT NULL,
            maximum_accepted_accuracy_meters INTEGER NOT NULL,
            maximum_interpolation_gap_steps INTEGER NOT NULL,
            PRIMARY KEY(id)
        )
        """.trimIndent(),
    )
    connection.execSQL(
        "INSERT INTO coverage_settings " +
            "(id, maximum_accepted_accuracy_meters, maximum_interpolation_gap_steps) " +
            "VALUES (1, 25, 3)",
    )
}

/**
 * Rolls resolution-11 coverage up to its resolution-10 parents. H3 stores the resolution in
 * bits 52-55 and each child digit in three bits, so this can run entirely in SQLite without
 * loading the native H3 library during database startup.
 */
internal val MIGRATION_7_8 = Migration(7, 8) { connection ->
    val resolutionMask = 15L shl 52
    val resolutionTen = 10L shl 52
    val unusedChildDigit = 7L shl 12
    val parentExpression =
        "((cell_id & ${resolutionMask.inv()}) | $resolutionTen | $unusedChildDigit)"

    connection.execSQL(
        """
        CREATE TABLE coverage_cells_res10 (
            cell_id INTEGER NOT NULL,
            first_seen_at_ms INTEGER NOT NULL,
            last_seen_at_ms INTEGER NOT NULL,
            evidence_mask INTEGER NOT NULL,
            PRIMARY KEY(cell_id)
        )
        """.trimIndent(),
    )
    connection.execSQL(
        """
        INSERT INTO coverage_cells_res10
            (cell_id, first_seen_at_ms, last_seen_at_ms, evidence_mask)
        SELECT $parentExpression, MIN(first_seen_at_ms), MAX(last_seen_at_ms),
            MAX(evidence_mask & 1) | MAX(evidence_mask & 2) |
            MAX(evidence_mask & 4) | MAX(evidence_mask & 8)
        FROM coverage_cells
        GROUP BY $parentExpression
        """.trimIndent(),
    )
    connection.execSQL("DROP TABLE coverage_cells")
    connection.execSQL("ALTER TABLE coverage_cells_res10 RENAME TO coverage_cells")

    connection.execSQL(
        "UPDATE recording_session_points SET cell_id = $parentExpression",
    )

    connection.execSQL(
        """
        CREATE TABLE manual_route_claim_cells_res10 (
            claim_id INTEGER NOT NULL,
            cell_id INTEGER NOT NULL,
            PRIMARY KEY(claim_id, cell_id),
            FOREIGN KEY(claim_id) REFERENCES manual_route_claims(id)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    connection.execSQL(
        """
        INSERT OR IGNORE INTO manual_route_claim_cells_res10 (claim_id, cell_id)
        SELECT claim_id, $parentExpression FROM manual_route_claim_cells
        """.trimIndent(),
    )
    connection.execSQL("DROP TABLE manual_route_claim_cells")
    connection.execSQL(
        "ALTER TABLE manual_route_claim_cells_res10 RENAME TO manual_route_claim_cells",
    )
    connection.execSQL(
        "CREATE INDEX index_manual_route_claim_cells_claim_id " +
            "ON manual_route_claim_cells(claim_id)",
    )
    connection.execSQL(
        "CREATE INDEX index_manual_route_claim_cells_cell_id " +
            "ON manual_route_claim_cells(cell_id)",
    )
}

@Database(
    entities = [
        CoverageCellEntity::class,
        LocationSampleEntity::class,
        RecordingSessionEntity::class,
        RecordingSessionPointEntity::class,
        TrackingSettingsEntity::class,
        ManualRouteClaimEntity::class,
        ManualRouteClaimCellEntity::class,
        CoverageSettingsEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
internal abstract class KartoffelDatabase : RoomDatabase() {
    abstract fun coverageCells(): CoverageCellDao

    abstract fun locationSamples(): LocationSampleDao

    abstract fun recordingSessions(): RecordingSessionDao

    abstract fun recordingSessionPoints(): RecordingSessionPointDao

    abstract fun trackingSettings(): TrackingSettingsDao

    abstract fun manualRouteClaims(): ManualRouteClaimDao

    abstract fun coverageSettings(): CoverageSettingsDao

    val coverageSettingsRepository: RoomCoverageSettings by lazy {
        RoomCoverageSettings(coverageSettings())
    }

    companion object {
        internal const val NAME = "kartoffel.db"
        internal const val VERSION = 8

        @Volatile
        private var instance: KartoffelDatabase? = null

        fun open(context: Context): KartoffelDatabase = instance ?: synchronized(this) {
            instance ?: Room
                .databaseBuilder(
                    context.applicationContext,
                    KartoffelDatabase::class.java,
                    NAME,
                )
                .setDriver(AndroidSQLiteDriver())
                .addMigrations(
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                )
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .build()
                .also { instance = it }
        }

        @Synchronized
        fun closeForRestore() {
            instance?.close()
            instance = null
        }
    }
}
