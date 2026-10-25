package de.handeltheke.handeldesk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.handeltheke.handeldesk.HandelDeskApp
import de.handeltheke.handeldesk.ui.ScreenHeader
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HandelDeskApp
    val settings = app.container.bridgeSettings
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf(settings.baseUrl) }
    var deviceName by rememberSaveable { mutableStateOf(settings.deviceName) }
    var token by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Настройки Bridge", "Token хранится в Android Keystore", onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(url, { url = it }, label = { Text("Bridge URL (HTTPS)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(deviceName, { deviceName = it }, label = { Text("Имя устройства") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(token, { token = it }, label = { Text(if (settings.hasToken()) "Новый Device Token (оставь пустым, чтобы не менять)" else "Device Token") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = {
                settings.baseUrl = url
                settings.deviceName = deviceName
                if (token.isNotBlank()) { settings.setToken(token.trim()); token = "" }
                status = "Сохранено"
            }, modifier = Modifier.fillMaxWidth()) { Text("СОХРАНИТЬ") }
            OutlinedButton(onClick = {
                settings.baseUrl = url
                if (token.isNotBlank()) settings.setToken(token.trim())
                scope.launch {
                    testing = true
                    status = runCatching {
                        val root = app.container.bridge.get("schema")
                        val schema = root["schema"]?.jsonObject
                        val protocol = schema?.get("protocol")?.jsonPrimitive?.content ?: "?"
                        val mode = schema?.get("write_policy")?.jsonObject?.get("mode")?.jsonPrimitive?.content ?: "?"
                        "✓ Bridge OK · protocol $protocol · write mode $mode"
                    }.getOrElse { "Ошибка: ${it.message}" }
                    testing = false
                }
            }, enabled = !testing, modifier = Modifier.fillMaxWidth()) { Text("ПРОВЕРИТЬ СОЕДИНЕНИЕ") }
            if (testing) LinearProgressIndicator(Modifier.fillMaxWidth())
            status?.let { Text(it) }
            HorizontalDivider()
            Text("Безопасность: приложение запрещает отправлять Device Token по HTTP и не следует автоматическим redirect'ам.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
