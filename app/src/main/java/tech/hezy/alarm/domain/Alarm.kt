package tech.hezy.alarm.domain

import java.time.DayOfWeek
import java.time.ZonedDateTime

enum class AlarmTone(val storageKey: String) {
    Rise("rise"),
    Pulse("pulse"),
    Glass("glass"),
    Harbor("harbor"),
    Clarity("clarity");

    companion object {
        fun fromKey(key: String): AlarmTone = entries.firstOrNull { it.storageKey == key } ?: Rise
    }
}

data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val label: String,
    val days: Set<DayOfWeek>,
    val tone: AlarmTone,
    val snooze: Boolean,
    val snoozeMinutes: Int = 9,
)

fun nextTrigger(
    hour: Int,
    minute: Int,
    days: Set<DayOfWeek>,
    after: ZonedDateTime = ZonedDateTime.now(),
): ZonedDateTime {
    val candidates = if (days.isEmpty()) (0..1).map { after.plusDays(it.toLong()) }
    else (0..7).map { after.plusDays(it.toLong()) }.filter { it.dayOfWeek in days }
    return candidates
        .map { it.withHour(hour).withMinute(minute).withSecond(0).withNano(0) }
        .first { it.isAfter(after) }
}
