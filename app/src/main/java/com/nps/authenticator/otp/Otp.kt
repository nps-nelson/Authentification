package com.nps.authenticator.otp

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class OtpType { TOTP, HOTP }

enum class OtpAlgo(val macName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512");

    companion object {
        fun fromName(name: String?): OtpAlgo = when (name?.trim()?.uppercase()?.replace("-", "")) {
            "SHA256" -> SHA256
            "SHA512" -> SHA512
            else -> SHA1
        }
    }
}

/** HOTP (RFC 4226) et TOTP (RFC 6238). Aucune dépendance Android : testable en JVM pure. */
object Otp {

    fun hotp(secret: ByteArray, counter: Long, digits: Int, algo: OtpAlgo): String {
        val mac = Mac.getInstance(algo.macName)
        mac.init(SecretKeySpec(secret, algo.macName))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        var mod = 1
        repeat(digits) { mod *= 10 }
        return (binary % mod).toString().padStart(digits, '0')
    }

    fun totp(secret: ByteArray, timeMillis: Long, period: Int, digits: Int, algo: OtpAlgo): String =
        hotp(secret, timeMillis / 1000L / period, digits, algo)

    /** Millisecondes restantes avant le prochain code. */
    fun remainingMillis(timeMillis: Long, period: Int): Long {
        val p = period * 1000L
        return p - (timeMillis % p)
    }
}
