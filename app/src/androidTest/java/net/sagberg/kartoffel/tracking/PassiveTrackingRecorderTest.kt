package net.sagberg.kartoffel.tracking

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.storage.KartoffelDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PassiveTrackingRecorderTest {
    private lateinit var database: KartoffelDatabase
    private lateinit var recorder: PassiveTrackingRecorder

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KartoffelDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        recorder = PassiveTrackingRecorder(database)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun passiveFixRetainsTriggerModeAndDecision() = runBlocking {
        recorder.record(
            fix = RecordingLocationFix(
                coordinate = GeoCoordinate(59.9109, 10.7522),
                capturedAtMillis = 2_000,
                accuracyMeters = 8.0,
            ),
            trigger = PassiveFixTrigger.OPPORTUNISTIC,
            activity = RecordingActivity.WALKING,
        )
        recorder.record(
            fix = RecordingLocationFix(
                coordinate = GeoCoordinate(59.91, 10.75),
                capturedAtMillis = 3_000,
                accuracyMeters = 21.0,
            ),
            trigger = PassiveFixTrigger.FALLBACK_WINDOW,
            activity = RecordingActivity.UNKNOWN,
        )

        val samples = database.locationSamples().between(0, 4_000)
        assertEquals(listOf(true, false), samples.map { it.accepted })
        assertEquals(
            listOf("opportunistic", "fallback_window"),
            samples.map { it.trigger },
        )
        assertEquals(listOf("walking", "unknown"), samples.map { it.activityMode })
        assertEquals(PASSIVE_ACCURACY_REJECTION, samples.last().rejectionReason)
        assertEquals(1, database.coverageCells().all().size)
    }

    @Test
    fun nearbyConsecutiveAcceptedPassiveFixesUseShortGapInterpolation() = runBlocking {
        recorder.record(
            RecordingLocationFix(
                GeoCoordinate(59.9109, 10.7522),
                capturedAtMillis = 2_000,
                accuracyMeters = 8.0,
            ),
            PassiveFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )
        recorder.record(
            RecordingLocationFix(
                GeoCoordinate(59.910527, 10.751046),
                capturedAtMillis = 62_000,
                accuracyMeters = 8.0,
            ),
            PassiveFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )

        assertEquals(
            setOf(
                626169207098265599,
                626169207099809791,
                626169207099793407,
                626169207098388479,
            ),
            database.coverageCells().all().map { it.cellId }.toSet(),
        )
    }

    @Test
    fun oldOrRejectedPassiveEvidenceDoesNotBridgeAGap() = runBlocking {
        val start = GeoCoordinate(59.9109, 10.7522)
        val destination = GeoCoordinate(59.910527, 10.751046)
        recorder.record(
            RecordingLocationFix(start, 2_000, 8.0),
            PassiveFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )
        recorder.record(
            RecordingLocationFix(destination, 3_000, 30.0),
            PassiveFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )
        recorder.record(
            RecordingLocationFix(destination, 200_001, 8.0),
            PassiveFixTrigger.OPPORTUNISTIC,
            RecordingActivity.UNKNOWN,
        )

        assertEquals(2, database.coverageCells().all().size)
    }

    @Test
    fun opportunisticFixCannotBeEitherEndpointOfInterpolation() = runBlocking {
        val start = GeoCoordinate(59.9109, 10.7522)
        val destination = GeoCoordinate(59.910527, 10.751046)
        recorder.record(
            RecordingLocationFix(start, 2_000, 8.0),
            PassiveFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )
        recorder.record(
            RecordingLocationFix(destination, 3_000, 8.0),
            PassiveFixTrigger.OPPORTUNISTIC,
            RecordingActivity.UNKNOWN,
        )
        assertEquals(2, database.coverageCells().all().size)

        database.clearAllTables()

        recorder.record(
            RecordingLocationFix(start, 4_000, 8.0),
            PassiveFixTrigger.OPPORTUNISTIC,
            RecordingActivity.UNKNOWN,
        )
        recorder.record(
            RecordingLocationFix(destination, 5_000, 8.0),
            PassiveFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )

        assertEquals(2, database.coverageCells().all().size)
    }
}
