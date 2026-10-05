package com.vaultgallery.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vaultgallery.app.data.PhotoEntity
import com.vaultgallery.app.ui.theme.Emerald
import com.vaultgallery.app.ui.theme.Gold
import com.vaultgallery.app.util.formatDuration
import com.vaultgallery.app.util.resolutionBadge
import com.vaultgallery.app.util.displayHeight
import com.vaultgallery.app.util.displayWidth

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCard(
    photo: PhotoEntity, selected: Boolean, selecting: Boolean, showName: Boolean,
    modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit,
    showDuration: Boolean = true, thumbPx: Int = 0,
) {
    val shape = RoundedCornerShape(14.dp)
    val label = buildString {
        append(if (photo.locked) (if (photo.isVideo) "Locked video" else "Locked photo") else photo.displayName)
        if (photo.isVideo && !photo.locked) append(", video, ${formatDuration(photo.durationMs)}")
        if (photo.favorite) append(", favorite")
        if (selected) append(", selected")
    }
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Gold.copy(alpha = .15f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = label }
    ) {
        if (photo.locked) {
            // Locked photos are NOT decoded at all: no pixels are loaded, so nothing can leak through a blur.
            Column(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, Emerald.copy(alpha = .7f)))),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Lock, null, tint = Gold, modifier = Modifier.size(28.dp))
                Text("⭐${photo.unlockCostStars} · 🪙${photo.unlockCostCoins}", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        } else {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            // thumbPx = 0: Coil sizes the request to the cell. Performance / Battery saver cap it lower to save decode work.
            val model = if (thumbPx > 0) coil.request.ImageRequest.Builder(ctx).data(photo.contentUri).size(thumbPx).crossfade(false).build() else photo.contentUri
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }

        // Static thumbnail + play icon + duration badge. Videos never autoplay in the grid. Locked videos show nothing.
        if (photo.isVideo && !photo.locked) VideoOverlay(photo, showDuration, detailed = showName)

        if (showName && !photo.locked) {
            Text(
                photo.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f))))
                    .padding(8.dp)
            )
        }

        if (selecting) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(24.dp).background(Color.Black.copy(alpha = .35f), CircleShape)
            )
        } else if (photo.favorite && !photo.locked) {
            // A single quiet badge instead of a permanent button on every tile.
            Icon(
                Icons.Default.Favorite, "Favorite", tint = Gold,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
            )
        }
    }
}

/** Small play glyph + duration. The resolution tag only appears when the user enabled detailed thumbnails. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.VideoOverlay(photo: PhotoEntity, showDuration: Boolean, detailed: Boolean) {
    Icon(
        Icons.Default.PlayArrow, null, tint = Color.White,
        modifier = Modifier.align(Alignment.Center).size(32.dp).background(Color.Black.copy(alpha = .45f), CircleShape).padding(4.dp),
    )
    if (showDuration && photo.durationMs > 0) {
        Text(
            formatDuration(photo.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                .background(Color.Black.copy(alpha = .6f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
    if (detailed) resolutionBadge(photo.displayWidth(), photo.displayHeight())?.let { badge ->
        Text(
            badge, color = Gold, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                .background(Color.Black.copy(alpha = .6f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Small thumbnail (trash, recent strip) with a play glyph for videos. Callers must not pass locked items. */
@Composable
fun MediaThumb(photo: PhotoEntity, modifier: Modifier) {
    Box(modifier) {
        AsyncImage(model = photo.contentUri, contentDescription = photo.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (photo.isVideo) {
            Icon(
                Icons.Default.PlayArrow, null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(26.dp).background(Color.Black.copy(alpha = .45f), CircleShape).padding(3.dp),
            )
        }
    }
}
