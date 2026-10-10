package com.nps.authenticator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nps.authenticator.data.Account
import com.nps.authenticator.otp.Otp
import com.nps.authenticator.otp.OtpType
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val all by vm.repo.accounts.collectAsState()
    val active = remember(all) { all.filter { it.deletedAt == null } }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var groupTarget by remember { mutableStateOf<Account?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (searching && vm.tab == 0) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text("Rechercher") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Text("Authentificateur")
                    }
                },
                actions = {
                    if (vm.tab == 0) {
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        }) {
                            Icon(if (searching) Icons.Default.Close else Icons.Default.Search, "Rechercher")
                        }
                    }
                    IconButton(onClick = { vm.go(Screen.Settings) }) {
                        Icon(Icons.Default.Settings, "Réglages")
                    }
                },
                colors = npsBarColors(),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = vm.tab == 0,
                    onClick = { vm.tab = 0 },
                    icon = { Icon(Icons.Default.Lock, null) },
                    label = { Text("2FA") },
                    colors = navColors(),
                )
                NavigationBarItem(
                    selected = vm.tab == 1,
                    onClick = { vm.tab = 1 },
                    icon = { Icon(Icons.Default.Menu, null) },
                    label = { Text("QR") },
                    colors = navColors(),
                )
                NavigationBarItem(
                    selected = vm.tab == 2,
                    onClick = { vm.tab = 2 },
                    icon = { Icon(Icons.Default.Refresh, null) },
                    label = { Text("Sauvegarde") },
                    colors = navColors(),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.go(Screen.Add) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Default.Add, "Ajouter un compte") }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { pad ->
        if (vm.tab == 1) {
            QrTab(vm, Modifier.padding(pad))
        } else if (vm.tab == 2) {
            BackupTab(vm, Modifier.padding(pad))
        } else {
            val filtered = remember(active, query) {
                if (query.isBlank()) active
                else active.filter { "${it.issuer} ${it.name}".contains(query.trim(), ignoreCase = true) }
            }
            val groups = remember(filtered) {
                filtered.groupBy { it.group }.toSortedMap(compareBy<String> { it.lowercase() })
            }

            LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                if (vm.repo.loadFailed) {
                    item(key = "load_failed") {
                        Card(
                            Modifier.fillMaxWidth().padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Text(
                                "Le fichier des comptes n'a pas pu être lu (clé de chiffrement perdue ?). " +
                                    "Il a été conservé à part, non supprimé. Vous pouvez restaurer une sauvegarde.",
                                Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (active.isEmpty()) "Aucun compte.\nAppuyez sur + pour en ajouter un."
                                else "Aucun résultat.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                groups.forEach { (g, list) ->
                    if (g.isNotBlank()) {
                        item(key = "group_$g") {
                            Text(
                                g,
                                Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    items(list, key = { it.id }) { acc ->
                        val icon = remember(acc.iconDomain, vm.iconVersion) { vm.icons.load(acc.iconDomain) }
                        AccountRow(
                            acc = acc,
                            icon = icon,
                            now = now,
                            onCopy = { vm.copy(currentCode(acc, System.currentTimeMillis())) },
                            onOpen = { vm.go(Screen.Detail(acc.id)) },
                            onGroup = { groupTarget = acc },
                            onNext = { vm.repo.incrementCounter(acc.id) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                item(key = "bottom_space") { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    groupTarget?.let { target ->
        TextInputDialog(
            title = "Groupe",
            initial = target.group,
            label = "Nom du groupe (vide = aucun)",
            suggestions = active.map { it.group }.filter { it.isNotBlank() }.distinct(),
            onDismiss = { groupTarget = null },
            onOk = {
                vm.repo.setGroup(target.id, it)
                groupTarget = null
            },
        )
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun AccountRow(
    acc: Account,
    icon: ImageBitmap?,
    now: Long,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onGroup: () -> Unit,
    onNext: () -> Unit,
) {
    val code = currentCode(acc, now)
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onCopy).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(acc.title, icon = icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                acc.issuer.ifBlank { acc.name },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (acc.issuer.isNotBlank() && acc.name.isNotBlank()) {
                Text(
                    acc.name,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatCode(code),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (acc.type == OtpType.TOTP) {
            CountdownRing(Otp.remainingMillis(now, acc.period), acc.period)
        } else {
            IconButton(onClick = onNext) { Icon(Icons.Default.Refresh, "Code suivant") }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Copier") }, onClick = { menu = false; onCopy() })
                DropdownMenuItem(text = { Text("Détails") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Groupe") }, onClick = { menu = false; onGroup() })
            }
        }
    }
}
