package tech.hezy.alarm.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import tech.hezy.alarm.MainActivity
import tech.hezy.alarm.domain.Alarm
import tech.hezy.alarm.domain.nextTrigger
import tech.hezy.alarm.ring.AlarmReceiver
import java.time.ZonedDateTime

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun schedule(alarm: Alarm) {
        cancel(alarm.id)
        if (!alarm.enabled) return
        val whenMs = nextTrigger(alarm.hour, alarm.minute, alarm.days).toInstant().toEpochMilli()
        set(alarm.id, whenMs, snooze = false)
    }

    fun snooze(alarm: Alarm, from: ZonedDateTime = ZonedDateTime.now()) {
        val whenMs = from.plusMinutes(alarm.snoozeMinutes.toLong()).toInstant().toEpochMilli()
        set(alarm.id, whenMs, snooze = true)
    }

    fun cancel(id: Long) {
        manager.cancel(firePending(id, snooze = false))
        manager.cancel(firePending(id, snooze = true))
    }

    fun rescheduleAll(alarms: List<Alarm>) {
        alarms.filter { it.enabled }.forEach(::schedule)
    }

    private fun set(id: Long, whenMs: Long, snooze: Boolean) {
        manager.setAlarmClock(
            AlarmManager.AlarmClockInfo(whenMs, showPending(id)),
            firePending(id, snooze),
        )
    }

    private fun firePending(id: Long, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(if (snooze) AlarmReceiver.ACTION_SNOOZE else AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ID, id)
        val request = if (snooze) id.toInt() + SNOOZE_OFFSET else id.toInt()
        return PendingIntent.getBroadcast(
            context, request, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showPending(id: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, id.toInt() + SHOW_OFFSET, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val SNOOZE_OFFSET = 100_000
        const val SHOW_OFFSET = 200_000
    }
}
