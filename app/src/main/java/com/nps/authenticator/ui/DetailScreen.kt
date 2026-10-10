package com.nps.authenticator.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nps.authenticator.otp.Otp
import com.nps.authenticator.otp.OtpAuthUri
import com.nps.authenticator.otp.OtpType
import kotlinx.coroutines.delay

@Composable
fun DetailScreen(vm: AppViewModel, id: String) {
    val all by vm.repo.accounts.collectAsState()
    val acc = all.firstOrNull { it.id == id && it.deletedAt == null }
    if (acc == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var renaming by remember { mutableStateOf(false) }
    var grouping by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showSecret by remember { mutableStateOf(false) }
    var editingDomain by remember { mutableStateOf(false) }
    var qrWarn by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }

    val code = currentCode(acc, now)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            NpsTopBar(
                title = acc.title,
                onBack = { vm.back() },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Supprimer") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Label("Nom du compte")
            FieldCard {
                Text(acc.name.ifBlank { "—" }, Modifier.weight(1f), fontSize = 16.sp)
                IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Modifier") }
            }

            Label("Groupe")
            FieldCard {
                Text(acc.group.ifBlank { "Aucun" }, Modifier.weight(1f), fontSize = 16.sp)
                IconButton(onClick = { grouping = true }) { Icon(Icons.Default.Edit, "Modifier") }
            }

            Label("Icône du service (domaine)")
            FieldCard {
                Text(acc.iconDomain.ifBlank { "Aucun (lettre)" }, Modifier.weight(1f), fontSize = 16.sp)
                IconButton(onClick = { editingDomain = true }) { Icon(Icons.Default.Edit, "Modifier") }
            }

            Label("Code d'authentification")
            FieldCard {
                if (acc.type == OtpType.TOTP) {
                    CountdownRing(Otp.remainingMillis(now, acc.period), acc.period)
                } else {
                    IconButton(onClick = { vm.repo.incrementCounter(acc.id) }) {
                        Icon(Icons.Default.Refresh, "Code suivant")
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    formatCode(code),
                    Modifier.weight(1f),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                TextButton(onClick = { vm.copy(code) }) { Text("Copier") }
            }

            Label("Détails")
            Text(
                "${acc.type.name} · ${acc.algo.name} · ${acc.digits} chiffres" +
                    if (acc.type == OtpType.TOTP) " · ${acc.period} s" else " · compteur ${acc.counter}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(onClick = { qrWarn = true }, Modifier.fillMaxWidth()) {
                Text("Afficher le QR (partager / enregistrer)")
            }
            OutlinedButton(onClick = { showSecret = !showSecret }, Modifier.fillMaxWidth()) {
                Text(if (showSecret) "Masquer la clé secrète" else "Afficher la clé secrète")
            }
            if (showSecret) {
                Text(
                    acc.secret.chunked(4).joinToString(" "),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Ne partagez jamais cette clé : quiconque la possède peut générer vos codes.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }
        }
    }

    if (editingDomain) {
        TextInputDialog(
            title = "Domaine de l'icône",
            initial = acc.domain,
            label = "ex. netlify.com (vide = automatique)",
            onDismiss = { editingDomain = false },
            onOk = {
                vm.setDomain(acc.id, it)
                editingDomain = false
            },
        )
    }
    if (qrWarn) {
        AlertDialog(
            onDismissRequest = { qrWarn = false },
            title = { Text("Afficher le QR ?") },
            text = {
                Text(
                    "Ce QR contient la clé secrète du compte. Quiconque le voit ou le reçoit peut " +
                        "générer vos codes. Ne le partagez qu'avec un appareil de confiance.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    qrWarn = false
                    qr = runCatching { QrGenerator.create(OtpAuthUri.build(acc)) }.getOrNull()
                    if (qr == null) vm.toast = "Impossible de générer le QR"
                }) { Text("Afficher") }
            },
            dismissButton = { TextButton(onClick = { qrWarn = false }) { Text("Annuler") } },
        )
    }
    qr?.let { bmp ->
        QrDialog(
            bitmap = bmp,
            fileName = "qr-" + acc.title.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png",
            vm = vm,
            onDismiss = { qr = null },
        )
    }
    if (renaming) {
        TextInputDialog(
            title = "Nom du compte",
            initial = acc.name,
            label = "Nom",
            onDismiss = { renaming = false },
            onOk = {
                vm.repo.rename(acc.id, it)
                renaming = false
            },
        )
    }
    if (grouping) {
        TextInputDialog(
            title = "Groupe",
            initial = acc.group,
            label = "Nom du groupe (vide = aucun)",
            suggestions = all.filter { it.deletedAt == null }.map { it.group }.filter { it.isNotBlank() }.distinct(),
            onDismiss = { grouping = false },
            onOk = {
                vm.repo.setGroup(acc.id, it)
                grouping = false
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce compte ?") },
            text = {
                Text(
                    "Il sera conservé 90 jours dans « Récemment supprimé ». " +
                        "Vérifiez que vous ne perdez pas l'accès au service avant de supprimer.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.repo.moveToTrash(acc.id)
                    vm.toast = "Déplacé dans la corbeille"
                    vm.back()
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FieldCard(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
