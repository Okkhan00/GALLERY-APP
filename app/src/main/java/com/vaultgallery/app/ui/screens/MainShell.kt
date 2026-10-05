package com.vaultgallery.app.ui.screens

import android.app.Activity
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultgallery.app.GalleryViewModel
import com.vaultgallery.app.data.AppSettings
import com.vaultgallery.app.security.AppLockManager

private data class Dest(val label: String, val icon: ImageVector)

private val DESTS = listOf(
    Dest("Home", Icons.Default.PhotoLibrary),
    Dest("Albums", Icons.Default.Folder),
    Dest("Favorites", Icons.Default.Favorite),
    Dest("Vault", Icons.Default.Lock),
)

/**
 * Four destinations only: Home, Albums, Favorites, Vault. Search, Trash, Storage and Settings live in each screen's menu.
 * Phones get a bottom bar, tablets / foldables (>= 600dp wide) get a navigation rail. The bar hides while selecting.
 * Also hosts the things that must work from any screen: the system "delete originals" dialog of Hide, Vault progress, messages.
 */
@Composable
fun MainShell(
    vm: GalleryViewModel, settings: AppSettings,
    onOpenMedia: (Int) -> Unit, onOpenAlbum: (String) -> Unit, onOpenVaultItem: (Int) -> Unit,
    onSettings: () -> Unit, onTrash: () -> Unit, onStorage: () -> Unit,
) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    val selection by vm.selection.collectAsStateWithLifecycle()
    val selecting = selection.isNotEmpty()
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    // ---- Hide: after the encrypted copy is verified, ask Android to delete the originals ----
    val hideReq by vm.hideRequest.collectAsStateWithLifecycle()
    var handledId by rememberSaveable { mutableStateOf(0L) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.finishHide(r.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(hideReq) {
        val req = hideReq ?: return@LaunchedEffect
        if (req.id == handledId) return@LaunchedEffect     // already showing (e.g. after rotation)
        handledId = req.id
        try {
            val pi = MediaStore.createDeleteRequest(ctx.contentResolver, req.uris)
            AppLockManager.skipNextLock = true
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } catch (e: Exception) {
            vm.finishHide(false)
        }
    }
    LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }
    val progress by vm.vaultProgress.collectAsStateWithLifecycle()
    progress?.let { p ->
        AlertDialog(
            onDismissRequest = {}, title = { Text(p.label) },
            text = { LinearProgressIndicator(progress = { p.done.toFloat() / p.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.cancelVaultWork() }) { Text("Cancel") } },
        )
    }

    val content: @Composable () -> Unit = {
        when (tab) {
            0 -> GalleryScreen(vm, settings, GalleryScope.Home, onOpenMedia, onSettings, onTrash, onStorage)
            1 -> AlbumsScreen(vm, onOpenAlbum)
            2 -> GalleryScreen(vm, settings, GalleryScope.Favorites, onOpenMedia, onSettings, onTrash, onStorage)
            else -> VaultScreen(vm, settings, onOpenVaultItem)
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                DESTS.forEachIndexed { i, d ->
                    NavigationRailItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(d.icon, null) }, label = { Text(d.label) })
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) { content() }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!selecting) NavigationBar {
                    DESTS.forEachIndexed { i, d ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(d.icon, null) }, label = { Text(d.label) })
                    }
                }
            },
        ) { inner ->
            Box(Modifier.padding(bottom = inner.calculateBottomPadding()).then(if (selecting) Modifier.navigationBarsPadding() else Modifier).fillMaxSize()) { content() }
        }
    }
}
