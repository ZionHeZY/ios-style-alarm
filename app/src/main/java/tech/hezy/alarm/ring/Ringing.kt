package tech.hezy.alarm.ring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.hezy.alarm.R
import tech.hezy.alarm.graph
import tech.hezy.alarm.mvi.MviViewModel

data class RingUiState(
    val label: String = "",
    val snoozeVisible: Boolean = true,
    val active: Boolean = false,
)

object RingSession {
    private val states = MutableStateFlow(RingUiState())
    val state: StateFlow<RingUiState> = states.asStateFlow()

    fun show(label: String, snoozeVisible: Boolean) {
        states.value = RingUiState(label, snoozeVisible, active = true)
    }

    fun clear() {
        states.value = RingUiState()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val snoozeFire = intent.action == ACTION_SNOOZE
        val graph = context.graph
        val alarm = graph.repository.find(id) ?: return
        if (!snoozeFire) {
            if (alarm.days.isEmpty()) graph.repository.save(alarm.copy(enabled = false))
            else graph.scheduler.schedule(alarm.copy(enabled = true))
        }
        RingingService.start(context, id)
    }

    companion object {
        const val ACTION_FIRE = "tech.hezy.alarm.FIRE"
        const val ACTION_SNOOZE = "tech.hezy.alarm.SNOOZE"
        const val EXTRA_ID = "alarm_id"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.graph
        graph.scheduler.rescheduleAll(graph.repository.all())
    }
}

class RingingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> silence()
            ACTION_SNOOZE -> {
                val id = ringingId
                val alarm = id?.let { graph.repository.find(it) }
                if (alarm != null) graph.scheduler.snooze(alarm)
                silence()
            }
            else -> {
                val id = intent?.getLongExtra(AlarmReceiver.EXTRA_ID, -1L) ?: -1L
                if (id >= 0) present(id)
            }
        }
        return START_NOT_STICKY
    }

    private fun present(id: Long) {
        val alarm = graph.repository.find(id) ?: return
        ringingId = id
        ensureChannel()
        val notification = alertNotification(alarm.label.ifBlank { getString(R.string.ringing) })
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        inForeground = true
        graph.player.play(alarm.tone)
        vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 400), 0))
        RingSession.show(alarm.label.ifBlank { getString(R.string.alarm_default_label) }, alarm.snooze)
        startActivity(
            Intent(this, RingingActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION,
            ),
        )
    }

    private fun silence() {
        graph.player.stop()
        vibrator().cancel()
        RingSession.clear()
        ringingId = null
        if (inForeground) stopForeground(STOP_FOREGROUND_REMOVE)
        inForeground = false
        stopSelf()
    }

    private fun alertNotification(label: String): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 0,
            Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(null, null)
        manager.createNotificationChannel(channel)
    }

    private fun vibrator() = getSystemService(Vibrator::class.java)

    companion object {
        private const val CHANNEL_ID = "alarms"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "tech.hezy.alarm.STOP"
        private const val ACTION_SNOOZE = "tech.hezy.alarm.SNOOZE_UI"
        private var ringingId: Long? = null
        private var inForeground = false

        fun start(context: Context, id: Long) {
            val intent = Intent(context, RingingService::class.java).putExtra(AlarmReceiver.EXTRA_ID, id)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) = context.startService(Intent(context, RingingService::class.java).setAction(ACTION_STOP))

        fun snooze(context: Context) = context.startService(Intent(context, RingingService::class.java).setAction(ACTION_SNOOZE))
    }
}

sealed interface RingIntent {
    data object Stop : RingIntent
    data object Snooze : RingIntent
}

class RingingViewModel(private val app: Context) : MviViewModel<RingUiState, RingIntent>(RingUiState()) {
    val session = RingSession.state

    override fun reduce(intent: RingIntent) {
        when (intent) {
            RingIntent.Stop -> RingingService.stop(app)
            RingIntent.Snooze -> RingingService.snooze(app)
        }
    }
}
