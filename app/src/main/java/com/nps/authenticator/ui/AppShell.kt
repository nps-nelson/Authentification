package com.nps.authenticator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nps.authenticator.data.Account

@Composable
fun NpsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8AB4FF),
            onPrimary = Color(0xFF002E69),
            primaryContainer = Color(0xFF164A8A),
            onPrimaryContainer = Color(0xFFD6E3FF),
            secondary = Color(0xFF72D5CB),
            background = Color(0xFF101828),
            surface = Color(0xFF172235),
            surfaceVariant = Color(0xFF26354B),
            onSurface = Color(0xFFF2F5FA),
            onSurfaceVariant = Color(0xFFB8C4D5),
            outlineVariant = Color(0xFF3D4B60),
        ),
        content = content,
    )
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Application verrouillée", style = MaterialTheme.typography.headlineSmall)
            Text("Authentifiez-vous pour accéder à vos codes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onUnlock) { Text("Déverrouiller") }
        }
    }
}

@Composable
fun TrashScreen(vm: AppViewModel) {
    val allAccounts by vm.repo.accounts.collectAsState()
    val deleted = remember(allAccounts) {
        allAccounts.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }
    }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            NpsTopBar(
                title = "Corbeille",
                onBack = { vm.back() },
                actions = {
                    if (deleted.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) { Text("Vider") }
                    }
                },
            )
        },
    ) { inset ->
        if (deleted.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inset).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("La corbeille est vide.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(inset)) {
                items(deleted, key = { it.id }) { account ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(account.title)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(account.title, fontWeight = FontWeight.SemiBold)
                            if (account.name.isNotBlank() && account.name != account.title) {
                                Text(account.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                            Text("Supprimé · conservation 90 jours", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        IconButton(onClick = {
                            vm.repo.restore(account.id)
                            vm.toast = "Compte restauré"
                        }) {
                            Icon(Icons.Default.Restore, contentDescription = "Restaurer")
                        }
                        IconButton(onClick = { pendingDelete = account }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Supprimer définitivement", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Supprimer définitivement ?") },
            text = { Text("« ${account.title} » sera supprimé de l’appareil et ne pourra pas être restauré.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.repo.deleteForever(account.id)
                    vm.toast = "Compte supprimé définitivement"
                    pendingDelete = null
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Annuler") } },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Vider la corbeille ?") },
            text = { Text("Tous les comptes supprimés seront effacés définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.repo.emptyTrash()
                    vm.toast = "Corbeille vidée"
                    confirmEmpty = false
                }) { Text("Vider", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Annuler") } },
        )
    }
}
