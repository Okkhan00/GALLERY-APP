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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCard(
    photo: PhotoEntity, selected: Boolean, selecting: Boolean, showName: Boolean,
    modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit, onFavorite: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val label = buildString {
        append(if (photo.locked) "Locked photo" else photo.displayName)
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
            AsyncImage(model = photo.contentUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }

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
        } else if (!photo.locked) {
            IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    if (photo.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (photo.favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (photo.favorite) Gold else Color.White,
                    modifier = Modifier.size(22.dp).background(Color.Black.copy(alpha = .35f), CircleShape).padding(2.dp)
                )
            }
        }
    }
}
