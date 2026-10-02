package com.vaultgallery.app.ui.screens

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vaultgallery.app.GalleryViewModel
import com.vaultgallery.app.data.AppSettings
import com.vaultgallery.app.data.PhotoEntity
import com.vaultgallery.app.data.SettingsKeys
import com.vaultgallery.app.domain.UnlockResult
import com.vaultgallery.app.ui.components.ConfirmDialog
import com.vaultgallery.app.ui.components.ManageTagsDialog
import com.vaultgallery.app.ui.components.PhotoCard
import com.vaultgallery.app.ui.components.TagPickerDialog
import com.vaultgallery.app.ui.components.UnlockDialog
import com.vaultgallery.app.ui.theme.glass
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    vm: GalleryViewModel, settings: AppSettings,
    onOpen: (Int) -> Unit, onSettings: () -> Unit, onTrash: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val favOnly by vm.favoritesOnly.collectAsStateWithLifecycle()
    val tagFilter by vm.tagFilter.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val progress by vm.scanProgress.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    val bytes by vm.totalBytes.collectAsStateWithLifecycle()

    val list = photos ?: emptyList()
    val selecting = selection.isNotEmpty()
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (settings.theme) { 1 -> true; 2 -> false; else -> systemDark }

    var menu by remember { mutableStateOf(false) }
    var unlockFor by remember { mutableStateOf<PhotoEntity?>(null) }
    var tagPicker by remember { mutableStateOf(false) }
    var manageTags by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    val potd by produceState<PhotoEntity?>(null, list.size) { value = vm.photoOfTheDay() }

    BackHandler(selecting) { vm.clearSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selection.size} selected") },
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Default.Close, "Cancel selection") } },
                    actions = { IconButton(onClick = { vm.selectAll(list) }) { Icon(Icons.Default.SelectAll, "Select all") } },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Vault Gallery", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                            Text(
                                "${list.size} photos · ${Formatter.formatShortFileSize(ctx, bytes)}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        Text("⭐${wallet?.stars ?: 0} 🪙${wallet?.coins ?: 0}", style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { vm.setSetting(SettingsKeys.THEME, if (effectiveDark) 2 else 1) }) {
                            Icon(if (effectiveDark) Icons.Default.Brightness7 else Icons.Default.Brightness4, if (effectiveDark) "Switch to light mode" else "Switch to dark mode")
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                listOf("1 column", "2 columns", "3 columns", "Masonry").forEachIndexed { i, l ->
                                    DropdownMenuItem(
                                        text = { Text("Layout: $l") },
                                        trailingIcon = { if (settings.layout == i) Icon(Icons.Default.Check, "Selected") },
                                        onClick = { vm.setSetting(SettingsKeys.LAYOUT, i); menu = false })
                                }
                                listOf("Newest first", "Oldest first", "Name", "Largest first").forEachIndexed { i, l ->
                                    DropdownMenuItem(
                                        text = { Text("Sort: $l") },
                                        trailingIcon = { if (settings.sort == i) Icon(Icons.Default.Check, "Selected") },
                                        onClick = { vm.setSetting(SettingsKeys.SORT, i); menu = false })
                                }
                                DropdownMenuItem(text = { Text("Trash") }, onClick = { menu = false; onTrash() })
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
                                DropdownMenuItem(text = { Text("Rescan photos") }, onClick = { menu = false; vm.scan() })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = .9f)),
                )
            }
        },
        bottomBar = {
            if (selecting) {
                BottomAppBar(actions = {
                    IconButton(onClick = { vm.setFavorite(selection, true) }) { Icon(Icons.Default.Favorite, "Favorite selected") }
                    IconButton(onClick = { vm.setFavorite(selection, false) }) { Icon(Icons.Default.FavoriteBorder, "Unfavorite selected") }
                    IconButton(onClick = { tagPicker = true }) { Icon(Icons.Default.Sell, "Edit tags of selected") }
                    IconButton(onClick = { confirmLock = true }) { Icon(Icons.Default.Lock, "Lock selected photos") }
                    IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Default.Delete, "Move selected to trash") }
                })
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            progress?.let { (done, total) ->
                LinearProgressIndicator(progress = { done.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = query, onValueChange = { vm.query.value = it },
                placeholder = { Text("Search name, caption or tag") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Default.Clear, "Clear search") } },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = !favOnly && tagFilter < 0, onClick = { vm.favoritesOnly.value = false; vm.tagFilter.value = -1 },
                        label = { Text("All") }, leadingIcon = if (!favOnly && tagFilter < 0) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
                }
                item {
                    FilterChip(selected = favOnly, onClick = { vm.favoritesOnly.value = !favOnly },
                        label = { Text("Favorites") }, leadingIcon = if (favOnly) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
                }
                items(tags, key = { it.id }) { t ->
                    FilterChip(selected = tagFilter == t.id, onClick = { vm.tagFilter.value = if (tagFilter == t.id) -1 else t.id },
                        label = { Text("${t.name} (${t.photoCount})") }, leadingIcon = if (tagFilter == t.id) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
                }
                item { AssistChip(onClick = { manageTags = true }, label = { Text("Manage tags") }) }
            }

            val showStrips = query.isEmpty() && !favOnly && tagFilter < 0 && !selecting
            val click: (Int, PhotoEntity) -> Unit = { index, p ->
                when {
                    selecting -> vm.toggleSelect(p.id)
                    p.locked -> unlockFor = p
                    else -> onOpen(index)
                }
            }
            val openFromStrip: (PhotoEntity) -> Unit = { p -> list.indexOfFirst { it.id == p.id }.takeIf { it >= 0 }?.let(onOpen) }
            val strips: @Composable () -> Unit = { Strips(potd, recent.filter { !it.locked }, openFromStrip) }

            when {
                photos == null -> Unit
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isNotEmpty() || favOnly || tagFilter >= 0) "No photos match your filters" else "No photos found yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                settings.layout == 3 -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2), contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalItemSpacing = 8.dp, modifier = Modifier.fillMaxSize(),
                ) {
                    if (showStrips) item(span = StaggeredGridItemSpan.FullLine) { strips() }
                    itemsIndexed(list, key = { _, p -> p.id }) { i, p ->
                        PhotoCard(p, p.id in selection, selecting, settings.showNames, Modifier.fillMaxWidth().aspectOf(p),
                            { click(i, p) }, { vm.toggleSelect(p.id) }, { vm.toggleFavorite(p) })
                    }
                }
                else -> {
                    val cols = when (settings.layout) { 0 -> 1; 1 -> 2; else -> 3 }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols), contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize(),
                    ) {
                        if (showStrips) item(span = { GridItemSpan(maxLineSpan) }) { strips() }
                        itemsIndexed(list, key = { _, p -> p.id }) { i, p ->
                            PhotoCard(p, p.id in selection, selecting, settings.showNames,
                                Modifier.fillMaxWidth().then(if (cols == 1) Modifier.height(220.dp) else Modifier.aspectRatio(1f)),
                                { click(i, p) }, { vm.toggleSelect(p.id) }, { vm.toggleFavorite(p) })
                        }
                    }
                }
            }
        }
    }

    if (confirmTrash) ConfirmDialog("Move to trash?", "${selection.size} photo(s) will move to the app's trash. You can restore them later.", "Move to trash", { vm.trash(selection.toSet()) }, { confirmTrash = false })
    if (confirmLock) ConfirmDialog("Lock photos?", "${selection.size} photo(s) will be locked and need Stars or Coins to open.", "Lock", { vm.lockPhotos(selection.toSet()) }, { confirmLock = false })
    if (tagPicker) TagPickerDialog(tags, { vm.addTag(selection, it) }, { vm.removeTag(selection, it) }, vm::createTag) { tagPicker = false }
    if (manageTags) ManageTagsDialog(tags, vm::renameTag, vm::deleteTag, vm::createTag) { manageTags = false }
    unlockFor?.let { p ->
        UnlockDialog(p, wallet, onUnlock = { cur ->
            scope.launch {
                val msg = when (vm.unlock(p.id, cur)) {
                    UnlockResult.Success -> "Unlocked"
                    UnlockResult.NotEnough -> "Not enough credits"
                    else -> "Already unlocked"
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                unlockFor = null
            }
        }, onDismiss = { unlockFor = null })
    }
}

private fun Modifier.aspectOf(p: PhotoEntity): Modifier =
    this.aspectRatio(if (p.width > 0 && p.height > 0) (p.width.toFloat() / p.height).coerceIn(0.5f, 2f) else 1f)

@Composable
private fun Strips(potd: PhotoEntity?, recent: List<PhotoEntity>, onOpen: (PhotoEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        if (potd != null) {
            Text("Photo of the day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Box(Modifier.fillMaxWidth().height(160.dp).glass().clickable { onOpen(potd) }) {
                AsyncImage(potd.contentUri, "Photo of the day: ${potd.displayName}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        if (recent.isNotEmpty()) {
            Text("Recently viewed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recent, key = { it.id }) { p ->
                    AsyncImage(p.contentUri, p.displayName, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(p) })
                }
            }
        }
    }
}
