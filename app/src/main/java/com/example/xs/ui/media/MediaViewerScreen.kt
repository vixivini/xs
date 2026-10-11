package com.example.xs.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.xs.ui.MainViewModel
import com.example.xs.ui.components.VideoPlayer
import com.example.xs.util.MediaUtils

@Composable
fun MediaViewerScreen(
    viewModel: MainViewModel,
    initialIndex: Int,
    onBack: () -> Unit
) {
    val mediaUrls by viewModel.viewingMedia.collectAsState()
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { mediaUrls.size })

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2,
            pageSpacing = 16.dp,
            userScrollEnabled = true 
        ) { page ->
            if (page < mediaUrls.size) {
                val url = mediaUrls[page]
                if (MediaUtils.isVideoUrl(url)) {
                    VideoPlayer(
                        url = url,
                        modifier = Modifier.fillMaxSize(),
                        onRefreshRequest = { viewModel.refreshFeed() }
                    )
                } else {
                    ZoomableMediaItem(url)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Espaçador para equilibrar o menu da direita e manter o contador no centro
            Box(modifier = Modifier.size(48.dp))
            
            if (mediaUrls.size > 1) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${mediaUrls.size}",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            
            // Spacer para manter o contador centralizado
            Box(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
fun ZoomableMediaItem(url: String) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Usamos um Box interno para aplicar as transformações sem consumir os gestos do Pager quando scale == 1
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 3f
                        }
                    }
                )
            }
            .pointerInput(scale) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()

                        // LÓGICA DE OURO: Só consome o evento se houver zoom 
                        // OU se o usuário estiver tentando fazer o gesto de pinça (zoom != 1)
                        // Se scale == 1 e for apenas um deslize, o evento passa para o Pager
                        if (scale > 1f || zoom != 1f) {
                            event.changes.forEach { it.consume() }
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                        
                        if (event.changes.all { !it.pressed }) break
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .decoderFactory(VideoFrameDecoder.Factory())
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}
