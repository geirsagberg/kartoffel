package net.sagberg.kartoffel.tracking

import android.content.Context
import android.os.SystemClock
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnostics
import net.sagberg.kartoffel.storage.KartoffelDatabase

internal object PassiveTrackingManager {
    private val activityState = PassiveActivityState()
    private val captureState = PassiveCaptureState()

    suspend fun enable(context: Context): Boolean {
        if (!context.hasRequiredPassiveTrackingPermissions()) return false
        orchestrator(context).enable(
            wallTimeMillis = System.currentTimeMillis(),
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
        return true
    }

    suspend fun disable(context: Context) {
        orchestrator(context).disable()
    }

    suspend fun restore(context: Context) {
        val orchestrator = orchestrator(context)
        val preference = DatabasePassiveTrackingGateway(
            KartoffelDatabase.open(context),
        ).preference()
        if (preference.enabled && !context.hasRequiredPassiveTrackingPermissions()) {
            orchestrator.disable()
        } else {
            orchestrator.restore()
        }
    }

    suspend fun reconcilePermissions(context: Context) {
        hasPermissionForPassiveEvent(context)
    }

    suspend fun pauseForRecordingSession(context: Context) {
        orchestrator(context).pauseForRecordingSession()
    }

    suspend fun resumeAfterRecordingSession(context: Context) {
        if (!hasPermissionForPassiveEvent(context)) return
        orchestrator(context).resumeAfterRecordingSession(
            wallTimeMillis = System.currentTimeMillis(),
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
    }

    suspend fun onActivity(
        context: Context,
        activity: RecordingActivity,
        observedAtElapsedRealtimeMillis: Long,
    ) {
        if (!hasPermissionForPassiveEvent(context)) return
        orchestrator(context).onActivity(
            activity = activity,
            observedAtElapsedRealtimeMillis = observedAtElapsedRealtimeMillis,
            currentElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
    }

    suspend fun onFallback(context: Context) {
        if (!hasPermissionForPassiveEvent(context)) return
        orchestrator(context).onFallback(SystemClock.elapsedRealtime())
    }

    suspend fun onCaptureEnded(context: Context) {
        if (!hasPermissionForPassiveEvent(context)) return
        orchestrator(context).onCaptureEnded(SystemClock.elapsedRealtime())
    }

    suspend fun onFix(
        context: Context,
        fix: RecordingLocationFix,
        capturedAtElapsedRealtimeMillis: Long,
        trigger: PassiveFixTrigger,
    ): RecordingLocationDecision? {
        if (!hasPermissionForPassiveEvent(context)) return null
        return orchestrator(context).onFix(fix, capturedAtElapsedRealtimeMillis, trigger)
    }

    private suspend fun hasPermissionForPassiveEvent(context: Context): Boolean {
        val preference = DatabasePassiveTrackingGateway(
            KartoffelDatabase.open(context),
        ).preference()
        if (!preference.enabled || context.hasRequiredPassiveTrackingPermissions()) return true
        orchestrator(context).disable()
        return false
    }

    private fun orchestrator(context: Context): PassiveTrackingOrchestrator {
        val appContext = context.applicationContext
        return PassiveTrackingOrchestrator(
            gateway = DatabasePassiveTrackingGateway(KartoffelDatabase.open(appContext)),
            actions = AndroidPassiveTrackingActions(appContext),
            activityState = activityState,
            captureState = captureState,
            diagnostics = LiveTrackingDiagnostics.processInstance,
        )
    }
}
