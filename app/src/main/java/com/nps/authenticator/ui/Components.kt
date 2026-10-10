package com.nps.authenticator.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nps.authenticator.data.Account
import com.nps.authenticator.otp.Base32
import com.nps.authenticator.otp.Otp
import com.nps.authenticator.otp.OtpType

/** Code courant d'un compte (TOTP selon l'heure, HOTP selon le compteur). */
fun currentCode(acc: Account, nowMillis: Long): String = runCatching {
    val counter = if (acc.type == OtpType.TOTP) nowMillis / 1000L / acc.period else acc.counter
    Otp.hotp(Base32.decode(acc.secret), counter, acc.digits, acc.algo)
}.getOrDefault("------")

fun formatCode(code: String): String {
    if (code.length < 4) return code
    val cut = if (code.length % 2 == 0) code.length / 2 else 3
    return code.take(cut) + " " + code.drop(cut)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpsTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            }
        },
        actions = actions,
        colors = npsBarColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun npsBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
)

private val avatarPalette = listOf(
    0xFF4FC3F7, 0xFF81C784, 0xFFFFB74D, 0xFFBA68C8,
    0xFFE57373, 0xFF4DB6AC, 0xFFF06292, 0xFF9575CD,
).map { Color(it) }

@Composable
fun Avatar(text: String, size: Dp = 48.dp, icon: ImageBitmap? = null) {
    if (icon != null) {
        // Pastille claire : les logos sombres (ex. GitHub) restent visibles sur le thème sombre.
        Box(
            Modifier.size(size).clip(CircleShape).background(Color(0xFFE8EDF2)),
            contentAlignment = Alignment.Center,
        ) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size * 0.62f))
        }
        return
    }
    val letter = text.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    val c = avatarPalette[(text.lowercase().hashCode() and 0x7FFFFFFF) % avatarPalette.size]
    Box(
        Modifier.size(size).clip(CircleShape).background(c.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = c, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

/** Anneau de compte à rebours (rouge dans les 5 dernières secondes). */
@Composable
fun CountdownRing(remainingMillis: Long, periodSeconds: Int) {
    val fraction = (remainingMillis / (periodSeconds * 1000f)).coerceIn(0f, 1f)
    val seconds = ((remainingMillis + 999) / 1000).toInt()
    val warn = seconds <= 5
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxSize(),
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeWidth = 3.dp,
        )
        Text(
            "$seconds",
            fontSize = 13.sp,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun PasswordDialog(
    title: String,
    confirm: Boolean,
    onDismiss: () -> Unit,
    onOk: (CharArray) -> Unit,
) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val tooShort = confirm && p1.isNotEmpty() && p1.length < 8
    val mismatch = confirm && p2.isNotEmpty() && p1 != p2
    val valid = if (confirm) p1.length >= 8 && p1 == p2 else p1.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = p1,
                    onValueChange = { p1 = it },
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = tooShort,
                    supportingText = { if (confirm) Text("8 caractères minimum") },
                )
                if (confirm) {
                    OutlinedTextField(
                        value = p2,
                        onValueChange = { p2 = it },
                        label = { Text("Confirmer") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = mismatch,
                        supportingText = { if (mismatch) Text("Les mots de passe ne correspondent pas") },
                    )
                    Text(
                        "Sans ce mot de passe, la sauvegarde est irrécupérable.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOk(p1.toCharArray()) }, enabled = valid) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    suggestions: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onOk: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = true,
                )
                if (suggestions.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { s ->
                            AssistChip(onClick = { value = s }, label = { Text(s) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(value.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
