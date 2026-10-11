package com.example.xs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.xs.util.AppDiagnosticLogger

@Composable
fun SafeAsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    var imageUrl by remember(url) { mutableStateOf(url) }
    var hasFailedHighRes by remember(url) { mutableStateOf(false) }

    Box(
        modifier = modifier.background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .listener(
                    onError = { _, result ->
                        val err = result.throwable.message ?: "Erro desconhecido"
                        AppDiagnosticLogger.log("ERROR", "Erro ao carregar imagem ($imageUrl): $err")
                        if (!hasFailedHighRes) {
                            hasFailedHighRes = true
                            val fallback = if (imageUrl.contains(".jpg") && !imageUrl.contains(".md.jpg")) {
                                imageUrl.replace(".jpg", ".md.jpg")
                            } else if (imageUrl.contains(".png") && !imageUrl.contains(".md.png")) {
                                imageUrl.replace(".png", ".md.png")
                            } else {
                                null
                            }
                            if (fallback != null) {
                                AppDiagnosticLogger.log("MEDIA", "Fallback ativado para thumbnail: $fallback")
                                imageUrl = fallback
                            }
                        }
                    }
                )
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale
        )
    }
}
