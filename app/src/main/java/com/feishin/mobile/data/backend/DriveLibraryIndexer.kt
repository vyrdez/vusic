package com.feishin.mobile.data.backend

import android.content.Context
import android.net.ConnectivityManager
import com.feishin.mobile.core.common.CrashGuard
import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.ProfileIdTag
import com.feishin.mobile.data.auth.DriveScanCancellationRegistry
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.db.CachedCoverDao
import com.feishin.mobile.data.db.CachedCoverEntity
import com.feishin.mobile.data.db.DriveFileModTime
import com.feishin.mobile.data.db.DriveTrackDao
import com.feishin.mobile.data.db.DriveTrackEntity
import com.feishin.mobile.data.remote.drive.DriveApi
import com.feishin.mobile.data.remote.drive.DriveFileDto
import com.feishin.mobile.data.remote.drive.DriveFileListResponse
import com.feishin.mobile.data.remote.drive.DriveRangeReader
import com.feishin.mobile.data.remote.drive.DriveTagParser
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.repo.ScanStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Incremental two-stage Google Drive library indexer.
 *
 * Stage 1 (Discovery, `totalCount == null`):
 * - Scoped (`driveRootFolderId != null`): MIME-filter-free BFS batching up to 25 folder IDs per query,
 *   ignoring `application/vnd.google-apps.shortcut`, collecting audio files and folder cover artwork
 *   (`cover.jpg`, `cover.png`, `folder.jpg`, `front.jpg`), and building `parentFolderPath`.
 * - Scan-all (`driveRootFolderId == null`): audio allowlist query + folder tree lookup for `parentFolderPath`
 *   and folder artwork.
 *
 * Stage 2 (Indexing, `totalCount != null`):
 * - Skips unchanged files (`remoteModMs <= existingModMs && tagsParsed`).
 * - Respects Wi-Fi-only / metered network settings before heavy HTTP Range tag fetches.
 * - Uses `Semaphore(3)` for concurrent Range tag extraction and flushes batch upserts (32 rows).
 * - Prunes deleted/trashed rows and orphaned `"$profileId:cov:$fileId"` covers ONLY after a complete,
 *   non-aborted, non-cancelled traversal.
 */
