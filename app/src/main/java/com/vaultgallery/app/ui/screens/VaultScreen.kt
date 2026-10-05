package com.vaultgallery.app.ui.screens

import android.graphics.Bitmap
import android.text.format.Formatter
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vaultgallery.app.GalleryViewModel
import com.vaultgallery.app.data.AppSettings
import com.vaultgallery.app.data.VaultItemEntity
import com.vaultgallery.app.security.SecureFlag
import com.vaultgallery.app.security.VaultDataSource
import com.vaultgallery.app.security.VaultSession
import com.vaultgallery.app.ui.components.ConfirmDialog
import com.vaultgallery.app.ui.components.EmptyState
import com.vaultgallery.app.ui.components.LocalVaultAuth
import com.vaultgallery.app.ui.components.ShimmerGrid
import com.vaultgallery.app.ui.theme.Gold
import com.vaultgallery.app.util.PLAYBACK_SPEEDS
import com.vaultgallery.app.util.formatDuration
import com.vaultgallery.app.util.speedLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Decrypted thumbnails live only in memory, and only while a vault screen is open. */
object VaultThumbCache {
    private val cache = object : LruCache<Long, Bitmap>((Runtime.getRuntime().maxMemory() / 32).toInt().coerceAtLeast(1_048_576)) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }
    fun get(id: Long): Bitmap? = cache.get(id)
    fun put(id: Long, bmp: Bitmap) { cache.put(id, bmp) }
    fun clear() { cache.evictAll() }
}

/**
 * Wraps every vault screen. Blocks screenshots / recents previews while it is composed, re-checks the auto-lock timeout
 * whenever the app returns to the foreground, and drops decrypted thumbnails when the vault is left.
 */
