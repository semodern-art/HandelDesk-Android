package de.handeltheke.handeldesk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.handeltheke.handeldesk.HandelDeskApp
import de.handeltheke.handeldesk.data.repo.ImageCacheRepository
import de.handeltheke.handeldesk.data.repo.SnapshotRepository
import de.handeltheke.handeldesk.ui.ScreenHeader
import kotlinx.coroutines.launch

@Composable
fun PrepareOfflineScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HandelDeskApp
    val scope = rememberCoroutineScope()
    val total by app.container.db.products().observeTotalCount().collectAsState(initial = 0)
    var progress by remember { mutableStateOf(SnapshotRepository.Progress(0, 0, "")) }
    var imageProgress by remember { mutableStateOf(ImageCacheRepository.Progress(0, 0, "")) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var lastSnapshot by remember { mutableStateOf<String?>(null) }
    var imageMode by rememberSaveable { mutableStateOf("main") }
    var cacheStats by remember { mutableStateOf(0 to 0L) }
    LaunchedEffect(Unit) {
        lastSnapshot = app.container.db.settings().get("snapshot_at")
        cacheStats = app.container.imageCache.cacheStats()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Подготовить Offline", "Каталог локально: $total товаров", onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Перед полётом обнови Snapshot. Уже изменённые товары Snapshot не перезаписывает.")
            Text("Последний Snapshot: ${lastSnapshot ?: "не выполнялся"}", style = MaterialTheme.typography.bodySmall)
            Text("Изображения для офлайна:", style = MaterialTheme.typography.titleSmall)
            listOf("none" to "Без изображений", "main" to "Только главные", "all" to "Все изображения").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(selected = imageMode == value, onClick = { imageMode = value })
                    Text(label, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Text("В кеше: ${cacheStats.first} файлов · ${"%.1f".format(cacheStats.second / 1048576.0)} MB", style = MaterialTheme.typography.bodySmall)
            if (running) {
                if (progress.total > 0) {
                    LinearProgressIndicator(progress = { progress.current.toFloat() / progress.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                    Text(progress.message, style = MaterialTheme.typography.bodySmall)
                }
                if (imageProgress.total > 0) {
                    LinearProgressIndicator(progress = { imageProgress.current.toFloat() / imageProgress.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                    Text("Фото ${imageProgress.current}/${imageProgress.total}: ${imageProgress.path.takeLast(60)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = {
                scope.launch {
                    running = true; message = null; imageProgress = ImageCacheRepository.Progress(0,0,"")
                    runCatching {
                        app.container.snapshot.refresh { progress = it }
                        app.container.imageCache.prepare(imageMode) { imageProgress = it }
                    }.onSuccess {
                        message = "✓ OFFLINE READY"
                        lastSnapshot = app.container.db.settings().get("snapshot_at")
                        cacheStats = app.container.imageCache.cacheStats()
                    }.onFailure { message = "Ошибка: ${it.message}" }
                    running = false
                }
            }, enabled = !running, modifier = Modifier.fillMaxWidth()) { Text("ПОДГОТОВИТЬ OFFLINE") }
            message?.let { Text(it, color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            HorizontalDivider()
            Text("После статуса OFFLINE READY каталог, справочники и выбранный кеш фото доступны без сети.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
