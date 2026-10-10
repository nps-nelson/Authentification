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

/**
 * Stockage local des comptes : un seul fichier chiffré (AES-GCM / Keystore),
 * écrit de façon atomique. Les comptes supprimés restent 90 jours dans la corbeille.
 */
class AccountRepository(context: Context) {
    private val dir: File = context.applicationContext.filesDir
    private val file = File(dir, "accounts.bin")
    private val state = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = state.asStateFlow()

    /** Vrai si le fichier existant n'a pas pu être lu (il est alors mis de côté, jamais écrasé). */
    var loadFailed: Boolean = false
        private set

    init {
        load()
        purgeExpired()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val plain = KeystoreCipher.decrypt(file.readBytes())
            val arr = JSONObject(String(plain, Charsets.UTF_8)).getJSONArray("accounts")
            state.value = List(arr.length()) { Account.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            loadFailed = true
            file.renameTo(File(dir, "accounts.bin.corrupt-${System.currentTimeMillis()}"))
        }
    }

    @Synchronized
    private fun persist(list: List<Account>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val bytes = KeystoreCipher.encrypt(
            JSONObject().put("accounts", arr).toString().toByteArray(Charsets.UTF_8),
        )
        val tmp = File(dir, "accounts.bin.tmp")
        tmp.writeBytes(bytes)
        Files.move(
            tmp.toPath(), file.toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
        )
        state.value = list
    }

    @Synchronized
    private fun update(f: (List<Account>) -> List<Account>) = persist(f(state.value))

    private fun List<Account>.mapId(id: String, f: (Account) -> Account) =
        map { if (it.id == id) f(it) else it }

    /** Retourne false si le même compte existe déjà. */
    @Synchronized
    fun add(acc: Account): Boolean {
        if (state.value.any { it.deletedAt == null && it.sameKey(acc) }) return false
        persist(state.value + acc)
        return true
    }

    /** Fusionne une liste (import). Retourne le nombre de comptes réellement ajoutés. */
    @Synchronized
    fun merge(list: List<Account>): Int {
        var added = 0
        var current = state.value
        for (a in list) {
            if (current.any { it.sameKey(a) }) continue
            val safe = if (current.any { it.id == a.id }) a.copy(id = Account.newId()) else a
            current = current + safe.copy(deletedAt = null)
            added++
        }
        if (added > 0) persist(current)
        return added
    }

    fun rename(id: String, name: String) = update { l -> l.mapId(id) { it.copy(name = name.trim()) } }
    fun setGroup(id: String, group: String) = update { l -> l.mapId(id) { it.copy(group = group.trim()) } }
    fun setDomain(id: String, domain: String) = update { l -> l.mapId(id) { it.copy(domain = domain) } }
    fun moveToTrash(id: String) = update { l -> l.mapId(id) { it.copy(deletedAt = System.currentTimeMillis()) } }
    fun restore(id: String) = update { l -> l.mapId(id) { it.copy(deletedAt = null) } }
    fun deleteForever(id: String) = update { l -> l.filterNot { it.id == id } }
    fun emptyTrash() = update { l -> l.filter { it.deletedAt == null } }
    fun incrementCounter(id: String) = update { l -> l.mapId(id) { it.copy(counter = it.counter + 1) } }

    private fun purgeExpired() {
        val limit = System.currentTimeMillis() - TRASH_MILLIS
        if (state.value.any { (it.deletedAt ?: Long.MAX_VALUE) < limit }) {
            update { l -> l.filterNot { (it.deletedAt ?: Long.MAX_VALUE) < limit } }
        }
    }

    companion object {
        const val TRASH_DAYS = 90
        const val TRASH_MILLIS = TRASH_DAYS * 86_400_000L
    }
}
