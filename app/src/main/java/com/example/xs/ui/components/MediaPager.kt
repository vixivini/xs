package com.example.xs.ui.components

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.xs.util.MediaUtils

@Composable
fun MediaPager(
    mediaUrls: List<String>,
    modifier: Modifier = Modifier,
    shouldPlay: Boolean = true,
    onMediaClick: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val totalItems = mediaUrls.size
    val pagerState = rememberPagerState(pageCount = { totalItems })
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

    Box(modifier = modifier.background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0 // Evita carregar 40 itens na memória
        ) { page ->
            val mediaUrl = mediaUrls[page]
            val isVideo = MediaUtils.isVideoUrl(mediaUrl)
            val isDirectStream = MediaUtils.isDirectStream(mediaUrl)

            Box(
                modifier = Modifier.fillMaxSize().clickable { onMediaClick(page) },
                contentAlignment = Alignment.Center
            ) {
                if (isVideo) {
                    if (isDirectStream) {
                        // Se for MP4 real, toca no ExoPlayer
                        VideoPlayer(
                            url = mediaUrl,
                            modifier = Modifier.fillMaxSize(),
                            isMuted = true,
                            showControls = true,
                            shouldPlay = shouldPlay && currentPage == page
                        )
                    } else {
                        // Se for página HTML (Embed), mostra botão e NÃO manda pro ExoPlayer!
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Vídeo Externo",
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val intent = CustomTabsIntent.Builder().build()
                                    intent.launchUrl(context, Uri.parse(mediaUrl))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Assistir Vídeo Externo")
                            }
                        }
                    }
                } else {
                    // MÍDIA IMAGEM: REMOVIDO O TAMANHO FIXO DE 1080px QUE TRAVAVA O APP!
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mediaUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // Contador leve no topo (1/40) em vez de desenhar dezenas de bolinhas
        if (totalItems > 1) {
            Text(
                text = "${currentPage + 1}/$totalItems",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}