package net.sagberg.kartoffel.tracking

import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.PassiveTrackingPreference
import net.sagberg.kartoffel.storage.PassiveTrackingPreferences

internal class DatabasePassiveTrackingGateway(
    private val database: KartoffelDatabase,
) : PassiveTrackingGateway {
    private val preferences = PassiveTrackingPreferences(database.trackingSettings())
    private val recorder = PassiveTrackingRecorder(database)

    override suspend fun preference(): PassiveTrackingPreference = preferences.current()

    override suspend fun enable(passivePeriodStartedAtMillis: Long) {
        preferences.enable(passivePeriodStartedAtMillis)
    }

    override suspend fun disable() {
        preferences.disable()
    }

    override suspend fun recordingSessionActive(): Boolean =
        database.recordingSessions().active() != null

    override suspend fun record(
        fix: RecordingLocationFix,
        trigger: PassiveFixTrigger,
        activity: RecordingActivity,
    ): RecordingLocationDecision = recorder.record(fix, trigger, activity)
}
