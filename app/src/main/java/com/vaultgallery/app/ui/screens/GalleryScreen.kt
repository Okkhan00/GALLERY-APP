package com.vaultgallery.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vaultgallery.app.GalleryViewModel
import com.vaultgallery.app.data.AppSettings
import com.vaultgallery.app.data.PhotoEntity
import com.vaultgallery.app.data.SettingsKeys
import com.vaultgallery.app.data.TagCount
import com.vaultgallery.app.domain.UnlockResult
import com.vaultgallery.app.security.AppLockManager
import com.vaultgallery.app.ui.components.AppSheet
import com.vaultgallery.app.ui.components.ConfirmDialog
import com.vaultgallery.app.ui.components.EmptyState
import com.vaultgallery.app.ui.components.LocalVaultAuth
import com.vaultgallery.app.ui.components.ManageTagsDialog
import com.vaultgallery.app.ui.components.MediaThumb
import com.vaultgallery.app.ui.components.PhotoCard
import com.vaultgallery.app.ui.components.SheetTitle
import com.vaultgallery.app.ui.components.ShimmerGrid
import com.vaultgallery.app.ui.components.TagPickerDialog
import com.vaultgallery.app.ui.components.UnlockDialog
import com.vaultgallery.app.ui.theme.glass
import com.vaultgallery.app.util.TimelineGrouper
import com.vaultgallery.app.util.displayHeight
import com.vaultgallery.app.util.displayWidth
import com.vaultgallery.app.util.shareMedia
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId

/** Which slice of the library a gallery-style screen shows. Home and Favorites are bottom-nav tabs; an Album is pushed on top. */
sealed interface GalleryScope {
    data object Home : GalleryScope
    data object Favorites : GalleryScope
    data class Album(val name: String) : GalleryScope
}

private sealed interface GridEntry
private data class HeaderEntry(val position: Int, val label: String) : GridEntry
private data class CellEntry(val index: Int, val photo: PhotoEntity) : GridEntry

private fun GridEntry.key(): Any = when (this) {
    is HeaderEntry -> "h:$position:$label"   // position keeps keys unique even if a label repeats
    is CellEntry -> photo.id
}

