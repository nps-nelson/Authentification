package com.nps.authenticator

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.activity.compose.setContent
import com.nps.authenticator.ui.AddScreen
import com.nps.authenticator.ui.AppViewModel
import com.nps.authenticator.ui.DetailScreen
import com.nps.authenticator.ui.HomeScreen
import com.nps.authenticator.ui.Screen
import com.nps.authenticator.ui.SettingsScreen

class MainActivity : FragmentActivity() {
    private lateinit var vm: AppViewModel
    private var backgroundedAt = 0L
    private var prompt: BiometricPrompt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[AppViewModel::class.java]
        prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    vm.locked = false
                    backgroundedAt = 0L
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )

        setContent {
            val toast = vm.toast
            val currentContext = LocalContext.current
            LaunchedEffect(toast) {
                if (toast != null) {
                    Toast.makeText(currentContext, toast, Toast.LENGTH_SHORT).show()
                    vm.toast = null
                }
            }
            DisposableEffect(vm.secure) {
                if (vm.secure) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { }
            }
            AuthenticatorTheme {
                BackHandler(enabled = vm.screen !is Screen.Home && !vm.locked) { vm.back() }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (vm.lockEnabled && vm.locked) {
                        LockedScreen(onUnlock = ::requestUnlock)
                    } else {
                        when (val screen = vm.screen) {
                            Screen.Home -> HomeScreen(vm)
                            Screen.Add -> AddScreen(vm)
                            Screen.Settings -> SettingsScreen(vm)
                            is Screen.Detail -> DetailScreen(vm, screen.id)
                            Screen.Trash -> HomeScreen(vm)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        if (::vm.isInitialized && vm.lockEnabled) backgroundedAt = System.currentTimeMillis()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (::vm.isInitialized && vm.lockEnabled && backgroundedAt > 0L &&
            System.currentTimeMillis() - backgroundedAt >= LOCK_AFTER_BACKGROUND_MS
        ) {
            vm.locked = true
        }
    }

    private fun requestUnlock() {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authentification")
            .setSubtitle("Déverrouillez vos codes")
            .setAllowedAuthenticators(
                BiometricPrompt.Authenticators.BIOMETRIC_WEAK or
                    BiometricPrompt.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        prompt?.authenticate(info)
    }

    companion object {
        private const val LOCK_AFTER_BACKGROUND_MS = 15_000L
    }
}

@Composable
private fun AuthenticatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8AB4FF),
            onPrimary = Color(0xFF002E69),
            primaryContainer = Color(0xFF164A8A),
            onPrimaryContainer = Color(0xFFD6E3FF),
            secondary = Color(0xFF72D5CB),
            background = Color(0xFF101828),
            surface = Color(0xFF172235),
            surfaceVariant = Color(0xFF26354B),
            onSurface = Color(0xFFF2F5FA),
            onSurfaceVariant = Color(0xFFB8C4D5),
            outlineVariant = Color(0xFF3D4B60),
        ),
        content = content,
    )
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Application verrouillée", style = MaterialTheme.typography.headlineSmall)
            Text("Authentifiez-vous pour accéder à vos codes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onUnlock) { Text("Déverrouiller") }
        }
    }
}
