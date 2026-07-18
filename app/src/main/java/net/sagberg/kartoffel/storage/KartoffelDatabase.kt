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

@Database(
    entities = [
        CoverageCellEntity::class,
        LocationSampleEntity::class,
        RecordingSessionEntity::class,
        RecordingSessionPointEntity::class,
        TrackingSettingsEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
internal abstract class KartoffelDatabase : RoomDatabase() {
    abstract fun coverageCells(): CoverageCellDao

    abstract fun locationSamples(): LocationSampleDao

    abstract fun recordingSessions(): RecordingSessionDao

    abstract fun recordingSessionPoints(): RecordingSessionPointDao

    abstract fun trackingSettings(): TrackingSettingsDao

    companion object {
        @Volatile
        private var instance: KartoffelDatabase? = null

        fun open(context: Context): KartoffelDatabase = instance ?: synchronized(this) {
            instance ?: Room
                .databaseBuilder(
                    context.applicationContext,
                    KartoffelDatabase::class.java,
                    "kartoffel.db",
                )
                .setDriver(AndroidSQLiteDriver())
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .build()
                .also { instance = it }
        }
    }
}
