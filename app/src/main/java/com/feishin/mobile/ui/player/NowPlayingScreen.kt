package com.feishin.mobile.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.favorites.FavoritesStore
import com.feishin.mobile.data.favorites.PlaylistEvents
import com.feishin.mobile.data.settings.BackgroundStyle
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.core.ui.BlurSurfaceRole
import com.feishin.mobile.core.ui.NowPlayingColors
import com.feishin.mobile.core.ui.NowPlayingPalette
import com.feishin.mobile.core.ui.PlayingColors
import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.BlurredArtworkBackground
import com.feishin.mobile.ui.components.CoverImage
import com.feishin.mobile.ui.components.EqualizerOverlay
import com.feishin.mobile.ui.components.MarqueeTitle
import com.feishin.mobile.ui.components.NowPlayingBarTint
import com.feishin.mobile.ui.components.PlaylistPickerSheet
import com.feishin.mobile.ui.components.SheetState
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.components.rememberSheetState
import com.feishin.mobile.ui.components.scrimAlpha
import com.feishin.mobile.ui.theme.LocalPlayingColors
import com.feishin.mobile.ui.theme.LocalVusicDark
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.playingColor
import com.feishin.mobile.ui.components.formatDuration
import com.feishin.mobile.ui.components.shareText
import com.feishin.mobile.core.common.PlaybackProgress
import com.feishin.mobile.core.common.QueueHighlight
import com.feishin.mobile.ui.theme.VusicColorMath
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicSpacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState
import kotlin.math.roundToInt

object NowPlayingTouchHelper {
    fun shouldDismissInputOnExpand(expanded: Boolean): Boolean = expanded

    fun onNowPlayingExpand(
        expanded: Boolean,
        clearFocus: () -> Unit,
        hideKeyboard: () -> Unit,
    ): Boolean {
        if (expanded) {
            clearFocus()
            hideKeyboard()
            return true
        }
        return false
    }

    fun onCompositionEntry(
        clearFocus: () -> Unit,
        hideKeyboard: () -> Unit,
    ) {
        clearFocus()
        hideKeyboard()
    }

    fun consumesPointerInput(): Boolean = true
}

/**
 * Full-screen Now Playing. Playback lives in the service, so rotation and the
 * system back gesture never disturb it; back (and swipe-down) only closes this UI.
 */
