package de.handeltheke.handeldesk.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.handeltheke.handeldesk.HandelDeskApp
import de.handeltheke.handeldesk.core.HandelthekeSeoPolicy
import de.handeltheke.handeldesk.data.local.ProductEntity
import de.handeltheke.handeldesk.domain.*
import de.handeltheke.handeldesk.ui.ScreenHeader
import de.handeltheke.handeldesk.ui.components.AutoSaveField
import de.handeltheke.handeldesk.ui.components.CachedProductImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@Composable
fun ProductScreen(uuid: String, onBack: () -> Unit, onReplace: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as HandelDeskApp
    val product by app.container.db.products().observe(uuid).collectAsState(initial = null)
    val categories by app.container.db.references().observeCategories().collectAsState(initial = emptyList())
    val manufacturers by app.container.db.references().observeManufacturers().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var primaryLanguageId by remember { mutableStateOf("1") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var manufacturerMenu by remember { mutableStateOf(false) }
    var showNewManufacturer by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var newManufacturerName by rememberSaveable { mutableStateOf("") }
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var uiMessage by remember { mutableStateOf<String?>(null) }
    var donor by remember { mutableStateOf<ProductEntity?>(null) }

    LaunchedEffect(Unit) { primaryLanguageId = app.container.db.settings().get("primary_language_id") ?: "1" }
    LaunchedEffect(product?.sourceProductId) {
        donor = product?.sourceProductId?.let { app.container.db.products().getByProductId(it) }
    }

    val p = product
    if (p == null) {
        Column { ScreenHeader("Товар", onBack = onBack); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        return
    }
    val payload = remember(p.localJson) { JsonTools.objectOf(p.localJson) }
    val core = payload["product"]?.jsonObject ?: buildJsonObject {}
    val descriptions = payload["descriptions"]?.jsonObject ?: buildJsonObject {}
    val desc = descriptions[primaryLanguageId]?.jsonObject ?: descriptions.values.firstOrNull()?.jsonObject ?: buildJsonObject {}
    val selectedCategories = (payload["categories"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.intOrNull }.toSet()
    val manufacturerName = manufacturers.firstOrNull { it.manufacturerId == p.manufacturerId }?.name ?: "—"
    val validation = remember(p.localJson, primaryLanguageId) { ValidationEngine.validate(p, primaryLanguageId) }
    val checklist = remember(p.localJson, donor?.localJson) { donor?.let { ValidationEngine.donorChecklist(p, it) } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(p.name.ifBlank { "Новый товар" }, "${p.productId?.let { "#$it" } ?: "LOCAL"} · ${p.state} · ${p.workflowState}", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val mainImage = core["image"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (mainImage.isNotBlank()) CachedProductImage(mainImage, modifier = Modifier.fillMaxWidth(), size = 160.dp)
                        AutoSaveField(
                            value = core["model"]?.jsonPrimitive?.contentOrNull.orEmpty(), label = "Model",
                            onSave = { app.container.productEditor.updateField(uuid, "product.model", JsonPrimitive(it)) }, modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AutoSaveField(
                                value = core["price"]?.jsonPrimitive?.contentOrNull.orEmpty(), label = "Цена",
                                onSave = { app.container.productEditor.updateField(uuid, "product.price", JsonPrimitive(it.replace(',', '.'))) }, modifier = Modifier.weight(1f)
                            )
                            Column(Modifier.weight(1f)) {
                                Text("Остаток", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedButton(onClick = { scope.launch { app.container.productEditor.adjustQuantity(uuid, -1) } }) { Text("−1") }
                                    Text(p.quantity.toString(), modifier = Modifier.padding(top = 12.dp))
                                    OutlinedButton(onClick = { scope.launch { app.container.productEditor.adjustQuantity(uuid, +1) } }) { Text("+1") }
                                    OutlinedButton(onClick = { scope.launch { app.container.productEditor.adjustQuantity(uuid, +2) } }) { Text("+2") }
                                }
                            }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Статус", modifier = Modifier.weight(1f))
                            Switch(checked = p.status == 1, onCheckedChange = { checked -> scope.launch { app.container.productEditor.updateField(uuid, "product.status", JsonPrimitive(if (checked) 1 else 0)) } })
                        }
                        ExposedDropdownMenuBox(expanded = manufacturerMenu, onExpandedChange = { manufacturerMenu = it }) {
                            OutlinedTextField(value = manufacturerName, onValueChange = {}, readOnly = true, label = { Text("Производитель") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded = manufacturerMenu, onDismissRequest = { manufacturerMenu = false }) {
                                DropdownMenuItem(text = { Text("＋ Новый производитель") }, onClick = { manufacturerMenu = false; showNewManufacturer = true })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("—") }, onClick = { manufacturerMenu = false; scope.launch { app.container.productEditor.updateField(uuid, "product.manufacturer_id", JsonPrimitive(0)) } })
                                manufacturers.forEach { m ->
                                    DropdownMenuItem(text = { Text(m.name) }, onClick = { manufacturerMenu = false; scope.launch { app.container.productEditor.updateField(uuid, "product.manufacturer_id", JsonPrimitive(m.manufacturerId)) } })
                                }
                            }
                        }
                        OutlinedButton(onClick = { showCategories = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Категории: ${selectedCategories.size} · ${categories.filter { it.categoryId in selectedCategories }.take(3).joinToString { it.name }}")
                        }
                        if (p.sourceProductId != null) Button(onClick = { onReplace(uuid) }, modifier = Modifier.fillMaxWidth()) { Text("⚡ Быстрая замена по донору") }
                    }
                }
            }

            if (checklist?.applicable == true) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Donor Checklist", style = MaterialTheme.typography.titleMedium)
                        checklist.items.forEach { item -> Text("${when(item.status){"ok"->"✓";"error"->"✕";else->"⚠"}} ${item.label}${if(item.message.isNotBlank()) " — ${item.message}" else ""}") }
                    }
                }
            }

            if (validation.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Проверка", style = MaterialTheme.typography.titleMedium)
                        validation.forEach { issue -> Text("${if(issue.severity==Severity.ERROR) "✕" else "⚠"} ${issue.message}") }
                    }
                }
            }

            item {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) { Text(if (showAll) "Скрыть полную карточку" else "ПОЛНАЯ КАРТОЧКА") }
            }

            if (showAll) {
                item { ProductSection("General · Контент") {
                    AutoSaveField(desc["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), "Название", { app.container.productEditor.updateField(uuid, "descriptions.$primaryLanguageId.name", JsonPrimitive(it)) }, Modifier.fillMaxWidth())
                    AutoSaveField(desc["description"]?.jsonPrimitive?.contentOrNull.orEmpty(), "Description (HTML)", { app.container.productEditor.updateField(uuid, "descriptions.$primaryLanguageId.description", JsonPrimitive(it)) }, Modifier.fillMaxWidth(), singleLine = false, minLines = 5)
                    AutoSaveField(desc["meta_title"]?.jsonPrimitive?.contentOrNull.orEmpty(), "Meta Title", { app.container.productEditor.updateField(uuid, "descriptions.$primaryLanguageId.meta_title", JsonPrimitive(it)) }, Modifier.fillMaxWidth())
                    AutoSaveField(desc["meta_description"]?.jsonPrimitive?.contentOrNull.orEmpty(), "Meta Description", { app.container.productEditor.updateField(uuid, "descriptions.$primaryLanguageId.meta_description", JsonPrimitive(it)) }, Modifier.fillMaxWidth(), singleLine = false, minLines = 2)
                    AutoSaveField(desc["meta_keyword"]?.jsonPrimitive?.contentOrNull.orEmpty(), "Meta Keywords", { app.container.productEditor.updateField(uuid, "descriptions.$primaryLanguageId.meta_keyword", JsonPrimitive(it)) }, Modifier.fillMaxWidth())
                    AutoSaveField(desc["tag"]?.jsonPrimitive?.contentOrNull.orEmpty(), "Tags", { app.container.productEditor.updateField(uuid, "descriptions.$primaryLanguageId.tag", JsonPrimitive(it)) }, Modifier.fillMaxWidth())
                } }
                item { ProductSection("Data · Данные") {
                    listOf("sku" to "SKU", "upc" to "UPC", "ean" to "EAN", "jan" to "JAN", "isbn" to "ISBN", "mpn" to "MPN", "location" to "Location").forEach { (field,label) ->
                        AutoSaveField(core[field]?.jsonPrimitive?.contentOrNull.orEmpty(), label, { app.container.productEditor.updateField(uuid, "product.$field", JsonPrimitive(it)) }, Modifier.fillMaxWidth())
                    }
                } }
                item { ProductSection("Options · Опции") { OptionsEditor(uuid, payload, app, scope) } }
                item { ProductSection("SEO URL") { SeoEditor(uuid, payload, app) } }
                item { ProductSection("Изображения") {
                    Text("Главное: ${core["image"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "—" }}")
                    val images = (payload["images"] as? JsonArray).orEmpty()
                    Text("Дополнительных: ${images.size}")
                    images.forEachIndexed { i, img -> Text("${i+1}. ${img.jsonObject["image"]?.jsonPrimitive?.contentOrNull.orEmpty()}") }
                } }
                item { ProductSection("Локальная заметка") {
                    AutoSaveField(p.localNote, "Не синхронизируется", { app.container.productEditor.setLocalNote(uuid, it) }, Modifier.fillMaxWidth(), singleLine = false, minLines = 2)
                } }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { app.container.productEditor.addToQueue(uuid) } }, modifier = Modifier.weight(1f)) { Text("В очередь") }
            Button(onClick = {
                scope.launch {
                    runCatching { app.container.productEditor.setWorkflowState(uuid, "READY") }
                        .onFailure { uiMessage = it.message ?: "Не удалось отметить товар готовым" }
                }
            }, modifier = Modifier.weight(1f)) { Text(if (p.workflowState == "READY") "✓ Готов" else "Отметить готовым") }
        }
    }

    if (showCategories) {
        AlertDialog(
            onDismissRequest = { showCategories = false },
            title = { Text("Категории") },
            text = {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    item {
                        TextButton(onClick = { showCategories = false; showNewCategory = true }) { Text("＋ Новая категория") }
                        HorizontalDivider()
                    }
                    itemsIndexed(categories) { _, c ->
                        val checked = c.categoryId in selectedCategories
                        Row(Modifier.fillMaxWidth().clickable {
                            val next = selectedCategories.toMutableSet().apply { if (checked) remove(c.categoryId) else add(c.categoryId) }.sorted()
                            scope.launch { app.container.productEditor.updateField(uuid, "categories", JsonArray(next.map(::JsonPrimitive))) }
                        }.padding(vertical = 6.dp)) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(c.name, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategories = false }) { Text("Готово") } }
        )
    }

    if (showNewManufacturer) {
        AlertDialog(
            onDismissRequest = { showNewManufacturer = false },
            title = { Text("Новый производитель") },
            text = { OutlinedTextField(newManufacturerName, { newManufacturerName = it }, label = { Text("Название") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { app.container.references.createManufacturer(newManufacturerName) }
                            .onSuccess { created ->
                                app.container.productEditor.updateField(uuid, "product.manufacturer_id", JsonPrimitive(created.manufacturerId))
                                newManufacturerName = ""; showNewManufacturer = false
                            }.onFailure { uiMessage = it.message }
                    }
                }) { Text("Создать и выбрать") }
            },
            dismissButton = { TextButton(onClick = { showNewManufacturer = false }) { Text("Отмена") } }
        )
    }

    if (showNewCategory) {
        AlertDialog(
            onDismissRequest = { showNewCategory = false },
            title = { Text("Новая категория") },
            text = { OutlinedTextField(newCategoryName, { newCategoryName = it }, label = { Text("Название") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { app.container.references.createCategory(newCategoryName, primaryLanguageId = primaryLanguageId) }
                            .onSuccess { created ->
                                val next = (selectedCategories + created.categoryId).sorted()
                                app.container.productEditor.updateField(uuid, "categories", JsonArray(next.map(::JsonPrimitive)))
                                newCategoryName = ""; showNewCategory = false
                            }.onFailure { uiMessage = it.message }
                    }
                }) { Text("Создать и выбрать") }
            },
            dismissButton = { TextButton(onClick = { showNewCategory = false }) { Text("Отмена") } }
        )
    }

    if (uiMessage != null) {
        AlertDialog(
            onDismissRequest = { uiMessage = null },
            title = { Text("HandelDesk") },
            text = { Text(uiMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = { uiMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun ProductSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(title.startsWith("General")) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(if (open) "⌃" else "⌄")
            }
            if (open) { Spacer(Modifier.height(8.dp)); Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
        }
    }
}

@Composable
private fun OptionsEditor(uuid: String, payload: JsonObject, app: HandelDeskApp, scope: kotlinx.coroutines.CoroutineScope) {
    val options = (payload["options"] as? JsonArray).orEmpty()
    if (options.isEmpty()) { Text("Опций нет"); return }
    options.forEachIndexed { oi, optElement ->
        val option = optElement.jsonObject
        Text("Option ID ${option["option_id"]?.jsonPrimitive?.contentOrNull ?: "?"}", style = MaterialTheme.typography.titleSmall)
        val values = (option["values"] as? JsonArray).orEmpty()
        values.forEachIndexed { vi, valueElement ->
            val value = valueElement.jsonObject
            val q = value["quantity"]?.jsonPrimitive?.intOrNull ?: 0
            val price = value["price"]?.jsonPrimitive?.contentOrNull.orEmpty()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("#${value["option_value_id"]?.jsonPrimitive?.contentOrNull ?: "?"}", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    val newValues = values.toMutableList(); newValues[vi] = JsonObject(value.toMutableMap().apply { put("quantity", JsonPrimitive(q - 1)) })
                    val newOption = JsonObject(option.toMutableMap().apply { put("values", JsonArray(newValues)) })
                    val newOptions = options.toMutableList(); newOptions[oi] = newOption
                    scope.launch { app.container.productEditor.updateField(uuid, "options", JsonArray(newOptions)) }
                }) { Text("−") }
                Text(q.toString(), modifier = Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = {
                    val newValues = values.toMutableList(); newValues[vi] = JsonObject(value.toMutableMap().apply { put("quantity", JsonPrimitive(q + 1)) })
                    val newOption = JsonObject(option.toMutableMap().apply { put("values", JsonArray(newValues)) })
                    val newOptions = options.toMutableList(); newOptions[oi] = newOption
                    scope.launch { app.container.productEditor.updateField(uuid, "options", JsonArray(newOptions)) }
                }) { Text("+") }
                Text("€ $price", modifier = Modifier.padding(top = 12.dp))
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun SeoEditor(uuid: String, payload: JsonObject, app: HandelDeskApp) {
    val rows = (payload["seo_urls"] as? JsonArray).orEmpty()
    if (rows.isEmpty()) { Text("SEO URL отсутствует"); return }
    rows.forEachIndexed { index, rowElement ->
        val row = rowElement.jsonObject
        AutoSaveField(
            value = row["keyword"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            label = "SEO · lang ${row["language_id"]?.jsonPrimitive?.contentOrNull ?: "?"}",
            onSave = { raw -> app.container.productEditor.updateField(uuid, "seo_urls.$index.keyword", JsonPrimitive(HandelthekeSeoPolicy.slug(raw))) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
