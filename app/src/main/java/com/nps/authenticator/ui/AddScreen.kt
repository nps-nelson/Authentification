package com.nps.authenticator.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nps.authenticator.otp.Base32
import com.nps.authenticator.otp.OtpAlgo
import com.nps.authenticator.otp.OtpType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun AddScreen(vm: AppViewModel) {
    var page by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { NpsTopBar("Ajouter un compte", onBack = { vm.back() }) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(
                selectedTabIndex = page,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(selected = page == 0, onClick = { page = 0 }, text = { Text("Scanner") })
                Tab(selected = page == 1, onClick = { page = 1 }, text = { Text("Saisie manuelle") })
            }
            if (page == 0) ScanPage(vm) else ManualPage(vm)
        }
    }
}

@Composable
private fun ScanPage(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    var torch by remember { mutableStateOf(false) }
    val busy = remember { AtomicBoolean(false) }

    fun handle(raw: String) {
        if (!busy.compareAndSet(false, true)) return
        if (vm.handleScanned(raw)) {
            vm.go(Screen.Home)
        } else {
            scope.launch {
                delay(2000)
                busy.set(false)
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scanImage(ctx, uri) { raw ->
                if (raw != null) handle(raw) else vm.toast = "Aucun code QR détecté dans cette image"
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            QrScannerView(torch = torch, onCode = { handle(it) }, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.Center).size(240.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "L'accès à la caméra est nécessaire pour scanner un QR code.\n" +
                        "Sinon, utilisez « Image » ou la saisie manuelle.",
                    textAlign = TextAlign.Center,
                    color = Color.White,
                )
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Autoriser la caméra") }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (granted) {
                OutlinedButton(onClick = { torch = !torch }) { Text(if (torch) "Flash : ON" else "Flash") }
            }
            OutlinedButton(onClick = { pick.launch("image/*") }) { Text("Image") }
        }
    }
}

@Composable
private fun ManualPage(vm: AppViewModel) {
    var issuer by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(OtpType.TOTP) }
    var algo by remember { mutableStateOf(OtpAlgo.SHA1) }
    var digits by remember { mutableIntStateOf(6) }
    var period by remember { mutableStateOf("30") }

    val secretError = secret.isNotBlank() && runCatching { Base32.decode(secret) }.isFailure
    val periodInt = period.toIntOrNull()
    val valid = secret.isNotBlank() && !secretError &&
        (issuer.isNotBlank() || name.isNotBlank()) &&
        (type == OtpType.HOTP || (periodInt != null && periodInt in 1..3600))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = issuer,
            onValueChange = { issuer = it },
            label = { Text("Service (ex. GitHub)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nom du compte (ex. moi@mail.com)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            label = { Text("Clé secrète (Base32)") },
            singleLine = true,
            isError = secretError,
            supportingText = { if (secretError) Text("Clé invalide : lettres A–Z et chiffres 2–7 uniquement") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Password,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Type", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OtpType.entries.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name) })
            }
        }
        Text("Algorithme", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OtpAlgo.entries.forEach { a ->
                FilterChip(selected = algo == a, onClick = { algo = a }, label = { Text(a.name) })
            }
        }
        Text("Chiffres", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6, 7, 8).forEach { d ->
                FilterChip(selected = digits == d, onClick = { digits = d }, label = { Text("$d") })
            }
        }
        if (type == OtpType.TOTP) {
            OutlinedTextField(
                value = period,
                onValueChange = { period = it.filter(Char::isDigit).take(4) },
                label = { Text("Période (secondes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = {
                if (vm.addManual(issuer, name, secret, type, algo, digits, periodInt ?: 30)) {
                    vm.go(Screen.Home)
                }
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Ajouter") }
    }
}
