package com.nps.authenticator.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** QR d'un compte : Partager (feuille Android) ou Enregistrer (sélecteur de fichier, sans permission). */
@Composable
fun QrDialog(bitmap: Bitmap, fileName: String, vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) vm.saveImage(uri, bitmap)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Un QR a besoin d'un fond clair pour rester lisible par les scanners.
                    Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR code du compte",
                            modifier = Modifier.size(240.dp),
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
                Text(
                    "Contient la clé secrète : ne le partagez qu'avec un appareil de confiance.",
                    Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { shareImage(ctx, bitmap) }) { Text("Partager") }
                TextButton(onClick = { save.launch(fileName) }) { Text("Enregistrer") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
