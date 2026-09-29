package com.npsnelson.authentification.data

import android.content.Context
import java.security.MessageDigest

class AuthRepository(context: Context) {
    private val prefs = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)
    private val demoEmail = "demo@auth.app"
    private val demoPasswordHash = hash("Demo1234!")

    fun isAuthenticated(): Boolean = prefs.getBoolean(KEY_AUTHENTICATED, false)

    fun authenticate(email: String, password: String): Boolean {
        val success = email.trim().equals(demoEmail, ignoreCase = true) && hash(password) == demoPasswordHash
        if (success) prefs.edit().putBoolean(KEY_AUTHENTICATED, true).apply()
        return success
    }

    fun logout() = prefs.edit().clear().apply()

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object { const val KEY_AUTHENTICATED = "authenticated" }
}
