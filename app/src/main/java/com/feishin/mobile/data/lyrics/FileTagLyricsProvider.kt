package com.feishin.mobile.data.lyrics

import android.content.Context
import android.net.Uri
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.domain.repo.LyricsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class FileTagLyricsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LyricsProvider {

    override suspend fun lyricsForSong(
        songId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        localUri: String?,
        localModifiedSec: Long,
    ): Result<Lyrics> = withContext(Dispatchers.IO) {
        val uriStr = localUri ?: return@withContext Result.Ok(Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.FILE_TAGS))
        try {
            val stream = if (uriStr.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(uriStr))
            } else {
                File(uriStr).inputStream()
            }

            if (stream == null) {
                return@withContext Result.Ok(Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.FILE_TAGS))
            }

            val lyrics = stream.use { EmbeddedLyricsExtractor.extract(it) }
            Result.Ok(lyrics ?: Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.FILE_TAGS))
        } catch (_: Exception) {
            Result.Ok(Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.FILE_TAGS))
        }
    }
}
