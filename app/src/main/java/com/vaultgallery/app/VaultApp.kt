package com.vaultgallery.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache

/**
 * App-wide Coil loader. VideoFrameDecoder lets AsyncImage show a real preview frame for video content URIs.
 * It scales the frame to the requested size, so grid thumbnails never decode a full-resolution frame, and results
 * go through Coil's memory cache so scrolling back is instant.
 */
class VaultApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .crossfade(true)
        .build()
}
