package com.feishin.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.feishin.mobile.ui.detail.AlbumDetailScreen
import com.feishin.mobile.ui.detail.ArtistDetailScreen
import com.feishin.mobile.ui.detail.PlaylistDetailScreen
import com.feishin.mobile.ui.home.HomeScreen
import com.feishin.mobile.ui.library.GenreScreen
import com.feishin.mobile.ui.library.LibraryScreen
import com.feishin.mobile.ui.offline.DownloadsScreen
import com.feishin.mobile.ui.components.MiniPlayer
import com.feishin.mobile.ui.player.PlayerViewModel
import com.feishin.mobile.ui.search.SearchScreen
import com.feishin.mobile.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{id}"
    const val PLAYLIST = "playlist/{id}"
    const val DOWNLOADS = "downloads"
    const val GENRE = "genre/{name}"
    fun album(id: String) = "album/$id"
    fun artist(id: String) = "artist/$id"
    fun playlist(id: String) = "playlist/$id"
    fun genre(name: String) = "genre/$name"
}

@Composable
fun MainScaffold(main: MainViewModel = hiltViewModel(), onLogout: () -> Unit) {
    val nav = rememberNavController()

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val topLevel = setOf(Routes.HOME, Routes.LIBRARY, Routes.SEARCH, Routes.SETTINGS)

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(main.player)
                if (route in topLevel || route == null) {
                    NavigationBar {
                        val items = listOf(
                            Triple(Routes.HOME, "Home", Icons.Default.Home),
                            Triple(Routes.LIBRARY, "Library", Icons.Default.LibraryMusic),
                            Triple(Routes.SEARCH, "Search", Icons.Default.Search),
                            Triple(Routes.SETTINGS, "Settings", Icons.Default.Settings),
                        )
                        items.forEach { (r, label, icon) ->
                            NavigationBarItem(
                                selected = route == r || (route == null && r == Routes.HOME),
                                onClick = {
                                    nav.navigate(r) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, label) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(player = main.player, repo = main.repo, onAlbum = { nav.navigate(Routes.album(it)) })
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        player = main.player, repo = main.repo,
                        onAlbum = { nav.navigate(Routes.album(it)) },
                        onArtist = { nav.navigate(Routes.artist(it)) },
                        onPlaylist = { nav.navigate(Routes.playlist(it)) },
                        onGenre = { nav.navigate(Routes.genre(it)) },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        player = main.player, repo = main.repo,
                        onAlbum = { nav.navigate(Routes.album(it)) },
                        onArtist = { nav.navigate(Routes.artist(it)) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onLogout = onLogout, onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) })
                }
                composable(Routes.ALBUM) {
                    AlbumDetailScreen(player = main.player, repo = main.repo, onArtist = { nav.navigate(Routes.artist(it)) })
                }
                composable(Routes.ARTIST) {
                    ArtistDetailScreen(repo = main.repo, onAlbum = { nav.navigate(Routes.album(it)) })
                }
                composable(Routes.PLAYLIST) {
                    PlaylistDetailScreen(player = main.player, repo = main.repo, onBack = { nav.popBackStack() })
                }
                composable(Routes.DOWNLOADS) { DownloadsScreen() }
                composable(Routes.GENRE) {
                    GenreScreen(repo = main.repo, onAlbum = { nav.navigate(Routes.album(it)) })
                }
            }
        }
    }
}
