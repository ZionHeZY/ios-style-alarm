package tech.hezy.alarm

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.hezy.alarm.ui.AlarmHome
import tech.hezy.alarm.ui.AlarmIntent
import tech.hezy.alarm.ui.AlarmListViewModel
import tech.hezy.alarm.ui.IosFontFamily

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        val app = application as AlarmApplication
        setContent {
            val model = viewModel<AlarmListViewModel>(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        AlarmListViewModel(
                            app.graph.repository,
                            app.graph.scheduler,
                            app.graph.player,
                            getString(R.string.alarm_default_label),
                        ) as T
                },
            )
            val state by model.state.collectAsState()
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                model.dispatch(AlarmIntent.ShowNotificationPrompt(!granted))
            }
            LaunchedEffect(Unit) {
                val granted = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = IosFontFamily)) {
                AlarmHome(state, model::dispatch)
            }
        }
    }
}
