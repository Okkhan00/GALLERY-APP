package com.vaultgallery.app.ui.screens

import android.content.ClipData
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vaultgallery.app.GalleryViewModel
import com.vaultgallery.app.data.AppSettings
import com.vaultgallery.app.data.PhotoEntity
import com.vaultgallery.app.domain.UnlockResult
import com.vaultgallery.app.security.AppLockManager
import com.vaultgallery.app.ui.components.ConfirmDialog
import com.vaultgallery.app.ui.components.LocalVaultAuth
import com.vaultgallery.app.ui.components.UnlockDialog
import com.vaultgallery.app.ui.theme.Gold
import com.vaultgallery.app.util.PLAYBACK_SPEEDS
import com.vaultgallery.app.util.PlayerFailure
import com.vaultgallery.app.util.classifyPlaybackError
import com.vaultgallery.app.util.displayHeight
import com.vaultgallery.app.util.displayWidth
import com.vaultgallery.app.util.findFragmentActivity
import com.vaultgallery.app.util.formatDuration
import com.vaultgallery.app.util.message
import com.vaultgallery.app.util.speedLabel
import com.vaultgallery.app.util.videoFormatLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val SEEK_STEP_MS = 10_000L
private const val AUTO_HIDE_MS = 3_500L

/**
 * Full-screen video viewer. [startIndex] indexes the same list the gallery grid shows (photos + videos, current
 * filter/sort/search), so Previous/Next walk the videos in that exact order.
 */
@Composable
fun VideoPlayerScreen(vm: GalleryViewModel, settings: AppSettings, startIndex: Int, onBack: () -> Unit, onTools: (Long) -> Unit) {
    val all by vm.photos.collectAsStateWithLifecycle()
    val list = all ?: return Box(Modifier.fillMaxSize().background(Color.Black))
    val videos = remember(list) { list.filter { it.isVideo } }

    var currentId by rememberSaveable {
        mutableStateOf(list.getOrNull(startIndex)?.takeIf { it.isVideo }?.id ?: videos.firstOrNull()?.id ?: -1L)
    }
    val idx = videos.indexOfFirst { it.id == currentId }
    var lastIdx by remember { mutableIntStateOf(0) }
    SideEffect { if (idx >= 0) lastIdx = idx }

    // The current video disappeared (trashed here, or deleted elsewhere): move to a neighbour or leave.
    LaunchedEffect(idx, videos.size) {
        if (idx < 0) {
            if (videos.isEmpty()) onBack() else currentId = videos[lastIdx.coerceIn(0, videos.size - 1)].id
        }
    }
    val video = videos.getOrNull(idx) ?: return Box(Modifier.fillMaxSize().background(Color.Black))

    LaunchedEffect(video.id) { if (!video.locked) vm.touchRecent(video.id) }

    if (video.locked) {
        LockedVideoPane(vm, video, onBack)
    } else {
        PlayerPane(
            vm = vm, settings = settings, video = video, ordinal = idx + 1, total = videos.size,
            hasPrev = idx > 0, hasNext = idx < videos.size - 1,
            onPrev = { videos.getOrNull(idx - 1)?.let { currentId = it.id } },
            onNext = { videos.getOrNull(idx + 1)?.let { currentId = it.id } },
            onBack = onBack, onTools = onTools,
        )
    }
}

/** A locked video is never opened: no ExoPlayer is created and no frame is decoded until it is unlocked. */
@Composable
private fun LockedVideoPane(vm: GalleryViewModel, video: PhotoEntity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, tint = Gold, modifier = Modifier.size(40.dp))
            Text("This video is locked", color = Color.White, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = { dialog = true }, modifier = Modifier.padding(top = 12.dp)) { Text("Unlock") }
        }
    }
    if (dialog) {
        UnlockDialog(video, wallet, onUnlock = { cur ->
            scope.launch {
                val msg = if (vm.unlock(video.id, cur) == UnlockResult.Success) "Unlocked" else "Not enough credits"
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show(); dialog = false
            }
        }, onDismiss = { dialog = false })
    }
}

@AndroidOptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerPane(
    vm: GalleryViewModel, settings: AppSettings, video: PhotoEntity, ordinal: Int, total: Int,
    hasPrev: Boolean, hasNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onBack: () -> Unit, onTools: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val activity = remember(ctx) { ctx.findFragmentActivity() }

    // ---- user-visible state; rememberSaveable keeps speed/mute/volume/fullscreen across config changes and process death ----
    var speed by rememberSaveable { mutableStateOf(settings.videoSpeedPct / 100f) }
    var muted by rememberSaveable { mutableStateOf(false) }
    var volume by rememberSaveable { mutableStateOf(1f) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var speedMenu by remember { mutableStateOf(false) }
    var volumeMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var confirmHide by remember { mutableStateOf(false) }
    val auth = LocalVaultAuth.current

    // ---- player state ----
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<PlayerFailure?>(null) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(video.durationMs) }
    var seeking by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var flashSide by remember { mutableIntStateOf(0) }   // -1 rewind, 1 forward, 0 none
    var flashKey by remember { mutableIntStateOf(0) }

    // One ExoPlayer for this screen. Audio focus, calls, other apps and "becoming noisy" (headphones/Bluetooth
    // disconnect) are all handled by ExoPlayer itself through these two settings.
    val exo = remember {
        ExoPlayer.Builder(ctx)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    // No background playback: the player is released when this screen leaves composition.
    DisposableEffect(exo) { onDispose { exo.release() } }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED
                if (state == Player.STATE_READY && exo.duration != C.TIME_UNSET) dur = exo.duration
            }
            override fun onPlayerError(error: PlaybackException) {
                // Only the error CODE is used; no raw message or stack trace reaches the UI, and no URI is logged.
                failure = classifyPlaybackError(error.errorCode)
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // Load (or switch to) the current video. Resume from the saved position when enabled.
    LaunchedEffect(video.id) {
        failure = null; ended = false; pos = 0; dur = video.durationMs; buffering = true
        exo.setMediaItem(MediaItem.fromUri(Uri.parse(video.contentUri)))
        exo.setPlaybackSpeed(speed)
        exo.prepare()
        val resumeAt = video.lastPositionMs
        val canResume = settings.videoRememberPosition && resumeAt > 3_000 && (video.durationMs <= 0 || resumeAt < video.durationMs - 3_000)
        if (canResume) exo.seekTo(resumeAt)
        exo.playWhenReady = true
    }

    LaunchedEffect(speed) { exo.setPlaybackSpeed(speed) }
    // Player-level volume only: the phone's own volume is never touched.
    LaunchedEffect(muted, volume) { exo.volume = if (muted) 0f else volume }

    LaunchedEffect(exo, seeking) {
        while (true) {
            if (!seeking) pos = exo.currentPosition.coerceAtLeast(0)
            delay(250)
        }
    }

    // Persist position every few seconds while playing, and when leaving.
    val latestPos by rememberUpdatedState(pos)
    val latestEnded by rememberUpdatedState(ended)
    val rememberPos = settings.videoRememberPosition
    LaunchedEffect(isPlaying, video.id) {
        while (isPlaying) { delay(5_000); if (rememberPos) vm.savePosition(video.id, exo.currentPosition) }
    }
    DisposableEffect(video.id) {
        onDispose { if (rememberPos) vm.savePosition(video.id, if (latestEnded) 0 else latestPos) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        exo.pause()
        if (rememberPos) vm.savePosition(video.id, exo.currentPosition)
    }

    // End of video: replay UI is shown; "play next" only when the user enabled it.
    LaunchedEffect(ended) {
        if (ended) {
            vm.savePosition(video.id, 0)
            if (settings.videoAutoNext && hasNext) onNext()
        }
    }

    // Controls fade away after inactivity while playing.
    LaunchedEffect(controls, isPlaying, interaction, speedMenu, volumeMenu, moreMenu) {
        if (controls && isPlaying && !speedMenu && !volumeMenu && !moreMenu) { delay(AUTO_HIDE_MS); controls = false }
    }
    LaunchedEffect(flashKey) { if (flashKey > 0) { delay(650); flashSide = 0 } }

    // Fullscreen: hide system bars, and go landscape for landscape clips. Restored when toggled off or on exit.
    DisposableEffect(fullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullscreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            if (video.displayWidth() >= video.displayHeight()) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (fullscreen) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    BackHandler(enabled = fullscreen) { fullscreen = false }

    fun poke() { interaction++ }
    fun seekBy(delta: Long) {
        val limit = if (dur > 0) dur else Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + delta).coerceIn(0, limit))
        pos = exo.currentPosition
    }
    fun togglePlay() {
        if (ended) { exo.seekTo(0); exo.play() } else if (exo.isPlaying) exo.pause() else exo.play()
        poke()
    }
    fun share() {
        val uri = Uri.parse(video.contentUri)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = video.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)       // content URI + temporary read grant; never a file path
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        exo.pause()
        AppLockManager.skipNextLock = true
        ctx.startActivity(Intent.createChooser(send, "Share video"))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false                       // our own Compose controls are drawn on top
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = exo
                }
            },
            // Screen stays awake only while a video is actually playing; cleared automatically when this view goes away.
            update = { it.keepScreenOn = isPlaying && settings.videoKeepAwake },
            modifier = Modifier.fillMaxSize(),
        )

        // Gesture layer: single tap shows/hides controls, double tap on the left/right half seeks 10 s.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controls = !controls; poke() },
                    onDoubleTap = { offset ->
                        val left = offset.x < size.width / 2f
                        seekBy(if (left) -SEEK_STEP_MS else SEEK_STEP_MS)
                        flashSide = if (left) -1 else 1; flashKey++
                    },
                )
            }
        )

        // Double-tap feedback.
        AnimatedVisibility(flashSide != 0, Modifier.align(if (flashSide < 0) Alignment.CenterStart else Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.padding(horizontal = 48.dp).background(Color.Black.copy(alpha = .55f), CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (flashSide < 0) Icons.Default.Replay10 else Icons.Default.Forward10, null, tint = Color.White)
                Text(" 10 s", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (buffering && failure == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Gold)

        // ---- Error state: never shows a stack trace ----
        failure?.let { f ->
            Column(
                Modifier.align(Alignment.Center).padding(32.dp).background(Color.Black.copy(alpha = .8f), MaterialTheme.shapes.large).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = Gold, modifier = Modifier.size(40.dp))
                Text("Unable to play this video.", color = Color.White, style = MaterialTheme.typography.titleMedium)
                if (f != PlayerFailure.OTHER) Text(f.message(), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { failure = null; buffering = true; exo.prepare(); exo.play() }) { Text("Retry") }
                    OutlinedButton(onClick = { showInfo = true }) { Text("Info") }
                    OutlinedButton(onClick = onBack) { Text("Close") }
                }
            }
        }

        // ---- Top bar ----
        AnimatedVisibility(controls, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(video.displayName, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text("Video $ordinal of $total", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { showInfo = true; poke() }) { Icon(Icons.Default.Info, "Video details", tint = Color.White) }
                Box {
                    IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "More options", tint = Color.White) }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (video.favorite) "Remove from favorites" else "Add to favorites") },
                            leadingIcon = { Icon(if (video.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
                            onClick = { moreMenu = false; vm.toggleFavorite(video) })
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { moreMenu = false; share() })
                        DropdownMenuItem(text = { Text("Trim, mute or save frame") }, leadingIcon = { Icon(Icons.Default.ContentCut, null) }, onClick = { moreMenu = false; exo.pause(); onTools(video.id) })
                        DropdownMenuItem(text = { Text("Hide in Vault") }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null) }, onClick = { moreMenu = false; confirmHide = true })
                        DropdownMenuItem(text = { Text("Move to trash") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { moreMenu = false; confirmTrash = true })
                    }
                }
            }
        }

        // ---- Center: previous / play-pause / next ----
        AnimatedVisibility(controls && failure == null, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onPrev() }, enabled = hasPrev, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous video", tint = if (hasPrev) Color.White else Color.White.copy(alpha = .3f), modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { togglePlay() }, modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = .5f), CircleShape)) {
                    Icon(
                        when { ended -> Icons.Default.Replay; isPlaying -> Icons.Default.Pause; else -> Icons.Default.PlayArrow },
                        when { ended -> "Replay"; isPlaying -> "Pause"; else -> "Play" },
                        tint = Color.White, modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = { onNext() }, enabled = hasNext, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipNext, "Next video", tint = if (hasNext) Color.White else Color.White.copy(alpha = .3f), modifier = Modifier.size(34.dp))
                }
            }
        }

        // ---- Bottom: time + seek bar, then transport / mute / speed / fullscreen ----
        AnimatedVisibility(controls && failure == null, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f))))
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(if (seeking) (dragFraction * dur).toLong() else pos), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = if (seeking) dragFraction else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                        onValueChange = { seeking = true; dragFraction = it; poke() },
                        onValueChangeFinished = { exo.seekTo((dragFraction * dur).toLong()); pos = exo.currentPosition; seeking = false },
                        colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = Color.White.copy(alpha = .25f)),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(formatDuration(dur), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { seekBy(-SEEK_STEP_MS); poke() }) { Icon(Icons.Default.Replay10, "Rewind 10 seconds", tint = Color.White) }
                        IconButton(onClick = { togglePlay() }) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color.White)
                        }
                        IconButton(onClick = { seekBy(SEEK_STEP_MS); poke() }) { Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Tap = mute/unmute. Long-press = volume slider (player volume only, device volume is untouched).
                        Box {
                            Box(
                                Modifier.size(48.dp).combinedClickable(onClick = { muted = !muted; poke() }, onLongClick = { volumeMenu = true }),
                                contentAlignment = Alignment.Center,
                            ) { Icon(if (muted || volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, if (muted) "Unmute" else "Mute", tint = Color.White) }
                            DropdownMenu(expanded = volumeMenu, onDismissRequest = { volumeMenu = false }) {
                                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Slider(value = volume, onValueChange = { volume = it; muted = false }, modifier = Modifier.width(160.dp))
                                }
                            }
                        }
                        Box {
                            TextButton(onClick = { speedMenu = true }) { Text(speedLabel(speed), color = Color.White) }
                            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                                PLAYBACK_SPEEDS.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(speedLabel(s)) },
                                        trailingIcon = { if (s == speed) Icon(Icons.Default.Check, "Selected") },
                                        onClick = { speed = s; speedMenu = false })
                                }
                            }
                        }
                        IconButton(onClick = { fullscreen = !fullscreen; poke() }) {
                            Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, if (fullscreen) "Exit fullscreen" else "Fullscreen", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (confirmTrash) ConfirmDialog("Move to trash?", "You can restore this video from Trash later.", "Move to trash", { vm.trash(listOf(video.id)) }, { confirmTrash = false })
    if (confirmHide) ConfirmDialog(
        "Hide in Vault?", "This video will be encrypted in the Vault and removed from your Gallery and other apps. Android will ask you to confirm deleting the original.",
        "Continue", { exo.pause(); auth.require { vm.hide(listOf(video.id)) } }, { confirmHide = false },
    )
    if (showInfo) VideoInfoDialog(vm, video) { showInfo = false }
}

@Composable
private fun VideoInfoDialog(vm: GalleryViewModel, v: PhotoEntity, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val tagNames by vm.tagNames(v.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Video") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(v.displayName, style = MaterialTheme.typography.titleSmall)
                Text("Duration: ${formatDuration(v.durationMs)}")
                if (v.width > 0) Text("Resolution: ${v.displayWidth()} × ${v.displayHeight()}")
                Text("Size: ${Formatter.formatShortFileSize(ctx, v.size)}")
                Text("Format: ${videoFormatLabel(v.mimeType, v.displayName)}")
                Text("MIME type: ${v.mimeType}")
                if (v.bucket.isNotBlank()) Text("Folder: ${v.bucket}")
                Text("Added: ${fmt.format(Date(v.dateAdded))}")
                Text("Modified: ${fmt.format(Date(v.dateModified))}")
                Text("Source: ${v.contentUri}")   // content:// reference only, never a file path
                Text("Tags: ${if (tagNames.isEmpty()) "none" else tagNames.joinToString()}")
                Text("Favorite: ${if (v.favorite) "yes" else "no"}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
