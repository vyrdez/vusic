package com.feishin.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.EntityActionsSheet
import com.feishin.mobile.ui.components.MiniPlayer
import com.feishin.mobile.ui.detail.AlbumDetailScreen
import com.feishin.mobile.ui.detail.ArtistDetailScreen
import com.feishin.mobile.ui.detail.PlaylistDetailScreen
import com.feishin.mobile.ui.home.HomeScreen
import com.feishin.mobile.ui.library.GenreScreen
import com.feishin.mobile.ui.library.GenresScreen
import com.feishin.mobile.ui.library.LibraryScreen
import com.feishin.mobile.ui.library.LibraryTab
import com.feishin.mobile.ui.navigation.NavRules
import com.feishin.mobile.ui.offline.DownloadsScreen
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
    fun library(tab: LibraryTab = LibraryTab.PLAYLISTS) = "library?tab=${tab.name}"

    /** Route pattern, for comparing against the current destination. */
    const val LIBRARY_PATTERN = "library?tab={tab}"
    fun album(id: String) = "album/$id"
    fun artist(id: String) = "artist/$id"
    fun playlist(id: String) = "playlist/$id"
    fun genre(name: String) = "genre/${android.net.Uri.encode(name)}"
}

/** Screens that get the shared back-arrow top app bar. */
private val DETAIL_ROUTES = NavRules.DETAIL_ROUTES

@Composable
fun MainScaffold(main: MainViewModel = hiltViewModel(), onLogout: () -> Unit) {
    val nav = rememberNavController()
    var showNowPlaying by remember { mutableStateOf(false) }
    var actionsTarget by remember { mutableStateOf<ActionTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val username by main.auth.loginState.collectAsState()
    val topBar = remember { TopBarController() }

    LaunchedEffect(Unit) {
        main.messenger.messages.collect { snackbar.showSnackbar(it) }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isDetail = route in DETAIL_ROUTES
    LaunchedEffect(route) { if (!isDetail) topBar.reset() }

    // Empty queue => nothing playing => the Now Playing overlay must go away.
    // Without this, clearing the queue leaves its transparent "Nothing playing"
    // placeholder stretched over every screen.
    val currentTrack by main.player.current.collectAsState()
    LaunchedEffect(currentTrack) { if (currentTrack == null) showNowPlaying = false }

    // Replay queued scrobbles/stars as soon as the network comes back.
    val online by main.network.isOnline.collectAsState()
    val pendingUploads by main.offlineSync.pendingCount.collectAsState(initial = 0)
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(online, pendingUploads) {
        if (online && pendingUploads > 0) {
            com.feishin.mobile.data.download.OfflineSyncWorker.schedule(context)
        }
    }

    CompositionLocalProvider(LocalTopBarController provides topBar) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                containerColor = VusicColors.Background,
                topBar = {
                    if (isDetail) {
                        AppTopBar(topBar, onBack = { nav.popBackStack() })
                    }
                },
                bottomBar = {
                    // Always visible: the nav bar and the mini player directly above it.
                    Column {
                        MiniPlayer(main.player, onOpen = { showNowPlaying = true })
                        AppNavigationBar(
                            currentRoute = route,
                            onSelect = { target -> nav.goToTab(target) },
                        )
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    // Effective offline: manual toggle, or auto mode following
                    // the live network. The banner respects "Show offline chip".
                    val settingsState by main.settings.flow.collectAsState(initial = null)
                    val effectiveOffline = settingsState?.let { s ->
                        s.offlineMode || (s.autoOffline && !online)
                    } == true
                    if (effectiveOffline && settingsState?.showOfflineChip != false) {
                        OfflineBanner(
                            modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
                        )
                    }
                    NavHost(nav, startDestination = Routes.HOME) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                player = main.player, repo = main.repo, messenger = main.messenger,
                                onAlbum = { nav.navigate(Routes.album(it)) },
                                onArtist = { id -> nav.navigate(Routes.artist(id)) },
                                onPlaylist = { id -> nav.navigate(Routes.playlist(id)) },
                                onSettings = { nav.goToTab(Routes.SETTINGS) },
                                onExplore = { tab -> nav.goToLibraryTab(tab) },
                                onGenres = { nav.navigate(Routes.GENRES) },
                                onActions = { actionsTarget = it },
                                username = username?.username.orEmpty(),
                            )
                        }
                        composable(
                            Routes.LIBRARY,
                            arguments = listOf(
                                navArgument("tab") {
                                    type = NavType.StringType
                                    defaultValue = LibraryTab.PLAYLISTS.name
                                },
                            ),
                        ) { entry ->
                            val tabArg = entry.arguments?.getString("tab")
                            LibraryScreen(
                                initialTab = tabArg?.let { name ->
                                    runCatching { LibraryTab.valueOf(name) }.getOrNull()
                                },
                                player = main.player, repo = main.repo, messenger = main.messenger,
                                downloads = main.downloads,
                                onAlbum = { nav.navigate(Routes.album(it)) },
                                onArtist = { nav.navigate(Routes.artist(it)) },
                                onPlaylist = { nav.navigate(Routes.playlist(it)) },
                                onGenre = { nav.navigate(Routes.genre(it)) },
                                onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) },
                                onActions = { actionsTarget = it },
                            )
                        }
                        composable(Routes.SEARCH) {
                            SearchScreen(
                                player = main.player, repo = main.repo, messenger = main.messenger,
                                settings = main.settings,
                                onAlbum = { nav.navigate(Routes.album(it)) },
                                onArtist = { nav.navigate(Routes.artist(it)) },
                                onPlaylist = { nav.navigate(Routes.playlist(it)) },
                                onActions = { actionsTarget = it },
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                onLogout = onLogout,
                                onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) },
                                onOpenLibraryDownloads = { nav.goToLibraryTab(LibraryTab.DOWNLOADS) },
                            )
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
                        composable(Routes.DOWNLOADS) {
                            DownloadsScreen(repo = main.repo)
                        }
                        composable(Routes.GENRE) {
                            GenreScreen(repo = main.repo, onAlbum = { nav.navigate(Routes.album(it)) })
                        }
                        composable(Routes.GENRES) {
                            GenresScreen(onGenre = { nav.navigate(Routes.genre(it)) })
                        }
                    }
                }
            }

            // Overlays draw above the scaffold in z-order: full-screen player
            // first, then action sheets on top of everything (a sheet opened
            // from Now Playing's ⋮ menu must be visible and tappable, which it
            // isn't when composed underneath the player's opaque background).
            AnimatedVisibility(
                visible = NavRules.shouldShowNowPlaying(showNowPlaying, currentTrack == null),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                NowPlayingScreen(
                    player = main.player,
                    repo = main.repo,
                    favs = main.favs,
                    downloads = main.downloads,
                    playlistEvents = main.playlistEvents,
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

            EntityActionsSheet(
                target = actionsTarget,
                onDismiss = { actionsTarget = null },
                player = main.player,
                repo = main.repo,
                settings = main.settings,
                messenger = main.messenger,
                favs = main.favs,
                downloads = main.downloads,
                cachedTracks = main.cachedTracks,
                playlistEvents = main.playlistEvents,
                onStartRadio = { showNowPlaying = true },
                onAlbum = {
                    actionsTarget = null
                    showNowPlaying = false
                    nav.navigate(Routes.album(it))
                },
                onArtist = {
                    actionsTarget = null
                    showNowPlaying = false
                    nav.navigate(Routes.artist(it))
                },
            )
        }
    }
}

