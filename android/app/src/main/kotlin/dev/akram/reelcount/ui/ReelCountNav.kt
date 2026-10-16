package dev.akram.reelcount.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.akram.reelcount.R
import dev.akram.reelcount.ReelCountApp
import dev.akram.reelcount.ui.debug.DebugScreen
import dev.akram.reelcount.ui.history.HistoryScreen
import dev.akram.reelcount.ui.home.HomeScreen
import dev.akram.reelcount.ui.onboarding.OnboardingScreen
import dev.akram.reelcount.ui.settings.SettingsScreen

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"
}

@Composable
fun ReelCountNav() {
    val context = LocalContext.current
    val app = context.applicationContext as ReelCountApp
    var ready by remember { mutableStateOf(false) }
    var onboardingDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        onboardingDone =
            app.container.settings
                .current()
                .onboardingDone
        ready = true
    }
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route != Routes.ONBOARDING
    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    NavItem(Routes.HOME, route, R.string.nav_home, Icons.Filled.Home) {
                        nav.navigate(Routes.HOME) { popUpTo(nav.graph.findStartDestination().id) { saveState = true } }
                    }
                    NavItem(Routes.HISTORY, route, R.string.nav_history, Icons.Filled.DateRange) {
                        nav.navigate(Routes.HISTORY) { popUpTo(nav.graph.findStartDestination().id) { saveState = true } }
                    }
                    NavItem(Routes.SETTINGS, route, R.string.nav_settings, Icons.Filled.Settings) {
                        nav.navigate(Routes.SETTINGS) { popUpTo(nav.graph.findStartDestination().id) { saveState = true } }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (onboardingDone) Routes.HOME else Routes.ONBOARDING,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }
            composable(Routes.HOME) { HomeScreen() }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenDebug = { nav.navigate(Routes.DEBUG) })
            }
            composable(Routes.DEBUG) { DebugScreen() }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    target: String,
    current: String?,
    label: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = current == target,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(label)) },
    )
}
