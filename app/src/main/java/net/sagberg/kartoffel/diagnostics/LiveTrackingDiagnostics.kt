package net.sagberg.kartoffel.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sagberg.kartoffel.tracking.RecordingActivity

internal enum class LocationUpdateState {
    INACTIVE,
    ACTIVE,
    SUSPENDED,
}

internal enum class RequestedIntervalReason {
    SESSION_START,
    ACTIVITY_MODE,
    SPEED_OVERRIDE,
    SAFE_FALLBACK,
    SUSPENDED_WHILE_STILL,
    PASSIVE_INITIAL,
    PASSIVE_WINDOW,
    OPPORTUNISTIC,
}

internal data class LatestFixDiagnostics(
    val capturedAtMillis: Long,
    val accuracyMeters: Double,
    val accepted: Boolean,
    val rejectionReason: String?,
) {
    init {
        require(accepted == (rejectionReason == null))
    }
}

internal data class LiveTrackingDiagnosticsState(
    val trackingActive: Boolean = false,
    val activityMode: RecordingActivity = RecordingActivity.UNKNOWN,
    val locationUpdateState: LocationUpdateState = LocationUpdateState.INACTIVE,
    val requestedLocationIntervalMillis: Long? = null,
    val intervalReason: RequestedIntervalReason? = null,
    val latestFix: LatestFixDiagnostics? = null,
)

internal class LiveTrackingDiagnostics {
    private val mutableState = MutableStateFlow(LiveTrackingDiagnosticsState())

    val state: StateFlow<LiveTrackingDiagnosticsState> = mutableState.asStateFlow()

    fun sessionStarted(intervalMillis: Long) {
        mutableState.value = LiveTrackingDiagnosticsState(
            trackingActive = true,
            locationUpdateState = LocationUpdateState.ACTIVE,
            requestedLocationIntervalMillis = intervalMillis,
            intervalReason = RequestedIntervalReason.SESSION_START,
        )
    }

    fun activityModeChanged(
        activityMode: RecordingActivity,
        intervalMillis: Long?,
    ) {
        mutableState.value = mutableState.value.copy(
            activityMode = activityMode,
            locationUpdateState = if (intervalMillis == null) {
                LocationUpdateState.SUSPENDED
            } else {
                LocationUpdateState.ACTIVE
            },
            requestedLocationIntervalMillis = intervalMillis,
            intervalReason = if (intervalMillis == null) {
                RequestedIntervalReason.SUSPENDED_WHILE_STILL
            } else if (activityMode == RecordingActivity.UNKNOWN) {
                RequestedIntervalReason.SAFE_FALLBACK
            } else {
                RequestedIntervalReason.ACTIVITY_MODE
            },
        )
    }

    fun activityObservedWhileAwaitingInitialFix(activityMode: RecordingActivity) {
        mutableState.value = mutableState.value.copy(activityMode = activityMode)
    }

    fun activityRecognitionUnavailable() {
        mutableState.value = mutableState.value.copy(
            activityMode = RecordingActivity.UNKNOWN,
            intervalReason = RequestedIntervalReason.SAFE_FALLBACK,
        )
    }

    fun fixReceived(fix: LatestFixDiagnostics) {
        mutableState.value = mutableState.value.copy(latestFix = fix)
    }

    fun intervalAcceleratedBySpeed(intervalMillis: Long) {
        mutableState.value = mutableState.value.copy(
            locationUpdateState = LocationUpdateState.ACTIVE,
            requestedLocationIntervalMillis = intervalMillis,
            intervalReason = RequestedIntervalReason.SPEED_OVERRIDE,
        )
    }

    fun sessionStopped() {
        mutableState.value = LiveTrackingDiagnosticsState()
    }

    fun passiveCaptureStarted(
        activityMode: RecordingActivity,
        intervalMillis: Long,
        reason: RequestedIntervalReason,
    ) {
        mutableState.value = mutableState.value.copy(
            trackingActive = true,
            activityMode = activityMode,
            locationUpdateState = LocationUpdateState.ACTIVE,
            requestedLocationIntervalMillis = intervalMillis,
            intervalReason = reason,
        )
    }

    fun passiveWaiting(activityMode: RecordingActivity) {
        mutableState.value = mutableState.value.copy(
            trackingActive = true,
            activityMode = activityMode,
            locationUpdateState = LocationUpdateState.SUSPENDED,
            requestedLocationIntervalMillis = null,
            intervalReason = if (activityMode == RecordingActivity.STILL) {
                RequestedIntervalReason.SUSPENDED_WHILE_STILL
            } else {
                RequestedIntervalReason.OPPORTUNISTIC
            },
        )
    }

    fun passiveStopped() {
        mutableState.value = LiveTrackingDiagnosticsState()
    }

    companion object {
        val processInstance = LiveTrackingDiagnostics()
    }
}
