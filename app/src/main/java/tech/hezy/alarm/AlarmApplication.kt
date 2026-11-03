package tech.hezy.alarm

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import tech.hezy.alarm.data.AlarmScheduler
import tech.hezy.alarm.data.AlarmSoundPlayer
import tech.hezy.alarm.data.FileAlarmRepository

class AlarmApplication : Application() {
    lateinit var graph: AppGraph
        private set

    private var homeStartedCount: Int = 0

    val isAlarmPageVisible: Boolean
        get() = homeStartedCount > 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity is MainActivity) homeStartedCount++
            }
            override fun onActivityStopped(activity: Activity) {
                if (activity is MainActivity) homeStartedCount = (homeStartedCount - 1).coerceAtLeast(0)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
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
