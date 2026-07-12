package net.sagberg.kartoffel.storage

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [CoverageCellEntity::class, LocationSampleEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class KartoffelDatabase : RoomDatabase() {
    abstract fun coverageCells(): CoverageCellDao

    abstract fun locationSamples(): LocationSampleDao

    companion object {
        fun open(context: Context): KartoffelDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                KartoffelDatabase::class.java,
                "kartoffel.db",
            )
                .setDriver(AndroidSQLiteDriver())
                .build()
    }
}
