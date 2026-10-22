package com.feishin.mobile.core.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coil fetcher for local device audio files and MediaStore album art.
 * Extracts embedded artwork via ContentResolver.loadThumbnail (API 29+)
 * or MediaMetadataRetriever embeddedPicture fallback.
 */
class LocalAudioCoverFetcher(
    private val context: Context,
    private val uri: Uri,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val scheme = uri.scheme
        if (scheme != "content" && scheme != "file") return@withContext null

        // 1. On Android 10+ (API 29+), try ContentResolver.loadThumbnail
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bmp = runCatching {
                context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
            }.getOrNull()
            if (bmp != null) {
                return@withContext DrawableResult(
                    drawable = BitmapDrawable(context.resources, bmp),
                    isSampled = false,
                    dataSource = DataSource.DISK,
                )
            }
        }

        // 2. Fall back to MediaMetadataRetriever embeddedPicture
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val picture = retriever.embeddedPicture
            if (picture != null) {
                val bmp = BitmapFactory.decodeByteArray(picture, 0, picture.size)
                if (bmp != null) {
                    return@withContext DrawableResult(
                        drawable = BitmapDrawable(context.resources, bmp),
                        isSampled = false,
                        dataSource = DataSource.DISK,
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore extraction errors
        } finally {
            runCatching { retriever.release() }
        }

        null
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val str = data.toString()
            if (str.contains("/audio/") || str.endsWith(".mp3") || str.endsWith(".flac") || str.endsWith(".m4a") || str.endsWith(".ogg")) {
                return LocalAudioCoverFetcher(context, data)
            }
            return null
        }
    }
}
