package de.handeltheke.handeldesk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.handeltheke.handeldesk.HandelDeskApp
import de.handeltheke.handeldesk.ui.ScreenHeader
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@Composable
fun SyncScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HandelDeskApp
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<JsonObject?>(null) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf("") }
    val pending by app.container.db.changes().observePending().collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Синхронизация", "Локальных операций: ${pending.count { it.included }}", onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                scope.launch {
                    running = true; message = null
                    runCatching { app.container.sync.preview() }
                        .onSuccess { preview = it; message = "Preview получен" }
                        .onFailure { message = "Ошибка: ${it.message}" }
                    running = false
                }
            }, enabled = !running && pending.any { it.included }, modifier = Modifier.fillMaxWidth()) { Text("1. ПРОВЕРИТЬ НА СЕРВЕРЕ") }
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
            preview?.let { p ->
                val conflicts = p["conflict_count"]?.jsonPrimitive?.intOrNull ?: 0
                val warnings = p["warning_count"]?.jsonPrimitive?.intOrNull ?: 0
                val allowed = p["apply_allowed"]?.jsonPrimitive?.booleanOrNull == true
                val mode = p["policy"]?.jsonObject?.get("mode")?.jsonPrimitive?.contentOrNull ?: "?"
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Bridge mode: $mode")
                        Text("Конфликты: $conflicts")
                        Text("Предупреждения: $warnings")
                        Text(if (allowed) "✓ Apply разрешён" else "✕ Apply заблокирован")
                        p["policy_errors"]?.jsonArray?.forEach { Text("• ${it.jsonPrimitive.content}") }
                    }
                }
                if (allowed) {
                    OutlinedTextField(confirm, { confirm = it }, label = { Text("Введите APPLY") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        scope.launch {
                            running = true
                            runCatching { app.container.sync.apply() }
                                .onSuccess { result -> message = "✓ Успешно ${result["success_count"]?.jsonPrimitive?.intOrNull ?: 0}; ошибок ${result["error_count"]?.jsonPrimitive?.intOrNull ?: 0}"; preview = null; confirm = "" }
                                .onFailure { message = "Ошибка Apply: ${it.message}" }
                            running = false
                        }
                    }, enabled = confirm == "APPLY" && !running, modifier = Modifier.fillMaxWidth()) { Text("2. APPLY") }
                }
            }
            message?.let { Text(it) }
            Text("Apply всегда выполняет свежий серверный Preview повторно внутри Bridge. READ ONLY и SAFE TEST контролируются сервером, а не только приложением.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
