package com.vaultgallery.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.vaultgallery.app.data.AppSettings
import com.vaultgallery.app.security.BiometricHelper
import com.vaultgallery.app.security.PinManager
import com.vaultgallery.app.security.VaultSession
import com.vaultgallery.app.util.findFragmentActivity

class VaultAuthController internal constructor(
    private val isOpen: () -> Boolean,
    private val request: MutableState<(() -> Unit)?>,
) {
    /** Runs [action] immediately while the vault session is valid; otherwise asks for biometrics / PIN first. */
    fun require(action: () -> Unit) { if (isOpen()) action() else request.value = action }
}

val LocalVaultAuth = staticCompositionLocalOf<VaultAuthController> { error("VaultAuthHost is missing") }

/**
 * One place that authenticates for the vault (open it, hide items). Uses the existing PinManager and BiometricHelper.
 * If no PIN exists yet the user is asked to create one; the vault never opens without one.
 */
@Composable
fun VaultAuthHost(pin: PinManager, settings: AppSettings, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findFragmentActivity() }
    val request = remember { mutableStateOf<(() -> Unit)?>(null) }
    val timeout by rememberUpdatedState(settings.vaultAutoLockSec)
    val controller = remember { VaultAuthController({ VaultSession.isOpen(timeout) }, request) }

    CompositionLocalProvider(LocalVaultAuth provides controller) { content() }

    val action = request.value ?: return
    val finish = { VaultSession.unlock(); request.value = null; action() }
    if (!pin.isSet()) {
        SetPinDialog(onDone = { p -> pin.setPin(p.toCharArray()); finish() }, onDismiss = { request.value = null })
    } else {
        val bio = settings.biometric && activity != null && BiometricHelper.available(ctx)
        var showPin by remember(action) { mutableStateOf(!bio) }
        LaunchedEffect(action) {
            if (bio && activity != null) BiometricHelper.prompt(activity, "Unlock Vault", onSuccess = { finish() }, onFallback = { showPin = true })
        }
        if (showPin) VerifyPinDialog(pin, "Unlock Vault", onVerified = { finish() }, onDismiss = { request.value = null })
    }
}
