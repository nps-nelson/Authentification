package com.nps.authenticator

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nps.authenticator.ui.AddScreen
import com.nps.authenticator.ui.AppViewModel
import com.nps.authenticator.ui.DetailScreen
import com.nps.authenticator.ui.HomeScreen
import com.nps.authenticator.ui.LockScreen
import com.nps.authenticator.ui.NpsTheme
import com.nps.authenticator.ui.Screen
import com.nps.authenticator.ui.SettingsScreen
import com.nps.authenticator.ui.TrashScreen

class MainActivity : FragmentActivity() {

    private val vm: AppViewModel by viewModels()
    private var stoppedAt = 0L
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (vm.secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            NpsTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LaunchedEffect(vm.secure) {
                        if (vm.secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    LaunchedEffect(vm.toast) {
                        vm.toast?.let {
                            Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                            vm.toast = null
                        }
                    }
                    if (vm.locked) LockScreen(onUnlock = { promptUnlock() }) else AppNav(vm)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Reverrouille si l'appli est restée plus de 15 s en arrière-plan.
        if (vm.lockEnabled && !vm.locked && stoppedAt > 0 &&
            SystemClock.elapsedRealtime() - stoppedAt > 15_000
        ) {
            vm.locked = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.locked) promptUnlock()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    private fun promptUnlock() {
        if (prompting) return
        val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Le verrouillage de l'appareil a été retiré : on ne bloque pas l'utilisateur dehors.
            vm.locked = false
            vm.toast = "Aucun verrouillage d'écran configuré : verrouillage de l'app ignoré"
            return
        }
        prompting = true
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Déverrouiller")
            .setSubtitle("Authentificateur NPS")
            .setAllowedAuthenticators(allowed)
            .build()
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    vm.locked = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }
            },
        ).authenticate(info)
    }
}

@Composable
private fun AppNav(vm: AppViewModel) {
    BackHandler(enabled = vm.screen !is Screen.Home) { vm.back() }
    when (val s = vm.screen) {
        Screen.Home -> HomeScreen(vm)
        Screen.Add -> AddScreen(vm)
        Screen.Trash -> TrashScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        is Screen.Detail -> DetailScreen(vm, s.id)
    }
}
