package com.vaultgallery.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vaultgallery.app.security.BiometricHelper
import com.vaultgallery.app.security.PinManager
import com.vaultgallery.app.ui.theme.Gold
import com.vaultgallery.app.util.findFragmentActivity
import kotlinx.coroutines.delay

@Composable
fun LockScreen(pin: PinManager, biometricEnabled: Boolean, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx.findFragmentActivity()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockoutMs by remember { mutableLongStateOf(pin.lockoutRemainingMs()) }
    val bio = biometricEnabled && activity != null && BiometricHelper.available(ctx)

    fun tryBiometric() { activity?.let { BiometricHelper.prompt(it, "Unlock Vault Gallery", onUnlocked) } }
    fun submit() {
        val ok = pin.verify(input.toCharArray())
        input = ""
        if (ok) onUnlocked() else {
            lockoutMs = pin.lockoutRemainingMs()
            error = if (lockoutMs > 0) "Too many attempts. Try again in ${lockoutMs / 1000}s" else "Incorrect PIN"
        }
    }

    LaunchedEffect(Unit) { if (bio) tryBiometric() }
    LaunchedEffect(lockoutMs) { while (lockoutMs > 0) { delay(1000); lockoutMs = pin.lockoutRemainingMs() } }

    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock, null, tint = Gold, modifier = Modifier.size(48.dp))
        Text("Vault Gallery", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 8.dp))
        OutlinedTextField(
            value = input, onValueChange = { if (it.all(Char::isDigit) && it.length <= 12) { input = it; error = null } },
            label = { Text("PIN") }, singleLine = true, enabled = lockoutMs <= 0, isError = error != null,
            supportingText = error?.let { { Text(it) } },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (input.length >= 4) submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { submit() }, enabled = input.length >= 4 && lockoutMs <= 0, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Unlock") }
        if (bio) TextButton(onClick = { tryBiometric() }) { Text("Use biometrics") }
    }
}
