package com.feishin.mobile.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.ui.LoadingBox
import com.feishin.mobile.ui.components.EmptyState
import com.feishin.mobile.ui.components.LocalMiniClearance
import com.feishin.mobile.ui.theme.VusicSpacing
import com.feishin.mobile.ui.components.ErrorState
import com.feishin.mobile.ui.components.SectionHeader
import com.feishin.mobile.ui.components.rememberSavableListState
import com.feishin.mobile.ui.theme.VusicColors

@Composable
fun GenresScreen(
    vm: LibraryViewModel = hiltViewModel(),
    onGenre: (String) -> Unit,
) {
    val ui by vm.genres.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshGenres() }
    // Simple list screen: show the title in the shared top bar immediately.
    val topBar = com.feishin.mobile.ui.LocalTopBarController.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        topBar.title = "Genres"
        topBar.alwaysShowTitle = true
    }
    Column(Modifier.fillMaxSize()) {
        when (val s = ui) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorState(s.message, vm::refresh)
            is UiState.Empty -> EmptyState("No genres")
            is UiState.Data -> LazyColumn(
                Modifier.fillMaxSize(),
                state = rememberSavableListState(),
                contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
            ) {
                items(s.value, key = { it.name }) { g ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .clickable(onClick = { onGenre(g.name) })
                            .padding(16.dp, 12.dp),
                    ) {
                        Text(g.name, modifier = Modifier.weight(1f))
                        Text(
                            "${g.albumCount} albums", style = MaterialTheme.typography.bodySmall,
                            color = VusicColors.SecondaryText,
                        )
                    }
                }
            }
        }
    }
}
