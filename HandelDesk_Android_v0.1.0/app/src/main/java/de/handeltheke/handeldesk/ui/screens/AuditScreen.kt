package de.handeltheke.handeldesk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.handeltheke.handeldesk.HandelDeskApp
import de.handeltheke.handeldesk.ui.ScreenHeader
import java.text.DateFormat
import java.util.Date

@Composable
fun AuditScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HandelDeskApp
    val events by app.container.db.audit().observeRecent().collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Audit Trail", "Неизменяемая история локальных действий", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { it.id }) { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(event.summary.ifBlank { event.eventType }, style = MaterialTheme.typography.titleSmall)
                        Text("${DateFormat.getDateTimeInstance().format(Date(event.createdAt))} · ${event.source}", style = MaterialTheme.typography.bodySmall)
                        if (!event.entityUuid.isNullOrBlank()) Text("${event.entityType}: ${event.entityUuid}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
