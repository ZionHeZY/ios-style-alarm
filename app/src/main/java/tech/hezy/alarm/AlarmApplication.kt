package tech.hezy.alarm

import android.app.Application
import android.content.Context
import tech.hezy.alarm.data.AlarmScheduler
import tech.hezy.alarm.data.AlarmSoundPlayer
import tech.hezy.alarm.data.FileAlarmRepository

class AlarmApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.scheduler.rescheduleAll(graph.repository.all())
    }
}

class AppGraph(context: Context) {
    val repository = FileAlarmRepository(context)
    val scheduler = AlarmScheduler(context)
    val player = AlarmSoundPlayer()
}

val Context.graph: AppGraph
    get() = (applicationContext as AlarmApplication).graph