@Composable
fun VaultGate(settings: AppSettings, content: @Composable (lockNow: () -> Unit) -> Unit) {
    val auth = LocalVaultAuth.current
    val timeout by rememberUpdatedState(settings.vaultAutoLockSec)
    var unlocked by remember { mutableStateOf(VaultSession.isOpen(settings.vaultAutoLockSec)) }

    DisposableEffect(Unit) {
        SecureFlag.enter()
        onDispose { SecureFlag.exit(); VaultSession.left(); VaultThumbCache.clear() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { VaultSession.left() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { unlocked = VaultSession.isOpen(timeout) }
    LaunchedEffect(unlocked) { if (!unlocked) auth.require { unlocked = true } }

    if (unlocked) {
        content { VaultSession.lock(); VaultThumbCache.clear(); unlocked = false }
    } else {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, tint = Gold, modifier = Modifier.size(48.dp))
            Text("Vault is locked", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Text("Unlock to see your private photos and videos.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Button(onClick = { auth.require { unlocked = true } }, modifier = Modifier.padding(top = 16.dp)) { Text("Unlock") }
        }
    }
}

@Composable
fun VaultScreen(vm: GalleryViewModel, settings: AppSettings, onOpenItem: (Int) -> Unit) {
    VaultGate(settings) { lockNow -> VaultContent(vm, settings, onOpenItem, lockNow) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun VaultContent(vm: GalleryViewModel, settings: AppSettings, onOpenItem: (Int) -> Unit, lockNow: () -> Unit) {
    val items by vm.vaultItems.collectAsStateWithLifecycle()
    val query by vm.vaultQuery.collectAsStateWithLifecycle()
    val list = items ?: emptyList()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var searchOpen by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    BackHandler(selecting) { selected = emptySet() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selecting) {
                val chosen = list.filter { it.id in selected }
                val allFav = chosen.isNotEmpty() && chosen.all { it.favorite }
                TopAppBar(
                    title = { Text("Selected: ${selected.size}") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, "Clear selection") } },
                    actions = {
                        IconButton(onClick = { vm.unhide(selected.toList()); selected = emptySet() }) { Icon(Icons.Default.Visibility, "Unhide selected") }
                        IconButton(onClick = { vm.setVaultFavorite(selected, !allFav) }) {
                            Icon(if (allFav) Icons.Default.FavoriteBorder else Icons.Default.Favorite, if (allFav) "Remove from favorites" else "Add to favorites")
                        }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteForever, "Delete selected permanently") }
                        Box {
                            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "More actions") }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(text = { Text("Select all") }, onClick = { moreMenu = false; selected = list.map { it.id }.toSet() })
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Vault", fontWeight = FontWeight.SemiBold)
                            Text("${list.size} private item${if (list.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) vm.vaultQuery.value = "" }) { Icon(Icons.Default.Search, "Search the Vault") }
                        IconButton(onClick = lockNow) { Icon(Icons.Default.Lock, "Lock the Vault now") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (!selecting && (searchOpen || query.isNotEmpty())) {
                OutlinedTextField(
                    value = query, onValueChange = { vm.vaultQuery.value = it }, placeholder = { Text("Search the Vault") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.vaultQuery.value = "" }) { Icon(Icons.Default.Clear, "Clear search") } },
                    shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            when {
                items == null -> ShimmerGrid()
                list.isEmpty() && query.isNotEmpty() -> EmptyState(Icons.Default.Search, "No matches", "Nothing in the Vault matches this search.")
                list.isEmpty() -> EmptyState(Icons.Default.Lock, "Your Vault is empty", "Your private photos and videos will appear here.")
                else -> {
                    val minCell = when (settings.layout) { 0 -> 200.dp; 1 -> 150.dp; else -> 104.dp }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minCell), contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize(),
                    ) {
                        items(list, key = { it.id }) { item ->
                            VaultThumb(
                                vm, item, selecting, item.id in selected,
                                onClick = {
                                    if (selecting) selected = if (item.id in selected) selected - item.id else selected + item.id
                                    else onOpenItem(list.indexOfFirst { it.id == item.id })
                                },
                                onLongClick = { selected = selected + item.id },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) ConfirmDialog(
        "Delete permanently?", "${selected.size} item(s) will be erased from the Vault. This can't be undone.", "Delete",
        { vm.deleteFromVault(selected.toList()); selected = emptySet() }, { confirmDelete = false },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultThumb(vm: GalleryViewModel, item: VaultItemEntity, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bmp by produceState(VaultThumbCache.get(item.id), item.id) {
        if (value == null) value = withContext(Dispatchers.IO) { vm.vaultStore.loadThumb(item) }?.also { VaultThumbCache.put(item.id, it) }
    }
    val shape = RoundedCornerShape(14.dp)
    val label = (if (item.isVideo) "Private video" else "Private photo") + (if (item.favorite) ", favorite" else "") + (if (selected) ", selected" else "")
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Gold.copy(alpha = .15f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).semantics { contentDescription = label },
    ) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (item.isVideo) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(32.dp).background(Color.Black.copy(alpha = .45f), CircleShape).padding(4.dp))
            if (item.durationMs > 0) Text(
                formatDuration(item.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (selecting) Box(
            Modifier.align(Alignment.TopStart).padding(8.dp).size(22.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = .35f), CircleShape)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall) }
        else if (item.favorite) Icon(Icons.Default.Favorite, "Favorite", tint = Gold, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
    }
}

// ============================ Viewer ============================

@Composable
fun VaultViewerScreen(vm: GalleryViewModel, settings: AppSettings, startIndex: Int, onBack: () -> Unit) {
    VaultGate(settings) { _ -> VaultViewerBody(vm, startIndex, onBack) }
}

private object Failed

@Composable
private fun VaultViewerBody(vm: GalleryViewModel, startIndex: Int, onBack: () -> Unit) {
    val items by vm.vaultItems.collectAsStateWithLifecycle()
    val list = items ?: return Box(Modifier.fillMaxSize().background(Color.Black))
    if (list.isEmpty()) { LaunchedEffect(Unit) { onBack() }; return Box(Modifier.fillMaxSize().background(Color.Black)) }

    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, list.lastIndex)) { list.size }
    var zoomed by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf<VaultItemEntity?>(null) }
    var info by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val current = list.getOrNull(pager.currentPage) ?: list.last()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize(), key = { list.getOrNull(it)?.id ?: it }) { page ->
            val item = list[page]
            if (item.isVideo) {
                val poster by produceState(VaultThumbCache.get(item.id), item.id) {
                    if (value == null) value = withContext(Dispatchers.IO) { vm.vaultStore.loadThumb(item) }?.also { VaultThumbCache.put(item.id, it) }
                }
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { chrome = !chrome }) }, contentAlignment = Alignment.Center) {
                    poster?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                    IconButton(onClick = { playing = item }, modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = .5f), CircleShape)) {
                        Icon(Icons.Default.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                }
            } else {
                val state by produceState<Any?>(null, item.id) {
                    value = withContext(Dispatchers.IO) { vm.vaultStore.decodeImage(item, 2048) } ?: Failed
                }
                when (val s = state) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
                    is Bitmap -> ZoomableBitmap(s, onTap = { chrome = !chrome }) { z -> if (page == pager.currentPage) zoomed = z }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Unable to open this photo.", color = Color.White) }
                }
            }
        }

        if (chrome && playing == null) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(current.displayName, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text("${pager.currentPage + 1} of ${list.size}", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f))))
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.setVaultFavorite(listOf(current.id), !current.favorite) }) {
                    Icon(if (current.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (current.favorite) "Remove from favorites" else "Add to favorites", tint = Color.White)
                }
                IconButton(onClick = { info = true }) { Icon(Icons.Default.Info, "Details", tint = Color.White) }
                IconButton(onClick = { vm.unhide(listOf(current.id)) }) { Icon(Icons.Default.Visibility, "Unhide: restore to Gallery", tint = Color.White) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteForever, "Delete permanently", tint = Color.White) }
            }
        }

        playing?.let { VaultVideoPlayer(vm, it) { playing = null } }
    }

    if (info) VaultInfoDialog(current) { info = false }
    if (confirmDelete) ConfirmDialog("Delete permanently?", "This item will be erased from the Vault. This can't be undone.", "Delete", { vm.deleteFromVault(listOf(current.id)) }, { confirmDelete = false })
}

