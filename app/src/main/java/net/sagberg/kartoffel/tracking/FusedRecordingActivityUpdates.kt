package net.sagberg.kartoffel.tracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class FusedRecordingActivityUpdates(
    context: Context,
    private val scope: CoroutineScope,
) : RecordingActivityUpdates {
    private val appContext = context.applicationContext
    private val client = ActivityRecognition.getClient(appContext)
    private val pendingIntent = PendingIntent.getService(
        appContext,
        0,
        Intent(appContext, RecordingSessionService::class.java)
            .setAction(ACTION_ACTIVITY_TRANSITION),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    private var listener: (suspend (RecordingActivity) -> Unit)? = null
    private var active = false

    @SuppressLint("MissingPermission")
    override fun start(listener: suspend (RecordingActivity) -> Unit) {
        check(!active) { "Recording activity updates are already active" }
        this.listener = listener
        active = true
        try {
            client.requestActivityTransitionUpdates(TRANSITION_REQUEST, pendingIntent)
        } catch (failure: RuntimeException) {
            active = false
            this.listener = null
            throw failure
        }
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        if (!active) return
        client.removeActivityTransitionUpdates(pendingIntent)
        active = false
        listener = null
    }

    fun handleTransitionIntent(intent: Intent) {
        if (intent.action != ACTION_ACTIVITY_TRANSITION) return
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        listener?.let { activeListener ->
            scope.launch {
                result.transitionEvents.forEach { event ->
                    event.activityType.toRecordingActivity()?.let { activity ->
                        activeListener(activity)
                    }
                }
            }
        }
    }

    companion object {
        internal const val ACTION_ACTIVITY_TRANSITION =
            "net.sagberg.kartoffel.action.RECORDING_ACTIVITY_TRANSITION"

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

private fun Int.toRecordingActivity(): RecordingActivity? = when (this) {
    DetectedActivity.STILL -> RecordingActivity.STILL
    DetectedActivity.WALKING -> RecordingActivity.WALKING
    DetectedActivity.RUNNING -> RecordingActivity.RUNNING
    DetectedActivity.ON_BICYCLE -> RecordingActivity.ON_BICYCLE
    DetectedActivity.IN_VEHICLE -> RecordingActivity.IN_VEHICLE
    else -> null
}
