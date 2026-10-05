package com.vaultgallery.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vaultgallery.app.GalleryViewModel
import com.vaultgallery.app.data.PhotoEntity
import com.vaultgallery.app.util.EditCrop
import com.vaultgallery.app.util.EditParams
import com.vaultgallery.app.util.EditPresets
import com.vaultgallery.app.util.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val SECTIONS = listOf("Presets", "Light", "Color", "Detail", "Crop")

/**
 * Non-destructive: the original file is never touched; "Save as copy" writes a new image. Tools are grouped into five tabs so
 * only a few controls are visible at a time. The preview is rendered from a downscaled bitmap, the saved copy from a bounded
 * larger one (with automatic fallback to smaller sizes if memory runs short).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: GalleryViewModel, photoId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val photo by produceState<PhotoEntity?>(null, photoId) { value = vm.getPhoto(photoId) }
    val preview by produceState<Bitmap?>(null, photo?.id) {
        val p = photo
        value = if (p == null || p.locked) null else withContext(Dispatchers.IO) {
            // One bounded decode for the preview; ~1024px keeps slider drags smooth even on big photos.
            ImageIO.load(ctx, Uri.parse(p.contentUri), 1024)
        }
    }

    var params by remember { mutableStateOf(EditParams()) }
    val undo = remember { mutableStateListOf<EditParams>() }
    val redo = remember { mutableStateListOf<EditParams>() }
    var dragBase by remember { mutableStateOf<EditParams?>(null) }
    var saving by remember { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(0) }
    var showOriginal by remember { mutableStateOf(false) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }

    fun commit(next: EditParams) { undo.add(params); redo.clear(); params = next }
    fun slide(next: EditParams) { if (dragBase == null) dragBase = params; params = next }
    fun finishSlide() { dragBase?.let { undo.add(it); redo.clear() }; dragBase = null }

    // Re-render shortly after the last change; an older render is cancelled when a newer one starts.
    LaunchedEffect(preview, params) {
        val base = preview ?: return@LaunchedEffect
        delay(30)
        rendered = withContext(Dispatchers.Default) { runCatching { ImageIO.render(base, params) }.getOrNull() } ?: rendered
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Edit") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { redo.add(params); params = undo.removeAt(undo.lastIndex) }, enabled = undo.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                IconButton(onClick = { undo.add(params); params = redo.removeAt(redo.lastIndex) }, enabled = redo.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                IconButton(onClick = { commit(EditParams()) }, enabled = params != EditParams()) { Icon(Icons.Default.Refresh, "Reset all edits") }
            },
        )
    }) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val shown = if (showOriginal) preview else (rendered ?: preview)
                if (shown == null) {
                    if (photo?.locked == true) Text("Unlock this photo before editing it.") else CircularProgressIndicator()
                } else {
                    Image(shown.asImageBitmap(), "Edit preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    // Hold to see the original; release to see the edit again.
                    Surface(
                        shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = .85f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).pointerInput(Unit) {
                            detectTapGestures(onPress = { showOriginal = true; tryAwaitRelease(); showOriginal = false })
                        },
                    ) { Text(if (showOriginal) "Original" else "Hold: before", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium) }
                }
            }
            Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(SECTIONS.indices.toList()) { i -> FilterChip(selected = section == i, onClick = { section = i }, label = { Text(SECTIONS[i]) }) }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                    when (section) {
                        0 -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(EditPresets.entries.toList(), key = { it.key }) { (name, preset) ->
                                val look = params.copy(rotation = 0, flipH = false, flipV = false, crop = 0)
                                FilterChip(selected = look == preset, onClick = { commit(preset.copy(rotation = params.rotation, flipH = params.flipH, flipV = params.flipV, crop = params.crop)) }, label = { Text(name) })
                            }
                        }
                        1 -> {
                            Adjust("Brightness", params.brightness, 50f..150f, { slide(params.copy(brightness = it)) }, ::finishSlide)
                            Adjust("Contrast", params.contrast, 50f..150f, { slide(params.copy(contrast = it)) }, ::finishSlide)
                            Adjust("Highlights", params.highlights, -100f..100f, { slide(params.copy(highlights = it)) }, ::finishSlide)
                            Adjust("Shadows", params.shadows, -100f..100f, { slide(params.copy(shadows = it)) }, ::finishSlide)
                        }
                        2 -> {
                            Adjust("Saturation", params.saturate, 0f..200f, { slide(params.copy(saturate = it)) }, ::finishSlide)
                            Adjust("Warmth", params.warmth, -100f..100f, { slide(params.copy(warmth = it)) }, ::finishSlide)
                            Adjust("Sepia", params.sepia, 0f..100f, { slide(params.copy(sepia = it)) }, ::finishSlide)
                        }
                        3 -> {
                            Adjust("Sharpness", params.sharpness, 0f..100f, { slide(params.copy(sharpness = it)) }, ::finishSlide)
                            Adjust("Blur", params.blur, 0f..10f, { slide(params.copy(blur = it)) }, ::finishSlide)
                            Adjust("Vignette", params.vignette, 0f..100f, { slide(params.copy(vignette = it)) }, ::finishSlide)
                        }
                        else -> {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                                items(EditCrop.OPTIONS.indices.toList()) { i ->
                                    FilterChip(selected = params.crop == i, onClick = { commit(params.copy(crop = i)) }, label = { Text(EditCrop.OPTIONS[i].first) })
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { commit(params.copy(rotation = (params.rotation + 90) % 360)) }) { Icon(Icons.Default.RotateRight, "Rotate 90 degrees") }
                                IconButton(onClick = { commit(params.copy(flipH = !params.flipH)) }) { Icon(Icons.Default.SwapHoriz, "Flip horizontally") }
                                IconButton(onClick = { commit(params.copy(flipV = !params.flipV)) }) { Icon(Icons.Default.SwapVert, "Flip vertically") }
                            }
                            Text("Crops are centered. The original is never changed.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Button(
                    enabled = !saving && photo != null && preview != null, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    onClick = {
                        val p = photo ?: return@Button
                        saving = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { runCatching { ImageIO.saveCopy(ctx, Uri.parse(p.contentUri), p.displayName, params) }.isSuccess }
                            saving = false
                            if (ok) { vm.markEdited(p.id, params.encode()); vm.scan(); Toast.makeText(ctx, "Saved a copy to Pictures/VaultGallery", Toast.LENGTH_SHORT).show(); onBack() }
                            else Toast.makeText(ctx, "Couldn't save the edited copy", Toast.LENGTH_LONG).show()
                        }
                    },
                ) { Text(if (saving) "Saving…" else "Save as copy") }
            }
        }
    }
}

@Composable
private fun Adjust(label: String, value: Int, range: ClosedFloatingPointRange<Float>, onChange: (Int) -> Unit, onFinished: () -> Unit) {
    Column {
        Text("$label  $value", style = MaterialTheme.typography.labelMedium)
        Slider(value = value.toFloat(), onValueChange = { onChange(it.roundToInt()) }, onValueChangeFinished = onFinished, valueRange = range)
    }
}
