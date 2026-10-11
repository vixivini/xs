package com.example.xs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.xs.ui.MainViewModel
import com.example.xs.ui.favorites.FavoritesScreen
import com.example.xs.ui.feed.FeedScreen
import com.example.xs.ui.media.MediaViewerScreen
import com.example.xs.ui.reels.ReelsScreen
import com.example.xs.ui.profile.FollowingListScreen
import com.example.xs.ui.profile.ProfileScreen
import com.example.xs.ui.profile.TopicProfileScreen
import com.example.xs.ui.theme.XsTheme
import com.example.xs.ui.webview.WebViewScreen
import com.example.xs.ui.profile.SettingsScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import android.util.Base64

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.WindowManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ativa modo imersivo total (Esconde barra de status e navegação do sistema)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        // Garante que a tela use 100% do espaço, inclusive sob o "furo" da câmera
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        setContent {
            XsTheme {
                val viewModel: MainViewModel = viewModel()
                MainScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    LaunchedEffect(currentDestination) {
        currentDestination?.route?.let { route ->
            com.example.xs.util.AppDiagnosticLogger.log("NAV", "Navegação para rota: $route")
        }
    }

    // Motor de rolagem para o topo sumir
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(scrollBehavior.nestedScrollConnection), // Conecta o scroll ao TopBar
        topBar = {
            val route = currentDestination?.route ?: ""
            // Mostra o topo apenas se não estiver no visualizador ou reels
            if (!route.startsWith("media_viewer") && !route.startsWith("reels") && !route.startsWith("topic_profile")) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "XS",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        bottomBar = {
            val route = currentDestination?.route ?: ""
            if (!route.startsWith("media_viewer")) {
                // Barra Inferior Compacta (Estilo Instagram)
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp
                ) {
                    NavigationBar(
                        modifier = Modifier.height(56.dp), // Reduzido de 80dp para 56dp
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp
                    ) {
                        val items = listOf(
                            Triple("feed", Icons.Outlined.Home, Icons.Default.Home),
                            Triple("explore", Icons.Outlined.Search, Icons.Default.Search),
                            Triple("reels", Icons.Outlined.Movie, Icons.Default.Movie),
                            Triple("profile", Icons.Outlined.Person, Icons.Default.Person)
                        )
                        
                        items.forEach { (route, icon, selectedIcon) ->
                            val isSelected = currentDestination?.route == route
                            NavigationBarItem(
                                icon = { 
                                    Icon(
                                        imageVector = if (isSelected) selectedIcon else icon,
                                        contentDescription = route,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                selected = isSelected,
                                onClick = { 
                                    if (isSelected && route == "feed") {
                                        viewModel.refreshFeed()
                                        viewModel.triggerScrollToTop()
                                    } else {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId)
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.Transparent,
                                    selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                    unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // No Reels e Explore, usamos o padding zero para preencher a tela toda
        val route = currentDestination?.route ?: ""
        val adjustedPadding = if (route == "reels" || route.startsWith("media_viewer")) PaddingValues(0.dp) else innerPadding

        NavHost(
            navController = navController,
            startDestination = "feed",
            modifier = Modifier.padding(adjustedPadding)
        ) {
            composable("feed") {
                FeedScreen(
                    viewModel = viewModel, 
                    onMediaClick = { _, index ->
                        navController.navigate("media_viewer/$index")
                    },
                    onProfileClick = { url, title ->
                        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                        navController.navigate("topic_profile/$encodedUrl/$encodedTitle")
                    }
                )
            }
            composable("web_add") {
                WebViewScreen(
                    onFavoriteClick = { url, title ->
                        viewModel.toggleFavorite(url, title)
                    },
                    onLikeClick = { url, title ->
                        viewModel.toggleBlogLike(url, title)
                    },
                    isFavorite = { url ->
                        viewModel.isFavorite(url)
                    },
                    isLiked = { url ->
                        viewModel.isBlogLiked(url)
                    }
                )
            }
            composable("explore") {
                FeedScreen(
                    viewModel = viewModel, 
                    isExplore = true,
                    onMediaClick = { _, index ->
                        navController.navigate("media_viewer/$index")
                    },
                    onProfileClick = { url, title ->
                        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                        navController.navigate("topic_profile/$encodedUrl/$encodedTitle")
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onWebAddClick = { navController.navigate("web_add") }
                )
            }
            composable("favorites") {
                FavoritesScreen(viewModel)
            }
            composable("reels") {
                ReelsScreen(
                    viewModel = viewModel,
                    onProfileClick = { url, title ->
                        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                        navController.navigate("topic_profile/$encodedUrl/$encodedTitle")
                    }
                )
            }
            composable("profile") {
                ProfileScreen(
                    viewModel = viewModel,
                    onPostClick = { _, index ->
                        navController.navigate("media_viewer/$index")
                    },
                    onFollowingClick = {
                        navController.navigate("following_list")
                    },
                    onSettingsClick = {
                        navController.navigate("settings")
                    }
                )
            }
            composable("following_list") {
                FollowingListScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onTopicClick = { topic ->
                        val encodedUrl = URLEncoder.encode(topic.url, StandardCharsets.UTF_8.toString())
                        val encodedTitle = URLEncoder.encode(topic.title, StandardCharsets.UTF_8.toString())
                        navController.navigate("topic_profile/$encodedUrl/$encodedTitle")
                    }
                )
            }
            composable("topic_profile/{url}/{title}") { backStackEntry ->
                val url = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("url") ?: "", StandardCharsets.UTF_8.toString())
                val title = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", StandardCharsets.UTF_8.toString())
                TopicProfileScreen(
                    viewModel = viewModel,
                    topicUrl = url,
                    topicTitle = title,
                    onBack = { navController.popBackStack() },
                    onPostClick = { _, index ->
                        navController.navigate("media_viewer/$index")
                    }
                )
            }
            composable("media_viewer/{index}") { backStackEntry ->
                val index = backStackEntry.arguments?.getString("index")?.toIntOrNull() ?: 0
                MediaViewerScreen(
                    viewModel = viewModel,
                    initialIndex = index,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
