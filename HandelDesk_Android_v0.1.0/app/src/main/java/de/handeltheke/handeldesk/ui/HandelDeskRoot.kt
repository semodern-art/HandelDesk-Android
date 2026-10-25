package de.handeltheke.handeldesk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import de.handeltheke.handeldesk.ui.screens.*

@Composable
fun HandelDeskRoot() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route.orEmpty()
    Scaffold(
        bottomBar = {
            if (!route.startsWith("product/") && !route.startsWith("donor_replace/")) {
                NavigationBar {
                    listOf(
                        Triple("products", "Товары", "▦"),
                        Triple("work", "Работа", "✓"),
                        Triple("new", "＋", "+"),
                        Triple("changes", "Изменения", "Δ"),
                        Triple("more", "Ещё", "⋯")
                    ).forEach { (r, label, symbol) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = { nav.navigate(r) { launchSingleTop = true } },
                            icon = { Text(symbol) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "products", modifier = Modifier.padding(padding)) {
            composable("products") { ProductsScreen(onOpen = { nav.navigate("product/$it") }, onNew = { nav.navigate("new") }) }
            composable("work") { WorkScreen(onOpen = { nav.navigate("product/$it") }) }
            composable("new") { NewProductScreen(onCreated = { uuid, fromDonor -> nav.navigate(if (fromDonor) "donor_replace/$uuid" else "product/$uuid") }) }
            composable("changes") { ChangesScreen(onOpen = { nav.navigate("product/$it") }, onSync = { nav.navigate("sync") }) }
            composable("more") { MoreScreen(onPrepare = { nav.navigate("prepare") }, onSync = { nav.navigate("sync") }, onSettings = { nav.navigate("settings") }, onAudit = { nav.navigate("audit") }) }
            composable("prepare") { PrepareOfflineScreen(onBack = { nav.popBackStack() }) }
            composable("sync") { SyncScreen(onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable("audit") { AuditScreen(onBack = { nav.popBackStack() }) }
            composable("product/{uuid}", arguments = listOf(navArgument("uuid") { type = NavType.StringType })) { entry ->
                ProductScreen(uuid = entry.arguments?.getString("uuid").orEmpty(), onBack = { nav.popBackStack() }, onReplace = { nav.navigate("donor_replace/$it") })
            }
            composable("donor_replace/{uuid}", arguments = listOf(navArgument("uuid") { type = NavType.StringType })) { entry ->
                DonorReplaceScreen(uuid = entry.arguments?.getString("uuid").orEmpty(), onDone = { nav.navigate("product/$it") { popUpTo("products") } })
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) TextButton(onClick = onBack) { Text("←") }
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
