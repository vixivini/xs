package com.example.xs.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Portrait
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.xs.ui.MainViewModel
import com.example.xs.util.MediaUtils

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicProfileScreen(
    viewModel: MainViewModel,
    topicUrl: String,
    topicTitle: String,
    onBack: () -> Unit,
    onPostClick: (List<String>, Int) -> Unit
) {
    val topicPosts by viewModel.topicPosts.collectAsState()
    val isLoading by viewModel.isTopicLoading.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    
    val cleanUrl = remember(topicUrl) { topicUrl.trimEnd('/') }
    val topicInfo = remember(favorites, cleanUrl) { 
        favorites.find { it.url.trimEnd('/') == cleanUrl } 
    }
    
    val posts = topicPosts[cleanUrl] ?: emptyList()
    val gridState = rememberLazyGridState()

    LaunchedEffect(cleanUrl) {
        viewModel.fetchTopicPosts(cleanUrl, topicTitle)
    }

    // Gatilho de scroll inteligente
    val lastVisibleItemIndex by remember {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    val totalItemsCount by remember {
        derivedStateOf { gridState.layoutInfo.totalItemsCount }
    }

    LaunchedEffect(lastVisibleItemIndex, totalItemsCount) {
        if (!isLoading && totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 5) {
            viewModel.loadMoreForTopic(cleanUrl, topicTitle)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { } // Removemos o TopAppBar para evitar duplicação e fixação do nome
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading && posts.isEmpty(),
            onRefresh = { viewModel.fetchTopicPosts(cleanUrl, topicTitle) },
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                // Header IDENTICO ao Instagram
                item(span = { GridItemSpan(3) }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar Circular
                            Box(
                                modifier = Modifier
                                    .size(86.dp)
                                    .clip(CircleShape)
                                    .background(Color.LightGray.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (topicInfo?.avatarUrl != null) {
                                    AsyncImage(
                                        model = topicInfo.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(text = topicTitle.take(1).uppercase(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            // Stats Row
                            Row(
                                modifier = Modifier.weight(1f).padding(start = 24.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProfileStatInsta(number = posts.size.toString(), label = "Posts")
                                ProfileStatInsta(number = "1.2k", label = "Followers")
                                ProfileStatInsta(number = "42", label = "Following")
                            }
                        }
                        
                        // Bio Section
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = topicTitle, 
                                fontWeight = FontWeight.Bold, 
                                style = MaterialTheme.typography.bodyLarge,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Blog Oficial • Conteúdo Digital",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Sincronização em tempo real de mídias e atualizações diretamente do fórum oficial.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            Text(
                                text = "simpcity.cr",
                                color = Color(0xFF00376B), // Azul link do Insta
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Tab Row Icons
                        TabRow(
                            selectedTabIndex = 0,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[0]),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    height = 1.dp
                                )
                            },
                            divider = { HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f)) }
                        ) {
                            Tab(selected = true, onClick = {}, icon = { Icon(Icons.Default.GridOn, null, modifier = Modifier.size(24.dp)) })
                            Tab(selected = false, onClick = {}, icon = { Icon(Icons.Outlined.VideoLibrary, null, modifier = Modifier.size(24.dp)) })
                            Tab(selected = false, onClick = {}, icon = { Icon(Icons.Outlined.Portrait, null, modifier = Modifier.size(24.dp)) })
                        }
                    }
                }

                items(
                    items = posts,
                    key = { post -> post.id },
                    contentType = { post -> if (post.mediaUrls.any { MediaUtils.isVideoUrl(it) }) "video" else "image" }
                ) { post ->
                    val firstMedia = post.mediaUrls.firstOrNull()
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { 
                                viewModel.setViewingMedia(post.mediaUrls, cleanUrl)
                                onPostClick(post.mediaUrls, 0) 
                            }
                    ) {
                        if (firstMedia != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(firstMedia)
                                    .decoderFactory(VideoFrameDecoder.Factory())
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            // Indicators estilo Insta
                            if (post.mediaUrls.size > 1) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp)
                                )
                            } else if (MediaUtils.isVideoUrl(firstMedia)) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (isLoading && posts.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileStatInsta(number: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = number, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = label, fontSize = 13.sp)
    }
}
