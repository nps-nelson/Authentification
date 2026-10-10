package com.nps.authenticator.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.PersistableBundle
import androidx.biometric.BiometricManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nps.authenticator.data.Account
import com.nps.authenticator.data.AccountRepository
import com.nps.authenticator.data.BackupCodec
import com.nps.authenticator.data.FetchResult
import com.nps.authenticator.data.IconFetcher
import com.nps.authenticator.data.IconStore
import com.nps.authenticator.data.QrRepository
import com.nps.authenticator.data.ServiceDomains
import com.nps.authenticator.otp.Base32
import com.nps.authenticator.otp.OtpAlgo
import com.nps.authenticator.otp.OtpAuthUri
import com.nps.authenticator.otp.OtpType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

sealed interface Screen {
    data object Home : Screen
    data object Add : Screen
    data object Trash : Screen
    data object Settings : Screen
    data class Detail(val id: String) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = AccountRepository(app)
    val qrs = QrRepository(app)
    val icons = IconStore(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    /** 0 = 2FA, 1 = QR, 2 = Sauvegarde */
    var tab by mutableIntStateOf(0)
    var toast by mutableStateOf<String?>(null)

    var lockEnabled by mutableStateOf(prefs.getBoolean("lock", false))
        private set
    var locked by mutableStateOf(lockEnabled)
    var secure by mutableStateOf(prefs.getBoolean("secure", true))
        private set
    var iconsEnabled by mutableStateOf(prefs.getBoolean("icons", true))
        private set

    /** Incrémenté quand de nouvelles icônes sont arrivées : fait rafraîchir l'affichage. */
    var iconVersion by mutableIntStateOf(0)
        private set

    private val syncing = AtomicBoolean(false)
    private val retryRounds = AtomicInteger(0)
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            syncIcons()
        }
    }

    init {
        // Les images de QR partagées sont temporaires : on nettoie à chaque démarrage.
        File(app.cacheDir, "shared_qr").deleteRecursively()
        runCatching { cm.registerDefaultNetworkCallback(netCallback) }
        syncIcons()
    }

    override fun onCleared() {
        runCatching { cm.unregisterNetworkCallback(netCallback) }
        super.onCleared()
    }

    fun go(s: Screen) {
        screen = s
    }

    fun back() {
        screen = Screen.Home
    }

    // ---------- Scan : 2FA ou autre QR ----------

    /** Retourne true si le QR a été traité (2FA ajouté, ou autre QR rangé dans l'onglet QR). */
    fun handleScanned(raw: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty()) return false
        return if (text.startsWith("otpauth://", true) || text.startsWith("otpauth-migration://", true)) {
            val ok = addFromText(text)
            if (ok) tab = 0
            ok
        } else {
            qrs.add(text)
            tab = 1
            toast = "QR enregistré dans l'onglet QR"
            true
        }
    }

    // ---------- Ajout 2FA ----------

    fun addFromText(raw: String): Boolean = try {
        val added = OtpAuthUri.parse(raw).count { repo.add(it) }
        toast = when (added) {
            0 -> "Ce compte existe déjà"
            1 -> "Compte ajouté"
            else -> "$added comptes ajoutés"
        }
        syncIcons()
        true
    } catch (e: Exception) {
        toast = "Code non reconnu : ${e.message ?: "format invalide"}"
        false
    }

    fun addManual(
        issuer: String,
        name: String,
        secret: String,
        type: OtpType,
        algo: OtpAlgo,
        digits: Int,
        period: Int,
    ): Boolean = try {
        Base32.decode(secret)
        val ok = repo.add(
            Account(
                id = Account.newId(),
                issuer = issuer.trim(),
                name = name.trim(),
                secret = Base32.normalize(secret),
                type = type,
                algo = algo,
                digits = digits,
                period = period,
            ),
        )
        toast = if (ok) "Compte ajouté" else "Ce compte existe déjà"
        syncIcons()
        true
    } catch (e: Exception) {
        toast = e.message ?: "Clé secrète invalide"
        false
    }

    // ---------- Presse-papiers ----------

    fun copy(code: String) {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("code", code)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        cm.setPrimaryClip(clip)
        toast = "Code copié"
    }

    fun copyText(text: String) {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("qr", text))
        toast = "Copié"
    }

    // ---------- Réglages ----------

    fun setLock(on: Boolean) {
        if (on) {
            val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val ok = BiometricManager.from(getApplication<Application>()).canAuthenticate(allowed) ==
                BiometricManager.BIOMETRIC_SUCCESS
            if (!ok) {
                toast = "Configurez d'abord un verrouillage d'écran ou une empreinte sur l'appareil"
                return
            }
        }
        lockEnabled = on
        prefs.edit().putBoolean("lock", on).apply()
    }

    fun updateSecure(on: Boolean) {
        secure = on
        prefs.edit().putBoolean("secure", on).apply()
    }

    fun updateIcons(on: Boolean) {
        iconsEnabled = on
        prefs.edit().putBoolean("icons", on).apply()
        if (on) syncIcons()
    }

    // ---------- Icônes des services ----------

    fun setDomain(id: String, input: String) {
        val cleaned = ServiceDomains.clean(input)
        if (input.isNotBlank() && cleaned.isEmpty()) {
            toast = "Domaine invalide (exemple : netlify.com)"
            return
        }
        repo.setDomain(id, cleaned)
        if (cleaned.isNotEmpty()) prefs.edit().remove("retry_$cleaned").apply()
        syncIcons()
    }

    private fun isOnline(): Boolean {
        val n = cm.activeNetwork ?: return false
        val c = cm.getNetworkCapabilities(n) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Télécharge, une seule fois, l'icône des services qui n'en ont pas encore.
     * Hors ligne : rien ne se passe, l'avatar reste la première lettre. Au retour du réseau, on réessaie.
     */
    fun syncIcons(fromEvent: Boolean = true) {
        if (!iconsEnabled) return
        if (fromEvent) retryRounds.set(0)
        if (!isOnline()) return
        if (!syncing.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            var got = false
            var transient = false
            try {
                val now = System.currentTimeMillis()
                val domains = repo.accounts.value
                    .filter { it.deletedAt == null }
                    .map { it.iconDomain }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .filter { !icons.has(it) && now >= prefs.getLong("retry_$it", 0L) }
                for (d in domains) {
                    if (!iconsEnabled) break
                    when (val r = IconFetcher.fetch(d)) {
                        is FetchResult.Ok -> {
                            icons.save(d, r.bitmap)
                            prefs.edit().remove("retry_$d").apply()
                            got = true
                        }
                        FetchResult.NotFound ->
                            prefs.edit().putLong("retry_$d", System.currentTimeMillis() + 7 * 86_400_000L).apply()
                        FetchResult.Transient -> transient = true
                    }
                }
            } finally {
                syncing.set(false)
            }
            if (got) withContext(Dispatchers.Main) { iconVersion++ }
            if (transient && retryRounds.incrementAndGet() <= 3) {
                delay(60_000)
                syncIcons(fromEvent = false)
            }
        }
    }

    // ---------- Images ----------

    fun saveImage(uri: Uri, bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IllegalStateException("Impossible d'écrire le fichier")
                    out.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                toast = "Image enregistrée"
            } catch (e: Exception) {
                toast = "Échec de l'enregistrement : ${e.message}"
            }
        }
    }

    // ---------- Sauvegarde chiffrée ----------

    fun exportTo(uri: Uri, password: CharArray) {
        val list = repo.accounts.value.filter { it.deletedAt == null }
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) { BackupCodec.export(list, password) }
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IllegalStateException("Impossible d'écrire le fichier")
                    out.use { it.write(bytes) }
                }
                toast = "Sauvegarde exportée (${list.size} comptes)"
            } catch (e: Exception) {
                toast = "Échec de l'export : ${e.message}"
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun importFrom(uri: Uri, password: CharArray) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Fichier illisible")
                    input.use { it.readBytes() }
                }
                val list = withContext(Dispatchers.Default) { BackupCodec.import(bytes, password) }
                val n = repo.merge(list)
                toast = if (n == 0) "Aucun nouveau compte (déjà présents)" else "$n compte(s) importé(s)"
                syncIcons()
            } catch (e: Exception) {
                toast = "Import impossible : mot de passe incorrect ou fichier invalide"
            } finally {
                password.fill('\u0000')
            }
        }
    }
}
