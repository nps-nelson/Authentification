package com.nps.authenticator.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun isLink(s: String): Boolean =
    s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)

fun qrKind(s: String): String = when {
    isLink(s) -> "Lien"
    s.startsWith("WIFI:", true) -> "Wi-Fi"
    s.startsWith("mailto:", true) -> "E-mail"
    s.startsWith("tel:", true) -> "Téléphone"
    s.startsWith("geo:", true) -> "Lieu"
    else -> "Texte"
}

fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareImage(ctx: Context, bitmap: Bitmap) {
    val dir = File(ctx.cacheDir, "shared_qr").apply { mkdirs() }
    val f = File(dir, "qr.png")
    f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("QR", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Partager le QR").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openLink(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // Aucune application pour ouvrir ce lien.
    }
}
