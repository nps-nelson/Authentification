package com.nps.authenticator.otp

import android.net.Uri
import android.util.Base64
import com.nps.authenticator.data.Account

/**
 * Lecture des QR codes :
 *  - otpauth://totp|hotp/...            (standard)
 *  - otpauth-migration://offline?data=  (export multi-comptes de Google Authenticator)
 */
object OtpAuthUri {

    fun parse(raw: String): List<Account> {
        val text = raw.trim()
        return when {
            text.startsWith("otpauth://", ignoreCase = true) -> listOf(parseSingle(Uri.parse(text)))
            text.startsWith("otpauth-migration://", ignoreCase = true) -> parseMigration(Uri.parse(text))
            else -> throw IllegalArgumentException("Format de code non reconnu")
        }
    }

    /** Reconstruit l'URI otpauth:// d'un compte (pour ré-afficher son QR). */
    fun build(a: Account): String {
        val type = if (a.type == OtpType.TOTP) "totp" else "hotp"
        val label = if (a.issuer.isNotBlank()) {
            Uri.encode(a.issuer) + ":" + Uri.encode(a.name)
        } else {
            Uri.encode(a.name)
        }
        val sb = StringBuilder("otpauth://").append(type).append('/').append(label)
        sb.append("?secret=").append(a.secret)
        if (a.issuer.isNotBlank()) sb.append("&issuer=").append(Uri.encode(a.issuer))
        sb.append("&algorithm=").append(a.algo.name)
        sb.append("&digits=").append(a.digits)
        if (a.type == OtpType.TOTP) sb.append("&period=").append(a.period)
        else sb.append("&counter=").append(a.counter)
        return sb.toString()
    }

    private fun parseSingle(uri: Uri): Account {
        val type = when (uri.host?.lowercase()) {
            "totp" -> OtpType.TOTP
            "hotp" -> OtpType.HOTP
            else -> throw IllegalArgumentException("Type OTP inconnu")
        }
        val label = (uri.path ?: "").removePrefix("/")
        val colon = label.indexOf(':')
        var issuer = if (colon >= 0) label.substring(0, colon).trim() else ""
        val name = (if (colon >= 0) label.substring(colon + 1) else label).trim()
        uri.getQueryParameter("issuer")?.trim()?.takeIf { it.isNotEmpty() }?.let { issuer = it }

        val secretRaw = uri.getQueryParameter("secret")
            ?: throw IllegalArgumentException("Clé secrète absente")
        Base32.decode(secretRaw) // validation

        return Account(
            id = Account.newId(),
            issuer = issuer,
            name = name,
            secret = Base32.normalize(secretRaw),
            type = type,
            algo = OtpAlgo.fromName(uri.getQueryParameter("algorithm")),
            digits = (uri.getQueryParameter("digits")?.toIntOrNull() ?: 6).coerceIn(6, 8),
            period = (uri.getQueryParameter("period")?.toIntOrNull() ?: 30).coerceIn(1, 3600),
            counter = uri.getQueryParameter("counter")?.toLongOrNull() ?: 0L,
        )
    }

    // ---------- Google Authenticator migration (protobuf minimal) ----------

    private fun parseMigration(uri: Uri): List<Account> {
        val data = uri.getQueryParameter("data")?.replace(' ', '+')
            ?: throw IllegalArgumentException("Données de migration absentes")
        val bytes = Base64.decode(data, Base64.DEFAULT)
        val r = Pb(bytes)
        val result = mutableListOf<Account>()
        while (r.more()) {
            val tag = r.varint().toInt()
            if ((tag ushr 3) == 1 && (tag and 7) == 2) {
                parseParams(Pb(r.bytes()))?.let { result += it }
            } else {
                r.skip(tag and 7)
            }
        }
        require(result.isNotEmpty()) { "Aucun compte dans ce code" }
        return result
    }

    private fun parseParams(r: Pb): Account? {
        var secret = ByteArray(0)
        var name = ""
        var issuer = ""
        var algo = 1
        var digits = 1
        var type = 2
        var counter = 0L
        while (r.more()) {
            val tag = r.varint().toInt()
            when (tag ushr 3) {
                1 -> secret = r.bytes()
                2 -> name = String(r.bytes(), Charsets.UTF_8)
                3 -> issuer = String(r.bytes(), Charsets.UTF_8)
                4 -> algo = r.varint().toInt()
                5 -> digits = r.varint().toInt()
                6 -> type = r.varint().toInt()
                7 -> counter = r.varint()
                else -> r.skip(tag and 7)
            }
        }
        if (secret.isEmpty() || algo == 4 /* MD5 non supporté */) return null
        if (issuer.isBlank() && name.contains(':')) {
            issuer = name.substringBefore(':').trim()
            name = name.substringAfter(':').trim()
        }
        return Account(
            id = Account.newId(),
            issuer = issuer,
            name = name,
            secret = Base32.encode(secret),
            type = if (type == 1) OtpType.HOTP else OtpType.TOTP,
            algo = when (algo) {
                2 -> OtpAlgo.SHA256
                3 -> OtpAlgo.SHA512
                else -> OtpAlgo.SHA1
            },
            digits = if (digits == 2) 8 else 6,
            counter = counter,
        )
    }

    private class Pb(private val b: ByteArray) {
        private var p = 0
        fun more() = p < b.size
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(p < b.size && shift < 64) { "Données corrompues" }
                val x = b[p++].toInt()
                result = result or ((x and 0x7F).toLong() shl shift)
                if (x and 0x80 == 0) break
                shift += 7
            }
            return result
        }
        fun bytes(): ByteArray {
            val n = varint().toInt()
            require(n >= 0 && p + n <= b.size) { "Données corrompues" }
            val r = b.copyOfRange(p, p + n)
            p += n
            return r
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> p += 8
                2 -> bytes()
                5 -> p += 4
                else -> throw IllegalArgumentException("Données corrompues")
            }
        }
    }
}
