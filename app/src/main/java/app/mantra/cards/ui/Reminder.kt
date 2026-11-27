package app.mantra.cards.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.mantra.cards.system.Daily

/** On or off, and at what time the daily reminder comes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReminderDialog(start: Daily.Setting, close: () -> Unit, save: (Daily.Setting) -> Unit) {
    var on by remember { mutableStateOf(start.on) }
    val time = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    AlertDialog(onDismissRequest = close, title = { Text("Напоминание") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Раз в день, если есть что повторить", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Muted)
                    Switch(on, { on = it })
                }
                if (on) TimeInput(time)
            }
        },
        confirmButton = { TextButton(onClick = {
            // Turning it on by hand asks for the permission again, in case it was refused before.
            if (on && Build.VERSION.SDK_INT >= 33 && !Daily.canNotify(context)) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            save(Daily.Setting(on, time.hour, time.minute))
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } })
}

/** Asks once for permission to show the reminder (Android 13 and newer). */
@Composable fun AskNotifications(wanted: Boolean) {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(wanted) {
        val prefs = context.getSharedPreferences("reminder", android.content.Context.MODE_PRIVATE)
        if (wanted && !Daily.canNotify(context) && !prefs.getBoolean("asked", false)) {
            prefs.edit().putBoolean("asked", true).apply()
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
