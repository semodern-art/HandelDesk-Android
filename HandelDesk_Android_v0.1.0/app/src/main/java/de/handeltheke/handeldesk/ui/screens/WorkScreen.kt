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
import de.handeltheke.handeldesk.data.local.ProductEntity
import de.handeltheke.handeldesk.ui.ScreenHeader

@Composable
fun WorkScreen(onOpen: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as HandelDeskApp
    val queue by app.container.db.queue().observeQueue().collectAsState(initial = emptyList())
    val changedCount by app.container.db.products().observeChangedCount().collectAsState(initial = 0)
    val draftCount by app.container.db.products().observeDraftCount().collectAsState(initial = 0)
    var products by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }
    LaunchedEffect(queue) { products = queue.mapNotNull { app.container.db.products().get(it.productUuid) } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Работа", "Очередь ${queue.size} · Изменено $changedCount · Черновики $draftCount")
        if (products.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp)) { Text("Рабочая очередь пуста. Добавляй товары из карточки — и в самолёте можно идти по ним последовательно.") }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products, key = { it.uuid }) { p ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(p.uuid) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text("${p.model} · ${"%.2f".format(p.price)} € · Qty ${p.quantity}")
                            Text("${p.state} · ${p.workflowState}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
