package com.feishin.mobile.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.auth.DriveAuthError
import com.feishin.mobile.data.remote.drive.DriveApi
import com.feishin.mobile.data.remote.drive.DriveFileDto
import com.feishin.mobile.ui.theme.VusicColors
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class DriveFolderItem(
    val id: String,
    val name: String,
    val isSuggestedMusic: Boolean = false,
)

@Immutable
data class DriveBreadcrumb(
    val id: String,
    val name: String,
)

@Immutable
data class DriveFolderPickerUiState(
    val breadcrumbs: List<DriveBreadcrumb> = listOf(DriveBreadcrumb(ROOT_FOLDER_ID, ROOT_BREADCRUMB_NAME)),
    val folders: List<DriveFolderItem> = emptyList(),
    val selectedFolder: DriveFolderItem? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
) {
    val currentFolderId: String
        get() = breadcrumbs.lastOrNull()?.id ?: ROOT_FOLDER_ID

    val currentFolderName: String
        get() = breadcrumbs.lastOrNull()?.name ?: ROOT_BREADCRUMB_NAME

    val canSelectCurrentFolder: Boolean
        get() = selectedFolder != null || breadcrumbs.size > 1

    /** Returns `(folderId, folderName)` for the active folder selection, or `null` at root with no pick. */
    fun resolvedSelection(): Pair<String, String>? = when {
        selectedFolder != null -> selectedFolder.id to selectedFolder.name
        breadcrumbs.size > 1 -> currentFolderId to currentFolderName
        else -> null
    }

    companion object {
        const val ROOT_FOLDER_ID = "root"
        const val ROOT_BREADCRUMB_NAME = "My Drive"
    }
}

fun interface DriveFolderDataSource {
    suspend fun listSubfolders(parentId: String): Result<List<DriveFileDto>>
}

/**
 * Queries Google Drive v3 folder children for [parentId] across pages using the Drive folder
 * query spec (`mimeType='application/vnd.google-apps.folder' and trashed=false and '$parentId' in parents`).
 */
internal suspend fun fetchDriveSubfolders(
    api: DriveApi,
    parentId: String,
    maxPages: Int = 25,
): Result<List<DriveFileDto>> = withContext(Dispatchers.IO) {
    try {
        val escapedParent = parentId.replace("'", "\\'")
        val query = "mimeType='application/vnd.google-apps.folder' and trashed=false and '$escapedParent' in parents"
        val collected = ArrayList<DriveFileDto>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = api.listFiles(
                q = query,
                fields = FOLDER_PICKER_FIELDS,
                pageSize = FOLDER_PICKER_PAGE_SIZE,
                pageToken = pageToken,
                orderBy = "name",
            )
            collected += response.files.filter { !it.trashed && it.id.isNotBlank() }
            pageToken = response.nextPageToken?.takeIf { it.isNotBlank() }
            pages++
        } while (pageToken != null && pages < maxPages)
        Result.Ok(collected)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Err(DriveAuthError.userMessage(e), e)
    }
}

private const val FOLDER_PICKER_FIELDS = "nextPageToken,files(id,name,parents)"
private const val FOLDER_PICKER_PAGE_SIZE = 200
private const val ALL_AUDIO_FILES_LABEL = "All audio files"

/**
 * Pure state holder / ViewModel for [DriveFolderPickerDialog], supporting JVM unit testing
 * of folder queries, `"Music"` suggested highlighting, breadcrumb navigation, and selection.
 */
class DriveFolderPickerViewModel(
    private val dataSource: DriveFolderDataSource,
    private val initialSelectedFolderId: String? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _ui = MutableStateFlow(DriveFolderPickerUiState())
    val ui: StateFlow<DriveFolderPickerUiState> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var initialLoadDone = false

    fun loadInitial() {
        if (initialLoadDone && (_ui.value.loading || _ui.value.folders.isNotEmpty() || _ui.value.errorMessage != null)) {
            return
        }
        initialLoadDone = true
        loadDirectory(
            breadcrumbs = listOf(
                DriveBreadcrumb(
                    DriveFolderPickerUiState.ROOT_FOLDER_ID,
                    DriveFolderPickerUiState.ROOT_BREADCRUMB_NAME,
                ),
            ),
            applySuggestedDefault = true,
        )
    }

    fun selectFolder(folder: DriveFolderItem) {
        _ui.value = _ui.value.copy(selectedFolder = folder)
    }

    fun openFolder(folder: DriveFolderItem) {
        val nextBreadcrumbs = _ui.value.breadcrumbs + DriveBreadcrumb(folder.id, folder.name)
        loadDirectory(
            breadcrumbs = nextBreadcrumbs,
            applySuggestedDefault = false,
        )
    }

    fun navigateToBreadcrumb(index: Int) {
        val current = _ui.value.breadcrumbs
        if (index !in current.indices) return
        if (index == current.lastIndex && _ui.value.errorMessage == null) return
        loadDirectory(
            breadcrumbs = current.take(index + 1),
            applySuggestedDefault = index == 0,
        )
    }

    fun retry() {
        val current = _ui.value
        loadDirectory(
            breadcrumbs = current.breadcrumbs,
            applySuggestedDefault = current.breadcrumbs.size == 1,
        )
    }

    fun cancelLoads() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun loadDirectory(
        breadcrumbs: List<DriveBreadcrumb>,
        applySuggestedDefault: Boolean,
    ) {
        loadJob?.cancel()
        val targetParentId = breadcrumbs.last().id
        _ui.value = _ui.value.copy(
            breadcrumbs = breadcrumbs,
            folders = emptyList(),
            selectedFolder = null,
            loading = true,
            errorMessage = null,
        )
        loadJob = viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                dataSource.listSubfolders(targetParentId)
            }
            when (result) {
                is Result.Ok -> {
                    val items = sortAndAnnotateFolders(result.value)
                    val preselected = when {
                        initialSelectedFolderId != null ->
                            items.firstOrNull { it.id == initialSelectedFolderId }
                        applySuggestedDefault ->
                            items.firstOrNull { it.isSuggestedMusic }
                        else -> null
                    }
                    _ui.value = _ui.value.copy(
                        folders = items,
                        selectedFolder = preselected,
                        loading = false,
                        errorMessage = null,
                    )
                }
                is Result.Err -> {
                    _ui.value = _ui.value.copy(
                        folders = emptyList(),
                        selectedFolder = null,
                        loading = false,
                        errorMessage = result.message.ifBlank { "Couldn't load Google Drive folders." },
                    )
                }
            }
        }
    }

    companion object {
        internal fun sortAndAnnotateFolders(raw: List<DriveFileDto>): List<DriveFolderItem> {
            val mapped = raw
                .filter { !it.trashed && it.id.isNotBlank() }
                .map { dto ->
                    val displayName = dto.name.trim().ifBlank { "Untitled folder" }
                    DriveFolderItem(
                        id = dto.id,
                        name = displayName,
                        isSuggestedMusic = displayName.equals("Music", ignoreCase = true),
                    )
                }
            return mapped.sortedWith(
                compareByDescending<DriveFolderItem> { it.isSuggestedMusic }
                    .thenBy { it.name.lowercase(Locale.ROOT) },
            )
        }
    }
}

