package com.example.xs.ui.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xs.ui.MainViewModel
import com.example.xs.ui.components.VideoPlayer
import com.example.xs.util.MediaUtils
import com.example.xs.util.VideoDownloaderManager
import com.example.xs.XsApplication
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(
    viewModel: MainViewModel,
    onProfileClick: (String, String) -> Unit
) {
    val posts by viewModel.feedPosts.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    
    val videoItems = remember(posts) {
        posts.flatMap { post ->
            val videosInPost = post.mediaUrls.filter { url ->
                val isTurbo = url.contains("turbo", ignoreCase = true)
                val isDirect = MediaUtils.isDirectVideo(url)
                if (isTurbo || isDirect) {
                    android.util.Log.d("ReelsFilter", "Incluindo: $url (Turbo: $isTurbo, Direct: $isDirect)")
                    true
                } else false
            }
            videosInPost.map { videoUrl ->
                ReelItem(
                    postId = post.id,
                    videoUrl = videoUrl,
                    postUrl = post.postUrl,
                    topicTitle = post.topicTitle,
                    author = post.author
                )
            }
        }
    }

    val context = LocalContext.current
    val okHttpClient = (context.applicationContext as XsApplication).okHttpClient
    
    // AUTO-CARREGAMENTO: Se abrir os Reels e o feed estiver vazio, carrega agora!
    LaunchedEffect(videoItems.isEmpty()) {
        if (videoItems.isEmpty() && !isRefreshing) {
            viewModel.refreshFeed()
        }
    }

    if (videoItems.isEmpty() && !isRefreshing) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Nenhum vídeo encontrado.\nArraste para baixo para atualizar.", 
                color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { videoItems.size })

    // GATILHO DE PAGINAÇÃO: Detecta quando está chegando no fim e carrega páginas anteriores do fórum
    LaunchedEffect(pagerState.currentPage) {
        if (videoItems.isNotEmpty() && pagerState.currentPage >= videoItems.size - 5) {
            viewModel.loadMore() // Chama a função que já temos no ViewModel para carregar páginas passadas
        }
    }

    // Lógica de Preload de Fundo (Sem pesar o player)
    LaunchedEffect(pagerState.currentPage) {
        val preloadRange = (pagerState.currentPage + 1)..(pagerState.currentPage + 5)
        preloadRange.forEach { index ->
            if (index < videoItems.size) {
                val nextUrl = videoItems[index].videoUrl
                if (nextUrl.contains("turbo")) {
                    launch { VideoDownloaderManager.downloadToTempFile(context, nextUrl, okHttpClient) }
                }
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshFeed() },
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2,
            key = { index -> videoItems[index].videoUrl + videoItems[index].postId } // CHAVE ESTÁVEL
        ) { page ->
            val item = videoItems[page]
            
            LaunchedEffect(item.postId, item.videoUrl) {
                if (!MediaUtils.isDirectVideo(item.videoUrl) && item.videoUrl.contains("turbo")) {
                    viewModel.refreshSavedPost(item.postId, item.postUrl, item.topicTitle)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                VideoPlayer(
                    url = item.videoUrl,
                    modifier = Modifier.fillMaxSize(),
                    shouldPlay = pagerState.currentPage == page,
                    isMuted = false,
                    showControls = true,
                    onRefreshRequest = { viewModel.refreshSavedPost(item.postId, item.postUrl, item.topicTitle) }
                )

                // UI LIMPA MAS COM IDENTIDADE DO BLOG (Opcional, mas atende o pedido de uniformidade)
                val blogInfo = viewModel.getFavoriteTopic(item.postUrl)
                val blogAvatar = blogInfo?.avatarUrl
                val blogName = blogInfo?.title ?: item.topicTitle

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .padding(bottom = 64.dp)
                        .clickable { onProfileClick(item.postUrl, blogName) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (blogAvatar != null) {
                            coil.compose.AsyncImage(
                                model = blogAvatar,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(blogName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = blogName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(2f, 2f), 4f)
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

data class ReelItem(
    val postId: String,
    val videoUrl: String,
    val postUrl: String,
    val topicTitle: String,
    val author: String
)
