package com.vislupus.stepanalyzer.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vislupus.stepanalyzer.AppContainer
import com.vislupus.stepanalyzer.R
import com.vislupus.stepanalyzer.ui.analysis.AnalysisScreen
import com.vislupus.stepanalyzer.ui.components.LucideIcon
import com.vislupus.stepanalyzer.ui.info.InfoScreen
import com.vislupus.stepanalyzer.ui.record.RecordScreen
import com.vislupus.stepanalyzer.ui.sessions.SessionsScreen
import com.vislupus.stepanalyzer.ui.settings.SettingsScreen

object Routes {
    const val RECORD = "record"
    const val SESSIONS = "sessions"
    const val SETTINGS = "settings"
    const val ANALYSIS = "analysis/{sessionId}"
    const val INFO = "info"
    fun analysis(id: Long) = "analysis/$id"
}

private data class Tab(val route: String, val icon: Int, val label: Int)

private val tabs = listOf(
    Tab(Routes.RECORD, R.drawable.ic_activity, R.string.tab_record),
    Tab(Routes.SESSIONS, R.drawable.ic_history, R.string.tab_sessions),
    Tab(Routes.SETTINGS, R.drawable.ic_settings, R.string.tab_settings),
)

/** Navigation host with the bottom bar on the three top-level screens. */
@Composable
fun AppRoot(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTopLevel = tabs.any { it.route == route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (isTopLevel) {
                TopAppBar(
                    title = { Text(stringResource(tabs.first { it.route == route }.label)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    for (tab in tabs) {
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                LucideIcon(
                                    tab.icon,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            label = { Text(stringResource(tab.label)) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val contentPadding: PaddingValues = if (isTopLevel) padding else PaddingValues(0.dp)
        NavHost(navController = nav, startDestination = Routes.RECORD) {
            composable(Routes.RECORD) {
                RecordScreen(container, contentPadding, onOpenSession = { nav.navigate(Routes.analysis(it)) })
            }
            composable(Routes.SESSIONS) {
                SessionsScreen(container, contentPadding, onOpenSession = { nav.navigate(Routes.analysis(it)) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(container, contentPadding, onOpenInfo = { nav.navigate(Routes.INFO) })
            }
            composable(
                Routes.ANALYSIS,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("sessionId") ?: 0L
                AnalysisScreen(container, id, onBack = { nav.popBackStack() }, onOpenInfo = { nav.navigate(Routes.INFO) })
            }
            composable(Routes.INFO) { InfoScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
