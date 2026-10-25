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
import de.handeltheke.handeldesk.data.local.ProductEntity
import de.handeltheke.handeldesk.ui.ScreenHeader
import kotlinx.coroutines.launch

@Composable
fun NewProductScreen(onCreated: (String, Boolean) -> Unit) {
    val app = LocalContext.current.applicationContext as HandelDeskApp
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf("menu") }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(ProductPage(emptyList(), 0, 1, 20, emptyList())) }
    var loading by remember { mutableStateOf(false) }
    val defaultSections = setOf("core_settings","price","descriptions","categories","manufacturer","attributes","options","extension_fields","images","stores","seo_urls")
    var donorToClone by remember { mutableStateOf<ProductEntity?>(null) }
    var cloneSections by remember { mutableStateOf(defaultSections) }
    val sectionLabels = listOf(
        "descriptions" to "Названия / описание / Meta", "categories" to "Категории", "manufacturer" to "Производитель",
        "attributes" to "Атрибуты", "options" to "Опции", "images" to "Изображения", "seo_urls" to "SEO URL-шаблон",
        "stores" to "Stores", "related" to "Related Products", "price" to "Цена", "core_settings" to "Системные настройки",
        "extension_fields" to "Дополнительные поля", "specials" to "Specials", "discounts" to "Discounts"
    )

    LaunchedEffect(query, mode) {
        if (mode == "donor") {
            loading = true
            results = app.container.productSearch.search(query, pageSize = 20)
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (mode == "donor") "Выбрать донора" else "Новый товар")
        if (mode == "menu") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { mode = "donor" }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Из донора") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val lang = app.container.db.settings().get("primary_language_id") ?: "1"
                        onCreated(app.container.productEditor.createBlank(lang), false)
                    }
                }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Пустой товар") }
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Из шаблона — следующий этап") }
            }
        } else {
            Column(Modifier.padding(horizontal = 12.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Найти донор") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Поддерживаются кавычки, -слово, OR, model:, sku:, jan:", style = MaterialTheme.typography.bodySmall)
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results.items, key = { it.uuid }) { donor ->
                    Card(Modifier.fillMaxWidth().clickable { donorToClone = donor; cloneSections = defaultSections }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(donor.name, style = MaterialTheme.typography.titleMedium)
                            Text("${donor.model} · #${donor.productId ?: "LOCAL"}")
                            Text("${"%.2f".format(donor.price)} € · Bestand ${donor.quantity}")
                        }
                    }
                }
            }
            TextButton(onClick = { mode = "menu" }, modifier = Modifier.padding(12.dp)) { Text("← Назад") }
        }
    }
    donorToClone?.let { donor ->
        AlertDialog(
            onDismissRequest = { donorToClone = null },
            title = { Text("Создать из донора #${donor.productId ?: "LOCAL"}") },
            text = {
                LazyColumn(Modifier.heightIn(max = 500.dp)) {
                    item { Text(donor.name, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(8.dp)) }
                    items(sectionLabels.size) { idx ->
                        val (key, label) = sectionLabels[idx]
                        val checked = key in cloneSections
                        Row(Modifier.fillMaxWidth().clickable { cloneSections = if (checked) cloneSections - key else cloneSections + key }.padding(vertical = 4.dp)) {
                            Checkbox(checked, onCheckedChange = null)
                            Text(label, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val lang = app.container.db.settings().get("primary_language_id") ?: "1"
                        val uuid = app.container.productEditor.createFromDonor(donor.uuid, cloneSections, lang)
                        donorToClone = null
                        onCreated(uuid, true)
                    }
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { donorToClone = null }) { Text("Отмена") } }
        )
    }

}
