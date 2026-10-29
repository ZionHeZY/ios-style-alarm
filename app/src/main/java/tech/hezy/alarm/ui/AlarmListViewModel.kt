package tech.hezy.alarm.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.hezy.alarm.data.AlarmRepository
import tech.hezy.alarm.data.AlarmScheduler
import tech.hezy.alarm.data.AlarmSoundPlayer
import tech.hezy.alarm.domain.Alarm
import tech.hezy.alarm.domain.AlarmTone
import tech.hezy.alarm.mvi.MviViewModel
import java.time.DayOfWeek
import java.time.LocalTime

data class EditorDraft(
    val id: Long,
    val isNew: Boolean,
    val hour: Int,
    val minute: Int,
    val label: String,
    val days: Set<DayOfWeek>,
    val tone: AlarmTone,
    val snooze: Boolean,
    val page: EditorPage,
    val snoozeMinutes: Int = 9,
)

enum class EditorPage { Form, Repeat, Label, Sound, Snooze }

data class AlarmListState(
    val alarms: List<Alarm> = emptyList(),
    val editing: Boolean = false,
    val armedDeleteId: Long? = null,
    val editor: EditorDraft? = null,
    val askNotification: Boolean = false,
)

sealed interface AlarmIntent {
    data object ToggleEdit : AlarmIntent
    data class ToggleEnabled(val id: Long) : AlarmIntent
    data class ArmDelete(val id: Long) : AlarmIntent
    data class Delete(val id: Long) : AlarmIntent
    data class Open(val id: Long?) : AlarmIntent
    data object CloseEditor : AlarmIntent
    data class ChangeTime(val hour: Int, val minute: Int) : AlarmIntent
    data class OpenPage(val page: EditorPage) : AlarmIntent
    data class ToggleDay(val day: DayOfWeek) : AlarmIntent
    data class ChangeLabel(val value: String) : AlarmIntent
    data class SelectTone(val tone: AlarmTone) : AlarmIntent
    data class ToggleSnooze(val enabled: Boolean) : AlarmIntent
    data class ChangeSnoozeMinutes(val minutes: Int) : AlarmIntent
    data object Save : AlarmIntent
    data object NotificationPromptHandled : AlarmIntent
    data class ShowNotificationPrompt(val show: Boolean) : AlarmIntent
}

class AlarmListViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val player: AlarmSoundPlayer,
    private val defaultLabel: String,
) : MviViewModel<AlarmListState, AlarmIntent>(AlarmListState()) {
    private var preview: Job? = null

    init {
        publish()
    }

    override fun reduce(intent: AlarmIntent) {
        when (intent) {
            AlarmIntent.ToggleEdit -> update {
                copy(editing = !editing, armedDeleteId = null)
            }
            is AlarmIntent.ToggleEnabled -> {
                val alarm = repository.find(intent.id) ?: return
                val next = alarm.copy(enabled = !alarm.enabled)
                repository.save(next)
                scheduler.schedule(next)
                publish()
            }
            is AlarmIntent.ArmDelete -> update { copy(armedDeleteId = intent.id) }
            is AlarmIntent.Delete -> {
                scheduler.cancel(intent.id)
                repository.delete(intent.id)
                if (state.value.editor?.id == intent.id) stopPreview()
                update { copy(armedDeleteId = null, editor = editor?.takeUnless { it.id == intent.id }) }
                publish()
            }
            is AlarmIntent.Open -> {
                player.stop()
                val now = LocalTime.now()
                val draft = intent.id?.let(repository::find)?.toDraft(isNew = false)
                    ?: EditorDraft(
                        id = repository.allocateId(),
                        isNew = true,
                        hour = now.hour,
                        minute = now.minute,
                        label = defaultLabel,
                        days = emptySet(),
                        tone = AlarmTone.Rise,
                        snooze = true,
                        page = EditorPage.Form,
                    )
                update { copy(editor = draft, editing = false, armedDeleteId = null) }
            }
            AlarmIntent.CloseEditor -> {
                stopPreview()
                update { copy(editor = null) }
            }
            is AlarmIntent.ChangeTime -> updateEditor { copy(hour = intent.hour, minute = intent.minute) }
            is AlarmIntent.OpenPage -> {
                if (intent.page != EditorPage.Sound) stopPreview()
                updateEditor { copy(page = intent.page) }
            }
            is AlarmIntent.ToggleDay -> updateEditor {
                val days = if (intent.day in this.days) this.days - intent.day else this.days + intent.day
                copy(days = days)
            }
            is AlarmIntent.ChangeLabel -> updateEditor { copy(label = intent.value) }
            is AlarmIntent.SelectTone -> {
                updateEditor { copy(tone = intent.tone) }
                stopPreview()
                player.play(intent.tone)
                preview = viewModelScope.launch {
                    delay(1_600)
                    player.stop()
                }
            }
            is AlarmIntent.ToggleSnooze -> updateEditor { copy(snooze = intent.enabled) }
            is AlarmIntent.ChangeSnoozeMinutes -> {
                if (intent.minutes in 1..15) updateEditor { copy(snoozeMinutes = intent.minutes) }
            }
            AlarmIntent.Save -> {
                val draft = state.value.editor ?: return
                stopPreview()
                val alarm = draft.toAlarm()
                repository.save(alarm)
                scheduler.schedule(alarm)
                update { copy(editor = null) }
                publish()
            }
            AlarmIntent.NotificationPromptHandled -> update { copy(askNotification = false) }
            is AlarmIntent.ShowNotificationPrompt -> update { copy(askNotification = intent.show) }
        }
    }

    private fun publish() {
        val alarms = repository.all().sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        update { copy(alarms = alarms) }
    }

    private fun updateEditor(block: EditorDraft.() -> EditorDraft) {
        update { copy(editor = editor?.block()) }
    }

    private fun stopPreview() {
        preview?.cancel()
        player.stop()
    }
}

private fun Alarm.toDraft(isNew: Boolean) = EditorDraft(
    id, isNew, hour, minute, label, days, tone, snooze, EditorPage.Form, snoozeMinutes,
)

private fun EditorDraft.toAlarm() = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    enabled = true,
    label = label.ifBlank { "" },
    days = days,
    tone = tone,
    snooze = snooze,
    snoozeMinutes = snoozeMinutes,
)
