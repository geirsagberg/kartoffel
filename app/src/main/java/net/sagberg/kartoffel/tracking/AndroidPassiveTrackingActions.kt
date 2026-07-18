package net.sagberg.kartoffel.tracking

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

internal class AndroidPassiveTrackingActions(
    context: Context,
) : PassiveTrackingActions {
    private val appContext = context.applicationContext
    private val locationClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val activityClient = ActivityRecognition.getClient(appContext)
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    @SuppressLint("MissingPermission")
    override fun registerActivityTransitions() {
        runCatching {
            activityClient.requestActivityTransitionUpdates(
                TRANSITION_REQUEST,
                activityPendingIntent(),
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun unregisterActivityTransitions() {
        runCatching { activityClient.removeActivityTransitionUpdates(activityPendingIntent()) }
    }

    @SuppressLint("MissingPermission")
    override fun registerOpportunisticFixes() {
        locationClient.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_PASSIVE, 10_000L)
                .setMinUpdateIntervalMillis(10_000L)
                .setMaxUpdateAgeMillis(0L)
                .build(),
            opportunisticPendingIntent(),
        )
    }

    override fun unregisterOpportunisticFixes() {
        locationClient.removeLocationUpdates(opportunisticPendingIntent())
    }

    @SuppressLint("MissingPermission")
    override fun startCapture(request: PassiveCaptureRequest) {
        val pendingIntent = capturePendingIntent(request.trigger)
        locationClient.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, request.intervalMillis)
                .setMinUpdateIntervalMillis(request.intervalMillis)
                .setMaxUpdateAgeMillis(0L)
                .setWaitForAccurateLocation(true)
                .setDurationMillis(request.durationMillis)
                .setMaxUpdates(request.maximumFixes)
                .build(),
            pendingIntent,
        )
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + request.durationMillis,
            captureEndedPendingIntent(),
        )
    }

    override fun stopCapture() {
        locationClient.removeLocationUpdates(capturePendingIntent(PassiveFixTrigger.MOVEMENT_WINDOW))
        alarmManager.cancel(captureEndedPendingIntent())
    }

    override fun scheduleFallback(delayMillis: Long) {
        alarmManager.setWindow(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMillis,
            FALLBACK_WINDOW_MILLIS,
            fallbackPendingIntent(),
        )
    }

    override fun cancelFallback() {
        alarmManager.cancel(fallbackPendingIntent())
    }

    private fun activityPendingIntent(): PendingIntent = broadcastPendingIntent(
        requestCode = REQUEST_ACTIVITY,
        action = PassiveTrackingReceiver.ACTION_ACTIVITY,
    )

    private fun opportunisticPendingIntent(): PendingIntent = broadcastPendingIntent(
        requestCode = REQUEST_OPPORTUNISTIC,
        action = PassiveTrackingReceiver.ACTION_OPPORTUNISTIC_FIX,
    )

    private fun capturePendingIntent(trigger: PassiveFixTrigger): PendingIntent =
        broadcastPendingIntent(
            requestCode = REQUEST_CAPTURE,
            action = PassiveTrackingReceiver.ACTION_CAPTURE_FIX,
            configure = { putExtra(PassiveTrackingReceiver.EXTRA_TRIGGER, trigger.name) },
        )

    private fun captureEndedPendingIntent(): PendingIntent = broadcastPendingIntent(
        requestCode = REQUEST_CAPTURE_ENDED,
        action = PassiveTrackingReceiver.ACTION_CAPTURE_ENDED,
    )

    private fun fallbackPendingIntent(): PendingIntent = broadcastPendingIntent(
        requestCode = REQUEST_FALLBACK,
        action = PassiveTrackingReceiver.ACTION_FALLBACK,
    )

    private fun broadcastPendingIntent(
        requestCode: Int,
        action: String,
        configure: Intent.() -> Unit = {},
    ): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        requestCode,
        Intent(appContext, PassiveTrackingReceiver::class.java).setAction(action).apply(configure),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val REQUEST_ACTIVITY = 20
        private const val REQUEST_OPPORTUNISTIC = 21
        private const val REQUEST_CAPTURE = 22
        private const val REQUEST_CAPTURE_ENDED = 23
        private const val REQUEST_FALLBACK = 24
        private const val FALLBACK_WINDOW_MILLIS = 15 * 60 * 1_000L

        private val TRANSITION_REQUEST = ActivityTransitionRequest(
            listOf(
                DetectedActivity.STILL,
                DetectedActivity.WALKING,
                DetectedActivity.RUNNING,
                DetectedActivity.ON_BICYCLE,
                DetectedActivity.IN_VEHICLE,
            ).map { activityType ->
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            },
        )
    }
}
