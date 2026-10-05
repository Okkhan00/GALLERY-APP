package com.vaultgallery.app.ui.screens

import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultgallery.app.GalleryViewModel
import com.vaultgallery.app.data.PhotoEntity
import com.vaultgallery.app.ui.components.ConfirmDialog
import com.vaultgallery.app.ui.components.MediaThumb
import com.vaultgallery.app.ui.theme.Gold
import com.vaultgallery.app.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private data class Slice(val label: String, val bytes: Long, val color: Color)

/** Device capacity plus a breakdown of the gallery. All gallery numbers are Room aggregates, so opening this screen never rescans storage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val typeBytes by vm.typeBytes.collectAsStateWithLifecycle()
    val trashBytes by vm.trashBytes.collectAsStateWithLifecycle()
    val vaultBytes by vm.vaultBytes.collectAsStateWithLifecycle()
    val videos by vm.largestVideos.collectAsStateWithLifecycle()
    val photos by vm.largestPhotos.collectAsStateWithLifecycle()
    val recent by vm.recentLarge.collectAsStateWithLifecycle()

    // Device totals: one cheap StatFs call. App data (cache + databases) is walked once, off the main thread, and remembered.
    val device = remember {
        runCatching {
            val st = StatFs(Environment.getExternalStorageDirectory().path)
            st.blockCountLong * st.blockSizeLong to st.availableBlocksLong * st.blockSizeLong
        }.getOrDefault(0L to 0L)
    }
    val appData by produceState(0L) {
        value = withContext(Dispatchers.IO) {
            fun size(f: File?): Long = f?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
            size(ctx.cacheDir) + size(ctx.getDatabasePath("vault_gallery.db").parentFile)
        }
    }

    var tab by rememberSaveable { mutableStateOf(0) }
    var bySize by rememberSaveable { mutableStateOf(true) }
    var confirm by remember { mutableStateOf<PhotoEntity?>(null) }

    val fmt = { b: Long -> Formatter.formatShortFileSize(ctx, b) }
    val slices = listOf(
        Slice("Photos", typeBytes.photoBytes, MaterialTheme.colorScheme.primary),
        Slice("Videos", typeBytes.videoBytes, MaterialTheme.colorScheme.secondary),
        Slice("Trash", trashBytes, MaterialTheme.colorScheme.error),
        Slice("Vault", vaultBytes, MaterialTheme.colorScheme.tertiary),
        Slice("Other Gallery data", appData, MaterialTheme.colorScheme.outline),
    )
    val total = slices.sumOf { it.bytes }.coerceAtLeast(1)

    Scaffold(topBar = {
        TopAppBar(title = { Text("Storage") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        val shown = when (tab) { 0 -> videos; 1 -> photos; else -> recent }.let { l -> if (bySize) l.sortedByDescending { it.size } else l.sortedByDescending { it.dateAdded } }
        LazyColumn(Modifier.padding(inner).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val (cap, free) = device
                if (cap > 0) {
                    val used = cap - free
                    Text("${fmt(used)} used of ${fmt(cap)}", style = MaterialTheme.typography.titleMedium)
                    Text("${fmt(free)} available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(used.toFloat() / cap).height(10.dp).background(MaterialTheme.colorScheme.primary))
                    }
                }
            }
            item {
                Text("Gallery", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    slices.filter { it.bytes > 0 }.forEach { Box(Modifier.weight(it.bytes.toFloat() / total).height(12.dp).background(it.color)) }
                }
            }
            // Labels and numbers carry the meaning, the colours only help.
            items(slices, key = { it.label }) { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(s.color))
                    Text(s.label, Modifier.padding(start = 10.dp).weight(1f))
                    Text(fmt(s.bytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Largest videos", "Largest photos", "Recent large").forEachIndexed { i, l -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(l) }) }
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilterChip(selected = bySize, onClick = { bySize = true }, label = { Text("Size") })
                    FilterChip(selected = !bySize, onClick = { bySize = false }, label = { Text("Date") })
                }
            }
            if (shown.isEmpty()) item { Text("Nothing to show yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(shown, key = { "${tab}-${it.id}" }) { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (p.locked) Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, "Locked item", tint = Gold) }
                    else MediaThumb(p, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(if (p.locked) "Locked item" else p.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.dateAdded))
                        Text(
                            "${fmt(p.size)} · $date" + if (p.isVideo) " · ${formatDuration(p.durationMs)}" else "",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { confirm = p }) { Icon(Icons.Default.Delete, "Move ${if (p.locked) "item" else p.displayName} to trash") }
                }
            }
        }
    }
    confirm?.let { p -> ConfirmDialog("Move to trash?", "You can restore it from Trash later.", "Move to trash", { vm.trash(listOf(p.id)) }, { confirm = null }) }
}
