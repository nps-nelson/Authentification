package com.nps.authenticator.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BackupTab(vm: AppViewModel, modifier: Modifier = Modifier) {
    val accounts by vm.repo.accounts.collectAsState()
    var exportUri by remember { mutableStateOf<Uri?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        exportUri = uri
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importUri = uri
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Sauvegarde chiffrée", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Exportez vos comptes dans un fichier protégé par un mot de passe. Gardez ce fichier et son mot de passe en lieu sûr.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                "${accounts.count { it.deletedAt == null }} compte(s) actif(s). Le mot de passe de sauvegarde n’est pas conservé par l’application.",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { createBackup.launch("authentification.npsbak") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Exporter une sauvegarde") }
        OutlinedButton(
            onClick = { openBackup.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Importer une sauvegarde") }
    }

    exportUri?.let { uri ->
        PasswordDialog(
            title = "Protéger la sauvegarde",
            confirm = true,
            onDismiss = { exportUri = null },
            onOk = { password ->
                exportUri = null
                vm.exportTo(uri, password)
            },
        )
    }
    importUri?.let { uri ->
        PasswordDialog(
            title = "Ouvrir la sauvegarde",
            confirm = false,
            onDismiss = { importUri = null },
            onOk = { password ->
                importUri = null
                vm.importFrom(uri, password)
            },
        )
    }
}
