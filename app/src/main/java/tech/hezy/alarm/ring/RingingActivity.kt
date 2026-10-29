package tech.hezy.alarm.ring

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import tech.hezy.alarm.R
import tech.hezy.glassalarmslider.GlassAlarmControls
import java.time.LocalTime
import tech.hezy.alarm.ui.IosFontFamily

class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
        )
        setContent {
            val model = viewModel<RingingViewModel>(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        RingingViewModel(applicationContext) as T
                },
            )
            val session by model.session.collectAsState()
            LaunchedEffect(session.active) {
                if (!session.active) finish()
            }
            CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = IosFontFamily)) {
                RingingScreen(
                    label = session.label.ifBlank { stringResource(R.string.ringing) },
                    snoozeVisible = session.snoozeVisible,
                    onSnooze = { model.dispatch(RingIntent.Snooze) },
                    onStop = { model.dispatch(RingIntent.Stop) },
                )
            }
        }
    }
}

@Composable
private fun RingingScreen(
    label: String,
    snoozeVisible: Boolean,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
) {
    val is24 = DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }
    val minute = now.minute.toString().padStart(2, '0')
    val time = if (is24) {
        "${now.hour}:$minute"
    } else {
        val hour = when (val value = now.hour % 12) { 0 -> 12; else -> value }
        "$hour:$minute"
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 430.dp).fillMaxWidth().fillMaxSize().statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(96.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_alarm_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp, 21.dp),
                )
                Text(label, color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(start = 6.dp))
            }
            Text(
                time,
                color = Color.White,
                fontSize = 104.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-1.5).sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            if (snoozeVisible) {
                GlassAlarmControls(Modifier.fillMaxWidth(), onSnooze = onSnooze, onStop = onStop)
            } else {
                Box(Modifier.fillMaxWidth().height(120.dp).clipToBounds()) {
                    GlassAlarmControls(
                        Modifier.fillMaxWidth().offset(y = (-120).dp),
                        onSnooze = {},
                        onStop = onStop,
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}
