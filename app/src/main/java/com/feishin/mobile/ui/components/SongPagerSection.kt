package com.feishin.mobile.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.VusicColors

/** Rows per page. Four leaves the pager dot row legible on every screen size. */
const val SONG_PAGER_ROWS = 4

/**
 * A song section rendered as a horizontal pager of [SONG_PAGER_ROWS]-row pages,
 * with a page-indicator dot row that appears only when there is more than one
 * page.
 *
 * Shared by Home's Quick picks and Forgotten favorites so both behave
 * identically; the caller supplies the source name used for playback history.
 * Renders nothing when there is no data, which is how the sections disappear.
 */
@Composable
fun SongPagerSection(
    tracks: List<Track>,
    sourceName: String,
    player: PlayerManager,
    repo: MusicDataRepository,
    nowPlayingId: String?,
    isPlaying: Boolean,
    onActions: (Track) -> Unit,
    modifier: Modifier = Modifier,
    onArtist: (String) -> Unit = {},
) {
    if (tracks.isEmpty()) return
    val pages = tracks.chunked(SONG_PAGER_ROWS)
    // Uniform page width on every page (no per-page size hack): the end
    // contentPadding lets the pager clamp on the last page so it sits flush
    // right with the previous page peeking on the left.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val pageWidth = maxWidth - 40.dp
        val pager = rememberPagerState(pageCount = { pages.size })
        Column(Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth(),
                pageSize = PageSize.Fixed(pageWidth),
                contentPadding = PaddingValues(horizontal = 16.dp),
                pageSpacing = 12.dp,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pager,
                    snapAnimationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ),
            ) { page ->
                Column(Modifier.fillMaxWidth()) {
                    pages[page].forEach { t ->
                        TrackRow(
                            track = t, artUrl = repo.coverArtUrl(t.coverArt, 128),
                            onClick = {
                                player.playAll(tracks, tracks.indexOf(t), source = sourceName)
                            },
                            onActions = { onActions(t) },
                            onArtist = onArtist,
                            isCurrent = t.id == nowPlayingId, isPlaying = isPlaying,
                        )
                    }
                    // Constant pager height: pad a short last page with blank
                    // rows so switching pages never changes the section height.
                    // One TrackRow is 68dp (56dp art + 6dp + 6dp padding).
                    repeat(SONG_PAGER_ROWS - pages[page].size) {
                        Spacer(Modifier.height(68.dp))
                    }
                }
            }
        if (pages.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pages.size) { i ->
                    // targetPage, not currentPage: the target only changes on a
                    // committed settle, so dots never flip jittery mid-swipe.
                    val selected = i == pager.targetPage
                    // The active dot grows instead of popping, so switching pages
                    // reads as movement rather than a jump.
                    val size by animateDpAsState(
                        targetValue = if (selected) 8.dp else 6.dp,
                        animationSpec = Motion.layoutDp(),
                        label = "pager-dot-$i",
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.35f,
                        animationSpec = Motion.fade(Motion.Fast),
                        label = "pager-dot-alpha-$i",
                    )
                    Box(
                        Modifier
                            .padding(3.dp)
                            .size(size)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha.coerceIn(0f, 1f))),
                    )
                }
            }
        }
    }
}
}