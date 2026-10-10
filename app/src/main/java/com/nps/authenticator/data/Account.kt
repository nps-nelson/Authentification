package com.nps.authenticator.data

import com.nps.authenticator.otp.OtpAlgo
import com.nps.authenticator.otp.OtpType
import org.json.JSONObject
import java.util.UUID

data class Account(
    val id: String,
    val issuer: String,
    val name: String,
    /** Clé secrète en Base32 (normalisée). Toujours stockée chiffrée sur disque. */
    val secret: String,
    val type: OtpType = OtpType.TOTP,
    val algo: OtpAlgo = OtpAlgo.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val counter: Long = 0L,
    val group: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    /** Domaine choisi pour l'icône (vide = déduit du nom du service). */
    val domain: String = "",
) {
    val iconDomain: String get() = ServiceDomains.clean(domain).ifBlank { ServiceDomains.guess(issuer) }

    val title: String get() = issuer.ifBlank { name.ifBlank { "Sans nom" } }

    fun sameKey(o: Account): Boolean =
        secret.equals(o.secret, ignoreCase = true) &&
            issuer.equals(o.issuer, ignoreCase = true) &&
            name.equals(o.name, ignoreCase = true)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("issuer", issuer)
        put("name", name)
        put("secret", secret)
        put("type", type.name)
        put("algo", algo.name)
        put("digits", digits)
        put("period", period)
        put("counter", counter)
        put("group", group)
        put("createdAt", createdAt)
        put("domain", domain)
        deletedAt?.let { put("deletedAt", it) }
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun fromJson(o: JSONObject): Account = Account(
            id = o.getString("id"),
            issuer = o.optString("issuer"),
            name = o.optString("name"),
            secret = o.getString("secret"),
            type = runCatching { OtpType.valueOf(o.optString("type", "TOTP")) }.getOrDefault(OtpType.TOTP),
            algo = OtpAlgo.fromName(o.optString("algo", "SHA1")),
            digits = o.optInt("digits", 6).coerceIn(6, 8),
            period = o.optInt("period", 30).coerceIn(1, 3600),
            counter = o.optLong("counter", 0L),
            group = o.optString("group"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            deletedAt = if (o.has("deletedAt")) o.getLong("deletedAt") else null,
            domain = o.optString("domain"),
        )
    }
}