@Singleton
class DriveLibraryIndexer internal constructor(
    private val driveApiProvider: (ServerProfile) -> DriveApi,
    private val profileStore: ProfileStore,
    private val driveRangeReader: DriveRangeReader,
    private val driveTrackDao: DriveTrackDao,
    private val cachedCoverDao: CachedCoverDao,
    private val settingsStore: SettingsStore,
    private val cancellationRegistry: DriveScanCancellationRegistry,
    private val json: Json,
    private val isNetworkMetered: () -> Boolean,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
    private val jitterProvider: () -> Double = { Random.nextDouble(0.85, 1.15) },
    externalScope: CoroutineScope? = null,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        backendFactory: BackendClientFactory,
        profileStore: ProfileStore,
        driveRangeReader: DriveRangeReader,
        driveTrackDao: DriveTrackDao,
        cachedCoverDao: CachedCoverDao,
        settingsStore: SettingsStore,
        cancellationRegistry: DriveScanCancellationRegistry,
        json: Json,
    ) : this(
        driveApiProvider = { profile -> backendFactory.driveApi(profile) },
        profileStore = profileStore,
        driveRangeReader = driveRangeReader,
        driveTrackDao = driveTrackDao,
        cachedCoverDao = cachedCoverDao,
        settingsStore = settingsStore,
        cancellationRegistry = cancellationRegistry,
        json = json,
        isNetworkMetered = { checkActiveNetworkMetered(context) },
    )

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO + CrashGuard.handler)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _scanStatuses = MutableStateFlow<Map<String, ScanStatus>>(emptyMap())
    val scanStatuses: StateFlow<Map<String, ScanStatus>> = _scanStatuses.asStateFlow()

    init {
        cancellationRegistry.register(REGISTRY_KEY) { profileId ->
            cancelScan(profileId)
        }
    }

    fun scanStatusFlow(profileId: String): Flow<ScanStatus> =
        scanStatuses
            .map { it[profileId] ?: ScanStatus(scanning = false, twoStage = true) }
            .distinctUntilChanged()

    /** Starts an asynchronous scan for [profileId] if one is not already active. */
    fun startScan(profileId: String): Result<Unit> {
        val profile = profileStore.byId(profileId)
            ?: return Result.Err("Server not found")
        if (profile.type != BackendType.GOOGLE_DRIVE) {
            return Result.Err("Not a Google Drive profile")
        }
        if (!profile.enabled) {
            return Result.Err("Server is disabled")
        }

        synchronized(activeJobs) {
            val existing = activeJobs[profileId]
            if (existing != null && existing.isActive) {
                return Result.Ok(Unit)
            }
            val prevLastScan = _scanStatuses.value[profileId]?.lastScan
            updateStatus(
                profileId,
                ScanStatus(
                    scanning = true,
                    count = 0,
                    folderCount = 0,
                    lastScan = prevLastScan,
                    totalCount = null,
                    quotaLimited = false,
                    twoStage = true,
                ),
            )
            val job = scope.launch {
                try {
                    runScan(profile)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    val count = runCatching { driveTrackDao.countForProfile(profileId) }.getOrDefault(0)
                    val cur = _scanStatuses.value[profileId]
                    updateStatus(
                        profileId,
                        ScanStatus(
                            scanning = false,
                            count = count,
                            folderCount = cur?.folderCount,
                            lastScan = cur?.lastScan,
                            totalCount = cur?.totalCount,
                            quotaLimited = cur?.quotaLimited ?: false,
                            twoStage = true,
                        ),
                    )
                } finally {
                    synchronized(activeJobs) {
                        val current = activeJobs[profileId]
                        if (current === coroutineContext[Job]) {
                            activeJobs.remove(profileId)
                        }
                    }
                }
            }
            activeJobs[profileId] = job
        }
        return Result.Ok(Unit)
    }

    /** Executes a full scan synchronously in the caller coroutine and returns the final [ScanStatus]. */
    suspend fun scanNow(profileId: String): Result<ScanStatus> {
        val profile = profileStore.byId(profileId)
            ?: return Result.Err("Server not found")
        if (profile.type != BackendType.GOOGLE_DRIVE) {
            return Result.Err("Not a Google Drive profile")
        }
        if (!profile.enabled) {
            return Result.Err("Server is disabled")
        }
        val prevLastScan = _scanStatuses.value[profileId]?.lastScan
        updateStatus(
            profileId,
            ScanStatus(
                scanning = true,
                count = 0,
                folderCount = 0,
                lastScan = prevLastScan,
                totalCount = null,
                quotaLimited = false,
                twoStage = true,
            ),
        )
        return try {
            Result.Ok(runScan(profile))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Err(e.message ?: "Google Drive scan failed", e)
        }
    }

    /** Returns the latest scan status for [profileId], falling back to the indexed Room count when idle. */
    suspend fun scanStatus(profileId: String): Result<ScanStatus> {
        val current = _scanStatuses.value[profileId]
        if (current != null) {
            return Result.Ok(current)
        }
        val count = driveTrackDao.countForProfile(profileId)
        return Result.Ok(
            ScanStatus(
                scanning = false,
                count = count,
                twoStage = true,
            ),
        )
    }

    /** Immediately cancels any active scan job for [profileId] and resets scanning state. */
    fun cancelScan(profileId: String) {
        val cancelledJob = synchronized(activeJobs) {
            activeJobs.remove(profileId)
        }
        cancelledJob?.cancel()
        _scanStatuses.update { map ->
            val prev = map[profileId] ?: return@update map
            if (!prev.scanning) map
            else map + (profileId to prev.copy(scanning = false))
        }
    }

    private data class DiscoveredAudio(
        val file: DriveFileDto,
        val parentFolderId: String?,
        val parentFolderPath: String,
    )

    private data class DiscoveryResult(
        val files: List<DiscoveredAudio>,
        val folderCoverIds: Map<String, String>,
        val folderCount: Int,
        val quotaLimited: Boolean,
    )

    private class DriveRateLimitExhaustedException(message: String) : Exception(message)

    private suspend fun runScan(profile: ServerProfile): ScanStatus {
        val profileId = profile.id
        val api = driveApiProvider(profile)
        val rootFolderId = profile.driveRootFolderId?.trim()?.takeIf { it.isNotEmpty() }
        val prevLastScan = _scanStatuses.value[profileId]?.lastScan

        val discovery = try {
            if (rootFolderId != null) {
                discoverScoped(profileId, api, rootFolderId, prevLastScan)
            } else {
                discoverAll(profileId, api, prevLastScan)
            }
        } catch (_: DriveRateLimitExhaustedException) {
            val dbCount = runCatching { driveTrackDao.countForProfile(profileId) }.getOrDefault(0)
            val aborted = ScanStatus(
                scanning = false,
                count = dbCount,
                folderCount = _scanStatuses.value[profileId]?.folderCount,
                lastScan = prevLastScan,
                totalCount = null,
                quotaLimited = true,
                twoStage = true,
            )
            updateStatus(profileId, aborted)
            return aborted
        }

        if (!currentCoroutineContext().isActive) {
            throw CancellationException("Drive scan cancelled")
        }

        val totalCount = discovery.files.size
        updateStatus(
            profileId,
            ScanStatus(
                scanning = true,
                count = 0,
                folderCount = discovery.folderCount,
                lastScan = prevLastScan,
                totalCount = totalCount,
                quotaLimited = discovery.quotaLimited,
                twoStage = true,
            ),
        )

        val existingList = driveTrackDao.allIdsAndModTimes(profileId)
        val existingByFileId = existingList.associateBy { it.driveFileId }
        val semaphore = Semaphore(RANGE_CONCURRENCY)
        val processedCount = AtomicInteger(0)
        var quotaLimited = discovery.quotaLimited
        val wifiOnly = runCatching {
            settingsStore.latestSettings?.downloadWifiOnly ?: settingsStore.flow.first().downloadWifiOnly
        }.getOrDefault(true)

        val toProcess = ArrayList<DiscoveredAudio>(totalCount)
        for (item in discovery.files) {
            val existing = existingByFileId[item.file.id]
            val remoteModMs = item.file.modifiedTimeMs
            if (existing != null && remoteModMs <= existing.modifiedTimeMs && existing.tagsParsed) {
                processedCount.incrementAndGet()
            } else {
                toProcess.add(item)
            }
        }

        if (processedCount.get() > 0) {
            updateStatus(
                profileId,
                ScanStatus(
                    scanning = true,
                    count = processedCount.get(),
                    folderCount = discovery.folderCount,
                    lastScan = prevLastScan,
                    totalCount = totalCount,
                    quotaLimited = quotaLimited,
                    twoStage = true,
                ),
            )
        }

        val chunks = toProcess.chunked(UPSERT_BATCH_SIZE)
        for (chunk in chunks) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Drive scan cancelled")
            }
            if (quotaLimited) break

            val outcomes = coroutineScope {
                chunk.map { item ->
                    async {
                        semaphore.withPermit {
                            indexSingleTrack(
                                profileId = profileId,
                                item = item,
                                existing = existingByFileId[item.file.id],
                                folderCoverFileId = item.parentFolderId?.let { discovery.folderCoverIds[it] },
                                wifiOnly = wifiOnly,
                            )
                        }
                    }
                }.awaitAll()
            }

            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Drive scan cancelled")
            }

            val entities = ArrayList<DriveTrackEntity>(outcomes.size)
            val covers = ArrayList<CachedCoverEntity>()
            for (outcome in outcomes) {
                if (outcome.quotaExhausted) {
                    quotaLimited = true
                }
                if (outcome.entity != null) {
                    entities.add(outcome.entity)
                }
                if (outcome.coverEntity != null) {
                    covers.add(outcome.coverEntity)
                }
            }

            if (covers.isNotEmpty()) {
                for (cover in covers) {
                    cachedCoverDao.upsert(cover)
                }
            }
            if (entities.isNotEmpty()) {
                driveTrackDao.upsertAll(entities)
            }

            val doneSoFar = processedCount.addAndGet(entities.size).coerceAtMost(totalCount)
            updateStatus(
                profileId,
                ScanStatus(
                    scanning = true,
                    count = doneSoFar,
                    folderCount = discovery.folderCount,
                    lastScan = prevLastScan,
                    totalCount = totalCount,
                    quotaLimited = quotaLimited,
                    twoStage = true,
                ),
            )
        }

        val completedTraversal = !quotaLimited && currentCoroutineContext().isActive
        if (completedTraversal) {
            pruneDeletedAndTrashed(
                profileId = profileId,
                discovered = discovery.files,
                existing = existingList,
            )
        }

        val finalCount = driveTrackDao.countForProfile(profileId)
        val finishedAt = if (completedTraversal) Instant.ofEpochMilli(clockMs()).toString() else prevLastScan
        val finalStatus = ScanStatus(
            scanning = false,
            count = finalCount,
            folderCount = discovery.folderCount,
            lastScan = finishedAt,
            totalCount = totalCount,
            quotaLimited = quotaLimited,
            twoStage = true,
        )
        updateStatus(profileId, finalStatus)
        return finalStatus
    }

    private data class IndexOutcome(
        val entity: DriveTrackEntity?,
        val coverEntity: CachedCoverEntity? = null,
        val quotaExhausted: Boolean = false,
    )

    private suspend fun indexSingleTrack(
        profileId: String,
        item: DiscoveredAudio,
        existing: DriveFileModTime?,
        folderCoverFileId: String?,
        wifiOnly: Boolean,
    ): IndexOutcome {
        val file = item.file
        val meteredBlocked = wifiOnly && isNetworkMetered()
        if (meteredBlocked) {
            // If we already have a fallback row with the same modifiedTime, preserve existing metadata.
            if (existing != null && file.modifiedTimeMs <= existing.modifiedTimeMs) {
                return IndexOutcome(entity = null)
            }
            val fallback = DriveTagParser.inferFromPathAndFileName(file.name, item.parentFolderPath)
            val fallbackCover = folderCoverFileId?.let { "$profileId:$it" }
            return IndexOutcome(
                entity = buildTrackEntity(
                    profileId = profileId,
                    item = item,
                    title = fallback.title,
                    artist = fallback.artist,
                    albumArtist = fallback.albumArtist,
                    album = fallback.album,
                    trackNo = fallback.trackNo,
                    year = fallback.year,
                    genre = fallback.genre,
                    durationSec = fallback.durationSec,
                    bitRate = fallback.bitRate,
                    suffix = fallback.suffix,
                    coverArtId = fallbackCover,
                    lyrics = null,
                    tagsParsed = false,
                ),
            )
        }

        var attempt = 0
        while (true) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Drive scan cancelled")
            }
            val result = driveRangeReader.readTrackMetadata(
                profileId = profileId,
                fileId = file.id,
                fileName = file.name,
                parentFolderPath = item.parentFolderPath,
                fileSizeBytes = file.sizeBytes,
                extractCoverArt = true,
            )

            val status = result.httpStatusCode
            val isRateLimited = status == 429 || (status == 403 && !result.retryAfterHeader.isNullOrBlank())
            if (isRateLimited) {
                if (attempt >= MAX_RATE_LIMIT_RETRIES) {
                    return IndexOutcome(entity = null, quotaExhausted = true)
                }
                val waitMs = computeRetryDelayMs(attempt, result.retryAfterHeader)
                attempt++
                sleeper(waitMs)
                continue
            }

            val rangeSucceeded = status != null && status in 200..299
            val meta = result.metadata
            val embeddedCoverId = result.coverArtId
            val coverFile = result.coverFile
            val coverEntity = if (embeddedCoverId != null && coverFile != null) {
                CachedCoverEntity(
                    coverArtId = embeddedCoverId,
                    filePath = coverFile.absolutePath,
                    sizeBytes = result.coverSizeBytes,
                )
            } else {
                null
            }
            val resolvedCoverId = embeddedCoverId ?: folderCoverFileId?.let { "$profileId:$it" }
            val entity = buildTrackEntity(
                profileId = profileId,
                item = item,
                title = meta.title,
                artist = meta.artist,
                albumArtist = meta.albumArtist,
                album = meta.album,
                trackNo = meta.trackNo,
                year = meta.year,
                genre = meta.genre,
                durationSec = meta.durationSec,
                bitRate = meta.bitRate,
                suffix = meta.suffix,
                coverArtId = resolvedCoverId,
                lyrics = meta.lyrics,
                tagsParsed = rangeSucceeded,
            )
            return IndexOutcome(entity = entity, coverEntity = coverEntity)
        }
    }

    private suspend fun discoverScoped(
        profileId: String,
        api: DriveApi,
        rootFolderId: String,
        prevLastScan: String?,
    ): DiscoveryResult {
        val queue = ArrayDeque<String>().apply { addLast(rootFolderId) }
        val visitedFolders = HashSet<String>().apply { add(rootFolderId) }
        val folderPaths = HashMap<String, String>().apply { put(rootFolderId, "") }
        val discovered = ArrayList<DiscoveredAudio>()
        val seenFileIds = HashSet<String>()
        val coverCandidates = HashMap<String, Pair<Int, String>>()

        while (queue.isNotEmpty()) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Drive scan cancelled")
            }
            val batch = ArrayList<String>(FOLDER_BATCH_SIZE)
            while (batch.size < FOLDER_BATCH_SIZE && queue.isNotEmpty()) {
                batch.add(queue.removeFirst())
            }
            val parentsClause = batch.joinToString(" or ") {
                "'${escapeDriveQueryLiteral(it)}' in parents"
            }
            val query = "($parentsClause) and trashed = false"

            listAllPages(profileId, api, query, orderBy = "folder,name") { page ->
                for (item in page.files) {
                    if (item.trashed || item.mimeType == MIME_SHORTCUT) continue
                    val parentId = item.parents.firstOrNull { it in folderPaths }
                        ?: item.parents.firstOrNull()
                    val parentPath = parentId?.let { folderPaths[it] }.orEmpty()

                    when {
                        item.mimeType == MIME_FOLDER -> {
                            if (visitedFolders.add(item.id)) {
                                val childPath = if (parentPath.isEmpty()) item.name else "$parentPath/${item.name}"
                                folderPaths[item.id] = childPath
                                queue.addLast(item.id)
                            }
                        }
                        coverPriority(item.name) >= 0 -> {
                            if (parentId != null) {
                                val prio = coverPriority(item.name)
                                val existing = coverCandidates[parentId]
                                if (existing == null || prio < existing.first) {
                                    coverCandidates[parentId] = prio to item.id
                                }
                            }
                        }
                        isAudioFile(item) -> {
                            if (seenFileIds.add(item.id)) {
                                discovered.add(
                                    DiscoveredAudio(
                                        file = item,
                                        parentFolderId = parentId,
                                        parentFolderPath = parentPath,
                                    ),
                                )
                            }
                        }
                    }
                }
                updateStatus(
                    profileId,
                    ScanStatus(
                        scanning = true,
                        count = discovered.size,
                        folderCount = visitedFolders.size,
                        lastScan = prevLastScan,
                        totalCount = null,
                        quotaLimited = false,
                        twoStage = true,
                    ),
                )
            }
        }

        return DiscoveryResult(
            files = discovered,
            folderCoverIds = coverCandidates.mapValues { it.value.second },
            folderCount = visitedFolders.size,
            quotaLimited = false,
        )
    }

    private suspend fun discoverAll(
        profileId: String,
        api: DriveApi,
        prevLastScan: String?,
    ): DiscoveryResult {
        val rawAudio = ArrayList<DriveFileDto>()
        val seenFileIds = HashSet<String>()

        listAllPages(profileId, api, SCAN_ALL_AUDIO_QUERY, orderBy = "name") { page ->
            for (item in page.files) {
                if (item.trashed || item.mimeType == MIME_SHORTCUT) continue
                if (isAudioFile(item) && seenFileIds.add(item.id)) {
                    rawAudio.add(item)
                }
            }
            updateStatus(
                profileId,
                ScanStatus(
                    scanning = true,
                    count = rawAudio.size,
                    folderCount = null,
                    lastScan = prevLastScan,
                    totalCount = null,
                    quotaLimited = false,
                    twoStage = true,
                ),
            )
        }

        if (rawAudio.isEmpty()) {
            return DiscoveryResult(
                files = emptyList(),
                folderCoverIds = emptyMap(),
                folderCount = 0,
                quotaLimited = false,
            )
        }

        val foldersById = HashMap<String, DriveFileDto>()
        val folderQuery = "mimeType = '$MIME_FOLDER' and trashed = false"
        listAllPages(profileId, api, folderQuery, orderBy = null) { page ->
            for (folder in page.files) {
                if (!folder.trashed && folder.mimeType == MIME_FOLDER) {
                    foldersById[folder.id] = folder
                }
            }
        }

        val resolvedFolderPaths = HashMap<String, String>()
        fun resolveFolderPath(folderId: String, visiting: MutableSet<String> = HashSet()): String {
            resolvedFolderPaths[folderId]?.let { return it }
            if (!visiting.add(folderId)) return ""
            val folder = foldersById[folderId] ?: return ""
            val parentId = folder.parents.firstOrNull { it in foldersById }
            val parentPath = if (parentId != null) resolveFolderPath(parentId, visiting) else ""
            val fullPath = if (parentPath.isEmpty()) folder.name else "$parentPath/${folder.name}"
            resolvedFolderPaths[folderId] = fullPath
            return fullPath
        }

        val distinctParentIds = rawAudio.mapNotNull { it.parents.firstOrNull() }.distinct()
        val coverCandidates = HashMap<String, Pair<Int, String>>()
        for (parentBatch in distinctParentIds.chunked(FOLDER_BATCH_SIZE)) {
            val parentsClause = parentBatch.joinToString(" or ") {
                "'${escapeDriveQueryLiteral(it)}' in parents"
            }
            val coverQuery = "($parentsClause) and trashed = false and " +
                "(name = 'cover.jpg' or name = 'cover.png' or name = 'folder.jpg' or name = 'front.jpg')"
            listAllPages(profileId, api, coverQuery, orderBy = null) { page ->
                for (item in page.files) {
                    if (item.trashed || item.mimeType == MIME_SHORTCUT) continue
                    val prio = coverPriority(item.name)
                    if (prio < 0) continue
                    val parentId = item.parents.firstOrNull() ?: continue
                    val existing = coverCandidates[parentId]
                    if (existing == null || prio < existing.first) {
                        coverCandidates[parentId] = prio to item.id
                    }
                }
            }
        }

        val discovered = rawAudio.map { file ->
            val parentId = file.parents.firstOrNull()
            val parentPath = parentId?.let { resolveFolderPath(it) }.orEmpty()
            DiscoveredAudio(
                file = file,
                parentFolderId = parentId,
                parentFolderPath = parentPath,
            )
        }

        return DiscoveryResult(
            files = discovered,
            folderCoverIds = coverCandidates.mapValues { it.value.second },
            folderCount = foldersById.size,
            quotaLimited = false,
        )
    }

    private suspend fun listAllPages(
        profileId: String,
        api: DriveApi,
        query: String,
        orderBy: String?,
        onPage: suspend (DriveFileListResponse) -> Unit,
    ) {
        var pageToken: String? = null
        val tag = ProfileIdTag(profileId)
        do {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Drive scan cancelled")
            }
            val response = executePageWithRetry {
                api.listFiles(
                    q = query,
                    fields = DriveApi.DEFAULT_FILE_FIELDS,
                    pageSize = DriveApi.DEFAULT_PAGE_SIZE,
                    pageToken = pageToken,
                    orderBy = orderBy,
                    profileIdTag = tag,
                )
            }
            onPage(response)
            pageToken = response.nextPageToken?.takeIf { it.isNotBlank() }
        } while (pageToken != null)
    }

    private suspend fun <T> executePageWithRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Drive scan cancelled")
            }
            try {
                return block()
            } catch (e: HttpException) {
                if (!isRateLimitHttpException(e)) {
                    throw e
                }
                if (attempt >= MAX_RATE_LIMIT_RETRIES) {
                    throw DriveRateLimitExhaustedException("Drive API rate limit retries exhausted")
                }
                val retryAfter = e.response()?.headers()?.get("Retry-After")
                val waitMs = computeRetryDelayMs(attempt, retryAfter)
                attempt++
                sleeper(waitMs)
            }
        }
    }

    private fun isRateLimitHttpException(e: HttpException): Boolean {
        val code = e.code()
        if (code == 429) return true
        if (code != 403) return false
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        return body.contains("rateLimitExceeded", ignoreCase = true) ||
            body.contains("userRateLimitExceeded", ignoreCase = true)
    }

    private fun computeRetryDelayMs(attempt: Int, retryAfterHeader: String?): Long {
        val parsed = parseRetryAfterMs(retryAfterHeader, clockMs())
        if (parsed != null) return parsed
        val baseMs = 1_000L shl attempt.coerceIn(0, MAX_RATE_LIMIT_RETRIES - 1)
        return (baseMs * jitterProvider()).toLong().coerceAtLeast(0L)
    }

    private suspend fun pruneDeletedAndTrashed(
        profileId: String,
        discovered: List<DiscoveredAudio>,
        existing: List<DriveFileModTime>,
    ) {
        val discoveredFileIds = discovered.asSequence().map { it.file.id }.toHashSet()
        val stale = existing.filter { it.driveFileId !in discoveredFileIds }
        if (stale.isEmpty()) return

        stale.map { it.songId }.chunked(DELETE_BATCH_SIZE).forEach { ids ->
            driveTrackDao.deleteByIds(ids)
        }
        for (item in stale) {
            val coverId = "$profileId:cov:${item.driveFileId}"
            cachedCoverDao.path(coverId)?.let { path ->
                runCatching { File(path).delete() }
            }
            cachedCoverDao.delete(coverId)
        }
    }

    private fun buildTrackEntity(
        profileId: String,
        item: DiscoveredAudio,
        title: String,
        artist: String,
        albumArtist: String,
        album: String,
        trackNo: Int?,
        year: Int?,
        genre: String?,
        durationSec: Int,
        bitRate: Int?,
        suffix: String?,
        coverArtId: String?,
        lyrics: Lyrics?,
        tagsParsed: Boolean,
    ): DriveTrackEntity {
        val file = item.file
        val effectiveAlbumArtist = albumArtist.ifBlank { artist }.ifBlank { "Unknown Artist" }
        val normalizedAlbum = album.ifBlank { "Unknown Album" }
        val albumKey = buildAlbumKey(profileId, effectiveAlbumArtist, normalizedAlbum)
        val artistKey = buildArtistKey(profileId, effectiveAlbumArtist)
        val lyricsJson = lyrics?.let { runCatching { json.encodeToString(Lyrics.serializer(), it) }.getOrNull() }

        return DriveTrackEntity(
            songId = GlobalId.format(profileId, file.id),
            profileId = profileId,
            driveFileId = file.id,
            fileName = file.name,
            parentFolderId = item.parentFolderId,
            parentFolderPath = item.parentFolderPath,
            title = title,
            artist = artist,
            albumArtist = albumArtist,
            album = normalizedAlbum,
            albumKey = albumKey,
            artistKey = artistKey,
            trackNo = trackNo,
            year = year,
            genre = genre,
            durationSec = durationSec,
            bitRate = bitRate,
            suffix = suffix,
            sizeBytes = file.sizeBytes,
            mimeType = file.mimeType,
            coverArtId = coverArtId,
            embeddedLyricsJson = lyricsJson,
            modifiedTimeMs = file.modifiedTimeMs,
            indexedAtMs = clockMs(),
            tagsParsed = tagsParsed,
        )
    }

    private fun updateStatus(profileId: String, status: ScanStatus) {
        _scanStatuses.update { it + (profileId to status) }
    }

    companion object {
        private const val REGISTRY_KEY = "DriveLibraryIndexer"
        const val MIME_FOLDER = "application/vnd.google-apps.folder"
        const val MIME_SHORTCUT = "application/vnd.google-apps.shortcut"
        const val FOLDER_BATCH_SIZE = 25
        const val UPSERT_BATCH_SIZE = 32
        const val DELETE_BATCH_SIZE = 500
        const val RANGE_CONCURRENCY = 3
        const val MAX_RATE_LIMIT_RETRIES = 5

        val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "opus", "ogg", "wav", "aac", "alac")
        private val COVER_FILE_NAMES = listOf("cover.jpg", "cover.png", "folder.jpg", "front.jpg")

        private val SCAN_ALL_AUDIO_QUERY =
            "trashed = false and (mimeType contains 'audio/' or " +
                "name contains '.mp3' or name contains '.flac' or name contains '.m4a' or " +
                "name contains '.opus' or name contains '.ogg' or name contains '.wav' or " +
                "name contains '.aac' or name contains '.alac')"

        fun isAudioFile(file: DriveFileDto): Boolean {
            if (file.trashed) return false
            if (file.mimeType == MIME_FOLDER || file.mimeType == MIME_SHORTCUT) return false
            if (file.mimeType.contains("audio/", ignoreCase = true)) return true
            val dot = file.name.lastIndexOf('.')
            if (dot <= 0 || dot == file.name.lastIndex) return false
            val ext = file.name.substring(dot + 1).lowercase(Locale.ROOT)
            return ext in AUDIO_EXTENSIONS
        }

        fun coverPriority(fileName: String): Int {
            val lower = fileName.trim().lowercase(Locale.ROOT)
            return COVER_FILE_NAMES.indexOf(lower)
        }

        fun buildAlbumKey(profileId: String, albumArtist: String, album: String): String {
            val input = "${albumArtist.trim().lowercase(Locale.ROOT)}\u0000${album.trim().lowercase(Locale.ROOT)}"
            return "$profileId:al:${shortSha256(input)}"
        }

        fun buildArtistKey(profileId: String, artist: String): String {
            val input = artist.trim().lowercase(Locale.ROOT)
            return "$profileId:ar:${shortSha256(input)}"
        }

        internal fun parseRetryAfterMs(header: String?, nowMs: Long): Long? {
            val raw = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            raw.toLongOrNull()?.let { seconds ->
                return (seconds * 1_000L).coerceAtLeast(0L)
            }
            return runCatching {
                val targetMs = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
                (targetMs - nowMs).coerceAtLeast(0L)
            }.getOrNull()
        }

        private fun shortSha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return buildString(16) {
                for (i in 0 until 8) {
                    val b = digest[i].toInt() and 0xFF
                    append(HEX_CHARS[b ushr 4])
                    append(HEX_CHARS[b and 0x0F])
                }
            }
        }

        private fun escapeDriveQueryLiteral(value: String): String =
            value.replace("\\", "\\\\").replace("'", "\\'")

        private fun checkActiveNetworkMetered(context: Context): Boolean {
            val cm = runCatching {
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            }.getOrNull() ?: return false
            return runCatching { cm.isActiveNetworkMetered }.getOrDefault(false)
        }

        private const val HEX_CHARS = "0123456789abcdef"
    }
}
