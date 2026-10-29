package tech.hezy.alarm.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.hezy.alarm.domain.Alarm
import tech.hezy.alarm.domain.AlarmTone
import java.io.File
import java.time.DayOfWeek

interface AlarmRepository {
    fun all(): List<Alarm>
    fun find(id: Long): Alarm?
    fun allocateId(): Long
    fun save(alarm: Alarm)
    fun delete(id: Long)
}

class FileAlarmRepository(context: Context) : AlarmRepository {
    private val file = File(context.filesDir, "alarms.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = Any()
    private var snapshot = read()

    override fun all(): List<Alarm> = synchronized(lock) { snapshot.alarms.map { it.toModel() } }

    override fun find(id: Long): Alarm? = all().firstOrNull { it.id == id }

    override fun allocateId(): Long = synchronized(lock) {
        val id = snapshot.nextId
        snapshot = snapshot.copy(nextId = id + 1)
        write()
        id
    }

    override fun save(alarm: Alarm) = synchronized(lock) {
        val stored = alarm.toStored()
        val rest = snapshot.alarms.filterNot { it.id == alarm.id }
        snapshot = snapshot.copy(alarms = rest + stored)
        write()
    }

    override fun delete(id: Long) = synchronized(lock) {
        snapshot = snapshot.copy(alarms = snapshot.alarms.filterNot { it.id == id })
        write()
    }

    private fun read(): Snapshot = runCatching {
        if (!file.exists()) Snapshot() else json.decodeFromString<Snapshot>(file.readText())
    }.getOrDefault(Snapshot())

    private fun write() {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(snapshot))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

@Serializable
private data class Snapshot(val nextId: Long = 1, val alarms: List<StoredAlarm> = emptyList())

@Serializable
private data class StoredAlarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val label: String,
    val days: List<Int>,
    val tone: String,
    val snooze: Boolean,
    val snoozeMinutes: Int = 9,
)

private fun StoredAlarm.toModel() = Alarm(
    id, hour, minute, enabled, label,
    days.map { DayOfWeek.of(it) }.toSet(),
    AlarmTone.fromKey(tone),
    snooze,
    snoozeMinutes,
)

private fun Alarm.toStored() = StoredAlarm(
    id, hour, minute, enabled, label,
    days.map { it.value }.sorted(),
    tone.storageKey,
    snooze,
    snoozeMinutes,
)