@Composable
fun NowPlayingScreen(
    player: PlayerManager,
    lyricsVm: LyricsViewModel = hiltViewModel(),
    repo: MusicDataRepository,
    favs: FavoritesStore,
    downloads: com.feishin.mobile.data.download.DownloadRepository,
    playlistEvents: PlaylistEvents,
    messenger: com.feishin.mobile.ui.components.UiMessenger,
    /**
     * The "Dynamic Now Playing color" setting. When false the screen must use
     * the app theme only - no artwork tint anywhere - so it is passed in
     * explicitly rather than inferred from a null scheme (a null scheme also
     * means "no artwork", which is a different case).
     */
    dynamicColor: Boolean,
    onClose: () -> Unit,
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
    bgStyle: BackgroundStyle = BackgroundStyle.GRADIENT,
    queueBgStyle: BackgroundStyle = BackgroundStyle.DEFAULT,
    nowPlayingSheet: SheetState? = null,
    lyricsListState: LazyListState = rememberLazyListState(),
    lastScrolledLyricsIndex: Int = -1,
    onUpdateLastScrolledLyricsIndex: ((Int) -> Unit)? = null,
    lastMeasuredLyricsViewportPx: Int = 0,
    onUpdateLastMeasuredLyricsViewportPx: ((Int) -> Unit)? = null,
) {
    BackHandler(onBack = onClose)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    val song by player.current.collectAsStateWithLifecycle()
    val current = song
    val plainLyricsScrollState = rememberScrollState()
    val lyricsUi by lyricsVm.ui.collectAsStateWithLifecycle()
    val lyricsSettings by lyricsVm.settings.flow.collectAsStateWithLifecycle(
        initialValue = lyricsVm.settings.latestSettings,
    )

    var internalLastScrolledIndex by rememberSaveable(current?.id) { mutableIntStateOf(-1) }
    val effectiveLastScrolledIndex = if (onUpdateLastScrolledLyricsIndex != null) {
        lastScrolledLyricsIndex
    } else {
        internalLastScrolledIndex
    }
    val updateLastScrolledIndex: (Int) -> Unit = onUpdateLastScrolledLyricsIndex ?: {
        internalLastScrolledIndex = it
    }
    var loadedTrackId by rememberSaveable { mutableStateOf<String?>(current?.id) }

    LaunchedEffect(current?.id) {
        lyricsVm.load(current)
        if (loadedTrackId != current?.id) {
            loadedTrackId = current?.id
            lyricsListState.scrollToItem(0)
            plainLyricsScrollState.scrollTo(0)
            updateLastScrolledIndex(-1)
        }
    }

    val qItems by player.queue.collectAsStateWithLifecycle()
    val curIdx by player.currentIndex.collectAsStateWithLifecycle()
    LaunchedEffect(qItems, curIdx) {
        if (curIdx in qItems.indices && curIdx + 1 in qItems.indices) {
            lyricsVm.prefetchNext(qItems[curIdx + 1].song)
        }
    }
    if (current == null) {
        // Unreachable while the scaffold only shows this overlay when something
        // is playing. Never render a placeholder here: a transparent box with
        // text would overlay whatever screen is underneath. Just close.
        LaunchedEffect(Unit) { onClose() }
        Box(Modifier.fillMaxSize())
        return
    }

    // ---- Palette ----------------------------------------------------------
    // Every colour on this screen comes from ONE resolved palette, so no
    // region can quietly keep a hardcoded literal while another one is fixed.
    //
    // - Artwork scheme present (dynamic color on, usable cover): as before.
    // - Dynamic color on but no scheme (no art / a cover with no usable hue):
    //   the legacy cover-derived dark gradient, unchanged.
    // - Dynamic color OFF: the app theme only. No artwork is sampled at all,
    //   which is what the setting promises; the Light theme gets a light
    //   surface with dark content instead of white text on a cover tint.
    val scheme = LocalPlayingColors.current
    val legacyCoverTint = dynamicColor && scheme == null

    // Legacy background: cover art's dominant colour, clamped dark enough
    // that its own content colour has strong contrast. Only ever sampled on
    // the legacy path (see above).
    val ctx = LocalContext.current
    val artUrl = player.coverArtUrl(current.coverArt, 512)
    val thumbUrl = player.coverArtUrl(current.coverArt, 128)
    val themeBackground = VusicColors.Background.toArgb().toLong() and 0xFFFFFFFFL
    var targetBg by remember { mutableStateOf(themeBackground) }
    LaunchedEffect(artUrl, scheme, legacyCoverTint) {
        // The shared scheme already sampled this art: decoding twice would
        // double the IO for the same pixels.
        if (!legacyCoverTint) {
            targetBg = themeBackground
            return@LaunchedEffect
        }
        if (artUrl == null) {
            targetBg = themeBackground
            return@LaunchedEffect
        }
        val sampled = withContext(Dispatchers.IO) {
            runCatching {
                // Small decode: we only need color statistics, not resolution.
                val req = ImageRequest.Builder(ctx).data(artUrl).size(128, 128).allowHardware(false).build()
                val bmp = (ctx.imageLoader.execute(req).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                bmp?.let { Palette.from(it).generate().getDominantColor(0) }
            }.getOrNull()
        }
        if (sampled != null && sampled != 0) {
            val raw = Color(sampled)
            val (r, g, b) = VusicColorMath.clampToDark(raw.red, raw.green, raw.blue)
            targetBg = Color(r, g, b).toArgb().toLong() and 0xFFFFFFFFL
        }
    }
    val bg by animateColorAsState(playingColor(targetBg), label = "np-bg")
    // Mid gradient stop: the same tint, darkened (opaque, so nothing bleeds through).
    val bgMid = VusicColorMath.scale(bg.red, bg.green, bg.blue, 0.55f)
        .let { (r, g, b) -> Color(r, g, b) }

    val schemeTop = scheme?.let { playingColor(it.surfaceTop) }
    val schemeBottom = scheme?.let { playingColor(it.surfaceBottom) }
    val palette: NowPlayingPalette = when {
        scheme != null -> NowPlayingColors.fromScheme(scheme)
        legacyCoverTint -> NowPlayingColors.legacyCoverGradient(
            bg.toArgb().toLong() and 0xFFFFFFFFL,
            bgMid.toArgb().toLong() and 0xFFFFFFFFL,
            themeBackground,
        )
        else -> {
            val cs = MaterialTheme.colorScheme
            NowPlayingColors.static(
                background = cs.background.toArgb().toLong() and 0xFFFFFFFFL,
                // The gradient always runs AWAY from the content ink: darkest
                // surface in Dark, lightest in Light.
                backdropEnd = if (LocalVusicDark.current) {
                    cs.surfaceContainerLowest.toArgb().toLong() and 0xFFFFFFFFL
                } else {
                    cs.surface.toArgb().toLong() and 0xFFFFFFFFL
                },
                onBackground = cs.onBackground.toArgb().toLong() and 0xFFFFFFFFL,
                onSurface = cs.onSurface.toArgb().toLong() and 0xFFFFFFFFL,
                onSurfaceVariant = cs.onSurfaceVariant.toArgb().toLong() and 0xFFFFFFFFL,
                surface = cs.surface.toArgb().toLong() and 0xFFFFFFFFL,
                surfaceContainer = cs.surfaceContainer.toArgb().toLong() and 0xFFFFFFFFL,
                primary = cs.primary.toArgb().toLong() and 0xFFFFFFFFL,
                onPrimary = cs.onPrimary.toArgb().toLong() and 0xFFFFFFFFL,
                primaryContainer = cs.primaryContainer.toArgb().toLong() and 0xFFFFFFFFL,
                onPrimaryContainer = cs.onPrimaryContainer.toArgb().toLong() and 0xFFFFFFFFL,
                track = PlayingColors.ringTrack(
                    cs.surface.toArgb().toLong() and 0xFFFFFFFFL,
                    cs.primary.toArgb().toLong() and 0xFFFFFFFFL,
                ),
            )
        }
    }
    // Every stop is OPAQUE so nothing bleeds through from the screen below.
    val backdrop = if (schemeTop != null && schemeBottom != null) {
        Brush.verticalGradient(listOf(schemeTop, schemeBottom))
    } else {
        Brush.verticalGradient(listOf(playingColor(palette.backdropTop), playingColor(palette.backdropBottom)))
    }
    val content = playingColor(palette.content)
    val dim = playingColor(palette.dim)
    // One solid strip colour: the scheme's bottom surface when artwork tint is
    // on, otherwise the last stop of the backdrop it sits on. 100% opaque, so
    // lyrics scrolling underneath can never bleed through.
    val bottomBgColor = schemeBottom ?: playingColor(palette.backdropBottom)

    // The system bars follow the surface that is really behind them, not the
    // theme flag: a tinted Now Playing can be light even in the Dark theme.
    // Reported on every palette change (keyed on the value, so no null gap)
    // and cleared only when the screen actually leaves the composition.
    LaunchedEffect(palette.lightBackdrop) { NowPlayingBarTint.report(palette.lightBackdrop) }
    DisposableEffect(Unit) { onDispose { NowPlayingBarTint.clear() } }

    val lyricsMode by lyricsVm.settings.lyricsModeState.collectAsStateWithLifecycle(
        initialValue = lyricsVm.settings.nowPlayingLyricsMode,
    )
    var userToggledTab by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A fresh queue (empty -> non-empty, e.g. playback after Clear) must not
    // inherit a stale open-sheet value from the previous session.
    val queue by player.queue.collectAsStateWithLifecycle()
    var hadEmptyQueue by remember { mutableStateOf(queue.isEmpty()) }

    // ---- Queue sheet --------------------------------------------------------
    // A single SheetState owns the position AND whether the sheet is open, so
    // the touch-blocking scrim is composed only while the sheet is genuinely up.
    // See SheetState for the freeze this replaced.
    val sheet = rememberSheetState()
    // Rotation / inset changes must never leave the sheet parked half-open.
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration) { sheet.settle(0f) }
    // Queue emptied under the sheet: close through the one close path.
    LaunchedEffect(queue.isEmpty()) {
        if (queue.isEmpty()) {
            hadEmptyQueue = true
            sheet.close()
        } else if (hadEmptyQueue) {
            hadEmptyQueue = false
            sheet.close()
            showPicker = false
        }
    }

    val isClosingOrDragging = nowPlayingSheet?.closingOrDragging == true
    var fallbackDragPx by remember { mutableFloatStateOf(0f) }
    val currentLyricsMode by rememberUpdatedState(lyricsMode)
    val currentLyricsUi by rememberUpdatedState(lyricsUi)

    val nowPlayingNestedScroll = remember(nowPlayingSheet) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var moved = false

            fun isLyricsAtTop(): Boolean {
                if (!currentLyricsMode) return true
                return when (currentLyricsUi) {
                    is LyricsUi.Synced ->
                        lyricsListState.firstVisibleItemIndex == 0 &&
                            lyricsListState.firstVisibleItemScrollOffset == 0
                    is LyricsUi.Plain ->
                        plainLyricsScrollState.value == 0
                    else -> true
                }
            }

            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                val npSheet = nowPlayingSheet ?: return androidx.compose.ui.geometry.Offset.Zero
                if (sheet.open) return androidx.compose.ui.geometry.Offset.Zero
                when (val handoff = com.feishin.mobile.core.common.QueueHandoff.decide(
                    deltaY = available.y,
                    sheetHeightPx = npSheet.sheetHeightPx,
                    userInput = source == NestedScrollSource.UserInput,
                    listAtTop = isLyricsAtTop(),
                    sheetDragging = npSheet.dragging,
                    sheetAtOpen = npSheet.progress >= 1f,
                )) {
                    is com.feishin.mobile.core.common.ListHandoff.PassToList -> {
                        if (npSheet.closingOrDragging) {
                            return androidx.compose.ui.geometry.Offset(0f, available.y)
                        }
                        return androidx.compose.ui.geometry.Offset.Zero
                    }
                    is com.feishin.mobile.core.common.ListHandoff.TakeBySheet -> {
                        npSheet.dragBy(handoff.deltaProgress)
                        moved = true
                    }
                }
                return androidx.compose.ui.geometry.Offset(0f, available.y)
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                val npSheet = nowPlayingSheet ?: return androidx.compose.ui.geometry.Offset.Zero
                if (sheet.open || source != NestedScrollSource.UserInput) {
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                val handoff = com.feishin.mobile.core.common.QueueHandoff.decide(
                    deltaY = available.y,
                    sheetHeightPx = npSheet.sheetHeightPx,
                    userInput = true,
                    listAtTop = isLyricsAtTop(),
                    sheetDragging = npSheet.dragging,
                    sheetAtOpen = npSheet.progress >= 1f,
                )
                if (handoff is com.feishin.mobile.core.common.ListHandoff.TakeBySheet) {
                    npSheet.dragBy(handoff.deltaProgress)
                    moved = true
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                val npSheet = nowPlayingSheet ?: return androidx.compose.ui.unit.Velocity.Zero
                if (moved) {
                    moved = false
                    npSheet.onDragEnd(available.y)
                    return available
                }
                if (npSheet.closingOrDragging) {
                    return available
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                val npSheet = nowPlayingSheet ?: return androidx.compose.ui.unit.Velocity.Zero
                if (moved) {
                    moved = false
                    npSheet.settle(0f)
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    // Outer root container at stationary translationY = 0f (never translates vertically).
    // Holds the stationary full-screen fading backdrop and the translating foreground UI layer,
    // and captures downward close drags in a stationary coordinate system.
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                if (it.height > 0 && nowPlayingSheet != null) {
                    nowPlayingSheet.sheetHeightPx = it.height.toFloat()
                }
            }
            .pointerInput(Unit) { detectTapGestures { /* consume */ } }
            .nestedScroll(nowPlayingNestedScroll)
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !sheet.open,
                state = rememberDraggableState { delta ->
                    val npSheet = nowPlayingSheet
                    if (npSheet != null) {
                        if (delta > 0f || npSheet.dragging || npSheet.progress < 1f) {
                            npSheet.dragByPixels(delta)
                        }
                    } else if (delta > 0f || fallbackDragPx > 0f) {
                        fallbackDragPx = (fallbackDragPx + delta).coerceAtLeast(0f)
                    }
                },
                onDragStopped = { velocity ->
                    val npSheet = nowPlayingSheet
                    if (npSheet != null) {
                        if (npSheet.dragging || npSheet.progress < 1f) {
                            npSheet.onDragEnd(velocity)
                        }
                    } else {
                        if (fallbackDragPx > 260f || velocity > 800f) onClose()
                        fallbackDragPx = 0f
                    }
                },
            ),
    ) {
        // Stationary full-screen background layer: covers 100% of the screen at translationY = 0f
        // and fades smoothly (0f -> 1f on expand, 1f -> 0f on collapse).
        val playerSurfaceColor = if (dynamicColor) {
            playingColor(palette.backdropBottom)
        } else {
            MaterialTheme.colorScheme.surface
        }
        val playerIsDark = scheme?.dark ?: LocalVusicDark.current
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = (nowPlayingSheet?.progress ?: 1f).coerceIn(0f, 1f)
                    translationY = 0f
                    alpha = (p / 0.65f).coerceIn(0f, 1f)
                }
                .background(playerSurfaceColor.copy(alpha = 1f)),
        ) {
            Crossfade(
                targetState = bgStyle,
                animationSpec = tween(durationMillis = Motion.scaled(Motion.Normal)),
                label = "np-bg-style",
            ) { style ->
                when (style) {
                    BackgroundStyle.DEFAULT -> Box(
                        Modifier
                            .fillMaxSize()
                            .background(playerSurfaceColor),
                    )
                    BackgroundStyle.GRADIENT -> Box(
                        Modifier
                            .fillMaxSize()
                            .background(backdrop),
                    )
                    BackgroundStyle.BLUR -> BlurredArtworkBackground(
                        coverId = artUrl ?: thumbUrl,
                        bitmap = null,
                        scrimColor = playerSurfaceColor,
                        isDark = playerIsDark,
                        modifier = Modifier.fillMaxSize(),
                        role = BlurSurfaceRole.PLAYER,
                    )
                }
            }
        }

        // Foreground UI content layer: translates vertically across the full screen height
        // and fades smoothly with the sheet progress.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = (nowPlayingSheet?.progress ?: 1f).coerceIn(0f, 1f)
                    val h = if (size.height > 0f) size.height else (nowPlayingSheet?.sheetHeightPx ?: 0f)
                    translationY = if (nowPlayingSheet != null) (1f - p) * h else fallbackDragPx
                    alpha = (p / 0.65f).coerceIn(0f, 1f)
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    // The bottom strip paints under the gesture bar and applies the
                    // navigation-bar inset itself, which is why this column only pads status bars.
                    .statusBarsPadding(),
            ) {
                // Top row: collapse far left, Song|Lyrics pill centred, ⋮ far right.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = VusicSpacing.ScreenHorizontal, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Close", tint = content)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        SongLyricsToggle(
                            lyricsMode = lyricsMode,
                            userToggledTab = userToggledTab,
                            onSelect = {
                                if (it != lyricsMode) userToggledTab = true
                                scope.launch { lyricsVm.settings.setNowPlayingLyricsMode(it) }
                            },
                            palette = palette,
                        )
                    }
                    var showLyricsMenu by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            if (lyricsMode) {
                                showLyricsMenu = true
                            } else {
                                onActions(ActionTarget.Track(current))
                            }
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.MoreVert, "Options", tint = content)
                    }
                    if (showLyricsMenu && lyricsSettings != null) {
                        LyricsMenuSheet(
                            track = current,
                            lyricsUi = lyricsUi,
                            lyricsVm = lyricsVm,
                            settings = lyricsSettings!!,
                            onDismiss = { showLyricsMenu = false },
                            onShowMessage = { messenger.show(it) },
                            onTrackDetails = {
                                showLyricsMenu = false
                                onActions(ActionTarget.Track(current))
                            },
                        )
                    }
                }
                // Song <-> Lyrics is a transition, not a swap: the two panels
                // cross-fade with a short horizontal travel while the selection pill
                // slides (see SongLyricsToggle). Playback, the progress bar and the
                // queue are untouched - they live in the player, not in here.
                AnimatedContent(
                    targetState = lyricsMode,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        if (!userToggledTab) {
                            androidx.compose.animation.EnterTransition.None togetherWith
                                androidx.compose.animation.ExitTransition.None
                        } else {
                            // targetState == true is heading to Lyrics: the incoming
                            // panel comes from the side it is travelling towards and the
                            // outgoing one leaves the opposite way. Motion.Slow is
                            // 300 ms and collapses to 0 when the system animation scale
                            // is off (the travel is zeroed too, so nothing jumps).
                            val forward = targetState
                            val travel = { full: Int -> if (Motion.durationScale == 0f) 0 else full / 5 }
                            (slideInHorizontally(animationSpec = Motion.slide()) { w ->
                                if (forward) travel(w) else -travel(w)
                            } + fadeIn(animationSpec = Motion.fade(Motion.Slow))) togetherWith
                                (slideOutHorizontally(animationSpec = Motion.slide()) { w ->
                                    if (forward) -travel(w) else travel(w)
                                } + fadeOut(animationSpec = Motion.fade(Motion.Slow)))
                        }
                    },
                    label = "np-tab-content",
                ) { showLyrics ->
                if (!showLyrics) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Nearly full-width art (16dp gutters) that still leaves room
                    // for everything below it on short screens: capped by height.
                    // Landscape/short screens get a compact budget instead.
                    val compact = maxHeight < 600.dp
                    val artSide = if (compact) {
                        minOf(maxWidth * 0.5f, maxHeight * 0.30f)
                    } else {
                        minOf(maxWidth - 32.dp, maxHeight * 0.42f)
                    }
                    val gapTop = if (compact) 12.dp else 24.dp
                    val gapArtTitle = if (compact) 8.dp else 12.dp
                    val gapArtistChips = if (compact) 8.dp else 18.dp
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(gapTop))
                        CoverImage(
                            artUrl, current.title,
                            modifier = Modifier.size(artSide),
                            roundedDp = 16.dp,
                            thumbnailUrl = thumbUrl,
                        )
                        Spacer(Modifier.height(gapArtTitle))
                        // Title row: balanced 48dp slots on both sides keep the text
                        // visually centred; short titles are centred, long ones marquee.
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.size(48.dp))
                            MarqueeTitle(
                                text = current.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = content,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                if (current.albumId?.isNotBlank() == true) {
                                    IconButton(onClick = { current.albumId?.let(onAlbum) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.ChevronRight, "Go to album", tint = dim)
                                    }
                                }
                            }
                        }
                        // Artist: centred when short, marquee with a clean ellipsis
                        // when long (multi-artist "A / B / C" included).
                        MarqueeTitle(
                            text = current.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = dim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = current.artistId?.isNotBlank() == true) {
                                    current.artistId?.let { onArtist(it) }
                                },
                        )
                        Spacer(Modifier.height(gapArtistChips))
                        // Optimistic favorite toggle with rollback; guarded so fast
                        // taps can't interleave requests. Converges to the server
                        // collection once loaded (the queue's snapshot can be stale).
                        var starred by remember(current.id) { mutableStateOf(current.starred) }
                        var starring by remember(current.id) { mutableStateOf(false) }
                        val downloadState by downloads.observeSong(current.id).collectAsStateWithLifecycle(initialValue = null)
                        val favTracks by favs.tracks.collectAsStateWithLifecycle()
                        val favsLoaded by favs.loaded.collectAsStateWithLifecycle()
                        LaunchedEffect(current.id, favTracks, favsLoaded) {
                            if (favsLoaded && !starring) {
                                starred = favTracks.any { it.id == current.id }
                            }
                        }
                        LaunchedEffect(current.id) {
                            scope.launch { favs.refresh() }
                        }
                        val capabilities = remember(current.id) { repo.capabilitiesFor(current.id) }
                        NpChipsRow(
                            content = content,
                            starred = starred,
                            local = current.isLocal,
                            capabilities = capabilities,
                            downloadState = downloadState,
                            onFavorite = {
                                if (current.isLocal || capabilities == BackendCapabilities.DEVICE_LOCAL || starring) return@NpChipsRow
                                val next = !starred
                                starred = next
                                starring = true
                                scope.launch {
                                    when (favs.setTrackStarred(current.id, next)) {
                                        is Result.Ok -> Unit
                                        is Result.Err -> {
                                            starred = !next
                                            messenger.show("Could not update favorite")
                                        }
                                    }
                                    starring = false
                                }
                            },
                            onSave = { showPicker = true },
                            onShare = { shareText(context, "${current.title} - ${current.artist}") },
                            onDownload = {
                                scope.launch {
                                    when (downloadState?.statusEnum) {
                                        com.feishin.mobile.data.db.DownloadStatus.COMPLETED -> {
                                            downloads.removeFromLibrary(current.id)
                                            messenger.show("Download removed")
                                        }
                                        com.feishin.mobile.data.db.DownloadStatus.DOWNLOADING,
                                        com.feishin.mobile.data.db.DownloadStatus.QUEUED,
                                        -> {
                                            downloads.pause(current.id)
                                            messenger.show("Download cancelled")
                                        }
                                        com.feishin.mobile.data.db.DownloadStatus.PAUSED,
                                        com.feishin.mobile.data.db.DownloadStatus.FAILED,
                                        -> {
                                            downloads.resume(current.id)
                                            messenger.show("Download resumed")
                                        }
                                        null -> {
                                            downloads.enqueueTracks(listOf(current))
                                            messenger.show("Downloading: ${current.title}")
                                        }
                                    }
                                }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        SeekBar(player, content)
                        Spacer(Modifier.height(4.dp))
                        ControlsRow(player, content)
                        // The queue entry sits near the bottom edge, above the
                        // gesture bar: tappable and swipe-up draggable.
                        Spacer(Modifier.weight(1f))
                        Spacer(Modifier.height(8.dp))
                    }
                }
                } else {
                    val isTabActive = isLyricsTabReadyToScroll(showLyrics, lyricsMode)
                    val isLeaving = !lyricsMode || isClosingOrDragging
                    LyricsPanel(
                        lyricsVm = lyricsVm,
                        player = player,
                        content = content,
                        dim = dim,
                        lazyListState = lyricsListState,
                        plainScrollState = plainLyricsScrollState,
                        onSearchAgain = { lyricsVm.searchAgain(current) },
                        settings = lyricsSettings,
                        isTabActive = isTabActive,
                        isLeaving = isLeaving,
                        lastScrolledIndex = effectiveLastScrolledIndex,
                        onUpdateLastScrolledIndex = updateLastScrolledIndex,
                        lastMeasuredViewportPx = lastMeasuredLyricsViewportPx,
                        onUpdateLastMeasuredViewportPx = onUpdateLastMeasuredLyricsViewportPx ?: {},
                        modifier = Modifier.fillMaxSize().lyricsEdgeFade(),
                    )
                }
                }
                // Reserve bottom clearance for the 72.dp peek strip + 8.dp bottom
                // spacer + navigation-bar inset; the single unified QueueSheet
                // container draws the peek strip and handle in this space.
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        .height(80.dp),
                )
            }

            // Unified single bottom sheet container: at progress = 0f it exposes the
            // 72.dp peek strip with the single 36.dp x 4.dp stroke indicator pill and
            // "Playing from <source>"; as progress moves 0f -> 1f that exact same pill
            // travels upward with the sheet's top edge while the Queue header, list and
            // bottom bar fade in directly below it.
            if (queue.isNotEmpty()) {
                QueueSheet(
                    sheet = sheet,
                    nowPlayingSheet = nowPlayingSheet,
                    player = player, repo = repo, messenger = messenger,
                    palette = palette, playlistEvents = playlistEvents,
                    dynamicColor = dynamicColor,
                    playerBgStyle = bgStyle,
                    playerSurfaceColor = playerSurfaceColor,
                    queueBgStyle = queueBgStyle,
                    currentCoverUrl = artUrl ?: thumbUrl,
                    onActions = { onActions(ActionTarget.Track(it)) },
                    onDismiss = { sheet.close() },
                )
            }
        }
    }
    // One close path for back press, tap-outside and the chevron.
    androidx.activity.compose.BackHandler(enabled = sheet.open) {
        sheet.close()
    }
    if (showPicker) {
        PlaylistPickerSheet(
            tracks = listOf(current), title = current.title,
            onDismiss = { showPicker = false }, repo = repo, messenger = messenger,
            playlistEvents = playlistEvents,
        )
    }
}

