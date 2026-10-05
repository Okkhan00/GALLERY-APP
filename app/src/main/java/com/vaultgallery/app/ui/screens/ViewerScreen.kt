package com.vaultgallery.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vaultgallery.app.GalleryViewModel
import com.vaultgallery.app.data.AppSettings
import com.vaultgallery.app.data.PhotoEntity
import com.vaultgallery.app.domain.UnlockResult
import com.vaultgallery.app.security.AppLockManager
import com.vaultgallery.app.ui.components.AppSheet
import com.vaultgallery.app.ui.components.ConfirmDialog
import com.vaultgallery.app.ui.components.LocalVaultAuth
import com.vaultgallery.app.ui.components.SheetAction
import com.vaultgallery.app.ui.theme.LocalAnimations
import com.vaultgallery.app.util.shareMedia
import com.vaultgallery.app.ui.components.UnlockDialog
import com.vaultgallery.app.ui.theme.Gold
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ViewerScreen(vm: GalleryViewModel, settings: AppSettings, startIndex: Int, onBack: () -> Unit, onEdit: (Long) -> Unit, onPlayVideo: (Int) -> Unit) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    val list = photos ?: return Box(Modifier.fillMaxSize().background(Color.Black))
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        Box(Modifier.fillMaxSize().background(Color.Black))
    } else {
        ViewerContent(vm, settings, list, startIndex, onBack, onEdit, onPlayVideo)
    }
}

@Composable
private fun ViewerContent(vm: GalleryViewModel, settings: AppSettings, list: List<PhotoEntity>, startIndex: Int, onBack: () -> Unit, onEdit: (Long) -> Unit, onPlayVideo: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, list.size - 1)) { list.size }
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    var zoomed by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var moreSheet by remember { mutableStateOf(false) }
    var confirmHide by remember { mutableStateOf(false) }
    val auth = LocalVaultAuth.current
    val anim = LocalAnimations.current
    val enterAnim = if (anim) fadeIn() else EnterTransition.None
    val exitAnim = if (anim) fadeOut() else ExitTransition.None
    var unlockFor by remember { mutableStateOf<PhotoEntity?>(null) }
    val photo = list.getOrNull(pager.currentPage)
    val progress = remember { Animatable(0f) }

    LaunchedEffect(photo?.id) { if (photo != null && !photo.locked) vm.touchRecent(photo.id) }

    // Slideshow: progress bar fills over the chosen interval, then advances.
    LaunchedEffect(playing, pager.currentPage, settings.slideshowSec) {
        progress.snapTo(0f)
        if (playing) {
            progress.animateTo(1f, tween(settings.slideshowSec * 1000, easing = LinearEasing))
            pager.animateScrollToPage((pager.currentPage + 1) % list.size)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, userScrollEnabled = !zoomed, key = { list.getOrNull(it)?.id ?: it }, modifier = Modifier.fillMaxSize()) { page ->
            val p = list[page]
            if (p.locked) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, null, tint = Gold, modifier = Modifier.padding(8.dp))
                    Text(if (p.isVideo) "This video is locked" else "This photo is locked", color = Color.White)
                    Button(onClick = { unlockFor = p }, modifier = Modifier.padding(top = 12.dp)) { Text("Unlock") }
                }
            } else if (p.isVideo) {
                // Swiping onto a video shows its poster frame; tapping play opens the dedicated player. Nothing autoplays here.
                Box(Modifier.fillMaxSize().clickable { onPlayVideo(page) }, contentAlignment = Alignment.Center) {
                    AsyncImage(p.contentUri, p.displayName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    Icon(
                        Icons.Default.PlayArrow, "Play video", tint = Color.White,
                        modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = .5f), CircleShape).padding(12.dp),
                    )
                }
            } else {
                ZoomableImage(p.contentUri, p.displayName, onTap = { chrome = !chrome }) { if (page == pager.currentPage) zoomed = it }
            }
        }

        AnimatedVisibility(chrome, Modifier.align(Alignment.TopCenter), enter = enterAnim, exit = exitAnim) {
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .55f)).statusBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                    Text(if (photo?.locked == true) "Locked photo" else photo?.displayName ?: "", color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("${pager.currentPage + 1}/${list.size}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { playing = !playing }) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause slideshow" else "Start slideshow", tint = Color.White)
                    }
                }
                if (playing) LinearProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxWidth())
            }
        }

        AnimatedVisibility(chrome, Modifier.align(Alignment.BottomCenter), enter = enterAnim, exit = exitAnim) {
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .55f)).navigationBarsPadding(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val usable = photo != null && !photo.locked
                val fav = photo?.favorite == true
                val heart by animateFloatAsState(if (fav && anim) 1.2f else 1f, spring(dampingRatio = 0.4f), label = "heart")
                IconButton(onClick = { photo?.let(vm::toggleFavorite) }, enabled = usable) {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (fav) "Remove from favorites" else "Add to favorites", tint = if (fav) Gold else Color.White,
                        modifier = Modifier.scale(heart),
                    )
                }
                IconButton(onClick = {
                    photo?.let { ctx.shareMedia(listOf(Uri.parse(it.contentUri)), listOf(it.mimeType), if (it.isVideo) "Share video" else "Share photo") }
                }, enabled = usable) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                IconButton(onClick = { confirmTrash = true }, enabled = photo != null) { Icon(Icons.Default.Delete, "Move to trash", tint = Color.White) }
                IconButton(onClick = { moreSheet = true }, enabled = photo != null) { Icon(Icons.Default.MoreVert, "More actions", tint = Color.White) }
            }
        }
    }

    if (moreSheet && photo != null) {
        val usable = !photo.locked
        AppSheet(onDismiss = { moreSheet = false }) {
            if (!photo.isVideo) SheetAction(Icons.Default.Edit, "Edit", enabled = usable) { moreSheet = false; onEdit(photo.id) }
            SheetAction(Icons.Default.Info, "Info", enabled = usable) { moreSheet = false; showInfo = true }
            SheetAction(Icons.Default.VisibilityOff, "Hide in Vault", enabled = usable) { moreSheet = false; confirmHide = true }
        }
    }
    if (confirmHide && photo != null) ConfirmDialog(
        "Hide in Vault?", "This item will be encrypted in the Vault and removed from your Gallery and other apps. Android will ask you to confirm deleting the original.",
        "Continue", { auth.require { vm.hide(listOf(photo.id)) } }, { confirmHide = false },
    )
    if (confirmTrash && photo != null) ConfirmDialog("Move to trash?", "You can restore it from Trash later.", "Move to trash", { vm.trash(listOf(photo.id)) }, { confirmTrash = false })
    if (showInfo && photo != null) InfoDialog(vm, photo) { showInfo = false }
    unlockFor?.let { p ->
        UnlockDialog(p, wallet, onUnlock = { cur ->
            scope.launch {
                val msg = if (vm.unlock(p.id, cur) == UnlockResult.Success) "Unlocked" else "Not enough credits"
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show(); unlockFor = null
            }
        }, onDismiss = { unlockFor = null })
    }
}

