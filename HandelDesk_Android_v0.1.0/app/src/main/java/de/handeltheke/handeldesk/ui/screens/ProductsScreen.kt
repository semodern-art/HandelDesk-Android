package de.handeltheke.handeldesk.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.handeltheke.handeldesk.HandelDeskApp
import de.handeltheke.handeldesk.data.repo.ProductPage
import de.handeltheke.handeldesk.ui.ScreenHeader
import de.handeltheke.handeldesk.ui.components.CachedProductImage
import de.handeltheke.handeldesk.domain.JsonTools
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.delay

@Composable
fun ProductsScreen(onOpen: (String) -> Unit, onNew: () -> Unit) {
    val app = LocalContext.current.applicationContext as HandelDeskApp
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var result by remember { mutableStateOf(ProductPage(emptyList(), 0, 1, 20, emptyList())) }
    var loading by remember { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(query, page) {
        loading = true
        delay(120)
        result = app.container.productSearch.search(query, page = page, pageSize = 20)
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Товары", "OFFLINE-FIRST · ${result.total} товаров")
        Column(Modifier.padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; page = 1 },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Поиск: название, Model, SKU, JAN…") },
                trailingIcon = { if (query.isNotBlank()) TextButton(onClick = { query = "" }) { Text("×") } }
            )
            TextButton(onClick = { showHelp = !showHelp }) { Text(if (showHelp) "Скрыть синтаксис" else "Операторы поиска") }
            if (showHelp) {
                Text("\"точная фраза\" · -слово · -\"фраза\" · Samsung OR LG · model: · sku: · jan: · ean: · mpn: · id:", style = MaterialTheme.typography.bodySmall)
            }
            if (result.errors.isNotEmpty()) Text(result.errors.joinToString(" · "), color = MaterialTheme.colorScheme.error)
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(result.items, key = { it.uuid }) { item ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(item.uuid) }) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val imagePath = remember(item.localJson) { JsonTools.objectOf(item.localJson)["product"]?.jsonObject?.get("image")?.jsonPrimitive?.contentOrNull.orEmpty() }
                        CachedProductImage(imagePath, size = 64.dp)
                        Column(Modifier.weight(1f)) {
                            Text(item.name.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleMedium)
                            Text(item.model.ifBlank { "Model —" }, style = MaterialTheme.typography.bodyMedium)
                            Text("${"%.2f".format(item.price)} € · Bestand ${item.quantity}", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (item.state != "SYNCED") AssistChip(onClick = {}, label = { Text(item.state) })
                                if (item.isDraft) AssistChip(onClick = {}, label = { Text("DRAFT") })
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val pages = if (result.total == 0L) 1 else ((result.total + result.pageSize - 1) / result.pageSize).toInt()
            OutlinedButton(onClick = { if (page > 1) page-- }, enabled = page > 1) { Text("←") }
            Text("${result.page} / $pages")
            OutlinedButton(onClick = { if (page < pages) page++ }, enabled = page < pages) { Text("→") }
            Button(onClick = onNew) { Text("＋ Новый") }
        }
    }
}