/** Timeline headers are inserted only when the list is date-ordered; other sorts stay a plain grid. */
private fun buildEntries(list: List<PhotoEntity>, timeline: Boolean): List<GridEntry> {
    if (!timeline) return list.mapIndexed { i, p -> CellEntry(i, p) }
    val grouper = TimelineGrouper(System.currentTimeMillis())
    val out = ArrayList<GridEntry>(list.size + 32)
    var last: String? = null
    list.forEachIndexed { i, p ->
        val label = grouper.label(p.dateTaken)
        if (label != last) { out += HeaderEntry(i, label); last = label }
        out += CellEntry(i, p)
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    vm: GalleryViewModel, settings: AppSettings, scope: GalleryScope,
    onOpen: (Int) -> Unit, onSettings: () -> Unit, onTrash: () -> Unit, onStorage: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val coroutines = rememberCoroutineScope()
    val auth = LocalVaultAuth.current
    val photos by vm.photos.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val favOnly by vm.favoritesOnly.collectAsStateWithLifecycle()
    val tagFilter by vm.tagFilter.collectAsStateWithLifecycle()
    val albumFilter by vm.albumFilter.collectAsStateWithLifecycle()
    val minSize by vm.minSize.collectAsStateWithLifecycle()
    val dateSince by vm.dateSince.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val progress by vm.scanProgress.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val newCount by vm.newCount.collectAsStateWithLifecycle()

    var videoAccess by remember { mutableStateOf(hasVideoAccess(ctx)) }
    val videoPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        videoAccess = hasVideoAccess(ctx); if (videoAccess) vm.scan()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { videoAccess = hasVideoAccess(ctx) }

    // Entering a different scope resets filters; returning from the viewer keeps them.
    LaunchedEffect(scope) {
        when (scope) {
            GalleryScope.Home -> vm.applyScope("home", false, null)
            GalleryScope.Favorites -> vm.applyScope("favorites", true, null)
            is GalleryScope.Album -> vm.applyScope("album:${scope.name}", false, scope.name)
        }
    }

    val numbers = remember { NumberFormat.getIntegerInstance() }
    val list = photos ?: emptyList()
    val selecting = selection.isNotEmpty()
    val isDateSort = settings.sort == 0 || settings.sort == 1
    val entries = remember(list, settings.timeline, isDateSort) { buildEntries(list, settings.timeline && isDateSort) }
    val thumbPx = when (settings.perfMode) { 0 -> 0; 1 -> 192; else -> 128 }
    val title = when (scope) {
        GalleryScope.Home -> "Gallery"
        GalleryScope.Favorites -> "Favorites"
        is GalleryScope.Album -> scope.name.ifBlank { "Other" }
    }
    val hasFilters = query.isNotEmpty() || tagFilter >= 0 || minSize > 0 || dateSince > 0 || (scope == GalleryScope.Home && (favOnly || albumFilter != null))

    var menu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var filterSheet by remember { mutableStateOf(false) }
    var unlockFor by remember { mutableStateOf<PhotoEntity?>(null) }
    var tagPicker by remember { mutableStateOf(false) }
    var manageTags by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    var confirmHide by remember { mutableStateOf(false) }
    val potd by produceState<PhotoEntity?>(null, list.size) { value = vm.photoOfTheDay() }

    if (onBack != null) BackHandler { onBack() }
    BackHandler(selecting) { vm.clearSelection() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selecting) {
                val chosen = list.filter { it.id in selection }
                val allFav = chosen.isNotEmpty() && chosen.all { it.favorite }
                TopAppBar(
                    title = { Text("Selected: ${selection.size}") },
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Default.Close, "Clear selection") } },
                    actions = {
                        IconButton(onClick = {
                            coroutines.launch {
                                val uris = vm.shareUris(selection)
                                if (uris.isEmpty()) Toast.makeText(ctx, "Locked items can't be shared", Toast.LENGTH_SHORT).show()
                                else ctx.shareMedia(uris, chosen.filter { !it.locked }.map { it.mimeType }, "Share")
                            }
                        }) { Icon(Icons.Default.Share, "Share selected") }
                        IconButton(onClick = { vm.setFavorite(selection, !allFav) }) {
                            Icon(if (allFav) Icons.Default.FavoriteBorder else Icons.Default.Favorite, if (allFav) "Remove selected from favorites" else "Add selected to favorites")
                        }
                        IconButton(onClick = { confirmHide = true }) { Icon(Icons.Default.VisibilityOff, "Hide selected in Vault") }
                        IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Default.Delete, "Move selected to trash") }
                        Box {
                            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "More actions") }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(text = { Text("Select all") }, onClick = { moreMenu = false; vm.selectAll(list) })
                                DropdownMenuItem(text = { Text("Tags…") }, onClick = { moreMenu = false; tagPicker = true })
                                DropdownMenuItem(text = { Text("Lock with Stars/Coins…") }, onClick = { moreMenu = false; confirmLock = true })
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (hasFilters) "${numbers.format(list.size)} of ${numbers.format(counts.total)} items" else "${numbers.format(list.size)} items",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) vm.query.value = "" }) { Icon(Icons.Default.Search, "Search") }
                        IconButton(onClick = { filterSheet = true }) { Icon(Icons.Default.Tune, "View and filter options") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("⭐ ${wallet?.stars ?: 0}   🪙 ${wallet?.coins ?: 0}") }, enabled = false, onClick = {})
                                DropdownMenuItem(text = { Text("Trash") }, onClick = { menu = false; onTrash() })
                                DropdownMenuItem(text = { Text("Storage") }, onClick = { menu = false; onStorage() })
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
                                DropdownMenuItem(text = { Text("Rescan media") }, onClick = { menu = false; vm.scan() })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            progress?.let { (done, total) ->
                LinearProgressIndicator(progress = { done.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
            }
            if (!selecting && (searchOpen || query.isNotEmpty())) {
                OutlinedTextField(
                    value = query, onValueChange = { vm.query.value = it },
                    placeholder = { Text("Search name, folder, caption or tag") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Default.Clear, "Clear search") } },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            if (!selecting) {
                val filterOptions = listOf(-1 to "All ${numbers.format(counts.total)}", 0 to "Photos ${numbers.format(counts.photos)}", 1 to "Videos ${numbers.format(counts.videos)}")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    filterOptions.forEachIndexed { i, (type, label) ->
                        SegmentedButton(
                            selected = settings.mediaFilter == type, onClick = { vm.setMediaFilter(type) },
                            shape = SegmentedButtonDefaults.itemShape(i, filterOptions.size),
                        ) { Text(label, maxLines = 1) }
                    }
                }
                // Only active filters take room here; everything else lives in the filter sheet.
                val showNew = scope == GalleryScope.Home && settings.newMediaBanner && newCount > 0 && dateSince == 0L
                val albumChip = scope == GalleryScope.Home && albumFilter != null
                if (showNew || tagFilter >= 0 || minSize > 0 || dateSince > 0 || albumChip || (scope == GalleryScope.Home && favOnly)) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showNew) item {
                            AssistChip(
                                onClick = { vm.showNewItems() },
                                label = { Text("${numbers.format(newCount)} new item${if (newCount == 1) "" else "s"}") },
                                leadingIcon = { Icon(Icons.Default.NewReleases, null, Modifier.size(18.dp)) },
                            )
                        }
                        if (albumChip) item { RemovableChip("Album: ${albumFilter?.ifBlank { "Other" }}") { vm.albumFilter.value = null } }
                        if (scope == GalleryScope.Home && favOnly) item { RemovableChip("Favorites") { vm.favoritesOnly.value = false } }
                        if (tagFilter >= 0) item { RemovableChip("Tag: ${tags.firstOrNull { it.id == tagFilter }?.name ?: ""}") { vm.tagFilter.value = -1 } }
                        if (minSize > 0) item { RemovableChip("Over ${Formatter.formatShortFileSize(ctx, minSize)}") { vm.minSize.value = 0 } }
                        if (dateSince > 0) item { RemovableChip("Recently added") { vm.dateSince.value = 0 } }
                    }
                }
            }

            val showStrips = scope == GalleryScope.Home && query.isEmpty() && !favOnly && tagFilter < 0 && !selecting &&
                settings.mediaFilter != 1 && albumFilter == null && minSize == 0L && dateSince == 0L
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
                photos == null -> ShimmerGrid()
                list.isEmpty() -> {
                    val noVideoPermission = settings.mediaFilter == 1 && !videoAccess && !hasFilters
                    if (noVideoPermission) {
                        EmptyState(
                            Icons.Default.PhotoLibrary, "Allow access to your videos",
                            "Vault Gallery needs permission to read videos on this device. Nothing is uploaded; the app has no internet permission.",
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { AppLockManager.skipNextLock = true; videoPermLauncher.launch(videoPermissions()) }) { Text("Grant Access") }
                                OutlinedButton(onClick = {
                                    AppLockManager.skipNextLock = true
                                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                                }) { Text("Open app settings") }
                            }
                        }
                    } else if (hasFilters) {
                        EmptyState(Icons.Default.Search, "No matches", "Nothing fits these filters.") {
                            TextButton(onClick = { vm.clearFilters(keepFavorites = scope == GalleryScope.Favorites, keepAlbum = scope is GalleryScope.Album) }) { Text("Clear filters") }
                        }
                    } else if (scope == GalleryScope.Favorites) {
                        EmptyState(Icons.Default.FavoriteBorder, "No favorites yet", "Your favorite photos and videos will appear here.")
                    } else {
                        val (t, s) = when (settings.mediaFilter) {
                            0 -> "No photos found." to "Photos on this device will show up here."
                            1 -> "No videos found." to "Videos on this device will show up here."
                            else -> "Nothing here yet." to "Photos and videos on this device will show up here."
                        }
                        EmptyState(Icons.Default.PhotoLibrary, t, s)
                    }
                }
                settings.layout == 3 -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(150.dp), contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalItemSpacing = 8.dp, modifier = Modifier.fillMaxSize(),
                ) {
                    if (showStrips) item(span = StaggeredGridItemSpan.FullLine) { strips() }
                    staggeredItems(entries, key = { it.key() }, span = { if (it is HeaderEntry) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane }) { e ->
                        when (e) {
                            is HeaderEntry -> SectionHeader(e.label)
                            is CellEntry -> PhotoCard(
                                e.photo, e.photo.id in selection, selecting, settings.showNames, Modifier.fillMaxWidth().aspectOf(e.photo),
                                { click(e.index, e.photo) }, { vm.toggleSelect(e.photo.id) }, settings.videoShowDuration, thumbPx,
                            )
                        }
                    }
                }
                else -> {
                    val minCell = when (settings.layout) { 0 -> 360.dp; 1 -> 150.dp; else -> 104.dp }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minCell), contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize(),
                    ) {
                        if (showStrips) item(span = { GridItemSpan(maxLineSpan) }) { strips() }
                        items(entries, key = { it.key() }, span = { if (it is HeaderEntry) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { e ->
                            when (e) {
                                is HeaderEntry -> SectionHeader(e.label)
                                is CellEntry -> PhotoCard(
                                    e.photo, e.photo.id in selection, selecting, settings.showNames,
                                    Modifier.fillMaxWidth().then(if (settings.layout == 0) Modifier.height(220.dp) else Modifier.aspectRatio(1f)),
                                    { click(e.index, e.photo) }, { vm.toggleSelect(e.photo.id) }, settings.videoShowDuration, thumbPx,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (filterSheet) ViewFilterSheet(vm, settings, scope, tags, albums.map { it.bucket to it.total }, tagFilter, favOnly, albumFilter, minSize,
        onManageTags = { filterSheet = false; manageTags = true }, onDismiss = { filterSheet = false })
    if (confirmTrash) ConfirmDialog("Move to trash?", "${selection.size} item(s) will move to the app's trash. You can restore them later.", "Move to trash", { vm.trash(selection.toSet()) }, { confirmTrash = false })
    if (confirmLock) ConfirmDialog("Lock items?", "${selection.size} item(s) will be locked and need Stars or Coins to open.", "Lock", { vm.lockPhotos(selection.toSet()) }, { confirmLock = false })
    if (confirmHide) ConfirmDialog(
        "Hide in Vault?",
        "${selection.size} item(s) will be encrypted in the Vault and removed from your Gallery and other apps. Android will ask you to confirm deleting the originals.",
        "Continue", { val ids = selection.toSet(); auth.require { vm.hide(ids) } }, { confirmHide = false },
    )
    if (tagPicker) TagPickerDialog(tags, { vm.addTag(selection, it) }, { vm.removeTag(selection, it) }, vm::createTag) { tagPicker = false }
    if (manageTags) ManageTagsDialog(tags, vm::renameTag, vm::deleteTag, vm::createTag) { manageTags = false }
    unlockFor?.let { p ->
        UnlockDialog(p, wallet, onUnlock = { cur ->
            coroutines.launch {
                val msg = if (vm.unlock(p.id, cur) == UnlockResult.Success) "Unlocked" else "Not enough credits"
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                unlockFor = null
            }
        }, onDismiss = { unlockFor = null })
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    AssistChip(onClick = onRemove, label = { Text(label, maxLines = 1) }, trailingIcon = { Icon(Icons.Default.Close, "Remove filter", Modifier.size(18.dp)) })
}

private fun Modifier.aspectOf(p: PhotoEntity): Modifier {
    val w = p.displayWidth(); val h = p.displayHeight()
    return this.aspectRatio(if (w > 0 && h > 0) (w.toFloat() / h).coerceIn(0.5f, 2f) else 1f)
}

@Composable
private fun Strips(potd: PhotoEntity?, recent: List<PhotoEntity>, onOpen: (PhotoEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        if (potd != null) {
            Text("Photo of the day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().height(140.dp).glass().clickable { onOpen(potd) }) {
                AsyncImage(potd.contentUri, "Photo of the day: ${potd.displayName}", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        if (recent.isNotEmpty()) {
            Text("Recently viewed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recent, key = { it.id }) { p ->
                    MediaThumb(p, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(p) })
                }
            }
        }
    }
}

private val SORT_CHIPS = listOf("Newest" to 0, "Oldest" to 1, "Name A–Z" to 2, "Name Z–A" to 4, "Largest" to 3, "Longest" to 5)
private val SIZE_CHIPS = listOf("Any size" to 0L, "10 MB+" to 10L * 1024 * 1024, "100 MB+" to 100L * 1024 * 1024, "1 GB+" to 1024L * 1024 * 1024)

/** Sort, grid size, timeline and every filter in one sheet, so the main screen stays almost empty. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewFilterSheet(
    vm: GalleryViewModel, settings: AppSettings, scope: GalleryScope, tags: List<TagCount>, albums: List<Pair<String, Int>>,
    tagFilter: Long, favOnly: Boolean, albumFilter: String?, minSize: Long, onManageTags: () -> Unit, onDismiss: () -> Unit,
) {
    var dateChoice by remember { mutableStateOf(-1) }
    AppSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetTitle("Sort by")
            ChipRow(SORT_CHIPS.map { it.first }, SORT_CHIPS.indexOfFirst { it.second == settings.sort }) { vm.setSetting(SettingsKeys.SORT, SORT_CHIPS[it].second) }
            SheetTitle("Grid size")
            ChipRow(listOf("Large", "Medium", "Small", "Masonry"), settings.layout.coerceIn(0, 3)) { vm.setSetting(SettingsKeys.LAYOUT, it) }
            SwitchRow("Group by date (timeline)", settings.timeline) { vm.setSetting(SettingsKeys.TIMELINE, it) }
            SwitchRow("Show details on thumbnails", settings.showNames) { vm.setSetting(SettingsKeys.SHOW_NAMES, it) }

            SheetTitle("Date added")
            ChipRow(listOf("Any time", "7 days", "30 days", "This year"), dateChoice) { i ->
                dateChoice = i
                val now = System.currentTimeMillis()
                vm.dateSince.value = when (i) {
                    1 -> now - 7L * 24 * 3600 * 1000
                    2 -> now - 30L * 24 * 3600 * 1000
                    3 -> LocalDate.now().withDayOfYear(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    else -> 0L
                }
            }
            SheetTitle("File size")
            ChipRow(SIZE_CHIPS.map { it.first }, SIZE_CHIPS.indexOfFirst { it.second == minSize }) { vm.minSize.value = SIZE_CHIPS[it].second }

            if (scope == GalleryScope.Home) {
                SwitchRow("Favorites only", favOnly) { vm.favoritesOnly.value = it }
                if (albums.isNotEmpty()) {
                    SheetTitle("Album")
                    ChipRow(listOf("All") + albums.take(10).map { it.first.ifBlank { "Other" } }, if (albumFilter == null) 0 else albums.take(10).indexOfFirst { it.first == albumFilter } + 1) { i ->
                        vm.albumFilter.value = if (i == 0) null else albums.take(10)[i - 1].first
                    }
                }
            }
            if (tags.isNotEmpty()) {
                SheetTitle("Tags")
                FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { t ->
                        FilterChip(selected = tagFilter == t.id, onClick = { vm.tagFilter.value = if (tagFilter == t.id) -1 else t.id }, label = { Text("${t.name} (${t.photoCount})") })
                    }
                }
            }
            TextButton(onClick = onManageTags, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Manage tags") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o -> FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(o) }) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
