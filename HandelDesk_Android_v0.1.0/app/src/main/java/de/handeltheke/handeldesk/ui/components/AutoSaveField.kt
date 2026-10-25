package de.handeltheke.handeldesk.ui.components

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@Composable
fun AutoSaveField(
    value: String,
    label: String,
    onSave: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    var local by remember(value) { mutableStateOf(value) }
    var initialized by remember(value) { mutableStateOf(false) }
    LaunchedEffect(local) {
        if (!initialized) { initialized = true; return@LaunchedEffect }
        delay(500)
        if (local != value) onSave(local)
    }
    OutlinedTextField(
        value = local,
        onValueChange = { local = it },
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
    )
}
