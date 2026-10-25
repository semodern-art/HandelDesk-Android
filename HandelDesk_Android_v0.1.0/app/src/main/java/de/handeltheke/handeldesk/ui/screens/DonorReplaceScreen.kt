package de.handeltheke.handeldesk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.handeltheke.handeldesk.HandelDeskApp
import de.handeltheke.handeldesk.domain.JsonTools
import de.handeltheke.handeldesk.domain.ValidationEngine
import de.handeltheke.handeldesk.ui.ScreenHeader
import kotlinx.coroutines.launch

@Composable
fun DonorReplaceScreen(uuid: String, onDone: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as HandelDeskApp
    val product by app.container.db.products().observe(uuid).collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var donorModel by remember { mutableStateOf("") }
    var newModel by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var donor by remember { mutableStateOf<de.handeltheke.handeldesk.data.local.ProductEntity?>(null) }

    LaunchedEffect(product?.sourceProductId) {
        donor = product?.sourceProductId?.let { app.container.db.products().getByProductId(it) }
        donorModel = donor?.model.orEmpty()
    }
    val p = product
    if (p == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    val checklist = donor?.let { ValidationEngine.donorChecklist(p, it) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Быстрая замена по донору", p.name)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(donorModel, { donorModel = it }, label = { Text("Старая модель / строка") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(newModel, { newModel = it }, label = { Text("Новая модель / строка") }, modifier = Modifier.fillMaxWidth())
            Text("Будут проверены Model, Name, Description, Tags, Meta и SEO. Уникальные SKU/JAN/EAN автоматически не меняются.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                scope.launch {
                    runCatching { app.container.productEditor.donorQuickReplace(uuid, donorModel, newModel) }
                        .onSuccess { message = "Изменено полей: $it" }
                        .onFailure { message = it.message }
                }
            }, enabled = donorModel.isNotBlank() && newModel.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("ПРИМЕНИТЬ ЗАМЕНУ") }
            message?.let { Text(it) }
            if (checklist?.applicable == true) {
                HorizontalDivider()
                Text("Donor Checklist", style = MaterialTheme.typography.titleMedium)
                checklist.items.forEach { item -> Text("${when(item.status){"ok"->"✓";"error"->"✕";else->"⚠"}} ${item.label} ${item.message}") }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onDone(uuid) }, modifier = Modifier.fillMaxWidth()) { Text("К КАРТОЧКЕ ТОВАРА") }
        }
    }
}
