package com.feishin.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.feishin.mobile.ui.components.MiniPlayerHeight
import com.feishin.mobile.ui.components.MiniPlayerNavGap

/** Gap between a message and the mini player / navigation bar. */
private val MessageGap = 8.dp

/**
 * Shared in-app message host: renders [SnackbarHost] content positioned fully
 * above the floating mini player (when visible) and above the bottom navigation
 * bar, with a small gap. When there is no mini player it sits just above the
 * navigation bar; when the keyboard is open it sits above the keyboard.
 *
 * Placed AFTER [MiniPlayer] in the NavHost Box so messages draw on top of the
 * card and its touch area never blocks a message action.
 *
 * @param miniPlayerVisible whether the floating card is currently on screen
 * @param miniPlayerDragY   downward drag travel in dp (0 at rest, >0 when
 *                          dragged toward the nav bar)
 * @param navBarHeightPx    measured height of the bottom nav bar (px)
 * @param imeBottomPx       measured height of the IME (px), 0 when closed
 */
@Composable
fun MessageHost(
    snackbar: SnackbarHostState,
    miniPlayerVisible: Boolean,
    miniPlayerDragY: Float,
    navBarHeightPx: Int,
    imeBottomPx: Int,
) {
    val density = LocalDensity.current
    val navBottomDp = with(density) { navBarHeightPx.toDp() }
    val keyboardBottomDp = with(density) { imeBottomPx.toDp() }
    // Whatever is closest to the bottom edge wins: the nav bar or the keyboard.
    val baseBottomDp = maxOf(navBottomDp, keyboardBottomDp)
    // Above the mini player only when it is visible and not covered by the keyboard.
    val miniExtraDp = if (miniPlayerVisible && keyboardBottomDp.value == 0f) {
        (MiniPlayerHeight - miniPlayerDragY.dp + MiniPlayerNavGap).coerceAtLeast(0.dp)
    } else {
        0.dp
    }
    val messageBottomDp = baseBottomDp + MiniPlayerNavGap + miniExtraDp

    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = messageBottomDp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        SnackbarHost(snackbar)
    }
}
