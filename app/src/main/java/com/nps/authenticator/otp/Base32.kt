package com.nps.authenticator.otp

import java.io.ByteArrayOutputStream

/** Base32 RFC 4648 (alphabet A-Z2-7), tolérant aux espaces, tirets et au padding. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun normalize(input: String): String =
        input.filterNot { it == ' ' || it == '-' || it == '\n' || it == '\t' }
            .trimEnd('=')
            .uppercase()

    fun decode(input: String): ByteArray {
        val s = normalize(input)
        require(s.isNotEmpty()) { "Clé secrète vide" }
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in s) {
            val v = ALPHABET.indexOf(c)
            require(v >= 0) { "Clé secrète invalide (caractère « $c »)" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        val bytes = out.toByteArray()
        require(bytes.isNotEmpty()) { "Clé secrète trop courte" }
        return bytes
    }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALPHABET[(buffer shr bits) and 0x1F])
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return sb.toString()
    }
}
