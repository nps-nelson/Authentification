package com.nps.authenticator.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Icônes de services téléchargées, conservées en local (96x96 PNG). */
class IconStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "icons").also { it.mkdirs() }
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    // Le nom de fichier vient d'un domaine déjà validé par ServiceDomains (a-z, 0-9, '.', '-').
    private fun file(domain: String) = File(dir, "$domain.png")

    fun has(domain: String): Boolean = domain.isNotEmpty() && file(domain).exists()

    fun load(domain: String): ImageBitmap? {
        if (domain.isEmpty()) return null
        cache[domain]?.let { return it }
        val f = file(domain)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.path) ?: return null
        return bmp.asImageBitmap().also { cache[domain] = it }
    }

    fun save(domain: String, bitmap: Bitmap) {
        val tmp = File(dir, "$domain.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val f = file(domain)
        f.delete()
        tmp.renameTo(f)
        cache.remove(domain)
    }
}
