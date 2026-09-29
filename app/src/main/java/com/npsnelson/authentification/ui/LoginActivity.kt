package com.npsnelson.authentification.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.npsnelson.authentification.R
import com.npsnelson.authentification.data.AuthRepository

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = AuthRepository(this)
        if (auth.isAuthenticated()) return openCamera()
        setContentView(R.layout.activity_login)
        val email = findViewById<TextInputEditText>(R.id.email)
        val password = findViewById<TextInputEditText>(R.id.password)
        val error = findViewById<TextView>(R.id.error)
        findViewById<MaterialButton>(R.id.login).setOnClickListener {
            val valid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.text?.toString().orEmpty()).matches()
            when {
                !valid -> showError(error, "Saisissez une adresse e-mail valide.")
                password.text.isNullOrBlank() -> showError(error, "Saisissez votre mot de passe.")
                !auth.authenticate(email.text.toString(), password.text.toString()) -> showError(error, "Identifiants incorrects.")
                else -> openCamera()
            }
        }
    }

    private fun showError(view: TextView, message: String) { view.text = message; view.visibility = TextView.VISIBLE }

    private fun openCamera() {
        startActivity(Intent(this, CameraActivity::class.java))
        finish()
    }
}
