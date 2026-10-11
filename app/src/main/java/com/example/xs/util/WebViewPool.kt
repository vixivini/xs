package com.example.xs.util

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.os.Message
import android.util.Log

object WebViewPool {
    private val pool = mutableMapOf<String, WebView>()
    private const val MAX_POOL_SIZE = 6
    private const val TAG = "WebViewPool"

    fun preload(context: Context, url: String) {
        val embedUrl = getEmbedUrl(url)
        if (pool.containsKey(embedUrl)) return
        
        if (pool.size >= MAX_POOL_SIZE) {
            val oldest = pool.keys.first()
            val wv = pool.remove(oldest)
            wv?.destroy()
        }

        val webView = createWebView(context)
        setupWebView(webView, isMuted = true, shouldPlay = false, embedUrl = embedUrl)
        
        val headers = mapOf("Referer" to "https://simpcity.cr/")
        webView.loadUrl(embedUrl, headers)
        pool[embedUrl] = webView
    }

    fun acquire(context: Context, url: String): Pair<WebView, Boolean> {
        val embedUrl = getEmbedUrl(url)
        val pooled = pool.remove(embedUrl)
        if (pooled != null) {
            (pooled.parent as? ViewGroup)?.removeView(pooled)
            return Pair(pooled, true)
        }
        return Pair(createWebView(context), false)
    }

    fun release(url: String, webView: WebView) {
        val embedUrl = getEmbedUrl(url)
        (webView.parent as? ViewGroup)?.removeView(webView)
        // Mute and pause when releasing
        applyPlaybackState(webView, isMuted = true, shouldPlay = false)
        
        if (!pool.containsKey(embedUrl) && pool.size < MAX_POOL_SIZE) {
            pool[embedUrl] = webView
        } else {
            webView.destroy()
        }
    }

    private fun createWebView(context: Context): WebView {
        return WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = Constants.USER_AGENT
                allowFileAccess = true
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(true)
            }
        }
    }

    fun setupWebView(webView: WebView, isMuted: Boolean, shouldPlay: Boolean, embedUrl: String) {
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                view?.evaluateJavascript("document.body.style.backgroundColor = 'black';", null)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val newUrl = request?.url?.toString() ?: return false
                if (newUrl.startsWith("intent://") || newUrl.startsWith("market://") || newUrl.startsWith("applinks:") || newUrl.startsWith("mailto:")) {
                    return true
                }
                val allowedDomains = listOf("turbo.cr", "bunkr", "b-cdn.net", "gofile.io", "pixeldrain.com", "simpcity.cr")
                val isAllowed = allowedDomains.any { newUrl.contains(it) } || 
                               newUrl == embedUrl || 
                               newUrl.startsWith("blob:") ||
                               newUrl.startsWith("data:")
                return !isAllowed
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val js = """
                    (function() {
                        var style = document.createElement('style');
                        style.innerHTML = 'body { background-color: black !important; } video { background-color: black !important; }';
                        document.head.appendChild(style);
                    })();
                """.trimIndent()
                view?.evaluateJavascript(js, null)
                applyPlaybackState(view, isMuted, shouldPlay)
                super.onPageFinished(view, url)
            }
        }

        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false
        }
    }

    fun applyPlaybackState(webView: WebView?, isMuted: Boolean, shouldPlay: Boolean) {
        val js = """
            (function() {
                window.open = function() { return null; };
                var links = document.getElementsByTagName('a');
                for (var i = 0; i < links.length; i++) {
                    links[i].target = '_self';
                }
                var vids = document.getElementsByTagName('video');
                for (var i = 0; i < vids.length; i++) {
                    var v = vids[i];
                    v.muted = $isMuted;
                    if ($shouldPlay) {
                        if (v.paused) {
                            var p = v.play();
                            if (p !== undefined) {
                                p.catch(function(error) {
                                    if (error.name !== 'AbortError') {
                                        console.error('Playback failed:', error);
                                    }
                                });
                            }
                        }
                    } else {
                        if (!v.paused) {
                            v.pause();
                        }
                    }
                }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    fun getEmbedUrl(url: String): String {
        return when {
            url.contains("turbo.cr/v/") -> url.replace("/v/", "/embed/")
            url.contains("turbo.cr/f/") -> url.replace("/f/", "/embed/")
            else -> url
        }
    }
    
    fun isWebViewVideo(url: String): Boolean {
        val path = url.lowercase().substringBefore("?")
        val isDirect = path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".webm") || 
                       path.endsWith(".mov") || path.contains(".mp4?") || path.contains(".m3u8?")
        
        return !isDirect && (
            url.contains("turbo.cr") || url.contains("bunkr") || 
            url.contains("gofile.io") || url.contains("pixeldrain.com")
        )
    }
}
