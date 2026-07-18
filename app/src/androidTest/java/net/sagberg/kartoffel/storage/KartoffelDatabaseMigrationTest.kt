package net.sagberg.kartoffel.storage

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.execSQL
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KartoffelDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-2-3.db"

    @Before
    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationsPreserveFixesAndRelationshipsAndAddPassivePreference() = runBlocking {
        createVersionTwoDatabase()

        val database = Room.databaseBuilder(context, KartoffelDatabase::class.java, databaseName)
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val sample = database.locationSamples().find(11)
            assertEquals("unknown", sample?.activityMode)
            assertEquals(7L, sample?.recordingSessionId)
            assertNotNull(database.recordingSessions().find(7))
            assertEquals(11L, database.recordingSessionPoints().forSession(7).single().sampleId)
            assertEquals(false, PassiveTrackingPreferences(database.trackingSettings()).current().enabled)
        } finally {
            database.close()
        }
    }

    private fun createVersionTwoDatabase() {
        AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { db ->
            db.execSQL("PRAGMA user_version = 2")
            db.execSQL(
                "CREATE TABLE coverage_cells (cell_id INTEGER NOT NULL, " +
                    "first_seen_at_ms INTEGER NOT NULL, last_seen_at_ms INTEGER NOT NULL, " +
                    "evidence_mask INTEGER NOT NULL, PRIMARY KEY(cell_id))",
            )
            db.execSQL(
                "CREATE TABLE location_samples (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "captured_at_ms INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, " +
                    "accuracy_meters REAL NOT NULL, source TEXT NOT NULL, trigger TEXT, " +
                    "accepted INTEGER NOT NULL, rejection_reason TEXT, recording_session_id INTEGER)",
            )
            db.execSQL(
                "CREATE INDEX index_location_samples_captured_at_ms " +
                    "ON location_samples(captured_at_ms)",
            )
            db.execSQL(
                "CREATE TABLE recording_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "started_at_ms INTEGER NOT NULL, ended_at_ms INTEGER)",
            )
            db.execSQL(
                "CREATE TABLE recording_session_points (sample_id INTEGER NOT NULL, " +
                    "recording_session_id INTEGER NOT NULL, captured_at_ms INTEGER NOT NULL, " +
                    "cell_id INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, " +
                    "PRIMARY KEY(sample_id), FOREIGN KEY(recording_session_id) " +
                    "REFERENCES recording_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(sample_id) REFERENCES location_samples(id) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX index_recording_session_points_recording_session_id_captured_at_ms " +
                    "ON recording_session_points(recording_session_id, captured_at_ms)",
            )
            db.execSQL(
                "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            db.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) " +
                    "VALUES (42, '4c6ca98641490aa33d43233d857ade6c')",
            )
            db.execSQL(
                "INSERT INTO recording_sessions " +
                    "(id, started_at_ms, ended_at_ms) VALUES (7, 1000, 3000)",
            )
            db.execSQL(
                "INSERT INTO location_samples " +
                    "(id, captured_at_ms, latitude, longitude, accuracy_meters, source, " +
                    "trigger, accepted, rejection_reason, recording_session_id) " +
                    "VALUES (11, 2000, 59.91, 10.75, 8.0, 'recording_session', " +
                    "'active_session', 1, NULL, 7)",
            )
            db.execSQL(
                "INSERT INTO recording_session_points " +
                    "(sample_id, recording_session_id, captured_at_ms, cell_id, latitude, longitude) " +
                    "VALUES (11, 7, 2000, 123, 59.91, 10.75)",
            )
        }
    }
}
