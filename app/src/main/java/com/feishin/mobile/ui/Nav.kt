package com.feishin.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.EntityActionsSheet
import com.feishin.mobile.ui.detail.AlbumDetailScreen
import com.feishin.mobile.ui.detail.ArtistDetailScreen
import com.feishin.mobile.ui.detail.PlaylistDetailScreen
import com.feishin.mobile.ui.home.HomeScreen
import com.feishin.mobile.ui.library.GenreScreen
import com.feishin.mobile.ui.library.GenresScreen
import com.feishin.mobile.ui.library.LibraryScreen
import com.feishin.mobile.ui.library.LibraryTab
import com.feishin.mobile.ui.offline.DownloadsScreen
import com.feishin.mobile.ui.components.MiniPlayer
import com.feishin.mobile.ui.player.NowPlayingScreen
import com.feishin.mobile.ui.search.SearchScreen
import com.feishin.mobile.ui.settings.SettingsScreen
import com.feishin.mobile.ui.theme.VusicColors

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library?tab={tab}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{id}"
    const val PLAYLIST = "playlist/{id}"
    const val DOWNLOADS = "downloads"
    const val GENRE = "genre/{name}"
    const val GENRES = "genres"
    fun library(tab: LibraryTab = LibraryTab.ALBUMS) = "library?tab=${tab.name}"
    fun album(id: String) = "album/$id"
    fun artist(id: String) = "artist/$id"
    fun playlist(id: String) = "playlist/$id"
    fun genre(name: String) = "genre/${android.net.Uri.encode(name)}"
}

@Composable
fun MainScaffold(main: MainViewModel = hiltViewModel(), onLogout: () -> Unit) {
    val nav = rememberNavController()
    var showNowPlaying by remember { mutableStateOf(false) }
    var actionsTarget by remember { mutableStateOf<ActionTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val username by main.auth.loginState.collectAsState()

    LaunchedEffect(Unit) {
        main.messenger.messages.collect { snackbar.showSnackbar(it) }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    fun isLibraryRoute(): Boolean = route?.startsWith("library") == true
    val topLevel = route == Routes.HOME || isLibraryRoute() || route == Routes.SEARCH || route == Routes.SETTINGS || route == null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = VusicColors.Background,
        bottomBar = {
            Column {
                MiniPlayer(main.player, onOpen = { showNowPlaying = true })
                if (topLevel) {
                    NavigationBar(containerColor = Color.Black) {
                        val items = listOf(
                            Triple(Routes.HOME, "Home", Icons.Default.Home),
                            Triple(Routes.library(), "Library", Icons.Default.LibraryMusic),
                            Triple(Routes.SEARCH, "Search", Icons.Default.Search),
                            Triple(Routes.SETTINGS, "Settings", Icons.Default.Settings),
                        )
                        items.forEach { (r, label, icon) ->
                            val selected = when (label) {
                                "Home" -> route == Routes.HOME || route == null
                                "Library" -> isLibraryRoute()
                                "Search" -> route == Routes.SEARCH
                                else -> route == Routes.SETTINGS
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(r) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, label) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Color.White,
                                    unselectedIconColor = VusicColors.SecondaryText,
                                    unselectedTextColor = VusicColors.SecondaryText,
                                    indicatorColor = Color(0xFF2A2A2A),
                                ),
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
                    HomeScreen(
                        player = main.player, repo = main.repo, messenger = main.messenger,
                        onAlbum = { nav.navigate(Routes.album(it)) },
                        onArtist = { id -> nav.navigate(Routes.artist(id)) },
                        onPlaylist = { id -> nav.navigate(Routes.playlist(id)) },
                        onSettings = { nav.navigate(Routes.SETTINGS) },
                        onExplore = { tab -> nav.navigate(Routes.library(tab)) },
                        onGenres = { nav.navigate(Routes.GENRES) },
                        onActions = { actionsTarget = it },
                        username = username?.username.orEmpty(),
                    )
                }
                composable(
                    Routes.LIBRARY,
                    arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = LibraryTab.ALBUMS.name }),
                ) {
                    LibraryScreen(
                        player = main.player, repo = main.repo, messenger = main.messenger,
                        onAlbum = { nav.navigate(Routes.album(it)) },
                        onArtist = { nav.navigate(Routes.artist(it)) },
                        onPlaylist = { nav.navigate(Routes.playlist(it)) },
                        onGenre = { nav.navigate(Routes.genre(it)) },
                        onActions = { actionsTarget = it },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        player = main.player, repo = main.repo, messenger = main.messenger,
                        settings = main.settings,
                        onAlbum = { nav.navigate(Routes.album(it)) },
                        onArtist = { nav.navigate(Routes.artist(it)) },
                        onActions = { actionsTarget = it },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onLogout = onLogout, onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) })
                }
                composable(Routes.ALBUM) {
                    AlbumDetailScreen(
                        player = main.player, repo = main.repo, messenger = main.messenger,
                        onArtist = { nav.navigate(Routes.artist(it)) },
                        onActions = { actionsTarget = it },
                    )
                }
                composable(Routes.ARTIST) {
                    ArtistDetailScreen(
                        player = main.player, repo = main.repo, messenger = main.messenger,
                        onAlbum = { nav.navigate(Routes.album(it)) },
                        onArtist = { nav.navigate(Routes.artist(it)) },
                        onActions = { actionsTarget = it },
                    )
                }
                composable(Routes.PLAYLIST) {
                    PlaylistDetailScreen(
                        player = main.player, repo = main.repo, messenger = main.messenger,
                        onActions = { actionsTarget = it },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.DOWNLOADS) { DownloadsScreen(repo = main.repo) }
                composable(Routes.GENRE) {
                    GenreScreen(repo = main.repo, onAlbum = { nav.navigate(Routes.album(it)) })
                }
                composable(Routes.GENRES) {
                    GenresScreen(onGenre = { nav.navigate(Routes.genre(it)) })
                }
            }
        }
    }

    EntityActionsSheet(
        target = actionsTarget,
        onDismiss = { actionsTarget = null },
        player = main.player,
        repo = main.repo,
        settings = main.settings,
        messenger = main.messenger,
        onAlbum = {
            actionsTarget = null
            nav.navigate(Routes.album(it))
        },
        onArtist = {
            actionsTarget = null
            nav.navigate(Routes.artist(it))
        },
    )

    AnimatedVisibility(
        visible = showNowPlaying,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        NowPlayingScreen(
            player = main.player,
            repo = main.repo,
            messenger = main.messenger,
            onClose = { showNowPlaying = false },
            onArtist = {
                showNowPlaying = false
                nav.navigate(Routes.artist(it))
            },
            onAlbum = {
                showNowPlaying = false
                nav.navigate(Routes.album(it))
            },
            onActions = { actionsTarget = it },
        )
    }
}