@Composable
private fun SongLyricsToggle(
    lyricsMode: Boolean,
    onSelect: (Boolean) -> Unit,
    palette: NowPlayingPalette,
    userToggledTab: Boolean = true,
) {
    // One accent pair for the whole screen: the "on" segment and its ink come
    // from the resolved palette, so the selected pill can never disagree with
    // the tinted backdrop behind it.
    val dim = playingColor(palette.dim)
    val accent = playingColor(palette.accent)
    val onAccent = playingColor(palette.onAccent)

    // The selection is ONE pill that slides between the two labels, instead of
    // each label swapping its own background, so switching tabs reads as the
    // selection travelling rather than the fill blinking. The segment widths
    // are measured from the real text, so the two labels keep their natural
    // sizes. The container background animates for the same reason.
    val density = LocalDensity.current
    var songPx by remember { mutableIntStateOf(0) }
    var lyricsPx by remember { mutableIntStateOf(0) }
    var innerHeightPx by remember { mutableIntStateOf(0) }
    val slide by animateFloatAsState(
        targetValue = if (lyricsMode) 1f else 0f,
        animationSpec = if (userToggledTab) Motion.value(Motion.Slow) else snap(),
        label = "np-tab-slide",
    )
    val container by animateColorAsState(
        targetValue = dim.copy(alpha = 0.18f),
        animationSpec = Motion.color(),
        label = "np-tab-container",
    )
    val padPx = with(density) { 3.dp.toPx() }
    val pillX = songPx * slide
    val pillW = songPx + (lyricsPx - songPx) * slide
    val pillH = (innerHeightPx - padPx * 2f).coerceAtLeast(0f)
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .onSizeChanged { innerHeightPx = it.height },
    ) {
        // Drawn only once both labels have been measured: a zero-width pill on
        // the first frame would flash against the left edge.
        if (songPx > 0 && lyricsPx > 0 && pillH > 0f) {
            Box(
                Modifier
                    .offset { IntOffset((pillX + padPx).roundToInt(), padPx.roundToInt()) }
                    .size(
                        width = with(density) { pillW.toDp() },
                        height = with(density) { pillH.toDp() },
                    )
                    .clip(RoundedCornerShape(17.dp))
                    .background(accent),
            )
        }
        Row(
            Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Song" to false, "Lyrics" to true).forEach { (label, value) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (lyricsMode == value) onAccent else dim,
                    modifier = Modifier
                        .onSizeChanged { if (value) lyricsPx = it.width else songPx = it.width }
                        .clip(RoundedCornerShape(17.dp))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
    }
}

internal data class NowPlayingChipsUi(
    val showFavorite: Boolean,
    val showSaveToPlaylist: Boolean,
    val showShare: Boolean,
    val showDownload: Boolean,
)

internal fun resolveNowPlayingChips(
    local: Boolean,
    capabilities: BackendCapabilities,
): NowPlayingChipsUi {
    val isRemote = !local && capabilities != BackendCapabilities.DEVICE_LOCAL
    return NowPlayingChipsUi(
        showFavorite = isRemote,
        showSaveToPlaylist = isRemote && capabilities.supportsServerPlaylists,
        showShare = true,
        showDownload = isRemote,
    )
}

