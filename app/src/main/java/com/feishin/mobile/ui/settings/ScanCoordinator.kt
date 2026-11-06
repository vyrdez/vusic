package com.feishin.mobile.ui.settings

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.cache.MetadataCacheKeys
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.domain.repo.ScanStatus
import com.feishin.mobile.ui.components.UiMessenger
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Pure UI state model for the "Scan library" settings row. */
sealed interface ScanUiState {
    val count: Int
    val folderCount: Int?
    val lastScan: String?

    data class Idle(
        override val count: Int = 0,
        override val folderCount: Int? = null,
        override val lastScan: String? = null,
    ) : ScanUiState

    data class Scanning(
        override val count: Int = 0,
        override val folderCount: Int? = null,
        override val lastScan: String? = null,
    ) : ScanUiState

    data class Finished(
        override val count: Int = 0,
        override val folderCount: Int? = null,
        override val lastScan: String? = null,
    ) : ScanUiState

    data class Error(
        val message: String,
        override val count: Int = 0,
        override val folderCount: Int? = null,
        override val lastScan: String? = null,
    ) : ScanUiState
}

fun formatScanCount(n: Int): String =
    NumberFormat.getIntegerInstance(Locale.US).format(n)

fun formatLastScan(raw: String?, zoneId: ZoneId = ZoneId.systemDefault()): String? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.trim()
    return runCatching {
        if (cleaned.contains("T")) {
            val zdt = if (cleaned.endsWith("Z") || cleaned.substringAfter("T").contains("+") || cleaned.substringAfter("T").contains("-")) {
                OffsetDateTime.parse(cleaned).atZoneSameInstant(zoneId)
            } else {
                LocalDateTime.parse(cleaned.take(19)).atZone(zoneId)
            }
            zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } else {
            cleaned
        }
    }.getOrDefault(cleaned)
}

fun mapScanError(error: Throwable?, message: String?): String {
    val serverMsg = message.orEmpty()
    return when {
        serverMsg.contains("Only admin", ignoreCase = true) ||
            serverMsg.contains("unauthorized", ignoreCase = true) ||
            serverMsg.contains("not authorized", ignoreCase = true) ||
            serverMsg.contains("permission", ignoreCase = true) ->
            "Only admin accounts can scan the library"

        error is java.net.SocketTimeoutException ||
            serverMsg.contains("timeout", ignoreCase = true) ||
            serverMsg.contains("timed out", ignoreCase = true) ->
            "Library scan timed out"

        error is java.net.UnknownHostException ||
            error is java.net.ConnectException ||
            error is java.io.IOException ||
            serverMsg.contains("unreachable", ignoreCase = true) ||
            serverMsg.contains("no connection", ignoreCase = true) ||
            serverMsg.contains("not found", ignoreCase = true) ->
            "Server unreachable"

        serverMsg.isNotBlank() -> serverMsg
        else -> "Server unreachable"
    }
}

fun formatScanDescription(
    state: ScanUiState,
    online: Boolean,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    if (!online) return "Unavailable offline"
    return when (state) {
        is ScanUiState.Idle -> {
            val formatted = formatLastScan(state.lastScan, zoneId)
            if (formatted != null) {
                if (state.count > 0) "Last scan: $formatted (${formatScanCount(state.count)} files)"
                else "Last scan: $formatted"
            } else {
                "Ask the server to rescan its folders"
            }
        }
        is ScanUiState.Scanning -> {
            if (state.count <= 0) {
                "Scanning…"
            } else {
                val files = "${formatScanCount(state.count)} files"
                if (state.folderCount != null && state.folderCount > 0) {
                    "Scanning… $files (${formatScanCount(state.folderCount)} folders)"
                } else {
                    "Scanning… $files"
                }
            }
        }
        is ScanUiState.Finished -> {
            val files = if (state.count > 0) "${formatScanCount(state.count)} files" else "completed"
            val base = if (state.folderCount != null && state.folderCount > 0) {
                "Scan finished: $files (${formatScanCount(state.folderCount)} folders)"
            } else {
                "Scan finished: $files"
            }
            val formatted = formatLastScan(state.lastScan, zoneId)
            if (formatted != null) "$base • Last scan: $formatted" else base
        }
        is ScanUiState.Error -> state.message
    }
}

/**
 * Coordinator for Subsonic library scan polling, safety timeouts, error translation,
 * and post-scan cache invalidation.
 */