@Composable
private fun ZoomableBitmap(bitmap: Bitmap, onTap: () -> Unit, onZoomChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale <= 1f) Offset.Zero else offset + panChange
        onZoomChange(scale > 1.02f)
    }
    Image(
        bitmap.asImageBitmap(), "Private photo", contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero; onZoomChange(scale > 1.02f) },
                )
            }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            // canPan is false at 1x, so a one-finger swipe still reaches the pager.
            .transformable(state, canPan = { scale > 1f }),
    )
}

@Composable
private fun VaultInfoDialog(v: VaultItemEntity, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (v.isVideo) "Private video" else "Private photo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(v.displayName, style = MaterialTheme.typography.titleSmall)
                if (v.isVideo) Text("Duration: ${formatDuration(v.durationMs)}")
                if (v.width > 0) Text("Resolution: ${v.width} × ${v.height}")
                Text("Size: ${Formatter.formatShortFileSize(ctx, v.size)}")
                Text("Type: ${v.mimeType}")
                Text("Taken: ${fmt.format(Date(v.dateTaken))}")
                Text("Hidden: ${fmt.format(Date(v.hiddenAt))}")
                Text("Stored encrypted inside Vault Gallery only")   // no file path is ever shown
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// ============================ Encrypted video playback ============================

@AndroidOptIn(UnstableApi::class)
@Composable
private fun VaultVideoPlayer(vm: GalleryViewModel, item: VaultItemEntity, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(item.durationMs) }
    var seeking by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    var controls by remember { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }

    val exo = remember {
        ExoPlayer.Builder(ctx)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY && exo.duration != C.TIME_UNSET) dur = exo.duration
            }
            override fun onPlayerError(error: PlaybackException) { failed = true }   // code only; no message or URI is shown or logged
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l); exo.release() }
    }
    LaunchedEffect(item.id) {
        // Bytes are decrypted on the fly while ExoPlayer reads them; nothing is written to disk or MediaStore.
        val factory = DataSource.Factory { VaultDataSource { vm.vaultStore.openReader(item) } }
        val source = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.Builder().setUri("vault://item/${item.id}").setMimeType(item.mimeType).build())
        exo.setMediaSource(source)
        exo.prepare()
        exo.playWhenReady = true
    }
    LaunchedEffect(speed) { exo.setPlaybackSpeed(speed) }
    LaunchedEffect(muted) { exo.volume = if (muted) 0f else 1f }
    LaunchedEffect(exo, seeking) { while (true) { if (!seeking) pos = exo.currentPosition.coerceAtLeast(0); delay(250) } }
    LaunchedEffect(controls, isPlaying, interaction) { if (controls && isPlaying) { delay(3_500); controls = false } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { exo.pause() }
    BackHandler { onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; player = exo } },
            update = { it.keepScreenOn = isPlaying },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onTap = { controls = !controls; interaction++ },
                onDoubleTap = { o ->
                    val delta = if (o.x < size.width / 2f) -10_000L else 10_000L
                    exo.seekTo((exo.currentPosition + delta).coerceIn(0, if (dur > 0) dur else Long.MAX_VALUE))
                },
            )
        })
        if (buffering && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Gold)
        if (failed) Column(
            Modifier.align(Alignment.Center).padding(32.dp).background(Color.Black.copy(alpha = .8f), MaterialTheme.shapes.large).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Unable to play this video.", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { failed = false; buffering = true; exo.prepare(); exo.play() }) { Text("Retry") }
                OutlinedButton(onClick = onClose) { Text("Close") }
            }
        }
        if (controls && !failed) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close player", tint = Color.White) }
                Text(item.displayName, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            IconButton(
                onClick = { if (exo.isPlaying) exo.pause() else exo.play(); interaction++ },
                modifier = Modifier.align(Alignment.Center).size(72.dp).background(Color.Black.copy(alpha = .5f), CircleShape),
            ) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(44.dp)) }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f))))
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(if (seeking) (drag * dur).toLong() else pos), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = if (seeking) drag else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                        onValueChange = { seeking = true; drag = it; interaction++ },
                        onValueChangeFinished = { exo.seekTo((drag * dur).toLong()); pos = exo.currentPosition; seeking = false },
                        colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = Color.White.copy(alpha = .25f)),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(formatDuration(dur), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row {
                        IconButton(onClick = { exo.seekTo((exo.currentPosition - 10_000).coerceAtLeast(0)); interaction++ }) { Icon(Icons.Default.Replay10, "Rewind 10 seconds", tint = Color.White) }
                        IconButton(onClick = { exo.seekTo((exo.currentPosition + 10_000).coerceAtMost(if (dur > 0) dur else Long.MAX_VALUE)); interaction++ }) { Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { muted = !muted; interaction++ }) { Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, if (muted) "Unmute" else "Mute", tint = Color.White) }
                        TextButton(onClick = { speed = PLAYBACK_SPEEDS[(PLAYBACK_SPEEDS.indexOf(speed) + 1).mod(PLAYBACK_SPEEDS.size)]; interaction++ }) { Text(speedLabel(speed), color = Color.White) }
                    }
                }
            }
        }
    }
}
