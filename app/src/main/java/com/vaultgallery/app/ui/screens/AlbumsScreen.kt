package com.vaultgallery.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vaultgallery.app.GalleryViewModel
import com.vaultgallery.app.data.AlbumInfo
import com.vaultgallery.app.ui.components.EmptyState
import java.text.NumberFormat

/** Placeholder used in navigation routes for the album whose MediaStore bucket name is empty. */
const val OTHER_ALBUM_ROUTE = "~other"

/** Smart albums: one tile per real MediaStore folder, rebuilt automatically as media changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(vm: GalleryViewModel, onOpenAlbum: (String) -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val numbers = NumberFormat.getIntegerInstance()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Albums", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        if (albums.isEmpty()) {
            Box(Modifier.padding(inner).fillMaxSize()) {
                EmptyState(Icons.Default.PhotoLibrary, "No albums yet", "Folders with photos and videos will appear here.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp), modifier = Modifier.padding(inner).fillMaxSize(),
                contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(albums, key = { it.bucket }) { a -> AlbumTile(a, numbers) { onOpenAlbum(a.bucket) } }
            }
        }
    }
}

@Composable
private fun AlbumTile(a: AlbumInfo, numbers: NumberFormat, onClick: () -> Unit) {
    val name = a.bucket.ifBlank { "Other" }
    val mix = buildString {
        append(numbers.format(a.total)).append(if (a.total == 1) " item" else " items")
        if (a.videos > 0 && a.photos > 0) append(" · ${numbers.format(a.videos)} video${if (a.videos == 1) "" else "s"}")
        else if (a.videos > 0) append(" · videos")
    }
    Column(Modifier.clickable(onClick = onClick).semantics { contentDescription = "$name, $mix" }) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (a.coverUri != null) AsyncImage(a.coverUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(mix, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
