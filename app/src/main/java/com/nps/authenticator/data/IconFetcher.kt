package com.nps.authenticator.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface FetchResult {
    data class Ok(val bitmap: Bitmap) : FetchResult

    /** Le site a répondu mais n'a pas d'icône exploitable. */
    data object NotFound : FetchResult

    /** Erreur réseau : on réessaiera plus tard. */
    data object Transient : FetchResult
}

/**
 * Télécharge l'icône directement depuis le site du service (aucun service tiers).
 * Seul le nom de domaine est contacté : jamais de clé, de code ni de nom de compte.
 */
object IconFetcher {
    private const val MAX_BYTES = 512 * 1024
    private const val TARGET = 96
    private val PATHS = listOf("/apple-touch-icon.png", "/favicon.ico")

    fun fetch(domain: String): FetchResult {
        var transient = false
        for (path in PATHS) {
            val res = runCatching { download("https://$domain$path") }
            if (res.isFailure) {
                transient = true
                continue
            }
            val bytes = res.getOrNull() ?: continue
            decode(bytes)?.let { return FetchResult.Ok(it) }
        }
        return if (transient) FetchResult.Transient else FetchResult.NotFound
    }

    private fun download(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "NPS-Authentificateur/1.0")
            if (conn.responseCode != 200) return null
            if (conn.url.protocol != "https") return null
            if (conn.contentLengthLong > MAX_BYTES) return null
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            conn.inputStream.use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return null
                }
            }
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w < 16 || h < 16 || w > 2048 || h > 2048) return null
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scale = minOf(TARGET.toFloat() / src.width, TARGET.toFloat() / src.height)
        val nw = (src.width * scale).toInt().coerceAtLeast(1)
        val nh = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, nw, nh, true)
        val out = Bitmap.createBitmap(TARGET, TARGET, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(scaled, (TARGET - nw) / 2f, (TARGET - nh) / 2f, null)
        return out
    }
}
