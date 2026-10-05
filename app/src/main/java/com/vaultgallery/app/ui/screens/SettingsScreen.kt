package com.vaultgallery.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

private val LOCK_VALUES = listOf(0, 30, 60, 300, -1)
private val LOCK_LABELS = listOf("Immediately", "30 s", "1 min", "5 min", "Never")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: GalleryViewModel, settings: AppSettings, pin: PinManager, onBack: () -> Unit, onStorage: () -> Unit, onTrash: () -> Unit) {
    val ctx = LocalContext.current
    var flow by remember { mutableStateOf(PinFlow.NONE) }
    val bioAvailable = remember { BiometricHelper.available(ctx) }
    val vaultCount by vm.vaultCount.collectAsStateWithLifecycle()
    val pinSet = remember(flow) { pin.isSet() }

    var photoAccess by remember { mutableStateOf(hasPhotoAccess(ctx)) }
    var videoAccess by remember { mutableStateOf(hasVideoAccess(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { photoAccess = hasPhotoAccess(ctx); videoAccess = hasVideoAccess(ctx) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        photoAccess = hasPhotoAccess(ctx); videoAccess = hasVideoAccess(ctx); vm.scan()
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        LazyColumn(Modifier.padding(inner).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            section("Appearance", startOpen = true) {
                Choices("Theme", listOf("System", "Dark", "Light", "AMOLED black"), settings.theme.coerceIn(0, 3)) { vm.setSetting(SettingsKeys.THEME, it) }
                Choices("Grid size", listOf("Large", "Medium", "Small", "Masonry"), settings.layout.coerceIn(0, 3)) { vm.setSetting(SettingsKeys.LAYOUT, it) }
                Toggle("Animations", settings.animations) { vm.setSetting(SettingsKeys.ANIMATIONS, it) }
                Toggle("Show details on thumbnails", settings.showNames) { vm.setSetting(SettingsKeys.SHOW_NAMES, it) }
            }
            section("Gallery") {
                val filters = listOf(-1, 0, 1)
                Choices("Default view", listOf("All", "Photos", "Videos"), filters.indexOf(settings.mediaFilter).coerceAtLeast(0)) { vm.setSetting(SettingsKeys.MEDIA_FILTER, filters[it]) }
                Choices("Sorting", listOf("Newest", "Oldest", "Name A–Z", "Largest", "Name Z–A", "Longest"), settings.sort.coerceIn(0, 5)) { vm.setSetting(SettingsKeys.SORT, it) }
                Toggle("Group by date (timeline)", settings.timeline) { vm.setSetting(SettingsKeys.TIMELINE, it) }
                Toggle("Show “new items” indicator", settings.newMediaBanner) { vm.setSetting(SettingsKeys.NEW_BANNER, it) }
                Choices("Slideshow speed", listOf("2 s", "3 s", "5 s", "10 s"), listOf(2, 3, 5, 10).indexOf(settings.slideshowSec).coerceAtLeast(0)) { vm.setSetting(SettingsKeys.SLIDESHOW, listOf(2, 3, 5, 10)[it]) }
            }
            section("Video") {
                Toggle("Play next video automatically", settings.videoAutoNext) { vm.setSetting(SettingsKeys.VIDEO_AUTO_NEXT, it) }
                val speeds = listOf(50, 75, 100, 125, 150, 200)
                Choices("Default playback speed", listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"), speeds.indexOf(settings.videoSpeedPct).coerceAtLeast(2)) { vm.setSetting(SettingsKeys.VIDEO_SPEED, speeds[it]) }
                Toggle("Remember playback position", settings.videoRememberPosition) { vm.setSetting(SettingsKeys.VIDEO_REMEMBER_POS, it) }
                Toggle("Keep screen awake during playback", settings.videoKeepAwake) { vm.setSetting(SettingsKeys.VIDEO_KEEP_AWAKE, it) }
                Toggle("Show video duration on thumbnails", settings.videoShowDuration) { vm.setSetting(SettingsKeys.VIDEO_SHOW_DURATION, it) }
            }
            section("Security") {
                Toggle("App lock (PIN)", settings.appLock) { on -> flow = if (on) PinFlow.ENABLE_SET else PinFlow.DISABLE_VERIFY }
                if (settings.appLock) {
                    Choices("Lock app after leaving it", LOCK_LABELS, LOCK_VALUES.indexOf(settings.autoLockSec).coerceAtLeast(0)) { vm.setSetting(SettingsKeys.AUTO_LOCK, LOCK_VALUES[it]) }
                    Text("The app also always asks for your PIN when it is opened from scratch.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Choices("Auto-lock the Vault", LOCK_LABELS, LOCK_VALUES.indexOf(settings.vaultAutoLockSec).coerceAtLeast(2)) { vm.setSetting(SettingsKeys.VAULT_AUTO_LOCK, LOCK_VALUES[it]) }
                if (settings.vaultAutoLockSec < 0) Text("With “Never”, the Vault stays open until you lock it or the app is closed.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                Toggle(if (bioAvailable) "Use biometrics (app lock and Vault)" else "Biometrics unavailable on this device", settings.biometric && bioAvailable, enabled = bioAvailable) { vm.setSetting(SettingsKeys.BIOMETRIC, it) }
                if (pinSet) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { flow = PinFlow.CHANGE_VERIFY }) { Text("Change PIN") }
                        if (settings.appLock) Button(onClick = { AppLockManager.lock() }) { Text("Lock now") }
                    }
                }
                Toggle("Block screenshots everywhere", settings.secureScreens) { vm.setSetting(SettingsKeys.SECURE, it) }
                Text("The Vault always blocks screenshots and hides its preview in recent apps. With app lock on, the whole app is hidden there too.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            section("Storage") {
                OutlinedButton(onClick = onStorage, modifier = Modifier.fillMaxWidth()) { Text("Storage analyzer") }
                OutlinedButton(onClick = onTrash, modifier = Modifier.fillMaxWidth()) { Text("Trash") }
                Choices("Performance mode", listOf("Balanced", "Performance", "Battery saver"), settings.perfMode.coerceIn(0, 2)) { vm.setSetting(SettingsKeys.PERF_MODE, it) }
                Text(
                    when (settings.perfMode) {
                        1 -> "Smaller thumbnails, fewer animations, lazier background scanning."
                        2 -> "Smallest thumbnails, no animations and the least background scanning."
                        else -> "Normal thumbnails and caching."
                    },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {
                    ctx.imageLoader.memoryCache?.clear(); ctx.imageLoader.diskCache?.clear()
                    Toast.makeText(ctx, "Thumbnail cache cleared. Your photos and videos are untouched.", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Clear thumbnail cache") }
            }
            section("Permissions") {
                Text("Photos: ${if (photoAccess) "allowed" else "not allowed"}")
                Text("Videos: ${if (videoAccess) "allowed" else "not allowed"}")
                if (!photoAccess || !videoAccess) {
                    Button(onClick = { AppLockManager.skipNextLock = true; permLauncher.launch(allMediaPermissions()) }, modifier = Modifier.fillMaxWidth()) { Text("Grant Access") }
                }
                OutlinedButton(onClick = {
                    AppLockManager.skipNextLock = true
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                }, modifier = Modifier.fillMaxWidth()) { Text("Open system app settings") }
            }
            if (BuildConfig.DEBUG) {
                section("Developer (debug builds only)") {
                    Button(onClick = { vm.debugGrant() }) { Text("Grant +100 ⭐ / +500 🪙 (test)") }
                }
            }
            section("About") {
                Text("Vault Gallery ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                Text("Privacy: everything stays on this device. The app has no network permission, no analytics and no uploads. Vault items are encrypted with keys protected by the Android Keystore. Stars and Coins are virtual in-app credits.", style = MaterialTheme.typography.bodySmall)
                Text("Open-source licenses: AndroidX (Jetpack Compose, Room, DataStore, Navigation, Biometric, Media3), Coil and Kotlin are used under the Apache License 2.0.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    when (flow) {
        PinFlow.ENABLE_SET -> SetPinDialog(
            onDone = { p -> pin.setPin(p.toCharArray()); vm.setSetting(SettingsKeys.APP_LOCK, true); AppLockManager.unlock(); flow = PinFlow.NONE },
            onDismiss = { flow = PinFlow.NONE })
        PinFlow.DISABLE_VERIFY -> VerifyPinDialog(pin, "Turn off app lock",
            onVerified = {
                // The PIN also guards the Vault, so it is only erased when the Vault is empty.
                if (vaultCount == 0) { pin.clear(); vm.setSetting(SettingsKeys.BIOMETRIC, false) }
                vm.setSetting(SettingsKeys.APP_LOCK, false)
                flow = PinFlow.NONE
            },
            onDismiss = { flow = PinFlow.NONE })
        PinFlow.CHANGE_VERIFY -> VerifyPinDialog(pin, "Enter current PIN", onVerified = { flow = PinFlow.CHANGE_SET }, onDismiss = { flow = PinFlow.NONE })
        PinFlow.CHANGE_SET -> SetPinDialog(onDone = { p -> pin.setPin(p.toCharArray()); flow = PinFlow.NONE }, onDismiss = { flow = PinFlow.NONE })
        PinFlow.NONE -> Unit
    }
}

/** A collapsible category: only its title is shown until opened, so the screen is never one long flat list. */
private fun LazyListScope.section(title: String, startOpen: Boolean = false, content: @Composable () -> Unit) {
    item(key = title) {
        var open by rememberSaveable(title) { mutableStateOf(startOpen) }
        Column {
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "Collapse $title" else "Expand $title")
            }
            if (open) Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
            HorizontalDivider()
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Choices(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o -> FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(o) }) }
        }
    }
}
