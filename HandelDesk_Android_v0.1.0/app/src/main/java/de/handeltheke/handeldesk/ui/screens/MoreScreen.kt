package de.handeltheke.handeldesk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.handeltheke.handeldesk.ui.ScreenHeader

@Composable
fun MoreScreen(onPrepare: () -> Unit, onSync: () -> Unit, onSettings: () -> Unit, onAudit: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Ещё")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onPrepare, modifier = Modifier.fillMaxWidth()) { Text("Подготовить Offline") }
            Button(onClick = onSync, modifier = Modifier.fillMaxWidth()) { Text("Синхронизация") }
            OutlinedButton(onClick = onAudit, modifier = Modifier.fillMaxWidth()) { Text("Audit Trail") }
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Настройки Bridge") }
            HorizontalDivider()
            Text("Android — равноправный HandelDesk-клиент. Все изменения сначала сохраняются локально.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
