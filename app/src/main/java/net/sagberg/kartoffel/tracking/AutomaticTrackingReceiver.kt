package net.sagberg.kartoffel.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.sagberg.kartoffel.coverage.GeoCoordinate

internal data class AutomaticActivityObservation(
    val activity: RecordingActivity,
    val observedAtElapsedRealtimeMillis: Long,
)

internal data class AutomaticLocationDelivery(
    val fix: RecordingLocationFix,
    val capturedAtElapsedRealtimeMillis: Long,
)

internal class AutomaticTrackingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    ACTION_RESTORE,
                    -> AutomaticTrackingManager.restore(context)
                    ACTION_ACTIVITY -> handleActivity(context, intent)
                    ACTION_OPPORTUNISTIC_FIX -> handleLocations(
                        context,
                        intent,
                        AutomaticFixTrigger.OPPORTUNISTIC,
                    )
                    ACTION_CAPTURE_FIX -> handleLocations(
                        context,
                        intent,
                        intent.getStringExtra(EXTRA_TRIGGER)
                            ?.let(AutomaticFixTrigger::valueOf)
                            ?: AutomaticFixTrigger.FALLBACK_WINDOW,
                    )
                    ACTION_CAPTURE_ENDED -> AutomaticTrackingManager.onCaptureEnded(context)
                    ACTION_FALLBACK -> AutomaticTrackingManager.onFallback(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleActivity(context: Context, intent: Intent) {
        intent.automaticActivityObservations().forEach { observation ->
            AutomaticTrackingManager.onActivity(
                context = context,
                activity = observation.activity,
                observedAtElapsedRealtimeMillis = observation.observedAtElapsedRealtimeMillis,
            )
        }
    }

    private suspend fun handleLocations(
        context: Context,
        intent: Intent,
        trigger: AutomaticFixTrigger,
    ) {
        for (delivery in intent.automaticLocationDeliveries()) {
            val decision = AutomaticTrackingManager.onFix(
                context = context,
                fix = delivery.fix,
                capturedAtElapsedRealtimeMillis = delivery.capturedAtElapsedRealtimeMillis,
                trigger = trigger,
            )
            if (trigger == AutomaticFixTrigger.INITIAL_ENABLE && decision?.accepted == true) break
        }
    }

    companion object {
        // PendingIntents registered with Android keep these values across app updates.
        internal const val ACTION_ACTIVITY = "net.sagberg.kartoffel.action.PASSIVE_ACTIVITY"
        internal const val ACTION_OPPORTUNISTIC_FIX =
            "net.sagberg.kartoffel.action.PASSIVE_OPPORTUNISTIC_FIX"
        internal const val ACTION_CAPTURE_FIX =
            "net.sagberg.kartoffel.action.PASSIVE_CAPTURE_FIX"
        internal const val ACTION_CAPTURE_ENDED =
            "net.sagberg.kartoffel.action.PASSIVE_CAPTURE_ENDED"
        internal const val ACTION_FALLBACK = "net.sagberg.kartoffel.action.PASSIVE_FALLBACK"
        internal const val ACTION_RESTORE = "net.sagberg.kartoffel.action.PASSIVE_RESTORE"
        internal const val EXTRA_TRIGGER = "passive_trigger"
    }
}

internal fun Intent.automaticActivityObservations(): List<AutomaticActivityObservation> =
    ActivityTransitionResult.extractResult(this)?.transitionEvents.orEmpty().mapNotNull { event ->
        event.activityType.toRecordingActivity()?.let { activity ->
            AutomaticActivityObservation(
                activity = activity,
                observedAtElapsedRealtimeMillis = event.elapsedRealTimeNanos / 1_000_000,
            )
        }
    }

internal fun Intent.automaticLocationDeliveries(): List<AutomaticLocationDelivery> =
    LocationResult.extractResult(this)?.locations.orEmpty()
        .sortedBy { it.elapsedRealtimeNanos }
        .map { location ->
            AutomaticLocationDelivery(
                fix = RecordingLocationFix(
                    coordinate = GeoCoordinate(location.latitude, location.longitude),
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
                capturedAtElapsedRealtimeMillis = location.elapsedRealtimeNanos / 1_000_000,
            )
        }