@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(VusicColors.SurfaceHigh)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(
            androidx.compose.material.icons.Icons.Default.CloudOff, null,
            tint = VusicColors.SecondaryText,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "You're offline — downloaded music only",
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = VusicColors.SecondaryText,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(controller: TopBarController, onBack: () -> Unit) {
    val title = controller.title
    val showTitle = (controller.alwaysShowTitle || controller.scrolled) && title != null
    TopAppBar(
        title = {
            if (showTitle) {
                Text(title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = VusicColors.Background,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
        ),
    )
}

@Composable
private fun AppNavigationBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val items = NavRules.BOTTOM_NAV_LABELS.map { label ->
        Triple(
            NavRules.bottomNavTarget(label),
            label,
            when (label) {
                "Home" -> Icons.Default.Home
                "Library" -> Icons.Default.LibraryMusic
                "Search" -> Icons.Default.Search
                else -> Icons.Default.Settings
            },
        )
    }
    NavigationBar(containerColor = Color.Black) {
        items.forEach { (target, label, icon) ->
            val selected = NavRules.selectedLabel(currentRoute) == label
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(target) },
                icon = { Icon(icon, label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = VusicColors.SecondaryText,
                    unselectedTextColor = VusicColors.SecondaryText,
                    indicatorColor = VusicColors.SurfaceHigh,
                ),
            )
        }
    }
}

/**
 * The single way to move between top-level tabs. Using the same options for the
 * bottom bar and the Home avatar keeps every tab reachable from anywhere (the
 * old avatar path pushed a second Settings destination that broke the back stack).
 *
 * When a detail screen (album/artist/playlist/genre) sits above the tabs it is
 * popped as its own step first. Folding it into the target tab's saved state via
 * `popUpTo(saveState = true)` + `restoreState = true` made `restoreState` re-create
 * the detail, so tapping Home from an album appeared to do nothing.
 */
private fun NavHostController.goToTab(route: String) {
    if (NavRules.isDetail(currentDestination?.route)) {
        popBackStack()
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Library tab switching must not be swallowed by restored state, so the existing
 * Library entry is replaced when a different tab is requested.
 */
private fun NavHostController.goToLibraryTab(tab: LibraryTab) {
    if (NavRules.libraryNeedsReplace(currentDestination?.route)) {
        navigate(Routes.library(tab)) {
            popUpTo(Routes.LIBRARY_PATTERN) { inclusive = true }
            launchSingleTop = true
        }
    } else {
        navigate(Routes.library(tab)) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
        }
    }
}