@Composable
private fun InfoDialog(vm: GalleryViewModel, p: PhotoEntity, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val tagNames by vm.tagNames(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("File: ${p.displayName}")
                if (p.isVideo) Text("Duration: ${com.vaultgallery.app.util.formatDuration(p.durationMs)}")
                if (p.bucket.isNotBlank()) Text("Folder: ${p.bucket}")
                Text("Size: ${Formatter.formatShortFileSize(ctx, p.size)}")
                if (p.width > 0) Text("Dimensions: ${p.width} × ${p.height}")
                Text("Taken: ${fmt.format(Date(p.dateTaken))}")
                Text("Modified: ${fmt.format(Date(p.dateModified))}")
                Text("Type: ${p.mimeType}")
                Text("Tags: ${if (tagNames.isEmpty()) "none" else tagNames.joinToString()}")
                Text("Favorite: ${if (p.favorite) "yes" else "no"}")
                Text("Edited: ${if (p.edited) "yes (copy saved)" else "no"}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Pinch / double-tap zoom and pan. Only consumes gestures while zoomed or multi-touch, so the pager still swipes at 1x. */
@Composable
private fun ZoomableImage(model: String, description: String, onTap: () -> Unit, onZoomChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier.fillMaxSize().onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        onZoomChange(scale > 1f)
                    })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (event.changes.size > 1 || scale > 1f) {
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            val maxX = size.width * (newScale - 1f) / 2f
                            val maxY = size.height * (newScale - 1f) / 2f
                            offset = if (newScale == 1f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                            scale = newScale
                            onZoomChange(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            model = model, contentDescription = description, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        )
    }
}
