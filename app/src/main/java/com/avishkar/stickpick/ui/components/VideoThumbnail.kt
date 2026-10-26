package com.avishkar.stickpick.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoThumbCache {
    val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
}

@Composable
fun VideoThumbnail(
    file: File,
    modifier: Modifier = Modifier,
    indicatorSize: Dp = 16.dp,
    contentScale: ContentScale = ContentScale.Fit
) {
    val path = file.absolutePath
    var bitmap by remember(path) { mutableStateOf(VideoThumbCache.cache.get(path)) }

    if (bitmap == null) {
        LaunchedEffect(path) {
            val bmp = withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(path)
                    r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (e: Exception) {
                    null
                } finally {
                    try { r.release() } catch (_: Exception) {}
                }
            }
            if (bmp != null) {
                VideoThumbCache.cache.put(path, bmp)
                bitmap = bmp
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                Modifier.size(indicatorSize),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
