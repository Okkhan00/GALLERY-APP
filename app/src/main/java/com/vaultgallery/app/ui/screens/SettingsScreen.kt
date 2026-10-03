package com.vaultgallery.app.ui.screens

import android.text.format.Formatter
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.vaultgallery.app.BuildConfig
import com.vaultgallery.app.GalleryViewModel
import com.vaultgallery.app.data.AppSettings
import com.vaultgallery.app.data.SettingsKeys
import com.vaultgallery.app.security.AppLockManager
import com.vaultgallery.app.security.BiometricHelper
import com.vaultgallery.app.security.PinManager
import com.vaultgallery.app.ui.components.SetPinDialog
import com.vaultgallery.app.ui.components.VerifyPinDialog

private enum class PinFlow { NONE, ENABLE_SET, DISABLE_VERIFY, CHANGE_VERIFY, CHANGE_SET }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: GalleryViewModel, settings: AppSettings, pin: PinManager, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var flow by remember { mutableStateOf(PinFlow.NONE) }
    val bioAvailable = remember { BiometricHelper.available(ctx) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        LazyColumn(Modifier.padding(inner).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            section("Appearance") {
                Choices("Theme", listOf("System", "Dark", "Light"), settings.theme) { vm.setSetting(SettingsKeys.THEME, it) }
                Choices("Gallery layout", listOf("1 col", "2 cols", "3 cols", "Masonry"), settings.layout) { vm.setSetting(SettingsKeys.LAYOUT, it) }
            }
            section("Gallery") {
                Choices("Sort order", listOf("Newest", "Oldest", "Name A–Z", "Largest", "Name Z–A", "Longest"), settings.sort) { vm.setSetting(SettingsKeys.SORT, it) }
                Choices("Slideshow speed", listOf("2 s", "3 s", "5 s", "10 s"), listOf(2, 3, 5, 10).indexOf(settings.slideshowSec).coerceAtLeast(0)) {
                    vm.setSetting(SettingsKeys.SLIDESHOW, listOf(2, 3, 5, 10)[it])
                }
                Toggle("Show file names on thumbnails", settings.showNames) { vm.setSetting(SettingsKeys.SHOW_NAMES, it) }
            }
            section("Video") {
                Toggle("Play next video automatically", settings.videoAutoNext) { vm.setSetting(SettingsKeys.VIDEO_AUTO_NEXT, it) }
                Text("Off by default: when a video ends you can replay it instead.", style = MaterialTheme.typography.labelSmall)
                val speeds = listOf(50, 75, 100, 125, 150, 200)
                Choices("Default playback speed", listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"), speeds.indexOf(settings.videoSpeedPct).coerceAtLeast(2)) {
                    vm.setSetting(SettingsKeys.VIDEO_SPEED, speeds[it])
                }
                Toggle("Remember playback position", settings.videoRememberPosition) { vm.setSetting(SettingsKeys.VIDEO_REMEMBER_POS, it) }
                Toggle("Keep screen awake during playback", settings.videoKeepAwake) { vm.setSetting(SettingsKeys.VIDEO_KEEP_AWAKE, it) }
                Toggle("Show video duration on thumbnails", settings.videoShowDuration) { vm.setSetting(SettingsKeys.VIDEO_SHOW_DURATION, it) }
            }
            section("Privacy & security") {
                Toggle("App lock (PIN)", settings.appLock) { on ->
                    if (on) flow = PinFlow.ENABLE_SET else flow = PinFlow.DISABLE_VERIFY
                }
                if (settings.appLock) {
                    Toggle(if (bioAvailable) "Unlock with biometrics" else "Biometrics unavailable on this device", settings.biometric && bioAvailable, enabled = bioAvailable) { vm.setSetting(SettingsKeys.BIOMETRIC, it) }
                    Choices("Auto-lock", listOf("Immediately", "1 min", "5 min", "15 min"), listOf(0, 60, 300, 900).indexOf(settings.autoLockSec).coerceAtLeast(0)) {
                        vm.setSetting(SettingsKeys.AUTO_LOCK, listOf(0, 60, 300, 900)[it])
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { flow = PinFlow.CHANGE_VERIFY }) { Text("Change PIN") }
                        Button(onClick = { AppLockManager.lock() }) { Text("Lock now") }
                    }
                }
                Toggle("Block screenshots and screen recording", settings.secureScreens) { vm.setSetting(SettingsKeys.SECURE, it) }
                Text("When app lock is on, the app also hides its contents in the recent-apps screen.", style = MaterialTheme.typography.labelSmall)
            }
            section("Storage") {
                val tb by vm.typeBytes.collectAsStateWithLifecycle()
                Text("Photos: ${Formatter.formatShortFileSize(ctx, tb.photoBytes)}")
                Text("Videos: ${Formatter.formatShortFileSize(ctx, tb.videoBytes)}")
                OutlinedButton(onClick = {
                    ctx.imageLoader.memoryCache?.clear(); ctx.imageLoader.diskCache?.clear()
                    Toast.makeText(ctx, "Thumbnail cache cleared. Your photos and videos are untouched.", Toast.LENGTH_SHORT).show()
                }) { Text("Clear thumbnail cache") }
            }
            if (BuildConfig.DEBUG) {
                section("Developer (debug builds only)") {
                    Button(onClick = { vm.debugGrant() }) { Text("Grant +100 ⭐ / +500 🪙 (test)") }
                }
            }
            section("About") {
                Text("Vault Gallery ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                Text("Local-first: no network permission, no analytics, no uploads. Stars and Coins are virtual in-app credits.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    when (flow) {
        PinFlow.ENABLE_SET -> SetPinDialog(
            onDone = { p -> pin.setPin(p.toCharArray()); vm.setSetting(SettingsKeys.APP_LOCK, true); AppLockManager.unlock(); flow = PinFlow.NONE },
            onDismiss = { flow = PinFlow.NONE })
        PinFlow.DISABLE_VERIFY -> VerifyPinDialog(pin, "Turn off app lock",
            onVerified = { pin.clear(); vm.setSetting(SettingsKeys.APP_LOCK, false); vm.setSetting(SettingsKeys.BIOMETRIC, false); flow = PinFlow.NONE },
            onDismiss = { flow = PinFlow.NONE })
        PinFlow.CHANGE_VERIFY -> VerifyPinDialog(pin, "Enter current PIN", onVerified = { flow = PinFlow.CHANGE_SET }, onDismiss = { flow = PinFlow.NONE })
        PinFlow.CHANGE_SET -> SetPinDialog(onDone = { p -> pin.setPin(p.toCharArray()); flow = PinFlow.NONE }, onDismiss = { flow = PinFlow.NONE })
        PinFlow.NONE -> Unit
    }
}

private fun LazyListScope.section(title: String, content: @Composable () -> Unit) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            content()
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Choices(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o -> FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(o) }) }
        }
    }
}
