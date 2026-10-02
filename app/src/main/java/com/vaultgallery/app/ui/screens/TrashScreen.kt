package com.vaultgallery.app.ui.screens

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vaultgallery.app.GalleryViewModel
import com.vaultgallery.app.security.AppLockManager
import com.vaultgallery.app.ui.components.ConfirmDialog
import kotlinx.coroutines.launch

/** App-level trash. "Delete forever" removes the real files, only after the system's own confirmation dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val trash by vm.trash.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var pendingDelete by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirm by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) { vm.purge(pendingDelete); selected = emptySet() }
        pendingDelete = emptySet()
    }
    val targets = if (selected.isEmpty()) trash.map { it.id }.toSet() else selected

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (selected.isEmpty()) "Trash (${trash.size})" else "${selected.size} selected") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { vm.restore(targets); selected = emptySet() }, enabled = trash.isNotEmpty()) { Icon(Icons.Default.RestoreFromTrash, "Restore") }
                IconButton(onClick = { confirm = true }, enabled = trash.isNotEmpty()) { Icon(Icons.Default.DeleteForever, if (selected.isEmpty()) "Empty trash" else "Delete selected forever") }
            },
        )
    }) { inner ->
        if (trash.isEmpty()) Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Trash is empty") }
        else LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.padding(inner).fillMaxSize(), contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(trash, key = { it.id }) { p ->
                val sel = p.id in selected
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                    .border(if (sel) 2.dp else 0.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    .clickable { selected = if (sel) selected - p.id else selected + p.id }) {
                    AsyncImage(p.contentUri, p.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (sel) Icon(Icons.Default.CheckCircle, "Selected", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
            }
        }
    }

    if (confirm) ConfirmDialog(
        "Delete forever?", "${targets.size} photo(s) will be permanently deleted from your device. This can't be undone. Android will ask you to confirm once more.", "Continue",
        onConfirm = {
            scope.launch {
                val ids = targets
                val uris = vm.urisFor(ids)
                if (uris.isEmpty()) return@launch
                pendingDelete = ids
                AppLockManager.skipNextLock = true
                val pi = MediaStore.createDeleteRequest(ctx.contentResolver, uris)
                launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            }
        },
        onDismiss = { confirm = false },
    )
}
