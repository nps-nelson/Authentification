package com.nps.authenticator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: AppViewModel) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { NpsTopBar("Réglages", onBack = { vm.back() }) },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingRow(
                title = "Verrouillage de l'application",
                desc = "Empreinte ou code de l'appareil requis à l'ouverture et après 15 s en arrière-plan.",
                checked = vm.lockEnabled,
                onChange = { vm.setLock(it) },
            )
            SettingRow(
                title = "Bloquer les captures d'écran",
                desc = "Masque aussi l'aperçu dans la liste des applications récentes.",
                checked = vm.secure,
                onChange = { vm.updateSecure(it) },
            )
            SettingRow(
                title = "Télécharger les icônes des services",
                desc = "Quand Internet est disponible, récupère l'icône depuis le site du service " +
                    "(ex. github.com) et la garde en local. Désactivé : aucune connexion de ce type.",
                checked = vm.iconsEnabled,
                onChange = { vm.updateIcons(it) },
            )
            OutlinedButton(onClick = { vm.go(Screen.Trash) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ouvrir la corbeille")
            }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Connexions Internet", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                    Text(
                        "L'application n'envoie jamais vos clés, vos codes ni vos noms de compte. " +
                            "Seul le nom de domaine d'un service est contacté pour récupérer son icône. " +
                            "Vos clés sont chiffrées sur l'appareil (Android Keystore).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            }
            Text("Version 1.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp)
            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