@Composable
private fun NpChipsRow(
    content: Color,
    starred: Boolean,
    local: Boolean,
    capabilities: BackendCapabilities,
    downloadState: com.feishin.mobile.data.db.DownloadEntity?,
    onFavorite: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
) {
    val chipsUi = resolveNowPlayingChips(local = local, capabilities = capabilities)
    val dlStatus = downloadState?.statusEnum
    val downloadLabel = when (dlStatus) {
        com.feishin.mobile.data.db.DownloadStatus.COMPLETED -> "Downloaded"
        com.feishin.mobile.data.db.DownloadStatus.DOWNLOADING,
        com.feishin.mobile.data.db.DownloadStatus.QUEUED,
        -> "${((downloadState?.progress ?: 0f) * 100).toInt()}%"
        com.feishin.mobile.data.db.DownloadStatus.PAUSED -> "Paused"
        com.feishin.mobile.data.db.DownloadStatus.FAILED -> "Retry"
        null -> "Download"
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (chipsUi.showFavorite) {
            NpChip(
                label = if (starred) "Favorited" else "Favorite",
                icon = if (starred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                content = content,
                accent = if (starred) FavoriteRed else null,
                onClick = onFavorite,
            )
        }
        if (chipsUi.showSaveToPlaylist) {
            NpChip("Save", Icons.Default.Save, content, onSave)
        }
        if (chipsUi.showShare) {
            NpChip("Share", Icons.Default.Share, content, onShare)
        }
        if (chipsUi.showDownload) {
            NpChip(
                label = downloadLabel,
                icon = if (dlStatus == com.feishin.mobile.data.db.DownloadStatus.COMPLETED) {
                    Icons.Default.DownloadDone
                } else {
                    Icons.Default.Download
                },
                content = content,
                onClick = onDownload,
            )
        }
    }
}

private val FavoriteRed = Color(0xFFFF6B6B)

@Composable
private fun NpChip(
    label: String,
    icon: ImageVector,
    content: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Color? = null,
) {
    val tint = accent ?: content
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(content.copy(alpha = if (enabled) 0.14f else 0.07f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = tint.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (enabled) label else "$label • soon",
            style = MaterialTheme.typography.labelLarge,
            color = tint.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/** How far the queue sheet travels when fully open, in pixels. */
private fun ArtistInfoPlaceholder() = Unit

@Composable
private fun SeekBar(player: PlayerManager, content: Color) {
    val pos by player.position.collectAsStateWithLifecycle()
    val dur by player.duration.collectAsStateWithLifecycle()
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var dragging by remember { mutableStateOf(false) }

    val known = !PlaybackProgress.isUnknown(dur)
    val progress = PlaybackProgress.progressWithScrub(pos, dur, scrubMs)

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(known, dur) {
                    if (!known || dur <= 0L) return@pointerInput
                    fun msAt(x: Float): Long {
                        val w = size.width
                        if (w <= 0) return 0L
                        return ((x / w).coerceIn(0f, 1f) * dur).toLong().coerceIn(0L, dur)
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { off -> dragging = true; scrubMs = msAt(off.x) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            scrubMs = msAt(change.position.x)
                        },
                        onDragEnd = {
                            scrubMs?.let { player.seekTo(it) }
                            scrubMs = null
                            dragging = false
                        },
                        onDragCancel = { scrubMs = null; dragging = false },
                    )
                }
                .pointerInput(known, dur) {
                    if (!known || dur <= 0L) return@pointerInput
                    detectTapGestures { off ->
                        val w = size.width
                        if (w > 0) {
                            player.seekTo(((off.x / w).coerceIn(0f, 1f) * dur).toLong().coerceIn(0L, dur))
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // 4dp track with the filled portion exactly matching progress.
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(content.copy(alpha = 0.28f)))
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(content),
            )
            // Circular thumb pinned to the end of the filled part; grows on drag.
            val thumb = if (dragging) 18.dp else 12.dp
            Box(
                Modifier
                    .offset(x = (maxWidth - thumb) * progress.coerceIn(0f, 1f))
                    .size(thumb)
                    .clip(CircleShape)
                    .background(content),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                PlaybackProgress.formatElapsed(scrubMs ?: pos),
                color = dim75(content), style = MaterialTheme.typography.bodySmall,
            )
            Text(
                PlaybackProgress.formatRemaining(pos, dur, scrubMs),
                color = dim75(content), style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun dim75(color: Color) = color.copy(alpha = 0.85f)

@Composable
private fun ControlsRow(player: PlayerManager, content: Color) {
    val playing by player.isPlaying.collectAsStateWithLifecycle()
    val shuffle by player.shuffleEnabled.collectAsStateWithLifecycle()
    val repeat by player.repeatMode.collectAsStateWithLifecycle()
    val active = content
    val idle = content.copy(alpha = 0.55f)

    // The favorite toggle lives in the chip row now and the queue opens from
    // the bottom area, so this row is transport only.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { player.toggleShuffle() }) {
            Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffle) active else idle)
        }
        IconButton(onClick = { player.skipToPrevious() }) {
            Icon(Icons.Default.SkipPrevious, "Previous", tint = content, modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { player.togglePlayPause() }, modifier = Modifier.size(76.dp)) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(
                    // Accent pair, not a white circle: on the Light theme's pale
                    // backdrop a white button nearly vanishes.
                    MaterialTheme.colorScheme.primary,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play",
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp),
                )
            }
        }
        IconButton(onClick = { player.skipToNext() }) {
            Icon(Icons.Default.SkipNext, "Next", tint = content, modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { player.cycleRepeat() }) {
            val mode = repeat
            Icon(
                if (mode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                "Repeat",
                tint = if (mode == Player.REPEAT_MODE_OFF) idle else active,
            )
        }
    }
}

@Composable
private fun SourceFooter(
    source: com.feishin.mobile.core.network.LyricsSource,
    dim: Color,
    modifier: Modifier = Modifier,
) {
    val text = when (source) {
        com.feishin.mobile.core.network.LyricsSource.LRCLIB -> "Lyrics from LRCLIB"
        com.feishin.mobile.core.network.LyricsSource.BETTER_LYRICS -> "Lyrics from BetterLyrics"
        com.feishin.mobile.core.network.LyricsSource.PAXSENIX -> "Lyrics from Paxsenix"
        com.feishin.mobile.core.network.LyricsSource.KUGOU -> "Lyrics from KuGou"
        com.feishin.mobile.core.network.LyricsSource.SERVER_METADATA -> "Lyrics from server"
        com.feishin.mobile.core.network.LyricsSource.FILE_TAGS -> "Lyrics from file tags"
        com.feishin.mobile.core.network.LyricsSource.CACHE -> "Lyrics cached"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = dim,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    )
}

@Composable
private fun IntroCountdownDots(
    firstStartMs: Long,
    currentPosMs: Long,
    content: Color,
    dim: Color,
) {
    val progress = (currentPosMs.toFloat() / firstStartMs.toFloat()).coerceIn(0f, 1f)
    val activeDot = (progress * 3).toInt().coerceIn(0, 3)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        for (dot in 0..2) {
            val isActive = dot <= activeDot
            val dotColor by animateColorAsState(
                targetValue = if (isActive) content else dim,
                animationSpec = androidx.compose.animation.core.tween(300),
                label = "dot-$dot",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}

/** Height of the lyrics edge fade at the top and bottom of the panel. */
private val EdgeFadeHeight: Dp = 56.dp

/**
 * Fades the top and bottom [EdgeFadeHeight] of the lyrics panel to alpha 0
 * with [BlendMode.DstIn]. The mask needs its own offscreen layer, otherwise
 * DstIn would erase what is already on screen behind the panel. The queue
 * strip sits outside this container, so it is never faded.
 */
private fun Modifier.lyricsEdgeFade(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val height = size.height
        if (height <= 0f) return@drawWithContent
        val fade = EdgeFadeHeight.toPx().coerceIn(0f, height / 2f)
        if (fade <= 0f) return@drawWithContent
        val stop = fade / height
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                stop to Color.Black,
                1f - stop to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LyricsPanel(
    lyricsVm: LyricsViewModel,
    player: PlayerManager,
    content: Color,
    dim: Color,
    lazyListState: LazyListState,
    plainScrollState: ScrollState,
    onSearchAgain: () -> Unit,
    modifier: Modifier = Modifier,
    settings: com.feishin.mobile.data.settings.SettingsStore.Settings? = null,
    isTabActive: Boolean = true,
    isLeaving: Boolean = false,
    lastScrolledIndex: Int = -1,
    onUpdateLastScrolledIndex: (Int) -> Unit = {},
    lastMeasuredViewportPx: Int = 0,
    onUpdateLastMeasuredViewportPx: (Int) -> Unit = {},
) {
    val ui by lyricsVm.ui.collectAsStateWithLifecycle()
    val s = settings

    val ctx = LocalContext.current
    DisposableEffect(s?.lyricsKeepScreenOn == true) {
        val window = (ctx as? android.app.Activity)?.window
        if (s?.lyricsKeepScreenOn == true) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(modifier.fillMaxWidth()) {
        when (val state = ui) {
            is LyricsUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = content, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
            }
            is LyricsUi.None -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("No lyrics for this song", color = dim, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onSearchAgain) {
                        Text("Search again")
                    }
                }
            }
            is LyricsUi.Instrumental -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("♪", color = content, style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Instrumental", color = dim, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    SourceFooter(state.source, dim)
                }
            }
            is LyricsUi.Plain -> {
                if (s?.lyricsShowUnsynced == false) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text("No lyrics for this song", color = dim, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = onSearchAgain) {
                                Text("Search again")
                            }
                        }
                    }
                } else {
                    val alignment = when (s?.lyricsAlignment) {
                        "LEFT" -> TextAlign.Start
                        "RIGHT" -> TextAlign.End
                        else -> TextAlign.Center
                    }
                    val pct = com.feishin.mobile.data.settings.SettingsStore.parseLyricsTextSizePercent(s?.lyricsTextSize ?: "100%")
                    val scale = pct / 100f
                    val textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = (16f * scale).sp,
                        lineHeight = (22f * scale).sp,
                    )
                    Column(
                        Modifier
                            .fillMaxSize()
                            .overscroll(com.feishin.mobile.core.common.PassThroughOverscroll)
                            .verticalScroll(plainScrollState)
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                    ) {
                        Text(
                            "Lyrics aren't synced",
                            color = dim,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        val plainLines = remember(state.text) { state.text.lines() }
                        plainLines.forEach { pLine ->
                            if (pLine.isBlank()) {
                                Spacer(Modifier.height(12.dp))
                            } else {
                                Text(
                                    pLine,
                                    color = content,
                                    style = textStyle,
                                    textAlign = alignment,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (s?.lyricsRomanize == true && com.feishin.mobile.data.lyrics.Romanizer.hasRomanizableScript(pLine)) {
                                    val rom = remember(pLine) { runCatching { com.feishin.mobile.data.lyrics.Romanizer.romanizeLine(pLine) }.getOrNull() }
                                    if (!rom.isNullOrBlank()) {
                                        Text(
                                            rom,
                                            color = dim.copy(alpha = 0.7f),
                                            style = textStyle.copy(
                                                fontSize = (16f * scale * 0.75f).sp,
                                                lineHeight = (22f * scale * 0.75f).sp,
                                            ),
                                            textAlign = alignment,
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        SourceFooter(state.source, dim)
                        Spacer(Modifier.height(96.dp))
                    }
                }
            }
            is LyricsUi.Synced -> SyncedLyricsList(
                lyrics = state.lyrics,
                player = player,
                content = content,
                dim = dim,
                settings = s,
                listState = lazyListState,
                isTabActive = isTabActive,
                isLeaving = isLeaving,
                lastScrolledIndex = lastScrolledIndex,
                onUpdateLastScrolledIndex = onUpdateLastScrolledIndex,
                lastMeasuredViewportPx = lastMeasuredViewportPx,
                onUpdateLastMeasuredViewportPx = onUpdateLastMeasuredViewportPx,
                onSeek = { ms -> if (s?.lyricsTapToSeek != false) player.seekTo(ms) },
            )
        }
    }
}

val LyricsDisplayTokens.QueueStripPadding: Dp get() = 88.dp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SyncedLyricsList(
    lyrics: com.feishin.mobile.core.network.Lyrics,
    player: PlayerManager,
    content: Color,
    dim: Color,
    settings: com.feishin.mobile.data.settings.SettingsStore.Settings?,
    listState: LazyListState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isTabActive: Boolean = true,
    isLeaving: Boolean = false,
    lastScrolledIndex: Int = -1,
    onUpdateLastScrolledIndex: (Int) -> Unit = {},
    lastMeasuredViewportPx: Int = 0,
    onUpdateLastMeasuredViewportPx: (Int) -> Unit = {},
) {
    val alignment = when (settings?.lyricsAlignment) {
        "LEFT" -> TextAlign.Start
        "RIGHT" -> TextAlign.End
        else -> TextAlign.Center
    }

    val pct = com.feishin.mobile.data.settings.SettingsStore.parseLyricsTextSizePercent(settings?.lyricsTextSize ?: "100%")
    val scale = pct / 100f
    val baseFontSize = 18f * scale
    val baseLineHeight = 24f * scale
    val lyricStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = baseFontSize.sp,
        lineHeight = baseLineHeight.sp,
        fontWeight = FontWeight.Bold,
    )

    val romanize = settings?.lyricsRomanize == true
    val autoScroll = settings?.lyricsAutoScroll != false
    val userOffsetMs = settings?.lyricsSyncOffsetMs ?: 0

    val density = LocalDensity.current
    val contentPaddingTopPx = with(density) { 32.dp.roundToPx() }
    val contentPaddingBottomPx = with(density) { 96.dp.roundToPx() }

    val pos by player.position.collectAsStateWithLifecycle()
    val isVisibleAndSettled = isTabActive && !isLeaving
    val livePos = remember(pos, lyrics, isTabActive, isLeaving) { player.livePositionMs() }
    val effectivePosition = livePos + userOffsetMs
    val activeIndex = remember(lyrics, effectivePosition) { lyrics.lineIndexAt(effectivePosition) }

    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var userScrolledAway by remember { mutableStateOf(false) }

    LaunchedEffect(isDragged, isLeaving) {
        if (isLeaving) {
            userScrolledAway = false
        } else if (isDragged) {
            userScrolledAway = true
        } else if (userScrolledAway) {
            delay(3500)
            userScrolledAway = false
        }
    }

    val currentLayoutSignature = remember(settings) {
        settings?.let {
            LyricsLayoutSignature(
                textSize = it.lyricsTextSize,
                lineGapPercent = it.lyricsLineGapPercent,
                romanize = it.lyricsRomanize,
            )
        }
    }

    val needsInitialSnap = autoScroll && activeIndex >= 0 && lyrics.lines.isNotEmpty()
    var isFirstEntry by remember(lyrics) { mutableStateOf(needsInitialSnap) }
    var wasVisibleAndSettled by remember { mutableStateOf(isVisibleAndSettled) }
    var lastSnappedViewportHeight by remember(lyrics) { mutableIntStateOf(0) }
    var lastSnappedSignature by remember(lyrics) { mutableStateOf<LyricsLayoutSignature?>(null) }

    // Synchronously derive entry/leave transitions during composition so neither
    // animateFloatAsState nor LaunchedEffect ever sees a 1-frame stale isFirstEntry.
    if (isVisibleAndSettled && !wasVisibleAndSettled) {
        wasVisibleAndSettled = true
        if (autoScroll && lyrics.lines.isNotEmpty()) {
            isFirstEntry = true
        }
        onUpdateLastScrolledIndex(-1)
    } else if (!isVisibleAndSettled && wasVisibleAndSettled) {
        wasVisibleAndSettled = false
        onUpdateLastScrolledIndex(-1)
    }

    val measuredViewportNow = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset)
        .takeIf { it > 0 } ?: lastMeasuredViewportPx.coerceAtLeast(0)

    // Synchronous pre-frame-1 snap when a real measured viewport (live or hoisted from last open)
    // and settings are already known. Never uses a screen-height estimate.
    val preSnappedThisFrame = remember(lyrics, isVisibleAndSettled, currentLayoutSignature, activeIndex >= 0) {
        if (isVisibleAndSettled && autoScroll && activeIndex >= 0 && lyrics.lines.isNotEmpty() && settings != null && measuredViewportNow > 0) {
            val initialOffset = computeLyricsScrollOffset(measuredViewportNow, contentPaddingTopPx, 0.35f)
            listState.requestScrollToItem(activeIndex, initialOffset)
            LyricsDebugProbe.logIfEnabled(
                ev = LyricsDebugProbe.EV_PRE_SNAP,
                posMs = livePos,
                userOffMs = userOffsetMs,
                songOffMs = lyrics.offsetMs,
                expIdx = activeIndex,
                renIdx = activeIndex,
                lastIdx = lastScrolledIndex,
                listState = listState,
                vpH = measuredViewportNow,
                padTop = contentPaddingTopPx,
                padBot = contentPaddingBottomPx,
                anchorY = computeLyricsAnchorPx(measuredViewportNow, 0.35f),
                tabVis = isTabActive,
                leaving = isLeaving,
                firstEntry = isFirstEntry,
                settingsLoaded = true,
                densityScale = density.density,
            )
            true
        } else {
            false
        }
    }

    var isInitialSnapComplete by remember(lyrics) {
        mutableStateOf(!needsInitialSnap || preSnappedThisFrame)
    }
    if (preSnappedThisFrame && !isInitialSnapComplete) {
        isInitialSnapComplete = true
    }

    LaunchedEffect(Unit) {
        LyricsDebugProbe.logIfEnabled(
            ev = LyricsDebugProbe.EV_ENTER_COMP,
            posMs = livePos,
            userOffMs = userOffsetMs,
            songOffMs = lyrics.offsetMs,
            expIdx = activeIndex,
            renIdx = activeIndex,
            lastIdx = lastScrolledIndex,
            listState = listState,
            vpH = measuredViewportNow,
            padTop = contentPaddingTopPx,
            padBot = contentPaddingBottomPx,
            anchorY = computeLyricsAnchorPx(measuredViewportNow, 0.35f),
            tabVis = isTabActive,
            leaving = isLeaving,
            firstEntry = isFirstEntry,
            settingsLoaded = settings != null,
            densityScale = density.density,
        )
        delay(500L)
        isInitialSnapComplete = true
    }

    LaunchedEffect(isLeaving) {
        if (isLeaving) {
            LyricsDebugProbe.logIfEnabled(
                ev = LyricsDebugProbe.EV_LEAVE_START,
                posMs = livePos,
                userOffMs = userOffsetMs,
                songOffMs = lyrics.offsetMs,
                expIdx = activeIndex,
                renIdx = activeIndex,
                lastIdx = lastScrolledIndex,
                listState = listState,
                vpH = measuredViewportNow,
                padTop = contentPaddingTopPx,
                padBot = contentPaddingBottomPx,
                anchorY = computeLyricsAnchorPx(measuredViewportNow, 0.35f),
                tabVis = isTabActive,
                leaving = true,
                firstEntry = isFirstEntry,
                settingsLoaded = settings != null,
                densityScale = density.density,
            )
        }
    }

    LaunchedEffect(
        activeIndex,
        isTabActive,
        isLeaving,
        userScrolledAway,
        autoScroll,
        isFirstEntry,
        currentLayoutSignature,
        measuredViewportNow,
    ) {
        if (!shouldRunLyricsScroll(isTabActive, isLeaving, userScrolledAway) || activeIndex < 0 || !autoScroll) {
            if (!autoScroll || activeIndex < 0) {
                isInitialSnapComplete = true
            }
            return@LaunchedEffect
        }
        // Gate initial snap until real settings are loaded so we never consume isFirstEntry
        // with default font size / 0ms sync offset when real settings are still null.
        if (settings == null) {
            return@LaunchedEffect
        }
        val viewport = listState.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
        val viewportHeight = if (viewport > 0) viewport else {
            withTimeoutOrNull(400L) {
                snapshotFlow { listState.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset } }
                    .filter { it > 0 }
                    .first()
            } ?: listState.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }.takeIf { it > 0 } ?: return@LaunchedEffect
        }
        val viewportChanged = lastSnappedViewportHeight != viewportHeight
        val settingsSignatureChanged = lastSnappedSignature != currentLayoutSignature
        if (viewportHeight > 0 && viewportHeight != lastMeasuredViewportPx) {
            onUpdateLastMeasuredViewportPx(viewportHeight)
        }
        if (viewportChanged) {
            LyricsDebugProbe.logIfEnabled(
                ev = LyricsDebugProbe.EV_VIEWPORT_MEASURED,
                posMs = livePos,
                userOffMs = userOffsetMs,
                songOffMs = lyrics.offsetMs,
                expIdx = activeIndex,
                renIdx = activeIndex,
                lastIdx = lastScrolledIndex,
                listState = listState,
                vpH = viewportHeight,
                padTop = contentPaddingTopPx,
                padBot = contentPaddingBottomPx,
                anchorY = computeLyricsAnchorPx(viewportHeight, 0.35f),
                tabVis = isTabActive,
                leaving = isLeaving,
                firstEntry = isFirstEntry,
                settingsLoaded = true,
                densityScale = density.density,
            )
        }

        val targetScrollOffset = computeLyricsScrollOffset(viewportHeight, contentPaddingTopPx, 0.35f)
        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        val forceSnapFromZero = listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset == 0 &&
            activeIndex > 0
        val forceSnap = shouldForceLyricsSnap(
            isFirstEntry = isFirstEntry,
            forceSnapFromZero = forceSnapFromZero,
            viewportChanged = viewportChanged,
            settingsSignatureChanged = settingsSignatureChanged,
        )
        val action = resolveLyricsScrollStep(forceSnap, activeIndex, lastScrolledIndex, itemInfo != null)

        when (action) {
            ScrollAction.SNAP -> {
                val wasFirstEntry = isFirstEntry
                listState.scrollToItem(activeIndex, targetScrollOffset)
                isFirstEntry = false
                isInitialSnapComplete = true
                lastSnappedViewportHeight = viewportHeight
                lastSnappedSignature = currentLayoutSignature
                onUpdateLastScrolledIndex(activeIndex)
                LyricsDebugProbe.logIfEnabled(
                    ev = LyricsDebugProbe.EV_SCROLL_STEP,
                    posMs = livePos,
                    userOffMs = userOffsetMs,
                    songOffMs = lyrics.offsetMs,
                    expIdx = activeIndex,
                    renIdx = activeIndex,
                    lastIdx = lastScrolledIndex,
                    listState = listState,
                    vpH = viewportHeight,
                    padTop = contentPaddingTopPx,
                    padBot = contentPaddingBottomPx,
                    anchorY = computeLyricsAnchorPx(viewportHeight, 0.35f),
                    tabVis = isTabActive,
                    leaving = isLeaving,
                    firstEntry = false,
                    settingsLoaded = true,
                    densityScale = density.density,
                )
                if (wasFirstEntry || viewportChanged || settingsSignatureChanged) {
                    LyricsDebugProbe.logIfEnabled(
                        ev = LyricsDebugProbe.EV_FRAME_SETTLED,
                        posMs = livePos,
                        userOffMs = userOffsetMs,
                        songOffMs = lyrics.offsetMs,
                        expIdx = activeIndex,
                        renIdx = activeIndex,
                        lastIdx = activeIndex,
                        listState = listState,
                        vpH = viewportHeight,
                        padTop = contentPaddingTopPx,
                        padBot = contentPaddingBottomPx,
                        anchorY = computeLyricsAnchorPx(viewportHeight, 0.35f),
                        tabVis = isTabActive,
                        leaving = isLeaving,
                        firstEntry = false,
                        settingsLoaded = true,
                        densityScale = density.density,
                        includeAnchorErrDp = true,
                    )
                }
            }
            ScrollAction.ANIMATE -> {
                if (itemInfo != null) {
                    val delta = computeLyricsScrollDelta(itemInfo.offset, viewportHeight, 0.35f)
                    if (kotlin.math.abs(delta) > 1f) {
                        listState.animateScrollBy(
                            value = delta,
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = computeScrollDurationMs(activeIndex - lastScrolledIndex),
                                easing = androidx.compose.animation.core.FastOutSlowInEasing,
                            ),
                        )
                    }
                }
                isFirstEntry = false
                isInitialSnapComplete = true
                lastSnappedViewportHeight = viewportHeight
                lastSnappedSignature = currentLayoutSignature
                onUpdateLastScrolledIndex(activeIndex)
                LyricsDebugProbe.logIfEnabled(
                    ev = LyricsDebugProbe.EV_SCROLL_STEP,
                    posMs = livePos,
                    userOffMs = userOffsetMs,
                    songOffMs = lyrics.offsetMs,
                    expIdx = activeIndex,
                    renIdx = activeIndex,
                    lastIdx = lastScrolledIndex,
                    listState = listState,
                    vpH = viewportHeight,
                    padTop = contentPaddingTopPx,
                    padBot = contentPaddingBottomPx,
                    anchorY = computeLyricsAnchorPx(viewportHeight, 0.35f),
                    tabVis = isTabActive,
                    leaving = isLeaving,
                    firstEntry = false,
                    settingsLoaded = true,
                    densityScale = density.density,
                )
            }
            null -> {
                isInitialSnapComplete = true
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overscroll(com.feishin.mobile.core.common.PassThroughOverscroll)
                .graphicsLayer { alpha = if (isInitialSnapComplete) 1f else 0f },
            contentPadding = PaddingValues(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(LyricsDisplayTokens.lineGap(settings?.lyricsLineGapPercent ?: 100)),
        ) {
            val showInterval = settings?.lyricsShowIntervalIndicator != false
            val firstLine = lyrics.lines.firstOrNull()
            if (showInterval && firstLine != null && firstLine.startMs >= 5000L && activeIndex == -1) {
                item(key = "intro_countdown") {
                    IntroCountdownDots(
                        firstStartMs = firstLine.startMs,
                        currentPosMs = effectivePosition,
                        content = content,
                        dim = dim,
                    )
                }
            }

            itemsIndexed(lyrics.lines, key = { i, line -> "$i-${line.startMs}" }) { i, line ->
                val active = i == activeIndex
                val d = if (activeIndex >= 0) kotlin.math.abs(i - activeIndex) else 3
                val animatedAlpha by animateFloatAsState(
                    targetValue = LyricsDisplayTokens.alphaForDistance(d),
                    animationSpec = if (isFirstEntry) snap() else androidx.compose.animation.core.tween(
                        durationMillis = LyricsDisplayTokens.AnimationDurationMs,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing,
                    ),
                    label = "lyrics-alpha",
                )
                val animatedScale by animateFloatAsState(
                    targetValue = if (active) LyricsDisplayTokens.ActiveScale else 1.0f,
                    animationSpec = if (isFirstEntry) snap() else androidx.compose.animation.core.tween(
                        durationMillis = LyricsDisplayTokens.AnimationDurationMs,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing,
                    ),
                    label = "lyrics-scale",
                )
                val displayText = if (line.text.isBlank()) (if (showInterval) "♪" else "") else line.text

                val respectAgent = settings?.lyricsRespectAgentPositioning == true
                val lineAlignment = if (respectAgent && line.agent != null) {
                    when {
                        line.agent.contains("v2", ignoreCase = true) ||
                        line.agent.contains("bg", ignoreCase = true) ||
                        line.agent.contains("back", ignoreCase = true) -> TextAlign.End
                        line.agent.contains("v1", ignoreCase = true) ||
                        line.agent.contains("lead", ignoreCase = true) -> TextAlign.Start
                        else -> alignment
                    }
                } else {
                    alignment
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeek(line.startMs + lyrics.offsetMs) }
                        .graphicsLayer {
                            alpha = animatedAlpha
                            scaleX = animatedScale
                            scaleY = animatedScale
                            transformOrigin = when (lineAlignment) {
                                TextAlign.Start -> TransformOrigin(0f, 0.5f)
                                TextAlign.End -> TransformOrigin(1f, 0.5f)
                                else -> TransformOrigin(0.5f, 0.5f)
                            }
                        },
                    horizontalAlignment = when (lineAlignment) {
                        TextAlign.Start -> Alignment.Start
                        TextAlign.End -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    },
                ) {
                    Text(
                        text = displayText,
                        color = content,
                        style = lyricStyle,
                        textAlign = lineAlignment,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (romanize && com.feishin.mobile.data.lyrics.Romanizer.hasRomanizableScript(line.text) && !line.romanizedText.isNullOrBlank()) {
                        val romAlpha by animateFloatAsState(
                            targetValue = LyricsDisplayTokens.romajiAlpha(LyricsDisplayTokens.alphaForDistance(d), active),
                            animationSpec = if (isFirstEntry) snap() else androidx.compose.animation.core.tween(
                                durationMillis = LyricsDisplayTokens.AnimationDurationMs,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing,
                            ),
                            label = "rom-alpha",
                        )
                        Text(
                            text = line.romanizedText,
                            color = content,
                            style = lyricStyle.copy(
                                fontSize = (baseFontSize * 0.60f).sp,
                                lineHeight = (baseLineHeight * 0.60f).sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            textAlign = lineAlignment,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 3.dp)
                                .graphicsLayer { alpha = romAlpha },
                        )
                    }
                }
            }

            item(key = "footer") {
                SourceFooter(lyrics.source, dim)
                Spacer(Modifier.height(48.dp))
            }
        }

        // Floating "Sync" pill button
        AnimatedVisibility(
            visible = userScrolledAway && activeIndex >= 0 && autoScroll,
            enter = fadeIn(androidx.compose.animation.core.tween(200)) + scaleIn(androidx.compose.animation.core.tween(200)),
            exit = fadeOut(androidx.compose.animation.core.tween(200)) + scaleOut(androidx.compose.animation.core.tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = LyricsDisplayTokens.QueueStripPadding, end = 24.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
                onClick = {
                    userScrolledAway = false
                    coroutineScope.launch {
                        val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                        val targetOffset = computeLyricsScrollOffset(viewportHeight, contentPaddingTopPx, 0.35f)
                        listState.animateScrollToItem(activeIndex, targetOffset)
                    }
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Sync",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Sync",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun StaticEqualizerOverlay(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(Modifier.width(3.dp).height(7.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
        Box(Modifier.width(3.dp).height(13.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
        Box(Modifier.width(3.dp).height(9.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
    }
}

/**
 * Fades the bottom edge of the scrolling queue list over [fadeHeight] right as
 * rows enter the overlaid bottom controls region, using an offscreen `DstIn`
 * alpha mask so the real sheet background (`Default`, `Gradient`, or `Blur`)
 * shows seamlessly behind both the fading list and transparent controls.
 */
private fun Modifier.queueBottomEdgeFade(
    fadeHeight: Dp,
    bottomControlsHeight: Dp,
    navBarBottomPx: Float,
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithCache {
        val stops = computeQueueBottomFadeStops(
            viewportHeightPx = size.height,
            fadeHeightPx = fadeHeight.toPx(),
            bottomControlsHeightPx = bottomControlsHeight.toPx(),
            navBarBottomPx = navBarBottomPx,
        )
        val fadeBrush = stops?.let { (startStop, endStop) ->
            Brush.verticalGradient(
                0f to Color.Black,
                startStop to Color.Black,
                endStop to Color.Transparent,
                1f to Color.Transparent,
            )
        }
        onDrawWithContent {
            drawContent()
            if (fadeBrush != null) {
                drawRect(
                    brush = fadeBrush,
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }

/**
 * The reorderable queue list. onMove mutates ONLY the local [displayOrder]
 * (cheap, per frame); [onCommit] fires once on drop and writes through to the
 * player. Stable keys are the unique per-entry queue ids, so two copies of
 * the same song stay distinct.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QueueList(
    displayOrder: List<com.feishin.mobile.playback.QueueItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    /**
     * Id of the entry the player is on. The highlight follows THIS, never an
     * index: a live reorder changes every row's index, so an index-based
     * highlight stays on the slot (and the song under it changes).
     */
    currentQueueId: Long?,
    isPlaying: Boolean,
    player: PlayerManager,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onMoveLocal: (Int, Int) -> Unit,
    onCommit: () -> Unit,
    onPlayQueueItem: (Long) -> Unit,
    /** Dismiss = remove the entry with THIS queue key, never a slot. */
    onRemove: (Long) -> Unit,
    onActions: (com.feishin.mobile.domain.model.Track) -> Unit,
    palette: NowPlayingPalette,
    queueBgStyle: BackgroundStyle,
    isDark: Boolean,
    bottomContentPaddingDp: Int,
    modifier: Modifier = Modifier,
) {
    val reorderState = rememberReorderableLazyColumnState(listState) { from, to ->
        onMoveLocal(from.index, to.index)
    }
    // The ids in the order they are shown RIGHT NOW - the local, in-drag order.
    // The highlight and the auto-scroll target both read the current entry's
    // identity out of this, so neither depends on the player's index staying in
    // step with a list that is mid-reorder.
    val displayIds = displayOrder.map { it.queueId }
    val currentDisplayIndex = QueueHighlight.indexOf(displayIds, currentQueueId)
    // Auto-scroll to the current item on track changes — but never while a
    // drag is in flight (the old per-frame animateScrollToItem fought the
    // gesture and was half the freeze).
    val anyDragging = reorderState.isAnyItemDragging
    LaunchedEffect(currentDisplayIndex, anyDragging) {
        if (!anyDragging && currentDisplayIndex >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(currentDisplayIndex)
        }
    }
    val highlightStyle = resolveQueueHighlightStyle(
        style = queueBgStyle,
        isDark = isDark,
        colorScheme = MaterialTheme.colorScheme,
    )
    val rowShape = RoundedCornerShape(QueuePolishSpecs.HIGHLIGHT_CORNER_RADIUS_DP.dp)
    val coverShape = RoundedCornerShape(QueuePolishSpecs.COVER_CORNER_RADIUS_DP.dp)
    val highlightAnimDurationMs = Motion.scaled(QueuePolishSpecs.HIGHLIGHT_CROSSFADE_MS)
    // Bottom padding clears the overlaid controls + nav bar inset so the last row scrolls free.
    LazyColumn(
        state = listState,
        // NO overscroll effect on this list, on purpose.
        //
        // A LazyColumn overscrolls at its boundaries by default, and Compose
        // takes the WHOLE drag into the effect the moment the list is at its
        // first item and the finger moves down: the delta is applied to the
        // stretch/glow and never enters the nested-scroll chain at all. That
        // is why the queue sheet could not be closed by dragging down on the
        // list - every nested-scroll callback received available.y == 0 from
        // the frame the list reached its top, and only the header's own
        // `draggable` could move the sheet.
        //
        // With a no-op effect the delta always reaches the connection, so
        // QueueHandoff can give it to the sheet. The cost is the stretch/glow
        // animation on this one list; the sheet moving with the finger in its
        // place is the behaviour that was asked for.
        modifier = modifier.fillMaxWidth().overscroll(com.feishin.mobile.core.common.PassThroughOverscroll),
        verticalArrangement = Arrangement.spacedBy(QueuePolishSpecs.ROW_VERTICAL_GAP_DP.dp),
        contentPadding = PaddingValues(
            start = QueuePolishSpecs.LIST_HORIZONTAL_PADDING_DP.dp,
            end = QueuePolishSpecs.LIST_HORIZONTAL_PADDING_DP.dp,
            bottom = bottomContentPaddingDp.dp,
        ),
    ) {
        itemsIndexed(
            displayOrder,
            key = { _, item -> item.queueId },
            contentType = { _, _ -> "item" },
        ) { idx, item ->
            // animateItem is a LazyItemScope extension: create it here (where
            // the scope is the receiver) and pass it into the row.
            val placement = Modifier.animateItem()
            ReorderableItem(reorderState, key = item.queueId) { isDragging ->
                // Drop = commit exactly once. The guard inside onCommit makes
                // the initial (non-drag) composition a no-op.
                LaunchedEffect(isDragging) { if (!isDragging) onCommit() }
                // A dismissing row can replay confirmValueChange while the list
                // is still settling. The player's removal is already idempotent
                // by key; this keeps the replay from even reaching it, so one
                // gesture can only ever mean one row.
                val removed = remember(item.queueId) { java.util.concurrent.atomic.AtomicBoolean(false) }
                // The lambdas below are remembered by the dismiss state, so a
                // parameter read from them must not be a snapshot either.
                val removeNow by rememberUpdatedState(onRemove)
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = {
                        // Identity, never position. `idx` is a snapshot of this
                        // row's slot from composition time; by the time the
                        // dismiss settles an earlier swipe may have shifted it,
                        // and this lambda is remembered with the first one. The
                        // queue key belongs to the row for its whole life.
                        if (it == SwipeToDismissBoxValue.EndToStart &&
                            removed.compareAndSet(false, true)
                        ) {
                            removeNow(item.queueId)
                            true
                        } else false
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    modifier = if (isDragging) Modifier else Modifier.clip(rowShape),
                    backgroundContent = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(rowShape),
                        )
                    },
                ) {
                    // Identity, not position: during a drag the rows around the
                    // current entry shift on every frame, and only the entry's
                    // own id says which one is still playing.
                    val highlight = QueueHighlight.state(displayIds, idx, currentQueueId)
                    val isCurrent = highlight.isCurrent
                    // Played rows (behind the current entry) dim but stay
                    // legible: secondary text clears 4.5:1, above the 3:1 bar.
                    val played = highlight.isPlayed
                    // Fade alpha within the same RGB so crossfading out never tints gray.
                    val targetRowBg = if (isCurrent) {
                        highlightStyle.containerColor
                    } else {
                        highlightStyle.containerColor.copy(alpha = 0f)
                    }
                    val targetRowBorder = if (isCurrent && highlightStyle.borderAlpha > 0f) {
                        highlightStyle.borderColor
                    } else {
                        highlightStyle.borderColor.copy(alpha = 0f)
                    }
                    // Every row gets an EXPLICIT colour: Color.Unspecified
                    // falls back to LocalContentColor, which is dark here
                    // (the sheet is an overlay with no Surface), so upcoming
                    // rows rendered dim instead of full strength.
                    val targetRowTitle = when {
                        isCurrent -> highlightStyle.titleColor
                        played -> playingColor(palette.sheetDim)
                        else -> playingColor(palette.sheetContent)
                    }
                    // (colors above are explicit: Unspecified would fall back
                    // to the dark LocalContentColor and dim these rows)
                    val targetRowSub = when {
                        isCurrent -> highlightStyle.subtitleColor
                        else -> playingColor(palette.sheetDim)
                    }
                    val targetRowIcon = when {
                        isCurrent -> highlightStyle.iconColor
                        else -> playingColor(palette.sheetDim)
                    }
                    val colorSpec = tween<Color>(durationMillis = highlightAnimDurationMs)
                    val rowBg by animateColorAsState(targetRowBg, colorSpec, label = "queue-row-bg")
                    val rowBorder by animateColorAsState(targetRowBorder, colorSpec, label = "queue-row-border")
                    val rowTitle by animateColorAsState(targetRowTitle, colorSpec, label = "queue-row-title")
                    val rowSub by animateColorAsState(targetRowSub, colorSpec, label = "queue-row-sub")
                    val rowIcon by animateColorAsState(targetRowIcon, colorSpec, label = "queue-row-icon")

                    Row(
                        placement
                            // Lift the dragged row above its neighbours so it
                            // is never painted under the rows it passes over.
                            .zIndex(if (isDragging) 1f else 0f)
                            .fillMaxWidth()
                            .heightIn(min = QueuePolishSpecs.ROW_MIN_HEIGHT_DP.dp)
                            .shadow(if (isDragging) 8.dp else 0.dp, rowShape)
                            .background(rowBg, rowShape)
                            .then(
                                if (rowBorder.alpha > 0f) {
                                    Modifier.border(0.5.dp, rowBorder, rowShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clip(rowShape)
                            .combinedClickable(
                                enabled = idx >= 0,
                                onClick = { if (idx >= 0) onPlayQueueItem(item.queueId) },
                                onLongClick = {
                                    // Long-press opens the same sheet as the ⋮
                                    // button, with a haptic tick for feedback.
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onActions(item.song)
                                },
                            )
                            .padding(QueuePolishSpecs.HIGHLIGHT_INNER_PADDING_DP.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val artUrl = player.coverArtUrl(item.song.coverArt, 128)
                        Box(
                            Modifier.size(QueuePolishSpecs.COVER_SIZE_DP.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 48dp art with 10dp concentric corners (20dp outer - 10dp padding).
                            CoverImage(
                                artUrl,
                                item.song.title,
                                modifier = Modifier.size(QueuePolishSpecs.COVER_SIZE_DP.dp),
                                roundedDp = QueuePolishSpecs.COVER_CORNER_RADIUS_DP.dp,
                            )
                            if (isCurrent) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .clip(coverShape)
                                        .background(Color.Black.copy(alpha = QueuePolishSpecs.COVER_SCRIM_ALPHA)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isPlaying) {
                                        EqualizerOverlay(Modifier.size(16.dp))
                                    } else {
                                        StaticEqualizerOverlay(Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        // Both text lines start at the same x (one column).
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = 16.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else null,
                                color = rowTitle,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${item.song.artist} • ${formatDuration(item.song.durationSec)}",
                                fontSize = 14.sp,
                                color = rowSub,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Explicit ⋮ entry point next to the drag handle, so
                        // the actions sheet is reachable without long-press.
                        IconButton(
                            onClick = { onActions(item.song) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Track options",
                                tint = rowIcon,
                            )
                        }
                        // Immediate drag from the handle: no long-press delay,
                        // and dragging anywhere else never starts a reorder.
                        // A 48dp box gives the handle a full-size touch target
                        // without making the glyph itself huge.
                        Box(
                            Modifier.size(48.dp).draggableHandle(
                                onDragStarted = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = { onCommit() },
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Reorder",
                                tint = rowIcon,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Queue bottom bar: shuffle left, close chevron centered, repeat right. Both
 * toggles show the player's live state and stay in sync with Now Playing.
 * Transparent container overlaid directly over the edge-faded sheet backdrop.
 */
@Composable
private fun QueueBottomBar(
    shuffleOn: Boolean,
    repeatMode: Int,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onClose: () -> Unit,
    palette: NowPlayingPalette,
    queueBgStyle: BackgroundStyle,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val controlColors = resolveQueueBottomControlColors(
        style = queueBgStyle,
        isDark = isDark,
        colorScheme = MaterialTheme.colorScheme,
        onSheetColor = playingColor(palette.sheetContent),
    )
    val pillShape = RoundedCornerShape(QueuePolishSpecs.HIGHLIGHT_CORNER_RADIUS_DP.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(QueuePolishSpecs.BOTTOM_CONTROLS_HEIGHT_DP.dp)
            .background(controlColors.containerColor),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .clip(pillShape)
                .then(
                    if (shuffleOn) {
                        Modifier
                            .background(controlColors.activePillColor, pillShape)
                            .then(
                                if (controlColors.activePillBorderColor.alpha > 0f) {
                                    Modifier.border(0.5.dp, controlColors.activePillBorderColor, pillShape)
                                } else {
                                    Modifier
                                },
                            )
                    } else {
                        Modifier
                    },
                ),
        ) {
            IconButton(onClick = onShuffle, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = if (shuffleOn) "Shuffle on" else "Shuffle off",
                    tint = if (shuffleOn) controlColors.activeIconTint else controlColors.inactiveIconTint,
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Close queue",
                tint = controlColors.chevronTint,
            )
        }
        val repeatActive = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF
        Box(
            Modifier
                .clip(pillShape)
                .then(
                    if (repeatActive) {
                        Modifier
                            .background(controlColors.activePillColor, pillShape)
                            .then(
                                if (controlColors.activePillBorderColor.alpha > 0f) {
                                    Modifier.border(0.5.dp, controlColors.activePillBorderColor, pillShape)
                                } else {
                                    Modifier
                                },
                            )
                    } else {
                        Modifier
                    },
                ),
        ) {
            IconButton(onClick = onRepeat, modifier = Modifier.size(48.dp)) {
                when (repeatMode) {
                    androidx.media3.common.Player.REPEAT_MODE_ONE -> Icon(
                        Icons.Default.RepeatOne, contentDescription = "Repeat one",
                        tint = controlColors.activeIconTint,
                    )
                    androidx.media3.common.Player.REPEAT_MODE_ALL -> Icon(
                        Icons.Default.Repeat, contentDescription = "Repeat all",
                        tint = controlColors.activeIconTint,
                    )
                    else -> Icon(
                        Icons.Default.Repeat, contentDescription = "Repeat off",
                        tint = controlColors.inactiveIconTint,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QueueSheet(
    sheet: SheetState,
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    palette: NowPlayingPalette,
    dynamicColor: Boolean = true,
    playerBgStyle: BackgroundStyle = BackgroundStyle.DEFAULT,
    playerSurfaceColor: Color = Color.Unspecified,
    queueBgStyle: BackgroundStyle = BackgroundStyle.DEFAULT,
    currentCoverUrl: String? = null,
    nowPlayingSheet: SheetState? = null,
    playlistEvents: PlaylistEvents? = null,
    onActions: (com.feishin.mobile.domain.model.Track) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val queue by player.queue.collectAsStateWithLifecycle()
    val currentIndex by player.currentIndex.collectAsStateWithLifecycle()
    val sourceName by player.sourceName.collectAsStateWithLifecycle()
    val radioActive by player.radioActive.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val shuffleOn by player.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by player.repeatMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val navBarBottomDp = with(density) { kotlin.math.ceil(navBarBottomPx.toDp().value).toInt() }
    val bottomContentPaddingDp = computeListBottomContentPaddingDp(
        controlsHeightDp = QueuePolishSpecs.BOTTOM_CONTROLS_HEIGHT_DP,
        navBarBottomInsetDp = navBarBottomDp,
    )
    val peekHeightPx = with(density) { 80.dp.toPx() } + navBarBottomPx
    var showSaveDialog by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // The header (Save pill, secondary line), the sheet gradient and every row
    // all read the one resolved palette.
    val content = playingColor(palette.sheetContent)
    val dim = playingColor(palette.dim)

    if (queue.isEmpty()) {
        // The last item is gone: close through the one close path.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    // ---- Commit-on-drop reorder -------------------------------------------
    // The library's onMove fires on EVERY drag frame. player.move() per frame
    // used to (1) emit a new queue (full-list recompose per frame), (2) IPC a
    // moveMediaItem to the service per frame, (3) write DataStore per frame,
    // and (4) re-trigger the auto-scroll below per frame — the loop that froze
    // the app after a few drags. Now onMove touches ONLY this local copy; the
    // player sees exactly ONE commitOrder() on drop.
    var displayOrder by remember(queue) { mutableStateOf(queue) }
    val liveIds = queue.map { it.queueId }
    val shownIds = displayOrder.map { it.queueId }
    fun commitIfDirty() {
        if (shownIds != liveIds) player.commitOrder(shownIds)
        // displayOrder re-syncs from remember(queue) once the player emits.
    }

    // Direct-manipulation sheet. The nested-scroll handoff lets the list scroll
    // normally and only takes over when the list is already at the top and the
    // finger drags down. Progress is read only inside graphicsLayer lambdas, so
    // dragging never recomposes a frame.
    //
    // Touch drags in the list settle through the Column's `draggable`
    // (onDragStopped); ballistic flings that keep scrolling after the finger
    // lifted never touch `draggable`, so onPostFling settles those here with
    // the fling's own velocity. Without it a fast downward fling at the top
    // of the list parked the sheet halfway with no settle ever coming.
    val nestedScroll = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            // Set when THIS connection moved the sheet, so the release can
            // settle it even when the Column's own `draggable` never owned the
            // gesture (a drag that starts over the list goes to the lazy list
            // first). Without this the sheet kept whatever progress the finger
            // left it at and rested halfway.
            var moved = false

            /** The list can only hand off once its first item is really showing. */
            fun listAtTop(): Boolean =
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0

            /**
             * The delta has to be taken HERE, before the list sees it.
             *
             * A LazyColumn overscrolls at its boundary, and from the frame the
             * list reaches its first item the overscroll effect owns the drag:
             * it reports the delta as consumed and dispatches nothing onward,
             * so `onPostScroll` sees available == 0. A handoff written only
             * there moved the sheet for exactly one frame (~80px) and then
             * stuck - the reported bug.
             */
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                when (val handoff = com.feishin.mobile.core.common.QueueHandoff.decide(
                    deltaY = available.y,
                    sheetHeightPx = sheet.sheetHeightPx,
                    userInput = source == NestedScrollSource.UserInput,
                    listAtTop = listAtTop(),
                    sheetDragging = sheet.dragging,
                    sheetAtOpen = sheet.progress >= 1f,
                )) {
                    is com.feishin.mobile.core.common.ListHandoff.PassToList ->
                        return androidx.compose.ui.geometry.Offset.Zero

                    is com.feishin.mobile.core.common.ListHandoff.TakeBySheet -> {
                        sheet.dragBy(handoff.deltaProgress)
                        moved = true
                    }
                }
                return androidx.compose.ui.geometry.Offset(0f, available.y)
            }

            /**
             * The LEFTOVER of a drag the list partly took: the frame where the
             * list was still one or two pixels from its first item, so it
             * consumed what it could and handed the rest back. That is the
             * "only the distance left after the list reached the top" part of
             * the gesture - without it there is a one-frame dead zone at the
             * boundary.
             *
             * Only a finger may move the sheet. A ballistic fling is dispatched
             * here too, with source = SideEffect; a downward fling still in
             * flight when the list reached its top used to arrive as leftover,
             * drag the sheet, latch `moved` and then close it on release. The
             * list owns its own fling; the sheet follows a finger only.
             */
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (source != NestedScrollSource.UserInput) {
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                val handoff = com.feishin.mobile.core.common.QueueHandoff.decide(
                    deltaY = available.y,
                    sheetHeightPx = sheet.sheetHeightPx,
                    userInput = true,
                    listAtTop = listAtTop(),
                    sheetDragging = sheet.dragging,
                    sheetAtOpen = sheet.progress >= 1f,
                )
                if (handoff is com.feishin.mobile.core.common.ListHandoff.TakeBySheet) {
                    sheet.dragBy(handoff.deltaProgress)
                    moved = true
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            // Dispatched on EVERY drag end (including a slow release with zero
            // velocity), so it is the reliable place to resolve a sheet that
            // this connection moved. It receives the release velocity, so a
            // decisive downward flick still closes even when the travel was
            // under POSITION_THRESHOLD.
            override suspend fun onPreFling(
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                if (moved) {
                    moved = false
                    sheet.onDragEnd(available.y)
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }

            // Safety net for a gesture that ended without the pre-fling
            // dispatch. Deliberately NO velocity close here: a fling the list
            // consumed that reaches the top must need a NEW gesture to close
            // the sheet, never resolve into a close from leftover velocity.
            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                if (moved) {
                    moved = false
                    sheet.settle(0f)
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    // Tinted gradient from the artwork scheme when it is present, the theme's
    // own surface pair when it is not. Brush either way so the background()
    // overload is unambiguous.
    val sheetBrush: Brush = Brush.verticalGradient(
        listOf(playingColor(palette.sheetTop), playingColor(palette.sheetBottom)),
    )
    val scheme = LocalPlayingColors.current
    val colorScheme = MaterialTheme.colorScheme
    val sheetSurfaceColor = if (dynamicColor) {
        playingColor(palette.sheetBottom)
    } else {
        colorScheme.surface
    }
    val sheetIsDark = scheme?.dark ?: LocalVusicDark.current
    val resolvedPlayerSurfaceColor = if (playerSurfaceColor == Color.Unspecified) {
        sheetSurfaceColor
    } else {
        playerSurfaceColor
    }
    val collapsedStripColors = remember(
        playerBgStyle,
        sheetIsDark,
        colorScheme,
        resolvedPlayerSurfaceColor,
        palette.content,
        palette.dim,
    ) {
        resolveCollapsedStripColors(
            playerStyle = playerBgStyle,
            isDark = sheetIsDark,
            colorScheme = colorScheme,
            playerSurfaceColor = resolvedPlayerSurfaceColor,
            contentColor = playingColor(palette.content),
            dimColor = playingColor(palette.dim),
        )
    }
    val sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(Modifier.fillMaxSize()) {
        // Scrim: composed ONLY while the sheet is open so touches on Now Playing
        // are never blocked while the sheet rests at its bottom peek strip.
        if (sheet.open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { /* consume */ } }
                    .graphicsLayer { alpha = scrimAlpha(sheet.progress) }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { sheet.close() },
                    ),
            )
        }
        Box(
            Modifier.fillMaxWidth()
                .fillMaxHeight(0.78f)
                .align(Alignment.BottomCenter)
                .onSizeChanged {
                    sheet.sheetHeightPx = (it.height.toFloat() - peekHeightPx).coerceAtLeast(1f)
                }
                .graphicsLayer {
                    val p = sheet.progress.coerceIn(0f, 1f)
                    val travel = (size.height - peekHeightPx).coerceAtLeast(1f)
                    translationY = (1f - p) * travel
                    // Keep the header grab area solid while sliding.
                    clip = p < 1f
                }
                .clip(sheetShape),
        ) {
            // Queue sheet background layer: alpha = 0f while collapsed so the
            // continuous Player backdrop shows seamlessly behind the peek strip,
            // fading in to 1f across progress 0f..0.25f on upward drag.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = queueSheetSurfaceAlpha(sheet.progress) }
                    .background(sheetSurfaceColor.copy(alpha = 1.0f)),
            ) {
                Crossfade(
                    targetState = queueBgStyle,
                    animationSpec = tween(durationMillis = Motion.scaled(Motion.Normal)),
                    modifier = Modifier.matchParentSize(),
                    label = "queue-bg-style",
                ) { style ->
                    when (style) {
                        BackgroundStyle.DEFAULT -> Box(
                            Modifier
                                .fillMaxSize()
                                .background(sheetSurfaceColor),
                        )
                        BackgroundStyle.GRADIENT -> Box(
                            Modifier
                                .fillMaxSize()
                                .background(sheetBrush),
                        )
                        BackgroundStyle.BLUR -> BlurredArtworkBackground(
                            coverId = currentCoverUrl,
                            bitmap = null,
                            scrimColor = sheetSurfaceColor,
                            isDark = sheetIsDark,
                            modifier = Modifier.fillMaxSize(),
                            role = BlurSurfaceRole.QUEUE,
                        )
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScroll)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (!sheet.open && !sheet.dragging && (delta > 0f || nowPlayingSheet?.dragging == true)) {
                                nowPlayingSheet?.dragByPixels(delta)
                            } else {
                                sheet.dragByPixels(delta)
                            }
                        },
                        // Fling velocity is normalized to progress units so the
                        // decision lives in SheetPhysics, not here.
                        onDragStopped = { velocity ->
                            if (nowPlayingSheet?.dragging == true) {
                                nowPlayingSheet.onDragEnd(velocity)
                            } else {
                                sheet.onDragEnd(velocity)
                            }
                        },
                    ),
            ) {
            // Single shared drag handle pill (36.dp x 4.dp, rounded) at the top edge.
            // At progress = 0f, tapping the 72.dp peek strip opens the queue sheet.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .then(
                        if (!sheet.open) {
                            Modifier
                                .heightIn(min = 72.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { sheet.openSheet() },
                                )
                        } else {
                            Modifier
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (!sheet.open) collapsedStripColors.handleColor else content.copy(alpha = 0.5f),
                        ),
                )
                Spacer(Modifier.height(6.dp))
                if (!sheet.open) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MarqueeTitle(
                            text = "Playing from ${sourceName ?: "Queue"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = collapsedStripColors.textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (sheet.open) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    // "Playing from <source>" centered below the stroke pill, fading out as sheet rises.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(52.dp)
                            .graphicsLayer {
                                val p = sheet.progress.coerceIn(0f, 1f)
                                alpha = (1f - p / 0.35f).coerceIn(0f, 1f)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        MarqueeTitle(
                            text = "Playing from ${sourceName ?: "Queue"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = collapsedStripColors.textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // Queue header, list and overlaid bottom bar fading in directly below that same single handle.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = sheet.progress.coerceIn(0f, 1f)
                                alpha = ((p - 0.15f) / 0.5f).coerceIn(0f, 1f)
                            },
                    ) {
                        Spacer(Modifier.height(6.dp))
                        // Header: big bold source title on the left; the Radio chip (when
                        // active) and the Save button right-aligned on the same row.
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Large + bold; marquees only when it actually overflows.
                            MarqueeTitle(
                                text = sourceName ?: "Queue (${queue.size})",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = content,
                                modifier = Modifier.weight(1f),
                            )
                            if (radioActive) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Radio",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FavoriteRed,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(FavoriteRed.copy(alpha = 0.14f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            // Save pill uses the scheme's primary/onPrimary (contrast
                            // enforced), not a hardcoded white — in Light theme the sheet
                            // is pale, so a white pill would vanish.
                            Text(
                                "Save",
                                style = MaterialTheme.typography.labelLarge,
                                color = playingColor(palette.onAccent),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(playingColor(palette.accent))
                                    .clickable { showSaveDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        // Secondary line: count + total runtime of the queue.
                        Text(
                            "${queue.size} songs · ${formatDuration(queue.sumOf { it.song.durationSec.toLong() }.toInt())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = playingColor(palette.sheetDim),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            QueueList(
                                displayOrder = displayOrder,
                                listState = listState,
                                // The player's current entry, by id. Read from the LIVE queue
                                // (not displayOrder), so the highlight is unaffected by the
                                // in-drag local order.
                                currentQueueId = queue.getOrNull(currentIndex)?.queueId,
                                isPlaying = isPlaying,
                                player = player,
                                haptics = haptics,
                                onMoveLocal = { from, to ->
                                    // Read `displayOrder` directly. It is a delegated state var,
                                    // so a lambda captured once by
                                    // rememberReorderableLazyColumnState still sees the CURRENT
                                    // order. `shownIds` was a plain val frozen at composition, so
                                    // every frame after the first reordered from the ORIGINAL
                                    // list and the drag drifted back to the start.
                                    displayOrder = displayOrder.toMutableList().apply { add(to, removeAt(from)) }
                                },
                                onCommit = { commitIfDirty() },
                                onPlayQueueItem = { qid -> player.playOrToggle(qid) },
                                onRemove = { id -> player.removeById(id) },
                                onActions = onActions,
                                palette = palette,
                                queueBgStyle = queueBgStyle,
                                isDark = sheetIsDark,
                                bottomContentPaddingDp = bottomContentPaddingDp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .queueBottomEdgeFade(
                                        fadeHeight = QueuePolishSpecs.BOTTOM_FADE_HEIGHT_DP.dp,
                                        bottomControlsHeight = QueuePolishSpecs.BOTTOM_CONTROLS_HEIGHT_DP.dp,
                                        navBarBottomPx = navBarBottomPx,
                                    ),
                            )
                            // Bottom bar: transparent overlay above nav bar inset.
                            QueueBottomBar(
                                shuffleOn = shuffleOn,
                                repeatMode = repeatMode,
                                onShuffle = { player.toggleShuffle() },
                                onRepeat = { player.cycleRepeat() },
                                onClose = onDismiss,
                                palette = palette,
                                queueBgStyle = queueBgStyle,
                                isDark = sheetIsDark,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                                    .padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save queue as playlist") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Playlist name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !busy && queue.isNotEmpty(),
                    onClick = {
                        busy = true
                        scope.launch {
                            when (repo.createPlaylist(name.trim(), queue.map { it.song.id })) {
                                is Result.Ok -> {
                                    messenger.show("Saved playlist $name")
                                    showSaveDialog = false
                                }
                                is Result.Err -> {
                                    messenger.show("Could not save playlist")
                                    busy = false
                                }
                            }
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    // A new playlist can't have duplicates, but adding the queue
                    // (radio included) to an existing one goes through the
                    // duplicate check in the picker.
                    TextButton(onClick = { showSaveDialog = false; showPicker = true }) { Text("Existing…") }
                    TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                }
            },
        )
    }
    if (showPicker && queue.isNotEmpty()) {
        PlaylistPickerSheet(
            tracks = queue.map { it.song }, title = "Queue (${queue.size})",
            onDismiss = { showPicker = false }, repo = repo, messenger = messenger,
            playlistEvents = playlistEvents,
        )
    }
}
