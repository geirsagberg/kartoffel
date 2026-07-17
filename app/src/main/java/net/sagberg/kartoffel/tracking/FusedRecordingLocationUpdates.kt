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
    private var intervalMillis: Long? = null
    private var requestVersion = 0

    @SuppressLint("MissingPermission")
    override fun start(
        intervalMillis: Long,
        listener: suspend (RecordingLocationFix) -> Unit,
    ) {
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
                                speedMetersPerSecond = if (location.hasSpeed()) {
                                    location.speed.toDouble()
                                } else {
                                    null
                                },
                            ),
                        )
                    }
                }
            }
        }
        callback = nextCallback
        this.intervalMillis = intervalMillis
        requestVersion += 1
        requestUpdates(nextCallback, intervalMillis)
    }

    @SuppressLint("MissingPermission")
    override fun updateInterval(intervalMillis: Long) {
        val activeCallback = checkNotNull(callback) { "Recording location updates are not active" }
        if (intervalMillis == this.intervalMillis) return
        this.intervalMillis = intervalMillis
        val version = ++requestVersion
        client.removeLocationUpdates(activeCallback).addOnCompleteListener {
            if (
                callback === activeCallback &&
                this.intervalMillis == intervalMillis &&
                requestVersion == version
            ) {
                requestUpdates(activeCallback, intervalMillis)
            }
        }
    }

    override fun stop() {
        callback?.let(client::removeLocationUpdates)
        callback = null
        intervalMillis = null
        requestVersion += 1
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(callback: LocationCallback, intervalMillis: Long) {
        client.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
                .setMinUpdateIntervalMillis(intervalMillis)
                .setMaxUpdateAgeMillis(0L)
                .build(),
            callback,
            looper,
        )
    }
}
