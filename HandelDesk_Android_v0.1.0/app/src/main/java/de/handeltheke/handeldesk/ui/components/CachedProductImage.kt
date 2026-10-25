package de.handeltheke.handeldesk.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.handeltheke.handeldesk.HandelDeskApp

@Composable
fun CachedProductImage(remotePath: String, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val app = LocalContext.current.applicationContext as HandelDeskApp
    val file = remember(remotePath) { app.container.imageCache.cachedFile(remotePath) }
    val bitmap = remember(file?.absolutePath, file?.lastModified()) {
        file?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) }?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
