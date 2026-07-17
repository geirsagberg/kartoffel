package net.sagberg.kartoffel.storage

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [
        CoverageCellEntity::class,
        LocationSampleEntity::class,
        RecordingSessionEntity::class,
        RecordingSessionPointEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
internal abstract class KartoffelDatabase : RoomDatabase() {
    abstract fun coverageCells(): CoverageCellDao

    abstract fun locationSamples(): LocationSampleDao

    abstract fun recordingSessions(): RecordingSessionDao

    abstract fun recordingSessionPoints(): RecordingSessionPointDao

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
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .build()
                .also { instance = it }
        }
    }
}
