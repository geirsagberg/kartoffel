package net.sagberg.kartoffel.diagnostics

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.LocationSampleEntity
import net.sagberg.kartoffel.storage.PersistedActivityMode
import net.sagberg.kartoffel.storage.RecordingSessionEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingSessionHistoryLoaderTest {
    private lateinit var database: KartoffelDatabase
    private lateinit var loader: RecordingSessionHistoryLoader

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KartoffelDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        loader = RecordingSessionHistoryLoader(database)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun noSessionIsAnEmptyState() = runBlocking {
        assertNull(loader.load(nowMillis = 10_000))
    }

    @Test
    fun activeSessionIsPreferredAndCountsEveryFixByMode() = runBlocking {
        val completedId = database.recordingSessions().insert(
            RecordingSessionEntity(startedAtMillis = 1_000, endedAtMillis = 2_000),
        )
        val activeId = database.recordingSessions().insert(
            RecordingSessionEntity(startedAtMillis = 5_000),
        )
        insertFix(completedId, "running", 1_500)
        insertFix(activeId, "walking", 6_000)
        insertFix(activeId, "walking", 7_000)
        insertFix(activeId, "future_mode", 8_000)

        assertEquals(
            RecordingSessionHistory(
                isActive = true,
                durationMillis = 5_000,
                fixCounts = mapOf(
                    PersistedActivityMode.WALKING to 2,
                    PersistedActivityMode.UNKNOWN to 1,
                ),
            ),
            loader.load(nowMillis = 10_000),
        )
    }

    @Test
    fun latestCompletedSessionIsUsedWhenNoneIsActive() = runBlocking {
        val olderId = database.recordingSessions().insert(
            RecordingSessionEntity(startedAtMillis = 1_000, endedAtMillis = 3_000),
        )
        val latestId = database.recordingSessions().insert(
            RecordingSessionEntity(startedAtMillis = 4_000, endedAtMillis = 9_000),
        )
        insertFix(olderId, "still", 2_000)
        insertFix(latestId, "cycling", 5_000)

        assertEquals(
            mapOf(PersistedActivityMode.CYCLING to 1),
            loader.load(nowMillis = 20_000)?.fixCounts,
        )
        assertEquals(5_000L, loader.load(nowMillis = 20_000)?.durationMillis)
    }

    private suspend fun insertFix(sessionId: Long, mode: String, capturedAtMillis: Long) {
        database.locationSamples().insert(
            LocationSampleEntity(
                capturedAtMillis = capturedAtMillis,
                latitude = 0.0,
                longitude = 0.0,
                accuracyMeters = 8.0,
                source = "recording_session",
                trigger = "active_session",
                accepted = true,
                rejectionReason = null,
                recordingSessionId = sessionId,
                activityMode = mode,
            ),
        )
    }
}
