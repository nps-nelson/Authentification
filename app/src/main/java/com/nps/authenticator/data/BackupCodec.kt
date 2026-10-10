package com.nps.authenticator.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Format de sauvegarde : JSON { v, iter, salt, iv, data }.
 * Clé = PBKDF2-HMAC-SHA256(mot de passe, sel, iter) ; données chiffrées en AES-256-GCM.
 * Le mot de passe n'est jamais stocké : sans lui, le fichier est irrécupérable.
 */
object BackupCodec {
    private const val ITERATIONS = 210_000

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun export(accounts: List<Account>, password: CharArray): ByteArray {
        val arr = JSONArray()
        accounts.forEach { arr.put(it.toJson()) }
        val plain = JSONObject()
            .put("app", "nps-authenticator")
            .put("accounts", arr)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val enc = cipher.doFinal(plain)

        val b64 = Base64.getEncoder()
        return JSONObject()
            .put("v", 1)
            .put("iter", ITERATIONS)
            .put("salt", b64.encodeToString(salt))
            .put("iv", b64.encodeToString(iv))
            .put("data", b64.encodeToString(enc))
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    /** Lève une exception si le mot de passe est faux ou si le fichier est invalide. */
    fun import(data: ByteArray, password: CharArray): List<Account> {
        val o = JSONObject(String(data, Charsets.UTF_8))
        require(o.optInt("v") == 1) { "Version de sauvegarde inconnue" }
        val b64 = Base64.getDecoder()
        val salt = b64.decode(o.getString("salt"))
        val iv = b64.decode(o.getString("iv"))
        val iterations = o.getInt("iter").coerceIn(10_000, 2_000_000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(b64.decode(o.getString("data")))
        val arr = JSONObject(String(plain, Charsets.UTF_8)).getJSONArray("accounts")
        return List(arr.length()) { Account.fromJson(arr.getJSONObject(it)) }
    }
}
