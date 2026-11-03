package tech.hezy.alarm.ring

import android.app.KeyguardManager
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
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.hezy.alarm.AlarmApplication
import tech.hezy.alarm.R
import tech.hezy.alarm.domain.Alarm
import tech.hezy.alarm.graph
import tech.hezy.alarm.mvi.MviViewModel

data class RingUiState(
    val hour: Int = 0,
    val minute: Int = 0,
    val label: String = "",
    val snoozeVisible: Boolean = true,
    val active: Boolean = false,
    val inAppBanner: Boolean = false,
)

object RingSession {
    private val states = MutableStateFlow(RingUiState())
    val state: StateFlow<RingUiState> = states.asStateFlow()

    fun show(hour: Int, minute: Int, label: String, snoozeVisible: Boolean, inAppBanner: Boolean) {
        states.value = RingUiState(hour, minute, label, snoozeVisible, active = true, inAppBanner = inAppBanner)
    }

    fun promoteToFullScreen() {
        states.value = states.value.copy(inAppBanner = false)
    }

    fun clear() {
        states.value = states.value.copy(active = false, inAppBanner = false)
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
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val unlocked = power.isInteractive && !keyguard.isKeyguardLocked
        val inAlarmPage = unlocked && (context.applicationContext as AlarmApplication).isAlarmPageVisible
        val label = alarm.label.ifBlank { context.getString(R.string.alarm_default_label) }

        RingSession.show(alarm.hour, alarm.minute, label, alarm.snooze, inAppBanner = inAlarmPage)
        RingingService.start(context, id, inAlarmPage)
        if (!inAlarmPage) {
            runCatching { RingingActivity.start(context) }
        }
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
                val inAlarmPage = intent?.getBooleanExtra(EXTRA_IN_ALARM_PAGE, isInAlarmPage()) ?: isInAlarmPage()
                if (id >= 0) present(id, inAlarmPage)
            }
        }
        return START_NOT_STICKY
    }

    private fun isInAlarmPage(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        val unlocked = power.isInteractive && !keyguard.isKeyguardLocked
        return unlocked && (applicationContext as AlarmApplication).isAlarmPageVisible
    }

    private fun present(id: Long, inAlarmPage: Boolean) {
        val alarm = graph.repository.find(id) ?: return
        ringingId = id
        ensureChannels()

        val label = alarm.label.ifBlank { getString(R.string.alarm_default_label) }
        RingSession.show(alarm.hour, alarm.minute, label, alarm.snooze, inAppBanner = inAlarmPage)

        val notification = alertNotification(alarm, label, highPriority = !inAlarmPage)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        inForeground = true
        graph.player.play(alarm.tone)
        vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 400), 0))

        if (!inAlarmPage) {
            runCatching { RingingActivity.start(this) }
        }
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

    private fun alertNotification(
        alarm: Alarm,
        label: String,
        highPriority: Boolean,
    ): Notification {
        val fullScreenPending = PendingIntent.getActivity(
            this, 0,
            Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPending = PendingIntent.getService(
            this, 1,
            Intent(this, RingingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozePending = PendingIntent.getService(
            this, 2,
            Intent(this, RingingService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val rv = RemoteViews(packageName, R.layout.notification_alarm_banner).apply {
            setTextViewText(R.id.banner_time, formatAlarmBannerTime(this@RingingService, alarm.hour, alarm.minute))
            setTextViewText(R.id.banner_label, label)
            setViewVisibility(R.id.banner_snooze, if (alarm.snooze) View.VISIBLE else View.GONE)
            setOnClickPendingIntent(R.id.banner_snooze, snoozePending)
            setOnClickPendingIntent(R.id.banner_stop, stopPending)
            setOnClickPendingIntent(R.id.banner_root, fullScreenPending)
        }

        val channel = if (highPriority) CHANNEL_ID else CHANNEL_SILENT_ID
        val builder = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(label)
            .setContentText(formatAlarmBannerTime(this, alarm.hour, alarm.minute))
            .setCustomContentView(rv)
            .setCustomBigContentView(rv)
            .setCustomHeadsUpContentView(rv)
            .setContentIntent(fullScreenPending)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)

        if (highPriority) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(fullScreenPending, true)
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
        }
        return builder.build()
    }

    private fun ensureChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val alert = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        val active = NotificationChannel(
            CHANNEL_SILENT_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(alert)
        manager.createNotificationChannel(active)
    }

    private fun vibrator() = getSystemService(Vibrator::class.java)

    companion object {
        private const val CHANNEL_ID = "alarms"
        private const val CHANNEL_SILENT_ID = "alarms_active"
        private const val NOTIFICATION_ID = 7
        private const val EXTRA_IN_ALARM_PAGE = "in_alarm_page"
        private const val ACTION_STOP = "tech.hezy.alarm.STOP"
        private const val ACTION_SNOOZE = "tech.hezy.alarm.SNOOZE_UI"
        private var ringingId: Long? = null
        private var inForeground = false

        fun start(context: Context, id: Long, inAlarmPage: Boolean = false) {
            val intent = Intent(context, RingingService::class.java)
                .putExtra(AlarmReceiver.EXTRA_ID, id)
                .putExtra(EXTRA_IN_ALARM_PAGE, inAlarmPage)
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