@Singleton
class ScanCoordinator(
    private val repo: MusicDataRepository,
    private val libraryRefresh: LibraryRefresh,
    private val messenger: UiMessenger,
    externalScope: CoroutineScope? = null,
    private val delayMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val ticker: (suspend (Long) -> Unit)? = null,
) {
    @Inject
    constructor(
        repo: MusicDataRepository,
        libraryRefresh: LibraryRefresh,
        messenger: UiMessenger,
    ) : this(
        repo = repo,
        libraryRefresh = libraryRefresh,
        messenger = messenger,
        externalScope = null,
        delayMs = DEFAULT_POLL_INTERVAL_MS,
        timeoutMs = DEFAULT_TIMEOUT_MS,
        ticker = null,
    )
    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var pollingJob: Job? = null

    fun startScan() {
        if (_state.value is ScanUiState.Scanning) return
        val prev = _state.value
        scope.launch {
            when (val res = repo.startScan()) {
                is Result.Err -> {
                    val err = mapScanError(res.cause, res.message)
                    _state.value = ScanUiState.Error(
                        message = err,
                        count = prev.count,
                        folderCount = prev.folderCount,
                        lastScan = prev.lastScan,
                    )
                    messenger.show(err)
                }
                is Result.Ok -> {
                    _state.value = ScanUiState.Scanning(
                        count = 0,
                        folderCount = null,
                        lastScan = prev.lastScan,
                    )
                    startPolling()
                }
            }
        }
    }

    fun refreshStatus() {
        if (_state.value is ScanUiState.Scanning) return
        scope.launch {
            when (val res = repo.scanStatus()) {
                is Result.Ok -> {
                    val s = res.value
                    if (s.scanning) {
                        _state.value = ScanUiState.Scanning(
                            count = s.count,
                            folderCount = s.folderCount,
                            lastScan = s.lastScan ?: _state.value.lastScan,
                        )
                        startPolling()
                    } else {
                        val cur = _state.value
                        if (cur !is ScanUiState.Finished) {
                            _state.value = ScanUiState.Idle(
                                count = s.count,
                                folderCount = s.folderCount,
                                lastScan = s.lastScan ?: cur.lastScan,
                            )
                        }
                    }
                }
                is Result.Err -> {
                    // Silent refresh error on passive status check
                }
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var elapsedMs = 0L
            var consecutiveFailures = 0
            while (isActive) {
                if (elapsedMs >= timeoutMs) {
                    val msg = "Library scan timed out"
                    _state.value = ScanUiState.Error(
                        message = msg,
                        count = _state.value.count,
                        folderCount = _state.value.folderCount,
                        lastScan = _state.value.lastScan,
                    )
                    messenger.show(msg)
                    break
                }

                if (ticker != null) {
                    ticker.invoke(delayMs)
                } else {
                    delay(delayMs)
                }
                elapsedMs += delayMs

                when (val res = repo.scanStatus()) {
                    is Result.Ok -> {
                        consecutiveFailures = 0
                        val s = res.value
                        if (s.scanning) {
                            _state.value = ScanUiState.Scanning(
                                count = s.count,
                                folderCount = s.folderCount,
                                lastScan = s.lastScan ?: _state.value.lastScan,
                            )
                        } else {
                            _state.value = ScanUiState.Finished(
                                count = s.count,
                                folderCount = s.folderCount,
                                lastScan = s.lastScan ?: _state.value.lastScan,
                            )
                            libraryRefresh.invalidateAll()
                            libraryRefresh.emitRefreshed(MetadataCacheKeys.ARTISTS)
                            libraryRefresh.emitRefreshed(MetadataCacheKeys.PLAYLISTS)
                            libraryRefresh.emitRefreshed("albums:alphabeticalByName:0")
                            break
                        }
                    }
                    is Result.Err -> {
                        consecutiveFailures++
                        if (consecutiveFailures >= 3) {
                            val msg = mapScanError(res.cause, res.message)
                            _state.value = ScanUiState.Error(
                                message = msg,
                                count = _state.value.count,
                                folderCount = _state.value.folderCount,
                                lastScan = _state.value.lastScan,
                            )
                            messenger.show(msg)
                            break
                        }
                    }
                }
            }
        }
    }

    fun cancelPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 1500L
        const val DEFAULT_TIMEOUT_MS = 15 * 60 * 1000L
    }
}
