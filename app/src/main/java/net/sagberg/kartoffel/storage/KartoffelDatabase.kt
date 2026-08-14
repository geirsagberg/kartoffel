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
    version = 7,
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

    companion object {
        internal const val NAME = "kartoffel.db"
        internal const val VERSION = 7

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