@Composable
fun DriveFolderPickerDialog(
    initialSelectedFolderId: String?,
    sessionKey: Any?,
    loadSubfolders: suspend (String) -> Result<List<DriveFileDto>>,
    onSelectFolder: (folderId: String, folderName: String) -> Unit,
    onScanAllAudio: (allAudioLabel: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerVm = remember(sessionKey) {
        DriveFolderPickerViewModel(
            dataSource = DriveFolderDataSource { parentId -> loadSubfolders(parentId) },
            initialSelectedFolderId = initialSelectedFolderId,
        )
    }
    DisposableEffect(pickerVm) {
        onDispose { pickerVm.cancelLoads() }
    }
    LaunchedEffect(pickerVm) {
        pickerVm.loadInitial()
    }

    val state by pickerVm.ui.collectAsStateWithLifecycle()
    val dialogShape = RoundedCornerShape(20.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 480.dp),
            shape = dialogShape,
            color = VusicColors.SurfaceHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "Choose Music folder",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))

                // Breadcrumbs
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        items = state.breadcrumbs,
                        key = { index, item -> "${index}_${item.id}" },
                    ) { index, crumb ->
                        val isCurrent = index == state.breadcrumbs.lastIndex
                        if (index > 0) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = VusicColors.SecondaryText,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        TextButton(
                            onClick = { pickerVm.navigateToBreadcrumb(index) },
                            enabled = !isCurrent,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        ) {
                            Text(
                                text = crumb.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    VusicColors.Accent
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 160.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Folder list / loading / error / empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 190.dp, max = 320.dp),
                ) {
                    when {
                        state.loading -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp,
                                    color = VusicColors.Accent,
                                )
                                Text(
                                    text = "Loading folders…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VusicColors.SecondaryText,
                                )
                            }
                        }

                        state.errorMessage != null -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = state.errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                                OutlinedButton(
                                    onClick = { pickerVm.retry() },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                ) {
                                    Text("Retry")
                                }
                            }
                        }

                        state.folders.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = VusicColors.SecondaryText,
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(
                                    text = "No folders found. Use \"Scan all audio files\" instead.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VusicColors.SecondaryText,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(
                                    items = state.folders,
                                    key = { it.id },
                                ) { folder ->
                                    DriveFolderRow(
                                        folder = folder,
                                        selected = state.selectedFolder?.id == folder.id,
                                        onSelect = { pickerVm.selectFolder(folder) },
                                        onOpen = { pickerVm.openFolder(folder) },
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { onScanAllAudio(ALL_AUDIO_FILES_LABEL) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(
                        text = "Scan all audio files (entire Drive)",
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val chosen = state.resolvedSelection()
                            if (chosen != null) {
                                onSelectFolder(chosen.first, chosen.second)
                            }
                        },
                        enabled = state.canSelectCurrentFolder,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VusicColors.Accent,
                            contentColor = VusicColors.OnAccent,
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text("Select Folder")
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveFolderRow(
    folder: DriveFolderItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    val rowShape = RoundedCornerShape(10.dp)
    val bgColor = when {
        selected -> VusicColors.Accent.copy(alpha = 0.12f)
        folder.isSuggestedMusic -> VusicColors.Surface
        else -> VusicColors.SurfaceHigh
    }
    val borderColor = if (selected) {
        VusicColors.Accent
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(rowShape)
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), rowShape)
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = if (selected || folder.isSuggestedMusic) VusicColors.Accent else VusicColors.SecondaryText,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (folder.isSuggestedMusic) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VusicColors.Accent.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = "Suggested",
                        style = MaterialTheme.typography.labelSmall,
                        color = VusicColors.Accent,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = VusicColors.Accent,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(
            onClick = onOpen,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open ${folder.name}",
                tint = VusicColors.SecondaryText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
