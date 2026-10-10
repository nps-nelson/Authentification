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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nps.authenticator.data.QrEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Historique des QR scannés qui ne sont pas du 2FA : Copier, Partager, Supprimer (+ Ouvrir pour un lien). */
@Composable
fun QrTab(vm: AppViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val entries by vm.qrs.entries.collectAsState()
    var detail by remember { mutableStateOf<QrEntry?>(null) }
    var toOpen by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    if (entries.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Aucun QR enregistré.\nLes QR scannés qui ne sont pas du 2FA (liens, texte…) apparaissent ici.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(modifier.fillMaxSize()) {
            items(entries, key = { it.id }) { e ->
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clickable { detail = e }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${qrKind(e.content)} · ${fmt.format(Date(e.createdAt))}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(e.content, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Copier") }, onClick = {
                                menu = false
                                vm.copyText(e.content)
                            })
                            DropdownMenuItem(text = { Text("Partager") }, onClick = {
                                menu = false
                                shareText(ctx, e.content)
                            })
                            if (isLink(e.content)) {
                                DropdownMenuItem(text = { Text("Ouvrir") }, onClick = {
                                    menu = false
                                    toOpen = e.content
                                })
                            }
                            DropdownMenuItem(
                                text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menu = false
                                    vm.qrs.delete(e.id)
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item(key = "bottom_space") { Spacer(Modifier.height(88.dp)) }
        }
    }

    detail?.let { e ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(qrKind(e.content)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        Text(e.content, fontSize = 15.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.copyText(e.content) }) { Text("Copier") }
                        TextButton(onClick = { shareText(ctx, e.content) }) { Text("Partager") }
                        if (isLink(e.content)) {
                            TextButton(onClick = {
                                toOpen = e.content
                                detail = null
                            }) { Text("Ouvrir") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Fermer") } },
        )
    }

    toOpen?.let { url ->
        AlertDialog(
            onDismissRequest = { toOpen = null },
            title = { Text("Ouvrir ce lien ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(url, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Vérifiez l'adresse : un QR peut mener vers un site piégé.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    toOpen = null
                    openLink(ctx, url)
                }) { Text("Ouvrir") }
            },
            dismissButton = { TextButton(onClick = { toOpen = null }) { Text("Annuler") } },
        )
    }
}
