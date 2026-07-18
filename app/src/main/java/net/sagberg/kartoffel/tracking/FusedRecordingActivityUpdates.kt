package net.sagberg.kartoffel.tracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class FusedRecordingActivityUpdates(
    context: Context,
    private val scope: CoroutineScope,
    private val onTransitionObserved: suspend (RecordingActivity, Long) -> Unit = { _, _ -> },
) : RecordingActivityUpdates {
    private val appContext = context.applicationContext
    private val client = ActivityRecognition.getClient(appContext)
    private val transitionPendingIntent = PendingIntent.getService(
        appContext,
        0,
        Intent(appContext, RecordingSessionService::class.java)
            .setAction(ACTION_ACTIVITY_TRANSITION),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    private val bootstrapPendingIntent = PendingIntent.getService(
        appContext,
        1,
        Intent(appContext, RecordingSessionService::class.java)
            .setAction(ACTION_ACTIVITY_BOOTSTRAP),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    private var listener: (suspend (RecordingActivityUpdate) -> Unit)? = null
    private var active = false
    private var bootstrapActive = false
    private var bootstrapTimeoutJob: Job? = null

    @SuppressLint("MissingPermission")
    override fun start(listener: suspend (RecordingActivityUpdate) -> Unit) {
        check(!active) { "Recording activity updates are already active" }
        this.listener = listener
        active = true
        try {
            client.requestActivityTransitionUpdates(TRANSITION_REQUEST, transitionPendingIntent)
            startBootstrap()
        } catch (failure: RuntimeException) {
            client.removeActivityTransitionUpdates(transitionPendingIntent)
            stopBootstrap()
            active = false
            this.listener = null
            throw failure
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopBootstrap() {
        if (!bootstrapActive) return
        bootstrapActive = false
        bootstrapTimeoutJob?.cancel()
        bootstrapTimeoutJob = null
        client.removeActivityUpdates(bootstrapPendingIntent)
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        if (!active) return
        client.removeActivityTransitionUpdates(transitionPendingIntent)
        stopBootstrap()
        active = false
        listener = null
    }

    fun handleActivityIntent(intent: Intent) {
        when (intent.action) {
            ACTION_ACTIVITY_TRANSITION -> handleTransitionIntent(intent)
            ACTION_ACTIVITY_BOOTSTRAP -> handleBootstrapIntent(intent)
        }
    }

    private fun handleTransitionIntent(intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        listener?.let { activeListener ->
            scope.launch {
                result.transitionEvents.forEach { event ->
                    event.activityType.toRecordingActivity()?.let { activity ->
                        onTransitionObserved(
                            activity,
                            event.elapsedRealTimeNanos / 1_000_000,
                        )
                        activeListener(RecordingActivityUpdate.Transition(activity))
                    }
                }
            }
        }
    }

    private fun handleBootstrapIntent(intent: Intent) {
        if (!bootstrapActive || !ActivityRecognitionResult.hasResult(intent)) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val sample = result.probableActivities
            .mapNotNull { detectedActivity ->
                detectedActivity.type.toRecordingActivity()?.let { activity ->
                    RecordingActivityUpdate.BootstrapSample(
                        activity = activity,
                        confidence = detectedActivity.confidence,
                    )
                }
            }
            .maxByOrNull(RecordingActivityUpdate.BootstrapSample::confidence)
            ?: return
        listener?.let { activeListener ->
            scope.launch { activeListener(sample) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBootstrap() {
        bootstrapActive = true
        client.requestActivityUpdates(BOOTSTRAP_INTERVAL_MILLIS, bootstrapPendingIntent)
            .addOnFailureListener { stopBootstrap() }
        bootstrapTimeoutJob = scope.launch {
            delay(BOOTSTRAP_TIMEOUT_MILLIS)
            stopBootstrap()
        }
    }

    companion object {
        internal const val ACTION_ACTIVITY_TRANSITION =
            "net.sagberg.kartoffel.action.RECORDING_ACTIVITY_TRANSITION"
        internal const val ACTION_ACTIVITY_BOOTSTRAP =
            "net.sagberg.kartoffel.action.RECORDING_ACTIVITY_BOOTSTRAP"
        internal const val BOOTSTRAP_INTERVAL_MILLIS = 1_000L
        internal const val BOOTSTRAP_TIMEOUT_MILLIS = 15_000L

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

internal fun Int.toRecordingActivity(): RecordingActivity? = when (this) {
    DetectedActivity.STILL -> RecordingActivity.STILL
    DetectedActivity.WALKING -> RecordingActivity.WALKING
    DetectedActivity.RUNNING -> RecordingActivity.RUNNING
    DetectedActivity.ON_BICYCLE -> RecordingActivity.ON_BICYCLE
    DetectedActivity.IN_VEHICLE -> RecordingActivity.IN_VEHICLE
    else -> null
}
