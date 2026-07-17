package net.sagberg.kartoffel.tracking

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.sagberg.kartoffel.coverage.GeoCoordinate

internal class FusedRecordingLocationUpdates(
    context: Context,
    private val scope: CoroutineScope,
) : RecordingLocationUpdates {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val looper = context.mainLooper
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start(listener: suspend (RecordingLocationFix) -> Unit) {
        check(callback == null) { "Recording location updates are already active" }
        val nextCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    scope.launch {
                        listener(
                            RecordingLocationFix(
                                coordinate = GeoCoordinate(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                ),
                                capturedAtMillis = location.time,
                                accuracyMeters = if (location.hasAccuracy()) {
                                    location.accuracy.toDouble()
                                } else {
                                    Double.MAX_VALUE
                                },
                            ),
                        )
                    }
                }
            }
        }
        callback = nextCallback
        client.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(500L)
                .setMaxUpdateAgeMillis(0L)
                .build(),
            nextCallback,
            looper,
        )
    }

    override fun stop() {
        callback?.let(client::removeLocationUpdates)
        callback = null
    }
}
