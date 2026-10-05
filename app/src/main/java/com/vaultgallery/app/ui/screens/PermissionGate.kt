package com.vaultgallery.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vaultgallery.app.security.AppLockManager

// Granular media permissions on Android 13+, one legacy permission below. minSdk is 30, so 30-32 use READ_EXTERNAL_STORAGE.
private fun requiredPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Permissions to request when only video access is missing (e.g. the user granted photos but denied videos). */
fun videoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

// The gate opens if EITHER photos or videos are readable; each media type then degrades on its own.
private fun hasAccess(ctx: Context): Boolean = when {
    Build.VERSION.SDK_INT >= 34 -> granted(ctx, Manifest.permission.READ_MEDIA_IMAGES) || granted(ctx, Manifest.permission.READ_MEDIA_VIDEO) ||
        granted(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> granted(ctx, Manifest.permission.READ_MEDIA_IMAGES) || granted(ctx, Manifest.permission.READ_MEDIA_VIDEO)
    else -> granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** All media permissions this app can use (photos + videos, plus the partial-access one on Android 14+). */
fun allMediaPermissions(): Array<String> = requiredPermissions()

fun hasPhotoAccess(ctx: Context): Boolean = when {
    Build.VERSION.SDK_INT >= 34 -> granted(ctx, Manifest.permission.READ_MEDIA_IMAGES) || granted(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> granted(ctx, Manifest.permission.READ_MEDIA_IMAGES)
    else -> granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
}

fun hasVideoAccess(ctx: Context): Boolean = when {
    Build.VERSION.SDK_INT >= 34 -> granted(ctx, Manifest.permission.READ_MEDIA_VIDEO) || granted(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> granted(ctx, Manifest.permission.READ_MEDIA_VIDEO)
    else -> granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Explains why photo access is needed, never crashes on denial, and offers an Open-settings shortcut. */
@Composable
fun PermissionGate(onGranted: () -> Unit, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasAccess(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasAccess(ctx) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasAccess(ctx) }

    if (granted) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onGranted() }
        content()
    } else {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Allow access to your photos and videos", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                "Vault Gallery reads your photos and videos on this device to show them in the gallery. Nothing is uploaded; the app has no internet permission.",
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp)
            )
            Button(onClick = { AppLockManager.skipNextLock = true; launcher.launch(requiredPermissions()) }) { Text("Allow media access") }
            OutlinedButton(
                onClick = {
                    AppLockManager.skipNextLock = true
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Open app settings") }
        }
    }
}
