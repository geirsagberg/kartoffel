package net.sagberg.kartoffel.tracking

import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.AutomaticTrackingPreference
import net.sagberg.kartoffel.storage.AutomaticTrackingPreferences

internal class DatabaseAutomaticTrackingGateway(
    private val database: KartoffelDatabase,
) : AutomaticTrackingGateway {
    private val preferences = AutomaticTrackingPreferences(database.trackingSettings())
    private val recorder = AutomaticTrackingRecorder(database)

    override suspend fun preference(): AutomaticTrackingPreference = preferences.current()

    override suspend fun enable(automaticPeriodStartedAtMillis: Long) {
        preferences.enable(automaticPeriodStartedAtMillis)
    }

    override suspend fun disable() {
        preferences.disable()
    }

    override suspend fun recordingActive(): Boolean =
        database.recordings().active() != null

    override suspend fun record(
        fix: RecordingLocationFix,
        trigger: AutomaticFixTrigger,
        activity: RecordingActivity,
    ): RecordingLocationDecision = recorder.record(fix, trigger, activity)
}
