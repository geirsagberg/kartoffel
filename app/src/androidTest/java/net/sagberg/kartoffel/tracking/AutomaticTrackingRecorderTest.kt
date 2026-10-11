package net.sagberg.kartoffel.tracking

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.RoomCoverageSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticTrackingRecorderTest {
    private lateinit var database: KartoffelDatabase
    private lateinit var recorder: AutomaticTrackingRecorder

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KartoffelDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        recorder = AutomaticTrackingRecorder(database)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun automaticFixRetainsTriggerModeAndDecision() = runBlocking {
        recorder.record(
            fix = RecordingLocationFix(
                coordinate = GeoCoordinate(59.9109, 10.7522),
                capturedAtMillis = 2_000,
                accuracyMeters = 8.0,
            ),
            trigger = AutomaticFixTrigger.OPPORTUNISTIC,
            activity = RecordingActivity.WALKING,
        )
        recorder.record(
            fix = RecordingLocationFix(
                coordinate = GeoCoordinate(59.91, 10.75),
                capturedAtMillis = 3_000,
                accuracyMeters = 26.0,
            ),
            trigger = AutomaticFixTrigger.FALLBACK_WINDOW,
            activity = RecordingActivity.UNKNOWN,
        )

        val samples = database.locationSamples().between(0, 4_000)
        assertEquals(listOf(true, false), samples.map { it.accepted })
        assertEquals(
            listOf("opportunistic", "fallback_window"),
            samples.map { it.trigger },
        )
        assertEquals(listOf("walking", "unknown"), samples.map { it.activityMode })
        assertEquals(AUTOMATIC_ACCURACY_REJECTION, samples.last().rejectionReason)
        assertEquals(1, database.coverageCells().all().size)
    }

    @Test
    fun changedAccuracySettingAppliesToTheNextAutomaticFix() = runBlocking {
        val settings = RoomCoverageSettings(database.coverageSettings())
        val firstFix = RecordingLocationFix(
            coordinate = GeoCoordinate(59.9109, 10.7522),
            capturedAtMillis = 2_000,
            accuracyMeters = 21.0,
        )

        settings.setMaximumAcceptedAccuracyMeters(20)
        val rejected = recorder.record(
            firstFix,
            AutomaticFixTrigger.FALLBACK_WINDOW,
            RecordingActivity.UNKNOWN,
        )
        settings.setMaximumAcceptedAccuracyMeters(25)
        val accepted = recorder.record(
            firstFix.copy(capturedAtMillis = 3_000),
            AutomaticFixTrigger.FALLBACK_WINDOW,
            RecordingActivity.UNKNOWN,
        )

        assertEquals(false, rejected.accepted)
        assertEquals(true, accepted.accepted)
    }

    @Test
    fun changedInterpolationSettingAppliesToTheNextAutomaticFix() = runBlocking {
        val settings = RoomCoverageSettings(database.coverageSettings())
        settings.setMaximumInterpolationDistanceSteps(1)
        recorder.record(
            RecordingLocationFix(
                GeoCoordinate(59.9109, 10.7522),
                capturedAtMillis = 2_000,
                accuracyMeters = 8.0,
            ),
            AutomaticFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )
        recorder.record(
            RecordingLocationFix(
                GeoCoordinate(59.910527, 10.751046),
                capturedAtMillis = 62_000,
                accuracyMeters = 8.0,
            ),
            AutomaticFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )
        assertEquals(2, database.coverageCells().all().size)

        settings.setMaximumInterpolationDistanceSteps(3)
        recorder.record(
            RecordingLocationFix(
                GeoCoordinate(59.9109, 10.7522),
                capturedAtMillis = 63_000,
                accuracyMeters = 8.0,
            ),
            AutomaticFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )

        assertEquals(3, database.coverageCells().all().size)
    }

    @Test
    fun oldOrRejectedAutomaticEvidenceDoesNotBridgeAGap() = runBlocking {
        val start = GeoCoordinate(59.9109, 10.7522)
        val destination = GeoCoordinate(59.910527, 10.751046)
        recorder.record(
            RecordingLocationFix(start, 2_000, 8.0),
            AutomaticFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )
        recorder.record(
            RecordingLocationFix(destination, 3_000, 30.0),
            AutomaticFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )
        recorder.record(
            RecordingLocationFix(destination, 200_001, 8.0),
            AutomaticFixTrigger.OPPORTUNISTIC,
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
            AutomaticFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )
        recorder.record(
            RecordingLocationFix(destination, 3_000, 8.0),
            AutomaticFixTrigger.OPPORTUNISTIC,
            RecordingActivity.UNKNOWN,
        )
        assertEquals(2, database.coverageCells().all().size)

        database.clearAllTables()

        recorder.record(
            RecordingLocationFix(start, 4_000, 8.0),
            AutomaticFixTrigger.OPPORTUNISTIC,
            RecordingActivity.UNKNOWN,
        )
        recorder.record(
            RecordingLocationFix(destination, 5_000, 8.0),
            AutomaticFixTrigger.MOVEMENT_WINDOW,
            RecordingActivity.WALKING,
        )

        assertEquals(2, database.coverageCells().all().size)
    }
}
