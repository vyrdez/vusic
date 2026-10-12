package com.feishin.mobile.ui.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.data.db.DownloadDao
import com.feishin.mobile.data.db.DownloadedTrack
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val dao: DownloadDao,
    private val repo: MusicDataRepository,
    private val client: OkHttpClient,
) : ViewModel() {
    val downloads = dao.observeAll()
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    fun download(song: Track, dir: File) {
        viewModelScope.launch {
            _busy.value = _busy.value + song.id
            try {
                withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(repo.downloadUrl(song)).build()
                    client.newCall(req).execute().use { resp ->
                        check(resp.isSuccessful) { "HTTP ${resp.code}" }
                        val ext = song.suffix?.takeIf { it.isNotBlank() } ?: "mp3"
                        val file = File(dir, "${song.id}.$ext")
                        file.parentFile?.mkdirs()
                        file.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
                        dao.upsert(
                            DownloadedTrack(
                                songId = song.id,
                                title = song.title,
                                artist = song.artist,
                                album = song.album,
                                filePath = file.absolutePath,
                                sizeBytes = file.length(),
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
            } finally {
                _busy.value = _busy.value - song.id
            }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.byId(id)?.let { runCatching { File(it.filePath).delete() } }
            dao.delete(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) { dao.clear() }
    }
}
