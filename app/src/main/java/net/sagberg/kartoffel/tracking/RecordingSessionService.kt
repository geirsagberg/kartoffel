package net.sagberg.kartoffel.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.sagberg.kartoffel.R
import net.sagberg.kartoffel.diagnostics.LiveTrackingDiagnostics
import net.sagberg.kartoffel.storage.KartoffelDatabase

internal class RecordingSessionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<RecordingCommand>(Channel.UNLIMITED)
    private lateinit var database: KartoffelDatabase
    private lateinit var locationUpdates: FusedRecordingLocationUpdates
    private lateinit var activityUpdates: FusedRecordingActivityUpdates
    private lateinit var orchestrator: RecordingSessionOrchestrator

    override fun onCreate() {
        super.onCreate()
        database = KartoffelDatabase.open(this)
        locationUpdates = FusedRecordingLocationUpdates(this, serviceScope)
        activityUpdates = FusedRecordingActivityUpdates(this, serviceScope)
        orchestrator = RecordingSessionOrchestrator(
            gateway = RecordingSessionRecorder(database),
            locationUpdates = locationUpdates,
            activityUpdates = activityUpdates,
            diagnostics = LiveTrackingDiagnostics.processInstance,
        )
        createNotificationChannel()
        serviceScope.launch {
            for (command in commands) {
                when (command) {
                    is RecordingCommand.Start -> {
                        promoteToForeground()
                        orchestrator.start(command.startedAtMillis)
                    }
                    is RecordingCommand.Stop -> {
                        orchestrator.stop(command.endedAtMillis)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelfResult(command.startId)
                    }
                    is RecordingCommand.ActivityUpdate -> {
                        if (!orchestrator.resumeActiveSession()) {
                            stopSelfResult(command.startId)
                            continue
                        }
                        promoteToForeground()
                        activityUpdates.handleActivityIntent(command.intent)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> enqueueStart(startId)
            ACTION_STOP -> commands.trySend(
                RecordingCommand.Stop(
                    startId = startId,
                    endedAtMillis = System.currentTimeMillis(),
                ),
            )
            FusedRecordingActivityUpdates.ACTION_ACTIVITY_TRANSITION,
            FusedRecordingActivityUpdates.ACTION_ACTIVITY_BOOTSTRAP,
            -> {
                commands.trySend(
                    RecordingCommand.ActivityUpdate(
                        startId = startId,
                        intent = checkNotNull(intent),
                    ),
                )
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        locationUpdates.stop()
        activityUpdates.stop()
        commands.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun enqueueStart(startId: Int) {
        promoteToForeground()
        commands.trySend(
            RecordingCommand.Start(
                startId = startId,
                startedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun promoteToForeground() {
        startForeground(
            NOTIFICATION_ID,
            recordingNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Recording Session",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun recordingNotification(): Notification {
        val stopIntent = Intent(this, RecordingSessionService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_my_location_24)
            .setContentTitle("Recording Session")
            .setContentText("Kartoffel is recording your route")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stopPendingIntent).build())
            .build()
    }

    companion object {
        private const val ACTION_START = "net.sagberg.kartoffel.action.START_RECORDING"
        private const val ACTION_STOP = "net.sagberg.kartoffel.action.STOP_RECORDING"
        private const val NOTIFICATION_CHANNEL_ID = "recording_session"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, RecordingSessionService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RecordingSessionService::class.java).setAction(ACTION_STOP),
            )
        }
    }

    private sealed interface RecordingCommand {
        val startId: Int

        data class Start(
            override val startId: Int,
            val startedAtMillis: Long,
        ) : RecordingCommand

        data class Stop(
            override val startId: Int,
            val endedAtMillis: Long,
        ) : RecordingCommand

        data class ActivityUpdate(
            override val startId: Int,
            val intent: Intent,
        ) : RecordingCommand
    }
}
