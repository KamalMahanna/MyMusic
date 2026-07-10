package com.mymusic.app.ui.navigation

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.navigation.NavController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.mymusic.app.data.NetworkConnectivityObserver
import com.mymusic.app.ui.screens.player.PlayerViewModel
import com.mymusic.app.ui.components.MiniPlayer
import com.mymusic.app.ui.screens.home.HomeScreen
import com.mymusic.app.ui.screens.library.LibraryScreen
import com.mymusic.app.ui.screens.search.SearchScreen
import com.mymusic.app.ui.screens.player.PlayerScreen
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import javax.inject.Inject

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", Icons.Rounded.Home)
    object Search : Screen("search", "Search", Icons.Rounded.Search)
    object Library : Screen("library", "Library", Icons.Rounded.LibraryMusic)
}

val items = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Library
)

@Composable
fun MyMusicNavGraph(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    networkConnectivityObserver: NetworkConnectivityObserver
) {
    val navController = rememberNavController()
    var isPlayerExpanded by remember { mutableStateOf(false) }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isMiniPlayerVisible = currentSong != null && !isPlayerExpanded

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // Observe network connectivity
    val isConnected by networkConnectivityObserver.isConnected.collectAsState()
    val isOffline = !isConnected

    // Determine start destination once based on initial connectivity.
    // This avoids the race where LaunchedEffect fires before NavHost is ready.
    val startDestination = remember {
        if (networkConnectivityObserver.isConnected.value) {
            Screen.Home.route
        } else {
            Screen.Library.route
        }
    }

    // When connectivity changes at runtime, force-navigate to Library (offline)
    // or allow free navigation (online). We skip the initial composition since
    // startDestination already handles it.
    var hasInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(isOffline) {
        if (!hasInitialized) {
            hasInitialized = true
            return@LaunchedEffect
        }
        if (isOffline) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != Screen.Library.route) {
                navController.navigate(Screen.Library.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (isTablet) {
                    // Tablet: nav tabs + mini player in a row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Only hide the nav tabs when offline, not the mini player
                        AnimatedVisibility(
                            visible = !isOffline,
                            enter = fadeIn(animationSpec = tween(300)) +
                                    slideInHorizontally(
                                        initialOffsetX = { -it },
                                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                                    ),
                            exit = fadeOut(animationSpec = tween(200)) +
                                    slideOutHorizontally(
                                        targetOffsetX = { -it },
                                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                                    )
                        ) {
                            CustomBottomNavigationContent(navController = navController)
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isMiniPlayerVisible,
                            enter = fadeIn(animationSpec = tween(durationMillis = 200)) + 
                                    slideInHorizontally(
                                        initialOffsetX = { it / 2 },
                                        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                    ),
                            exit = fadeOut(animationSpec = tween(durationMillis = 150)) + 
                                   slideOutHorizontally(
                                       targetOffsetX = { it / 2 },
                                       animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                   )
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(16.dp))
                                MiniPlayer(
                                    onPlayerClick = { isPlayerExpanded = true }
                                )
                            }
                        }
                    }
                } else {
                    // Phone: hide only the nav tabs when offline
                    AnimatedVisibility(
                        visible = !isOffline,
                        enter = fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                                ),
                        exit = fadeOut(animationSpec = tween(200)) +
                                slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                                )
                    ) {
                        CustomBottomNavigation(navController = navController)
                    }
                }
            }
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            val bottomBarPadding = innerPadding.calculateBottomPadding()
            
            // Exclude bottom padding from content Box so screens draw behind bottom bar
            val contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = 0.dp
            )

            // Calculate total bottom padding needed for screen lists to scroll past navbar + MiniPlayer
            val screenBottomPadding = if (isOffline) {
                // When offline, no bottom bar — only account for MiniPlayer
                if (isMiniPlayerVisible) 76.dp else 0.dp
            } else if (isTablet) {
                bottomBarPadding
            } else {
                bottomBarPadding + if (isMiniPlayerVisible) 76.dp else 0.dp
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
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
                        HomeScreen(
                            onPlaySong = { isPlayerExpanded = true },
                            bottomPadding = screenBottomPadding
                        )
                    }
                    composable(Screen.Search.route) {
                        SearchScreen(
                            isPlayerExpanded = isPlayerExpanded,
                            onPlaySong = { isPlayerExpanded = true },
                            bottomPadding = screenBottomPadding
                        )
                    }
                    composable(Screen.Library.route) {
                        LibraryScreen(
                            onPlaySong = { isPlayerExpanded = true },
                            bottomPadding = screenBottomPadding,
                            isOffline = isOffline
                        )
                    }
                }
                
                if (!isTablet) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isMiniPlayerVisible,
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
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (isOffline) 12.dp else bottomBarPadding - 4.dp)
                    ) {
                        MiniPlayer(
                            onPlayerClick = { isPlayerExpanded = true }
                        )
                    }
                }
            }
        }

        // Full Player Screen (Slides up from the bottom, covering the whole screen including the navbar)
        androidx.compose.animation.AnimatedVisibility(
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

@Composable
fun CustomBottomNavigation(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomBottomNavigationContent(navController = navController)
    }
}

@Composable
fun CustomBottomNavigationContent(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    Row(
        modifier = modifier
            .height(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { screen ->
            val isSelected = currentRoute == screen.route
            NavBarItem(
                screen = screen,
                isSelected = isSelected,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun NavBarItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Bouncy press effect (shrinks to 0.88f and bounces back)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "NavBarItemScale"
    )

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null // Disables default ripple so our custom press animation stands out cleanly
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            .then(
                if (isSelected) {
                    Modifier.padding(horizontal = 16.dp)
                } else {
                    Modifier.width(48.dp)
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = screen.title,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(animationSpec = tween(150, delayMillis = 80)) + 
                    slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)) { -it / 2 },
            exit = fadeOut(animationSpec = tween(100))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = screen.title,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
