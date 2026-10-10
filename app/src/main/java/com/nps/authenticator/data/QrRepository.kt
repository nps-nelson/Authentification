package com.nps.authenticator.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class QrEntry(val id: String, val content: String, val createdAt: Long)

/** Historique des QR scannés qui ne sont pas du 2FA. Chiffré comme les comptes. */
class QrRepository(context: Context) {
    private val dir: File = context.applicationContext.filesDir
    private val file = File(dir, "qrs.bin")
    private val state = MutableStateFlow<List<QrEntry>>(emptyList())
    val entries: StateFlow<List<QrEntry>> = state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val plain = KeystoreCipher.decrypt(file.readBytes())
            val arr = JSONObject(String(plain, Charsets.UTF_8)).getJSONArray("qrs")
            state.value = List(arr.length()) {
                val o = arr.getJSONObject(it)
                QrEntry(o.getString("id"), o.getString("content"), o.optLong("createdAt"))
            }
        } catch (e: Exception) {
            file.renameTo(File(dir, "qrs.bin.corrupt-${System.currentTimeMillis()}"))
        }
    }

    @Synchronized
    private fun persist(list: List<QrEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("content", it.content).put("createdAt", it.createdAt))
        }
        val bytes = KeystoreCipher.encrypt(
            JSONObject().put("qrs", arr).toString().toByteArray(Charsets.UTF_8),
        )
        val tmp = File(dir, "qrs.bin.tmp")
        tmp.writeBytes(bytes)
        Files.move(
            tmp.toPath(), file.toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
        )
        state.value = list
    }

    /** Ajoute en tête ; un contenu déjà présent est simplement remonté. */
    @Synchronized
    fun add(content: String) {
        val rest = state.value.filter { it.content != content }
        val entry = QrEntry(UUID.randomUUID().toString(), content, System.currentTimeMillis())
        persist((listOf(entry) + rest).take(MAX_ENTRIES))
    }

    @Synchronized
    fun delete(id: String) = persist(state.value.filterNot { it.id == id })

    companion object {
        const val MAX_ENTRIES = 200
    }
}
