package de.handeltheke.handeldesk.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.handeltheke.handeldesk.HandelDeskApp
import de.handeltheke.handeldesk.data.local.ChangeEntity
import de.handeltheke.handeldesk.ui.ScreenHeader
import kotlinx.coroutines.launch

@Composable
fun ChangesScreen(onOpen: (String) -> Unit, onSync: () -> Unit) {
    val app = LocalContext.current.applicationContext as HandelDeskApp
    val changes by app.container.db.changes().observePending().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(changes) {
        names = changes.map { it.entityUuid }.distinct().associateWith { app.container.db.products().get(it)?.name.orEmpty() }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Изменения", "Несинхронизировано: ${changes.size}")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(changes, key = { it.id }) { ch ->
                ChangeCard(ch, names[ch.entityUuid].orEmpty(), onOpen, onIncluded = { included -> scope.launch { app.container.db.changes().setIncluded(ch.id, included) } })
            }
        }
        Button(onClick = onSync, enabled = changes.any { it.included }, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("ПРОВЕРИТЬ СИНХРОНИЗАЦИЮ") }
    }
}

@Composable
private fun ChangeCard(ch: ChangeEntity, name: String, onOpen: (String) -> Unit, onIncluded: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = ch.entityType == "product") { onOpen(ch.entityUuid) }) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(ch.included, onCheckedChange = onIncluded)
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { ch.entityType }, style = MaterialTheme.typography.titleSmall)
                Text(ch.fieldPath)
                Text("${ch.operationType} · ${ch.afterJson.orEmpty().take(100)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
