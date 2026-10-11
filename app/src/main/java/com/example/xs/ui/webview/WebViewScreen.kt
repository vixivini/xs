package com.example.xs.ui.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.xs.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    onFavoriteClick: (url: String, title: String) -> Unit,
    onLikeClick: (url: String, title: String) -> Unit,
    isFavorite: suspend (String) -> Boolean,
    isLiked: suspend (String) -> Boolean
) {
    var currentUrlState by remember { mutableStateOf(Constants.BASE_URL) }
    var titleState by remember { mutableStateOf("Browser") }
    var webView: WebView? by remember { mutableStateOf(null) }
    var currentIsFavorite by remember { mutableStateOf(false) }
    var currentIsLiked by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentUrlState) {
        currentIsFavorite = isFavorite(currentUrlState)
        currentIsLiked = isLiked(currentUrlState)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(titleState, maxLines = 1) },
                    actions = {
                        // Botão de GOSTO (Coração)
                        IconButton(onClick = {
                            webView?.url?.let { currentUrl ->
                                val currentTitle = webView?.title ?: "No Title"
                                onLikeClick(currentUrl, currentTitle)
                                scope.launch {
                                    delay(200)
                                    currentIsLiked = isLiked(currentUrl)
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (currentIsLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Gosto",
                                tint = if (currentIsLiked) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Botão de SEGUIR/FAVORITO (Estrela)
                        IconButton(onClick = {
                            webView?.url?.let { currentUrl ->
                                val currentTitle = webView?.title ?: "No Title"
                                onFavoriteClick(currentUrl, currentTitle)
                                scope.launch {
                                    delay(200)
                                    currentIsFavorite = isFavorite(currentUrl)
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (currentIsFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Seguir",
                                tint = if (currentIsFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
                if (progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        
                        // Configurações críticas para passar pelo Cloudflare
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        allowFileAccess = true
                        allowContentAccess = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        
                        // User Agent de Chrome Mobile moderno
                        userAgentString = Constants.USER_AGENT
                    }
                    
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            // Injeta script para esconder que é um WebView/Bot
                            view?.evaluateJavascript("""
                                (function() {
                                    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                                    window.chrome = { runtime: {}, loadTimes: function() {}, csi: function() {}, app: {} };
                                    if (!window.navigator.languages) {
                                        Object.defineProperty(navigator, 'languages', { get: () => ['pt-BR', 'pt', 'en-US', 'en'] });
                                    }
                                })();
                            """.trimIndent(), null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            progress = 100
                            url?.let { 
                                currentUrlState = it
                                titleState = view?.title ?: "Browser"
                            }
                            // Sincroniza os cookies assim que o desafio é resolvido
                            CookieManager.getInstance().flush()
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            // Detecta se é a requisição principal para aplicar o bypass de headers se necessário
                            // Mas no Android 7.0+, o WebView costuma enviar o X-Requested-With automaticamente.
                            // O truque de passar um Map vazio no loadUrl ajuda na primeira página, 
                            // mas para recursos internos (AJAX), precisamos ser mais agressivos se o site bloquear.
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            
                            // Mantém a navegação dentro do WebView para o mesmo domínio
                            if (url.contains("simpcity.cr")) {
                                val headers = mutableMapOf<String, String>()
                                headers["X-Requested-With"] = ""
                                view?.loadUrl(url, headers)
                                return true
                            }
                            return false
                        }
                    }
                    
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }

                        override fun getDefaultVideoPoster(): Bitmap? {
                            // Retorna um bitmap vazio para evitar o erro de CORS do android-webview-video-poster
                            // e possíveis erros de encode de PNG ao tentar carregar o poster padrão.
                            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                        }
                    }

                    // Força a remoção do cabeçalho que identifica o App Android
                    val headers = mutableMapOf<String, String>()
                    headers["X-Requested-With"] = "" 
                    loadUrl(Constants.BASE_URL, headers)
                    
                    webView = this
                }
            },
            update = { 
                webView = it
            }
        )
    }
}
