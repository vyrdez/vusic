package com.feishin.mobile.ui.settings

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.auth.DriveScanCancellationRegistry
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.cache.MetadataCacheKeys
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.repo.BackendRegistry
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
import kotlinx.coroutines.flow.update
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
        val totalCount: Int? = null,
        val quotaLimited: Boolean = false,
        val twoStage: Boolean = false,
    ) : ScanUiState

    data class Finished(
        override val count: Int = 0,
        override val folderCount: Int? = null,
        override val lastScan: String? = null,
        val quotaLimited: Boolean = false,
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
            val total = state.totalCount
            when {
                total != null -> {
                    val pct = if (total > 0) {
                        ((state.count.coerceIn(0, total) * 100L) / total).toInt()
                    } else {
                        100
                    }
                    "Indexing… $pct% (${formatScanCount(state.count)}/${formatScanCount(total)})"
                }
                state.twoStage -> {
                    if (state.count <= 0) "Discovering…"
                    else "Discovering… ${formatScanCount(state.count)} files"
                }
                state.count <= 0 -> "Scanning…"
                else -> {
                    val files = "${formatScanCount(state.count)} files"
                    if (state.folderCount != null && state.folderCount > 0) {
                        "Scanning… $files (${formatScanCount(state.folderCount)} folders)"
                    } else {
                        "Scanning… $files"
                    }
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
            val withQuota = if (state.quotaLimited) "$base (rate limited)" else base
            val formatted = formatLastScan(state.lastScan, zoneId)
            if (formatted != null) "$withQuota • Last scan: $formatted" else withQuota
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
    private val backends: BackendRegistry? = null,
    cancellationRegistry: DriveScanCancellationRegistry? = null,
) {
    @Inject
    constructor(
        repo: MusicDataRepository,
        libraryRefresh: LibraryRefresh,
        messenger: UiMessenger,
        backends: BackendRegistry,
        cancellationRegistry: DriveScanCancellationRegistry,
    ) : this(
        repo = repo,
        libraryRefresh = libraryRefresh,
        messenger = messenger,
        externalScope = null,
        delayMs = DEFAULT_POLL_INTERVAL_MS,
        timeoutMs = DEFAULT_TIMEOUT_MS,
        ticker = null,
        backends = backends,
        cancellationRegistry = cancellationRegistry,
    )
    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val _profileStates = MutableStateFlow<Map<String, ScanUiState>>(emptyMap())
    val profileStates: StateFlow<Map<String, ScanUiState>> = _profileStates.asStateFlow()

    private val pollingJobs = mutableMapOf<String, Job>()

    init {
        cancellationRegistry?.register(CANCELLATION_REGISTRY_KEY) { profileId ->
            cancelScanForProfile(profileId)
        }
    }

    private fun cancelScanForProfile(profileId: String) {
        cancelPolling(profileId)
        _profileStates.update { map ->
            val current = map[profileId] ?: return@update map
            if (current is ScanUiState.Scanning) {
                map + (profileId to ScanUiState.Idle(
                    count = current.count,
                    folderCount = current.folderCount,
                    lastScan = current.lastScan,
                ))
            } else {
                map
            }
        }
    }

    private fun primaryScanProfileId(): String? =
        backends?.enabled()?.firstOrNull { it.capabilities.supportsLibraryScan }?.profile?.id

    private fun currentStateFor(profileId: String?): ScanUiState =
        if (profileId == null) _state.value else _profileStates.value[profileId] ?: ScanUiState.Idle()

    private fun updateStateFor(
        profileId: String?,
        next: ScanUiState,
        passiveRefresh: Boolean = false,
    ) {
        if (profileId != null) {
            _profileStates.update { it + (profileId to next) }
        }
        if (profileId == null || !passiveRefresh ||
            (_state.value !is ScanUiState.Scanning && _state.value !is ScanUiState.Finished)
        ) {
            _state.value = next
        }
    }

    private suspend fun executeStartScan(profileId: String?): Result<Unit> = when {
        profileId == null -> repo.startScan()
        backends != null -> backends.get(profileId)?.startScan() ?: Result.Err("Server not found")
        else -> repo.startScan(profileId)
    }

    private suspend fun executeScanStatus(profileId: String?): Result<ScanStatus> = when {
        profileId == null -> repo.scanStatus()
        backends != null -> backends.get(profileId)?.scanStatus() ?: Result.Err("Server not found")
        else -> repo.scanStatus(profileId)
    }

    fun startScan() {
        val targetId = primaryScanProfileId()
        if (targetId != null) startScan(targetId) else startScanInternal(null)
    }

    fun startScan(profileId: String) {
        startScanInternal(profileId)
    }

    private fun startScanInternal(profileId: String?) {
        val prev = currentStateFor(profileId)
        if (prev is ScanUiState.Scanning) return
        if (profileId != null && backends != null && backends.get(profileId) == null) {
            val err = "Server not found"
            updateStateFor(
                profileId,
                ScanUiState.Error(
                    message = err,
                    count = prev.count,
                    folderCount = prev.folderCount,
                    lastScan = prev.lastScan,
                ),
            )
            messenger.show(err)
            return
        }
        scope.launch {
            when (val res = executeStartScan(profileId)) {
                is Result.Err -> {
                    val err = mapScanError(res.cause, res.message)
                    updateStateFor(
                        profileId,
                        ScanUiState.Error(
                            message = err,
                            count = prev.count,
                            folderCount = prev.folderCount,
                            lastScan = prev.lastScan,
                        ),
                    )
                    messenger.show(err)
                }
                is Result.Ok -> {
                    val isTwoStage = profileId != null &&
                        backends?.get(profileId)?.profile?.type == BackendType.GOOGLE_DRIVE
                    updateStateFor(
                        profileId,
                        ScanUiState.Scanning(
                            count = 0,
                            folderCount = null,
                            lastScan = prev.lastScan,
                            totalCount = null,
                            quotaLimited = false,
                            twoStage = isTwoStage,
                        ),
                    )
                    startPolling(profileId)
                }
            }
        }
    }

    fun refreshStatus() {
        val targetId = primaryScanProfileId()
        if (targetId != null) refreshStatus(targetId) else refreshStatusInternal(null)
    }

    fun refreshStatus(profileId: String) {
        refreshStatusInternal(profileId)
    }

    private fun refreshStatusInternal(profileId: String?) {
        if (currentStateFor(profileId) is ScanUiState.Scanning) return
        scope.launch {
            when (val res = executeScanStatus(profileId)) {
                is Result.Ok -> {
                    val s = res.value
                    val cur = currentStateFor(profileId)
                    if (s.scanning) {
                        updateStateFor(
                            profileId,
                            ScanUiState.Scanning(
                                count = s.count,
                                folderCount = s.folderCount,
                                lastScan = s.lastScan ?: cur.lastScan,
                                totalCount = s.totalCount,
                                quotaLimited = s.quotaLimited,
                                twoStage = s.twoStage,
                            ),
                        )
                        startPolling(profileId)
                    } else if (cur !is ScanUiState.Finished) {
                        updateStateFor(
                            profileId,
                            ScanUiState.Idle(
                                count = s.count,
                                folderCount = s.folderCount,
                                lastScan = s.lastScan ?: cur.lastScan,
                            ),
                            passiveRefresh = true,
                        )
                    }
                }
                is Result.Err -> {
                    // Silent refresh error on passive status check
                }
            }
        }
    }

    private fun startPolling(profileId: String?) {
        val jobKey = profileId ?: DEFAULT_JOB_KEY
        val job = scope.launch {
            var elapsedMs = 0L
            var consecutiveFailures = 0
            while (isActive) {
                val cur = currentStateFor(profileId)
                if (elapsedMs >= timeoutMs) {
                    val msg = "Library scan timed out"
                    updateStateFor(
                        profileId,
                        ScanUiState.Error(
                            message = msg,
                            count = cur.count,
                            folderCount = cur.folderCount,
                            lastScan = cur.lastScan,
                        ),
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

                when (val res = executeScanStatus(profileId)) {
                    is Result.Ok -> {
                        consecutiveFailures = 0
                        val s = res.value
                        val latest = currentStateFor(profileId)
                        if (s.scanning) {
                            updateStateFor(
                                profileId,
                                ScanUiState.Scanning(
                                    count = s.count,
                                    folderCount = s.folderCount,
                                    lastScan = s.lastScan ?: latest.lastScan,
                                    totalCount = s.totalCount,
                                    quotaLimited = s.quotaLimited,
                                    twoStage = s.twoStage,
                                ),
                            )
                        } else {
                            updateStateFor(
                                profileId,
                                ScanUiState.Finished(
                                    count = s.count,
                                    folderCount = s.folderCount,
                                    lastScan = s.lastScan ?: latest.lastScan,
                                    quotaLimited = s.quotaLimited,
                                ),
                            )
                            if (s.quotaLimited) {
                                messenger.show("Google Drive rate limit reached — partial library saved")
                            }
                            libraryRefresh.invalidateAll()
                            libraryRefresh.emitRefreshed(MetadataCacheKeys.ARTISTS)
                            libraryRefresh.emitRefreshed(MetadataCacheKeys.PLAYLISTS)
                            libraryRefresh.emitRefreshed(MetadataCacheKeys.albums("alphabeticalByName", 0))
                            break
                        }
                    }
                    is Result.Err -> {
                        consecutiveFailures++
                        if (consecutiveFailures >= 3) {
                            val latest = currentStateFor(profileId)
                            val msg = mapScanError(res.cause, res.message)
                            updateStateFor(
                                profileId,
                                ScanUiState.Error(
                                    message = msg,
                                    count = latest.count,
                                    folderCount = latest.folderCount,
                                    lastScan = latest.lastScan,
                                ),
                            )
                            messenger.show(msg)
                            break
                        }
                    }
                }
            }
        }
        synchronized(pollingJobs) {
            pollingJobs.put(jobKey, job)?.cancel()
        }
    }

    fun cancelPolling(profileId: String) {
        synchronized(pollingJobs) {
            pollingJobs.remove(profileId)?.cancel()
        }
    }

    fun cancelPolling() {
        synchronized(pollingJobs) {
            pollingJobs.values.forEach { it.cancel() }
            pollingJobs.clear()
        }
    }

    companion object {
        private const val DEFAULT_JOB_KEY = "__default__"
        private const val CANCELLATION_REGISTRY_KEY = "ScanCoordinator"
        const val DEFAULT_POLL_INTERVAL_MS = 1500L
        const val DEFAULT_TIMEOUT_MS = 15 * 60 * 1000L
    }
}
