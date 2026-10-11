package com.example.xs.ui.components

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.xs.XsApplication
import com.example.xs.util.MediaUtils
import com.example.xs.util.Constants
import com.example.xs.util.AppDiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    isMuted: Boolean = false,
    showControls: Boolean = true,
    shouldPlay: Boolean = true,
    onRefreshRequest: () -> Unit = {}
) {
    // Força o player nativo (ExoPlayer) para absolutamente todos os vídeos por padrão
    NativeVideoPlayer(
        url = url, 
        modifier = modifier, 
        isMuted = isMuted,
        showControls = showControls,
        shouldPlay = shouldPlay,
        onRefreshRequest = onRefreshRequest
    )
}

@OptIn(UnstableApi::class)
@Composable
fun NativeVideoPlayer(
    url: String,
    modifier: Modifier,
    onRefreshRequest: () -> Unit,
    isMuted: Boolean = false,
    showControls: Boolean = true,
    shouldPlay: Boolean = true
) {
    val context = LocalContext.current
    val okHttpClient = (context.applicationContext as XsApplication).okHttpClient
    var errorMessage by remember(url) { mutableStateOf<String?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var playUri by remember(url) { mutableStateOf<Uri?>(null) }
    
    // Resolução assíncrona: se for link de embed, tenta extrair o .mp4/.m3u8 real antes de reproduzir
    LaunchedEffect(url) {
        isLoading = true
        val resolvedUrl = resolveEmbedToMp4(url, okHttpClient)
        Log.d("VideoPlayer", "URL original: $url -> URL resolvida para player: $resolvedUrl")
        
        val isHls = resolvedUrl.contains(".m3u8")
        if (resolvedUrl.contains("turbo.cr") && !isHls && !resolvedUrl.endsWith(".mp4")) {
            val localUri = com.example.xs.util.VideoDownloaderManager.downloadToTempFile(context, resolvedUrl, okHttpClient)
            if (localUri != null) {
                playUri = localUri
            } else {
                playUri = Uri.parse(resolvedUrl)
            }
        } else {
            playUri = Uri.parse(resolvedUrl)
        }
    }

    if (playUri == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    // Configuração do ExoPlayer com DefaultHttpDataSource.Factory e Headers HTTP Anti-Bloqueio
    val exoPlayer = remember(url) {
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://simpcity.cr/",
                    "Origin" to "https://simpcity.cr"
                )
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 5000, 500, 500)
            .build()

        AppDiagnosticLogger.log("VIDEO_REQUEST", "URL solicitada: $url | Headers: Referer=https://simpcity.cr/, Origin=https://simpcity.cr")

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                volume = if (isMuted) 0f else 1f
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        isLoading = false
                        val cause = error.cause?.message ?: "Desconhecida"
                        val errName = error.errorCodeName
                        val errCode = error.errorCode
                        errorMessage = "Erro na reprodução: ${error.localizedMessage ?: "Falha no stream"}"
                        AppDiagnosticLogger.log("VIDEO_FAIL", "errorCodeName: $errName | errorCode: $errCode | causa raiz: $cause | msg: ${error.message}")
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        val stateStr = when (state) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN"
                        }
                        AppDiagnosticLogger.log("VIDEO_STATE", "Estado alterado para: $stateStr")
                        if (state == Player.STATE_READY) {
                            isLoading = false
                            errorMessage = null
                        } else if (state == Player.STATE_BUFFERING) {
                            isLoading = true
                        }
                    }
                })
                prepare()
            }
    }

    // Atualiza apenas o MediaItem sem destruir o player (Transição Suave)
    LaunchedEffect(playUri) {
        playUri?.let { uri ->
            val currentMediaItem = exoPlayer.currentMediaItem
            if (currentMediaItem?.localConfiguration?.uri != uri) {
                val mediaItem = MediaItem.fromUri(uri)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = shouldPlay
            }
        }
    }

    LaunchedEffect(exoPlayer, shouldPlay) {
        exoPlayer.playWhenReady = shouldPlay
    }

    DisposableEffect(playUri) {
        onDispose {
            exoPlayer.release()
            playUri?.let { uri ->
                if (uri.scheme == "file" && uri.path?.contains("temp_videos") == true) {
                    com.example.xs.util.VideoDownloaderManager.deleteFile(uri)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = showControls
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                view.useController = showControls
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading && errorMessage == null) {
            CircularProgressIndicator(color = Color.White)
        }

        // APENAS se o ExoPlayer falhar fatalmente, exibe o botão de fallback para o navegador externo
        if (errorMessage != null) {
            VideoErrorUI(errorMessage!!, onRefreshRequest, url)
        }
    }
}

suspend fun resolveEmbedToMp4(url: String, okHttpClient: OkHttpClient): String = withContext(Dispatchers.IO) {
    if (!url.contains("embed") && !url.contains("turbo.cr") && !url.contains("/v/")) return@withContext url
    try {
        val targetUrl = if (url.contains("/v/")) url.replace("/v/", "/embed/") else url
        val startTime = System.currentTimeMillis()
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Referer", "https://simpcity.cr/")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val duration = System.currentTimeMillis() - startTime
            val code = response.code
            AppDiagnosticLogger.log("NET", "Embed GET $targetUrl -> Status: $code | Tempo: ${duration}ms")
            if (!response.isSuccessful) return@withContext url
            val body = response.body?.string() ?: ""
            val doc = Jsoup.parse(body, targetUrl)
            
            // 1. Check <source src="...">
            val sourceSrc = doc.select("video source[src], source[src]").firstOrNull()?.absUrl("src")
            if (!sourceSrc.isNullOrEmpty() && (sourceSrc.endsWith(".mp4") || sourceSrc.contains(".mp4") || sourceSrc.contains(".m3u8"))) {
                AppDiagnosticLogger.log("MEDIA", "Stream extraído via <source>: $sourceSrc")
                return@withContext sourceSrc
            }

            // 2. Regex for .mp4 or .m3u8 in body/scripts
            val regex = "https?://[^\"'\\s]+\\.(?:mp4|m3u8)(?:\\?[^\"'\\s]*)?".toRegex(RegexOption.IGNORE_CASE)
            val match = regex.find(body)
            if (match != null) {
                val found = match.value.replace("\\/", "/")
                AppDiagnosticLogger.log("MEDIA", "Stream extraído via Regex: $found")
                return@withContext found
            }
            AppDiagnosticLogger.log("MEDIA", "Nenhum stream direto encontrado no HTML do embed. Trecho HTML: ${body.take(200)}")
        }
    } catch (e: Exception) {
        AppDiagnosticLogger.log("ERROR", "Erro ao resolver embed para MP4 ($url): ${e.message}")
    }
    url
}

@Composable
fun VideoErrorUI(message: String, onRefreshRequest: () -> Unit, url: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefreshRequest) { Text("Tentar Novamente") }
            Button(
                onClick = {
                    try {
                        val customTabsIntent = androidx.browser.customtabs.CustomTabsIntent.Builder().build()
                        customTabsIntent.launchUrl(context, Uri.parse(url))
                    } catch (e: Exception) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) { Text("Abrir no Navegador Externo") }
        }
    }
}
