package com.feishin.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.feishin.mobile.data.db.DownloadEntity
import com.feishin.mobile.data.db.DownloadStatus
import com.feishin.mobile.ui.theme.VusicColors

/**
 * Small state indicator for a track or album: a download glyph while queued,
 * a progress ring while transferring, a check once stored on disk.
 */
@Composable
fun DownloadIndicator(
    state: DownloadEntity?,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    when (state.statusEnum) {
        DownloadStatus.FAILED -> Unit
        DownloadStatus.COMPLETED -> Icon(
            Icons.Default.DownloadDone, "Downloaded",
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(16.dp),
        )
        DownloadStatus.DOWNLOADING -> Box(modifier.size(16.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        }
        DownloadStatus.QUEUED, DownloadStatus.PAUSED -> Icon(
            Icons.Default.Downloading, "Download pending",
            tint = VusicColors.SecondaryText,
            modifier = modifier.size(16.dp),
        )
    }
}
