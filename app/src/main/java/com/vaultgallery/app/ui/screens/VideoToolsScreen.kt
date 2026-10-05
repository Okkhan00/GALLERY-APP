package com.vaultgallery.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vaultgallery.app.GalleryViewModel
import com.vaultgallery.app.data.PhotoEntity
import com.vaultgallery.app.util.VideoTools
import com.vaultgallery.app.util.formatDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Trim, remove audio and save a frame. Everything is saved as a NEW file; the original video is never changed.
 * The heavy work runs on a background dispatcher with a progress dialog that can be cancelled.
 */
@AndroidOptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoToolsScreen(vm: GalleryViewModel, videoId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val video by produceState<PhotoEntity?>(null, videoId) { value = vm.getPhoto(videoId) }

    var isPlaying by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(0L) }
    var removeAudio by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var working by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

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
                if (state == Player.STATE_READY && exo.duration != C.TIME_UNSET && dur == 0L) { dur = exo.duration; if (endMs == 0L) endMs = dur }
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l); exo.release() }
    }
    LaunchedEffect(video?.id) {
        val v = video ?: return@LaunchedEffect
        if (v.locked) return@LaunchedEffect   // locked videos are never opened
        dur = v.durationMs; endMs = v.durationMs
        exo.setMediaItem(MediaItem.fromUri(Uri.parse(v.contentUri)))
        exo.prepare()
    }
    LaunchedEffect(exo, seeking) {
        while (true) {
            if (!seeking) pos = exo.currentPosition.coerceAtLeast(0)
            if (previewing && endMs > 0 && pos >= endMs) { exo.pause(); previewing = false }
            delay(200)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { exo.pause() }

    val v = video
    Scaffold(topBar = {
        TopAppBar(title = { Text("Edit video") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        if (v == null) { Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Scaffold }
        if (v.locked) {
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Unlock this video before editing it.") }
            return@Scaffold
        }
        Column(Modifier.padding(inner).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
                AndroidView(
                    factory = { c -> PlayerView(c).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; player = exo } },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { previewing = false; if (exo.isPlaying) exo.pause() else exo.play() }) })
                IconButton(
                    onClick = { previewing = false; if (exo.isPlaying) exo.pause() else exo.play() },
                    modifier = Modifier.align(Alignment.Center).size(64.dp).background(Color.Black.copy(alpha = .5f), CircleShape),
                ) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(40.dp)) }
            }
            Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(if (seeking) (drag * dur).toLong() else pos), style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = if (seeking) drag else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                        onValueChange = { seeking = true; drag = it },
                        onValueChangeFinished = { exo.seekTo((drag * dur).toLong()); pos = exo.currentPosition; seeking = false },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(formatDuration(dur), style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { if (pos < endMs) startMs = pos }, modifier = Modifier.weight(1f)) { Text("Start: ${formatDuration(startMs)}") }
                    OutlinedButton(onClick = { if (pos > startMs) endMs = pos }, modifier = Modifier.weight(1f)) { Text("End: ${formatDuration(endMs)}") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Clip length ${formatDuration((endMs - startMs).coerceAtLeast(0))}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { exo.seekTo(startMs); exo.play(); previewing = true }) { Text("Preview") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Remove audio")
                    Switch(checked = removeAudio, onCheckedChange = { removeAudio = it })
                }
                Text(
                    "Saved as a new file in Movies/VaultGallery. A trimmed clip may start a moment before the chosen time, at the nearest keyframe.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    enabled = working == null && endMs > startMs, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        exo.pause(); progress = 0f; working = "Saving video…"
                        job = scope.launch {
                            try {
                                VideoTools.trim(ctx, Uri.parse(v.contentUri), v.displayName, startMs, if (endMs >= dur) 0L else endMs, removeAudio) { progress = it }
                                vm.scan()
                                Toast.makeText(ctx, "Saved to Movies/VaultGallery", Toast.LENGTH_SHORT).show()
                            } catch (e: CancellationException) {
                                Toast.makeText(ctx, "Cancelled", Toast.LENGTH_SHORT).show()
                                throw e
                            } catch (e: VideoTools.ToolException) {
                                Toast.makeText(ctx, e.message ?: "Couldn't save this video.", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Couldn't save this video.", Toast.LENGTH_LONG).show()
                            } finally { working = null; job = null }
                        }
                    },
                ) { Text(if (removeAudio) "Save silent copy" else "Save trimmed copy") }
                OutlinedButton(
                    enabled = working == null, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    onClick = {
                        val at = pos
                        working = "Saving frame…"; progress = 0f
                        job = scope.launch {
                            try {
                                VideoTools.saveFrame(ctx, Uri.parse(v.contentUri), v.displayName, at)
                                vm.scan()
                                Toast.makeText(ctx, "Frame saved to Pictures/VaultGallery", Toast.LENGTH_SHORT).show()
                            } catch (e: CancellationException) { throw e }
                            catch (e: VideoTools.ToolException) { Toast.makeText(ctx, e.message ?: "Couldn't save the frame.", Toast.LENGTH_LONG).show() }
                            catch (e: Exception) { Toast.makeText(ctx, "Couldn't save the frame.", Toast.LENGTH_LONG).show() }
                            finally { working = null; job = null }
                        }
                    },
                ) { Text("Save this frame as a photo") }
            }
        }
    }

    working?.let { label ->
        AlertDialog(
            onDismissRequest = {}, title = { Text(label) },
            text = { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { job?.cancel() }) { Text("Cancel") } },
        )
    }
}
