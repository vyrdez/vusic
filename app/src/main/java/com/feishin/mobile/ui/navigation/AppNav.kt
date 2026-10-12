package com.feishin.mobile.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.feishin.mobile.ui.MainScaffold
import com.feishin.mobile.ui.MainViewModel
import com.feishin.mobile.ui.login.LoginScreen

object AuthRoutes {
    const val LOGIN = "login"
    const val MAIN = "main"
}

/**
 * Single auth-level NavHost. The start destination is decided once from stored
 * login state; later login/logout transitions navigate with popUpTo(0) so no
 * app restart is ever needed. A blank frame is shown until stored state is read
 * (the read is synchronous, so this never flickers in practice).
 */
@Composable
fun AppNav(main: MainViewModel = hiltViewModel()) {
    val auth = main.auth
    val ready by auth.ready.collectAsState()
    if (!ready) {
        Box(Modifier.fillMaxSize())
        return
    }
    val creds by auth.loginState.collectAsState()
    val nav = rememberNavController()
    val start = remember { if (creds != null) AuthRoutes.MAIN else AuthRoutes.LOGIN }

    LaunchedEffect(creds) {
        val target = if (creds != null) AuthRoutes.MAIN else AuthRoutes.LOGIN
        if (nav.currentDestination?.route != target) {
            nav.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(nav, startDestination = start) {
        composable(AuthRoutes.LOGIN) { LoginScreen() }
        composable(AuthRoutes.MAIN) { MainScaffold(main = main, onLogout = { auth.logout() }) }
    }
}
