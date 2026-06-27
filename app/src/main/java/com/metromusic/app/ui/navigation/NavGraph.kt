package com.metromusic.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.metromusic.app.ui.components.MiniPlayer
import com.metromusic.app.ui.screens.home.HomeScreen
import com.metromusic.app.ui.screens.library.LibraryScreen
import com.metromusic.app.ui.screens.search.SearchScreen
import com.metromusic.app.ui.screens.player.PlayerScreen

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Search : Screen("search", "Search", Icons.Filled.Search)
    object Library : Screen("library", "Library", Icons.Filled.LibraryMusic)
}

val items = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Library
)

@Composable
fun MetroMusicNavGraph() {
    val navController = rememberNavController()
    var isPlayerExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 12 }
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)) { -it / 12 }
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { -it / 12 }
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)) { it / 12 }
                    }
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(onPlaySong = { isPlayerExpanded = true })
                    }
                    composable(Screen.Search.route) {
                        SearchScreen(
                            isPlayerExpanded = isPlayerExpanded,
                            onPlaySong = { isPlayerExpanded = true }
                        )
                    }
                    composable(Screen.Library.route) {
                        LibraryScreen(onPlaySong = { isPlayerExpanded = true })
                    }
                }
                
                // MiniPlayer slides and fades away as the full player slides up
                AnimatedVisibility(
                    visible = !isPlayerExpanded,
                    enter = fadeIn(animationSpec = tween(durationMillis = 200)) + 
                            slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                            ),
                    exit = fadeOut(animationSpec = tween(durationMillis = 150)) + 
                           slideOutVertically(
                               targetOffsetY = { it / 2 },
                               animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                           ),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    MiniPlayer(
                        onPlayerClick = { isPlayerExpanded = true }
                    )
                }
            }
        }

        // Full Player Screen (Slides up from the bottom, covering the whole screen including the navbar)
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = fadeIn(animationSpec = tween(durationMillis = 200)) + 
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                    ),
            exit = fadeOut(animationSpec = tween(durationMillis = 250)) + 
                   slideOutVertically(
                       targetOffsetY = { it },
                       animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                   )
        ) {
            PlayerScreen(
                onCollapse = { isPlayerExpanded = false }
            )
        }
    }
}
