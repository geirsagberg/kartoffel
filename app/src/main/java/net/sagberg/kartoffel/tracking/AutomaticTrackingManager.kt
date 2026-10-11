package net.sagberg.kartoffel.tracking

import android.content.Context
import android.os.SystemClock
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnostics
import net.sagberg.kartoffel.storage.KartoffelDatabase

internal object AutomaticTrackingManager {
    private val activityState = AutomaticActivityState()
    private val captureState = AutomaticCaptureState()

    suspend fun enable(context: Context): Boolean {
        if (!context.hasRequiredAutomaticTrackingPermissions()) return false
        orchestrator(context).enable(
            wallTimeMillis = System.currentTimeMillis(),
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
        return true
    }

    suspend fun disable(context: Context) {
        orchestrator(context).disable()
    }

    fun prepareForDatabaseRestore(context: Context) {
        AndroidAutomaticTrackingActions(context).apply {
            unregisterActivityTransitions()
            unregisterOpportunisticFixes()
            stopCapture()
            cancelFallback()
        }
        activityState.reset()
        captureState.stop()
    }

    suspend fun restore(context: Context) {
        val orchestrator = orchestrator(context)
        val preference = DatabaseAutomaticTrackingGateway(
            KartoffelDatabase.open(context),
        ).preference()
        if (preference.enabled && !context.hasRequiredAutomaticTrackingPermissions()) {
            orchestrator.disable()
        } else {
            orchestrator.restore()
        }
    }

    suspend fun reconcilePermissions(context: Context) {
        hasPermissionForAutomaticEvent(context)
    }

    suspend fun pauseForRecording(context: Context) {
        orchestrator(context).pauseForRecording()
    }

    suspend fun resumeAfterRecording(context: Context) {
        if (!hasPermissionForAutomaticEvent(context)) return
        orchestrator(context).resumeAfterRecording(
            wallTimeMillis = System.currentTimeMillis(),
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
    }

    suspend fun onActivity(
        context: Context,
        activity: RecordingActivity,
        observedAtElapsedRealtimeMillis: Long,
    ) {
        if (!hasPermissionForAutomaticEvent(context)) return
        orchestrator(context).onActivity(
            activity = activity,
            observedAtElapsedRealtimeMillis = observedAtElapsedRealtimeMillis,
            currentElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
    }

    suspend fun onFallback(context: Context) {
        if (!hasPermissionForAutomaticEvent(context)) return
        orchestrator(context).onFallback(SystemClock.elapsedRealtime())
    }

    suspend fun onCaptureEnded(context: Context) {
        if (!hasPermissionForAutomaticEvent(context)) return
        orchestrator(context).onCaptureEnded(SystemClock.elapsedRealtime())
    }

    suspend fun onFix(
        context: Context,
        fix: RecordingLocationFix,
        capturedAtElapsedRealtimeMillis: Long,
        trigger: AutomaticFixTrigger,
    ): RecordingLocationDecision? {
        if (!hasPermissionForAutomaticEvent(context)) return null
        return orchestrator(context).onFix(fix, capturedAtElapsedRealtimeMillis, trigger)
    }

    private suspend fun hasPermissionForAutomaticEvent(context: Context): Boolean {
        val preference = DatabaseAutomaticTrackingGateway(
            KartoffelDatabase.open(context),
        ).preference()
        if (!preference.enabled || context.hasRequiredAutomaticTrackingPermissions()) return true
        orchestrator(context).disable()
        return false
    }

    private fun orchestrator(context: Context): AutomaticTrackingOrchestrator {
        val appContext = context.applicationContext
        return AutomaticTrackingOrchestrator(
            gateway = DatabaseAutomaticTrackingGateway(KartoffelDatabase.open(appContext)),
            actions = AndroidAutomaticTrackingActions(appContext),
            activityState = activityState,
            captureState = captureState,
            diagnostics = LiveTrackingDiagnostics.processInstance,
        )
    }
}
