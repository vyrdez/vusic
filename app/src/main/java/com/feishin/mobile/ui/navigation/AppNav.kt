package com.feishin.mobile.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feishin.mobile.ui.MainScaffold
import com.feishin.mobile.ui.MainViewModel
import com.feishin.mobile.ui.components.MessageHost
import com.feishin.mobile.ui.login.LoginScreen
import com.feishin.mobile.ui.theme.Motion

/**
 * Auth-level gate switching between [LoginScreen] and [MainScaffold].
 *
 * A blank frame is held until [AuthRepository.ready] and stored settings are
 * loaded, so cold start never plays the login <-> app transition. Once ready,
 * in-session login/logout slides cleanly with [Motion.EaseOutQuint] (or snaps
 * when animations are disabled) underneath a single hoisted [MessageHost].
 */
@Composable
fun AppNav(main: MainViewModel = hiltViewModel()) {
    val auth = main.auth
    val ready by auth.ready.collectAsStateWithLifecycle()
    val settings by main.settings.flow.collectAsStateWithLifecycle(initialValue = main.settings.latestSettings)
    if (!ready || settings == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    val profiles by auth.loginState.collectAsStateWithLifecycle()
    val hasAccess = profiles.isNotEmpty() || settings?.deviceFilesStandalone == true

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(main.messenger) {
        main.messenger.messages.collect { snackbar.showSnackbar(it) }
    }

    val density = LocalDensity.current
    val defaultNavBarPx = WindowInsets.navigationBars.getBottom(density)
    var miniVisible by remember { mutableStateOf(false) }
    var miniDragY by remember { mutableFloatStateOf(0f) }
    var navBarHeightPx by remember { mutableIntStateOf(defaultNavBarPx) }

    LaunchedEffect(hasAccess, defaultNavBarPx) {
        if (!hasAccess) {
            miniVisible = false
            miniDragY = 0f
            navBarHeightPx = defaultNavBarPx
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = hasAccess,
            transitionSpec = {
                if (Motion.durationScale == 0f) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else if (targetState) {
                    (slideInHorizontally(
                        animationSpec = tween(Motion.scaled(250), easing = Motion.EaseOutQuint),
                    ) { it } + fadeIn(tween(Motion.scaled(210)))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(Motion.scaled(250), easing = Motion.EaseOutQuint),
                        ) { -it * 18 / 100 } + fadeOut(tween(Motion.scaled(210))))
                } else {
                    (slideInHorizontally(
                        animationSpec = tween(Motion.scaled(250), easing = Motion.EaseOutQuint),
                    ) { -it * 18 / 100 } + fadeIn(tween(Motion.scaled(210)))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(Motion.scaled(250), easing = Motion.EaseOutQuint),
                        ) { it } + fadeOut(tween(Motion.scaled(210))))
                }
            },
            label = "auth-gate",
        ) { loggedIn ->
            if (loggedIn) {
                MainScaffold(
                    main = main,
                    onLogout = { auth.logout() },
                    snackbar = snackbar,
                    onMessageClearanceChanged = { visible, dragY, barHeightPx ->
                        miniVisible = visible
                        miniDragY = dragY
                        navBarHeightPx = if (barHeightPx > 0) barHeightPx else defaultNavBarPx
                    },
                )
            } else {
                LoginScreen(messenger = main.messenger)
            }
        }

        MessageHost(
            snackbar = snackbar,
            miniPlayerVisible = miniVisible,
            miniPlayerDragY = miniDragY,
            navBarHeightPx = navBarHeightPx,
            imeBottomPx = WindowInsets.ime.getBottom(density),
        )
    }
}
