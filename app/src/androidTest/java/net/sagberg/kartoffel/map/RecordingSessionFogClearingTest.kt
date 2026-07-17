package net.sagberg.kartoffel.map

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.PersistedCoverageLoader
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.tracking.RecordingLocationFix
import net.sagberg.kartoffel.tracking.RecordingSessionRecorder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingSessionFogClearingTest {
    private lateinit var database: KartoffelDatabase

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KartoffelDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun acceptedRecordingSessionFixClearsItsPersistedFogPixel() = runBlocking {
        val coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522)
        val recorder = RecordingSessionRecorder(database)
        val sessionId = recorder.start(startedAtMillis = 500)
        recorder.record(
            sessionId,
            RecordingLocationFix(
                coordinate = coordinate,
                capturedAtMillis = 1_000,
                accuracyMeters = 8.0,
            ),
        )
        val snapshot = PersistedCoverageLoader(database.coverageCells()).load(revision = 1)
        val tile = fogTileForCoordinate(coordinate, zoom = 20)
        val renderedTile = FogOfWarTileProvider(snapshot).getTile(tile.x, tile.y, tile.zoom)
            ?: error("Expected Fog of War provider to return a tile")
        val data = renderedTile.data ?: error("Expected PNG tile bytes")
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        val clearedPixel = tile.pixelForCoordinate(coordinate)

        assertEquals(
            0,
            Color.alpha(
                bitmap.getPixel(
                    clearedPixel.x.roundToInt().coerceIn(0, FOG_TILE_SIZE - 1),
                    clearedPixel.y.roundToInt().coerceIn(0, FOG_TILE_SIZE - 1),
                ),
            ),
        )
    }
}
