package com.example.xs.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xs.ui.MainViewModel
import com.example.xs.ui.components.MediaPostCard
import com.example.xs.util.MediaUtils
import com.example.xs.util.VideoPreloader
import com.example.xs.util.WebViewPool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: MainViewModel, 
    isExplore: Boolean = false,
    onMediaClick: (List<String>, Int) -> Unit,
    onProfileClick: (String, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val posts by (if (isExplore) viewModel.explorePosts else viewModel.feedPosts).collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val savedPosts by viewModel.savedPosts.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.scrollToTopEvent.collect {
            listState.animateScrollToItem(0)
        }
    }

    // Lógica de Pre-load
    val lastVisibleIndex by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }

    LaunchedEffect(posts, lastVisibleIndex) {
        if (!viewModel.shouldPreload()) return@LaunchedEffect

        // Pré-carrega os próximos 10 posts que contêm vídeos
        val preloadRange = (lastVisibleIndex + 1)..(lastVisibleIndex + 10)
        preloadRange.forEach { index ->
            if (index < posts.size) {
                posts[index].mediaUrls.forEach { url ->
                    if (MediaUtils.isVideoUrl(url)) {
                        if (MediaUtils.isWebViewVideo(url)) {
                            WebViewPool.preload(context, url)
                        } else {
                            VideoPreloader.preload(context, url)
                        }
                    }
                }
            }
        }
    }

    // Detect when scrolling near the bottom - trigger earlier (15 items before end)
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 15
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshFeed() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            if (posts.isEmpty() && !isRefreshing) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum conteúdo de mídia encontrado nos seus favoritos.\nArraste para baixo para recarregar.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(
                    items = posts,
                    key = { post -> post.id },
                    contentType = { post -> if (post.mediaUrls.any { MediaUtils.isVideoUrl(it) }) "video" else "image" }
                ) { post ->
                    val isFirstVisible = remember(listState) {
                        derivedStateOf {
                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            if (visibleItems.isEmpty()) false
                            else {
                                // Find the item that is most centered
                                val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                                val closestItem = visibleItems.minByOrNull { Math.abs((it.offset + it.size / 2) - viewportCenter) }
                                closestItem?.key == post.id
                            }
                        }
                    }

                    val isSaved = remember(savedPosts) { savedPosts.any { it.id == post.id } }
                    
                    // Busca informações do Blog/Tópico para uniformidade (Independência total)
                    val blogInfo = viewModel.getFavoriteTopic(post.postUrl)
                    
                    MediaPostCard(
                        post = post, 
                        shouldPlay = isFirstVisible.value,
                        isSaved = isSaved,
                        isFavorite = blogInfo?.isFollowed ?: false,
                        isLiked = blogInfo?.isLiked ?: false,
                        showLikeIcon = isExplore, // SÓ MOSTRA O CORAÇÃO NO PESQUISAR
                        blogName = blogInfo?.title ?: post.topicTitle,
                        blogAvatar = blogInfo?.avatarUrl,
                        onSaveClick = { viewModel.toggleSavePost(post) },
                        onFollowClick = {
                            val targetUrl = blogInfo?.url ?: post.postUrl
                            val targetTitle = blogInfo?.title ?: post.topicTitle
                            viewModel.toggleFavorite(targetUrl, targetTitle)
                        },
                        onProfileClick = { onProfileClick(post.postUrl, blogInfo?.title ?: post.topicTitle) },
                        onMediaClick = { urls, index ->
                            viewModel.setViewingMedia(urls, blogInfo?.url ?: post.postUrl)
                            onMediaClick(urls, index)
                        },
                        onPostUpdated = { id, urls -> viewModel.updateSavedPostMedia(id, urls) }
                    )
                }
                
                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
