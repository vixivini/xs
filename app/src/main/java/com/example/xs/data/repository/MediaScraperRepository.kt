package com.example.xs.data.repository

import android.util.Log
import com.example.xs.data.model.MediaPost
import com.example.xs.util.Constants
import com.example.xs.util.MediaUtils
import com.example.xs.util.WebViewCookieJar
import com.example.xs.util.AppDiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

sealed class ScrapeResult {
    data class Success(val posts: List<MediaPost>, val prevPageUrl: String?, val topicAvatarUrl: String? = null) : ScrapeResult()
    object NeedsAuthentication : ScrapeResult()
    data class Error(val message: String) : ScrapeResult()
}

class MediaScraperRepository(private val client: OkHttpClient) {
    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, ScrapeResult>()
    private val activeJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<ScrapeResult>>()
    
    // Instancia o novo motor baseado em plugins (JDownloader style)
    private val extractorManager = com.example.xs.extractor.MediaExtractorManager(client)

    suspend fun fetchMediaFromTopic(url: String, topicTitle: String): ScrapeResult = withContext(Dispatchers.IO) {
        val cleanUrl = url.trimEnd('/')
        
        memoryCache[cleanUrl]?.let {
            AppDiagnosticLogger.log("CACHE", "Retornando resultado em cache para: $cleanUrl")
            return@withContext it
        }

        val existingJob = activeJobs[cleanUrl]
        if (existingJob != null) {
            AppDiagnosticLogger.log("CONCURRENCY", "Aguardando job ativo para URL duplicada: $cleanUrl")
            return@withContext existingJob.await()
        }

        val deferred = coroutineScope {
            async(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(cleanUrl)
                        .header("User-Agent", Constants.USER_AGENT)
                        .build()

                    val startTime = System.currentTimeMillis()
                    client.newCall(request).execute().use { response ->
                        val duration = System.currentTimeMillis() - startTime
                        val bodyString = response.body?.string() ?: ""
                        val code = response.code
                        val isCloudflare = isCloudflareChallenge(bodyString)

                        AppDiagnosticLogger.log("NET", "GET $cleanUrl -> Status: $code | Tempo: ${duration}ms | Cloudflare/Bloqueio: $isCloudflare")

                        if (code == 403 || code == 503 || isCloudflare) {
                            AppDiagnosticLogger.log("NET", "Bloqueio detectado (403/503/Cloudflare) para URL: $cleanUrl")
                            return@use ScrapeResult.NeedsAuthentication
                        }
                        if (!response.isSuccessful) {
                            AppDiagnosticLogger.log("NET", "Erro HTTP $code para URL: $cleanUrl")
                            return@use ScrapeResult.Error("HTTP ${response.code}")
                        }
                        
                        val doc = Jsoup.parse(bodyString, cleanUrl)
                        val lastPageUrl = doc.select("ul.pageNav-main li.pageNav-page:last-child a").attr("abs:href")
                        if (lastPageUrl.isNotEmpty() && lastPageUrl != cleanUrl && !cleanUrl.contains("page-")) {
                            return@use fetchMediaFromTopic(lastPageUrl, topicTitle)
                        }

                        val posts = parseTopicHtml(bodyString, topicTitle, cleanUrl)
                        val prevPageUrl = doc.select("a.pageNav-jump--prev, a.pageNav-prev").attr("abs:href").ifEmpty { null }
                        
                        val topicAvatar = doc.select("meta[property=og:image]").attr("content").ifEmpty {
                            doc.select("article.message--post .message-avatar img").firstOrNull()?.attr("abs:src")
                        }

                        val result = ScrapeResult.Success(posts, prevPageUrl, topicAvatar)
                        memoryCache[cleanUrl] = result
                        result
                    }
                } catch (e: Exception) {
                    AppDiagnosticLogger.log("ERROR", "Exceção em fetchMediaFromTopic ($cleanUrl): ${e.message}")
                    ScrapeResult.Error(e.message ?: "Erro desconhecido")
                } finally {
                    activeJobs.remove(cleanUrl)
                }
            }
        }

        activeJobs[cleanUrl] = deferred
        return@withContext deferred.await()
    }

    private fun isCloudflareChallenge(html: String): Boolean = html.contains("cf-challenge") || html.contains("Turnstile")

    private suspend fun parseTopicHtml(html: String, topicTitle: String, topicUrl: String): List<MediaPost> {
        val doc: Document = Jsoup.parse(html, topicUrl)
        val posts = doc.select("article.message--post")
        val mediaPosts = mutableListOf<MediaPost>()

        for (post in posts) {
            val postId = post.attr("data-content").filter { it.isDigit() }.ifEmpty { post.id() }
            val author = post.attr("data-author")
            val messageContent = post.selectFirst(".message-inner .message-cell--main .message-content") ?: continue
            
            val mediaDiscovery = mutableSetOf<String>()
            val mediaSignatures = mutableSetOf<String>()
            val externalLinks = mutableListOf<String>()

            fun addUniqueMedia(url: String) {
                val clean = cleanMediaUrl(url)
                if (isValidMedia(clean)) {
                    val sig = getMediaSignature(clean)
                    if (mediaSignatures.add(sig)) {
                        mediaDiscovery.add(clean)
                        AppDiagnosticLogger.log("MEDIA", "Mídia única adicionada: $clean (Sig: $sig)")
                    } else {
                        AppDiagnosticLogger.log("MEDIA", "Mídia duplicada ignorada: $clean (Sig: $sig)")
                    }
                }
            }
            
            // 1. CRAWLER DE IMAGENS (Busca profunda em anexos, galerias e imagens incorporadas)
            messageContent.select("img, [data-src], [data-url]").forEach { el ->
                val src = el.attr("abs:data-src").ifEmpty { 
                    el.attr("abs:data-url").ifEmpty { el.absUrl("src") }
                }
                
                var finalUrl = src
                val parentLink = el.parents().select("a[href]").first()
                if (parentLink != null) {
                    val href = parentLink.absUrl("href")
                    if (href.isNotEmpty() && isDirectMedia(href) && !href.contains(".md.") && !href.contains("/th/")) {
                        finalUrl = href
                    }
                }
                addUniqueMedia(finalUrl)
            }

            // 2. CRAWLER DE EMBEDS E LINKS (Lógica JDownloader: Analisa cada link externo)
            messageContent.select("a[href], iframe[src], video source[src]").forEach { el ->
                val url = if (el.tagName() == "a") el.absUrl("href") else el.absUrl("src")
                if (url.isNotEmpty()) {
                    if (MediaUtils.isDirectMedia(url)) {
                        addUniqueMedia(url)
                    } else if (extractorManager.isSupported(url)) {
                        val extractedList = extractorManager.extract(url)
                        extractedList.forEach { extractedUrl -> addUniqueMedia(extractedUrl) }
                    } else {
                        externalLinks.add(url)
                    }
                }
            }

            // 3. REGEX DISCOVERY (Último recurso: busca links soltos no texto que o HTML não marcou)
            val bodyText = messageContent.html()
            val linkRegex = "https?://[\\w\\d.-]+\\.[\\w\\d]{2,}/[\\w\\d._/\\-?&=%]*".toRegex()
            linkRegex.findAll(bodyText).forEach { match ->
                val url = match.value
                if (MediaUtils.isDirectMedia(url)) {
                    addUniqueMedia(url)
                } else if (extractorManager.isSupported(url) && !mediaDiscovery.any { it.contains(url) }) {
                     val extractedList = extractorManager.extract(url)
                     extractedList.forEach { extractedUrl -> addUniqueMedia(extractedUrl) }
                }
            }

            if (mediaDiscovery.isNotEmpty() || externalLinks.isNotEmpty()) {
                val postUrl = post.selectFirst(".message-attribution-main a")?.absUrl("href") ?: topicUrl
                val timestamp = post.selectFirst("time[data-time]")?.attr("data-time")?.toLongOrNull()?.times(1000) ?: 0L
                mediaPosts.add(MediaPost(
                    postId, author, messageContent.html(), 
                    mediaDiscovery.toList(), externalLinks.distinct(), 
                    timestamp, topicTitle, postUrl
                ))
            }
        }
        return mediaPosts
    }

    private fun getMediaSignature(url: String): String {
        val fileName = url.substringAfterLast("/").substringBefore("?")
        return fileName
            .replace(".md", "")
            .replace(".thumb", "")
            .substringBeforeLast(".")
    }


    private fun isValidMedia(url: String): Boolean {
        if (url.isEmpty() || url.contains("data:image")) return false
        val lower = url.lowercase()
        
        // 1. Blacklist estrita de ativos de interface, logos e extensões compactadas encadeadas (.rar.png, .zip.png, etc.)
        val blacklist = listOf(
            "favicon", "fav.ico", ".ico",
            "logo", "og-logo", "logo-small", "meta.png",
            "pixeldrain_32.png", "assets/img/",
            "twemoji", "emoji", "avatar", "smilies", "res/img/"
        )
        if (blacklist.any { lower.contains(it) }) return false

        val compoundInvalid = listOf(".rar.", ".zip.", ".7z.", ".tar.", "-rar-", "_rar_", "-zip-", "_zip_")
        if (compoundInvalid.any { lower.contains(it) }) return false

        // 2. Verificar dimensões explícitas no nome menores que 150px (ex: 16x16, 32x32, 100x100, favicon16)
        val dimensionRegex = "(?:\\D|^)(\\d{1,3})x(\\d{1,3})(?:\\D|$)".toRegex()
        dimensionRegex.find(lower)?.let { match ->
            val w = match.groupValues[1].toIntOrNull() ?: 0
            val h = match.groupValues[2].toIntOrNull() ?: 0
            if (w in 1..149 || h in 1..149) return false
        }
        val smallNumberRegex = "(?:icon|fav|thumb|sm|small)[-_]?(\\d{2,3})(?:\\D|$)".toRegex()
        smallNumberRegex.find(lower)?.let { match ->
            val num = match.groupValues[1].toIntOrNull() ?: 0
            if (num in 1..149) return false
        }

        val validExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".mp4", ".mov", ".webm", ".mkv", ".m3u8")
        val path = lower.substringBefore("?")
        if (!validExtensions.any { path.endsWith(it) || lower.contains("$it?") } && !isSupportedHost(url)) {
            return false
        }
        return true
    }

    private fun cleanMediaUrl(url: String): String {
        val lower = url.lowercase()
        // Para a rota /panda/, MANTÉM A URL EXATA (incluindo /th/), pois remover o /th/ gera HTTP 403
        if (lower.contains("/panda/")) {
            return url.substringBefore("#")
        }
        
        // Para /images4/ ou outras rotas, aplica a normalização de thumbnail
        val clean = url.replace("/thumbnails/", "/full/")
            .replace(".thumb.", ".")
            .replace(".md.jpg", ".jpg")
            .replace(".md.png", ".png")
            .replace(".sm.jpg", ".jpg")
            .replace(".small.jpg", ".jpg")
        
        // Remove fragmentos indesejados como #item=4 se presentes
        return clean.substringBefore("#")
    }

    private fun isSupportedHost(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("turbo.cr") || lower.contains("bunkr") || 
               lower.contains("bunkrr") || lower.contains("gofile.io") || 
               lower.contains("pixeldrain.com")
    }

    suspend fun resolveTurboStream(embedUrl: String): String? = withContext(Dispatchers.IO) {
        val targetUrl = when {
            embedUrl.contains("/v/") -> embedUrl.replace("/v/", "/embed/")
            embedUrl.contains("/f/") -> embedUrl.replace("/f/", "/embed/")
            else -> embedUrl
        }
        
        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://simpcity.cr/")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: ""

                val regex1 = """<source[^>]+src=["']([^"']+\.(?:mp4|m3u8)[^"']*)["']""".toRegex(RegexOption.IGNORE_CASE)
                val regex2 = """(?:file|source|src)\s*:\s*["']([^"']+\.(?:mp4|m3u8)[^"']*)["']""".toRegex(RegexOption.IGNORE_CASE)
                val regex3 = """https?://[^"'\s]+\.(?:mp4|m3u8)(?:\?[^"'\s]*)?""".toRegex(RegexOption.IGNORE_CASE)

                val m1 = regex1.find(body)?.groupValues?.get(1)
                if (!m1.isNullOrEmpty()) return@withContext m1.replace("\\/", "/")

                val m2 = regex2.find(body)?.groupValues?.get(1)
                if (!m2.isNullOrEmpty()) return@withContext m2.replace("\\/", "/")

                val m3 = regex3.find(body)?.value
                if (!m3.isNullOrEmpty()) return@withContext m3.replace("\\/", "/")

                return@withContext scrapeTurboCr(targetUrl)
            }
        } catch (e: Exception) {
            AppDiagnosticLogger.log("ERROR", "Erro no resolveTurboStream ($embedUrl): ${e.message}")
            null
        }
    }

    /**
     * Motor de busca estilo JDownloader: Entra nos links e extrai tudo o que for mídia
     */
    private suspend fun crawlExternalHost(url: String, mediaList: MutableSet<String>, linkList: MutableList<String>) {
        val lowerUrl = url.lowercase()
        
        val isFolderLink = lowerUrl.contains("gofile.io/d/") || 
                           lowerUrl.contains("bunkr.cr/a/") || lowerUrl.contains("bunkr.ws/a/") || lowerUrl.contains("bunkr.is/a/") ||
                           lowerUrl.contains("pixeldrain.com/l/") || lowerUrl.contains("pixeldrain.com/u/")

        when {
            lowerUrl.contains("turbo.cr") -> {
                val direct = resolveTurboStream(url)
                if (direct != null) {
                    mediaList.add(direct)
                    AppDiagnosticLogger.log("MEDIA", "Vídeo Turbo.cr direto extraído (Segundo Nível): $direct")
                } else {
                    // Fallback sem descarte: mantém o link original do embed para renderizar o botão externo
                    mediaList.add(url)
                    AppDiagnosticLogger.log("MEDIA", "Vídeo Turbo.cr mantido como embed externo (sem descarte): $url")
                }
            }
            lowerUrl.contains("bunkr") || lowerUrl.contains("bunkrr") -> {
                val bunkrMedia = scrapeBunkr(url)
                if (bunkrMedia.isNotEmpty()) {
                    mediaList.addAll(bunkrMedia)
                    AppDiagnosticLogger.log("MEDIA", "Mídias Bunkr extraídas (${bunkrMedia.size} itens) de: $url")
                } else {
                    AppDiagnosticLogger.log("MEDIA", "Pasta/Álbum Bunkr sem itens válidos ignorado: $url")
                }
            }
            lowerUrl.contains("gofile.io") -> {
                val gofileMedia = scrapeGofile(url)
                if (gofileMedia.isNotEmpty()) {
                    mediaList.addAll(gofileMedia)
                    AppDiagnosticLogger.log("MEDIA", "Mídias Gofile extraídas (${gofileMedia.size} itens) de: $url")
                } else {
                    AppDiagnosticLogger.log("MEDIA", "Pasta Gofile sem itens válidos ignorada: $url")
                }
            }
            lowerUrl.contains("pixeldrain.com") -> {
                val pixMedia = scrapePixeldrain(url)
                if (pixMedia.isNotEmpty()) {
                    mediaList.addAll(pixMedia)
                    AppDiagnosticLogger.log("MEDIA", "Mídias Pixeldrain extraídas (${pixMedia.size} itens) de: $url")
                } else {
                    AppDiagnosticLogger.log("MEDIA", "Link Pixeldrain de álbum/falha ignorado: $url")
                }
            }
            else -> {
                if (!isFolderLink) {
                    linkList.add(url)
                }
            }
        }
    }

    private fun isDirectMedia(url: String): Boolean {
        val lowerUrl = url.lowercase()
        val path = lowerUrl.substringBefore("?")
        val extensions = listOf(".mp4", ".mov", ".webm", ".mkv", ".avi", ".m3u8", ".mpd", ".jpg", ".jpeg", ".png", ".gif", ".webp")
        if (extensions.any { path.endsWith(it) || lowerUrl.contains("$it?") }) return true
        
        // Links diretos de hosts conhecidos extraídos pelos scrapers
        if (lowerUrl.contains("bunkr") && (lowerUrl.contains("/d/") || lowerUrl.contains("stream."))) return true
        if (lowerUrl.contains("gofile.io") && lowerUrl.contains("/download/")) return true
        if (lowerUrl.contains("b-cdn.net")) return true
        
        return false
    }

    private suspend fun scrapeTurboCr(url: String, depth: Int = 0): String? = withContext(Dispatchers.IO) {
        if (depth > 2) return@withContext null
        
        val scrapeUrl = when {
            url.contains("/v/") -> url.replace("/v/", "/embed/")
            url.contains("/f/") -> url.replace("/f/", "/embed/")
            else -> url
        }
        
        try {
            val request = Request.Builder()
                .url(scrapeUrl)
                .header("User-Agent", Constants.USER_AGENT)
                .header("Referer", "https://simpcity.cr/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "cross-site")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val doc = Jsoup.parse(body, scrapeUrl)

                // 1. Lógica avançada 2025/2026 estilo JDownloader:
                // Tenta encontrar URLs que estão "quebradas" ou concatenadas em scripts
                val scripts = doc.select("script").map { it.html() }
                
                // Padrão 1: Fontes em objetos JS (sources: [...], file: "...")
                val sourceRegex = "(?:file|src|sources?|url|p)\"??\\s*[:=]\\s*\"??(https?://[^\"]+\\.(?:mp4|m3u8|webm)[^\"]*)\"??".toRegex(RegexOption.IGNORE_CASE)
                
                Log.d("TurboCrawler", "Analisando Turbo.cr: $scrapeUrl")

                // Padrão 2: Captura de links em Base64 (Padrão 2025 - Turbo.cr frequentemente ofusca)
                val base64Regex = "(?:\"|')(aHR0cHM6[a-zA-Z0-9+/]+={0,2})(?:\"|')".toRegex()
                
                // Padrão 3: Escaped URLs (https:\/\/...)
                val escapedUrlRegex = "https?:\\\\/\\\\/[\\\\/\\w\\d\\.\\-]+?\\.(?:mp4|m3u8|webm)[\\w\\d\\.\\/\\-?&=%]*".toRegex(RegexOption.IGNORE_CASE)
                
                // Padrão 4: Captura de JSON em scripts (Padrão 2026)
                val jsonSourceRegex = "\"file\"\\s*:\\s*\"(https?://.*?)\"".toRegex()

                // Se houver redirect ou URL direta antes de carregar o HTML
                if (MediaUtils.isDirectStream(response.request.url.toString())) {
                    val finalUrl = response.request.url.toString()
                    AppDiagnosticLogger.log("EXTRACTOR", "Turbo.cr extraído via Redirect: $finalUrl")
                    return@withContext finalUrl
                }

                for (script in scripts) {
                    // Padrão 2026: Procura qualquer URL que aponte para um servidor de mídia conhecido
                    val anyUrlRegex = "https?://[\\w\\d.-]+(?:turbo|cdn|srv|storage)[\\w\\d.-]+\\.[\\w\\d]{2,}/[\\w\\d._/\\-?&=%]*".toRegex(RegexOption.IGNORE_CASE)
                    anyUrlRegex.findAll(script).forEach { match ->
                        val urlFound = match.value.replace("\\/", "/")
                        if (MediaUtils.isDirectVideo(urlFound) || urlFound.contains("/storage/")) {
                            Log.d("TurboCrawler", "Descoberta profunda de VÍDEO: $urlFound")
                            return@withContext urlFound
                        }
                    }

                    // Tenta capturar a string ofuscada do decodeURI (Padrão 2025/2026)
                    if (script.contains("decodeURI(")) {
                        val obfuscatedRegex = "(?:decodeURI|p|file|url)\\(\"([^\"]+)\"\\)".toRegex()
                        obfuscatedRegex.findAll(script).forEach { match ->
                            val rawEncoded = match.groupValues[1]
                            try {
                                val raw = java.net.URLDecoder.decode(rawEncoded, "UTF-8")
                                // Tenta um descodificador de deslocamento de caracteres (comum no Turbo)
                                for (shift in -30..30) {
                                    if (shift == 0) continue
                                    val decoded = raw.map { (it.code - shift).toChar() }.joinToString("")
                                    if (decoded.contains("https://") && (decoded.contains(".mp4") || decoded.contains(".m3u8"))) {
                                        val finalUrl = "https://" + decoded.substringAfter("https://").substringBefore("\"").substringBefore("'")
                                        Log.d("TurboCrawler", "Desofuscado com sucesso (shift $shift): $finalUrl")
                                        return@withContext finalUrl
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    }

                    // Tenta fontes diretas
                    sourceRegex.find(script)?.groupValues?.get(1)?.let {
                        val clean = it.replace("\\/", "/")
                        Log.d("TurboCrawler", "Encontrado sourceRegex: $clean")
                        if (MediaUtils.isDirectVideo(clean)) return@withContext clean
                    }

                    // Tenta JSON sources
                    jsonSourceRegex.find(script)?.groupValues?.get(1)?.let {
                        val clean = it.replace("\\/", "/")
                        Log.d("TurboCrawler", "Encontrado jsonSourceRegex: $clean")
                        if (MediaUtils.isDirectMedia(clean)) return@withContext clean
                    }

                    // Tenta URLs escapadas
                    escapedUrlRegex.find(script)?.value?.let {
                        val clean = it.replace("\\/", "/")
                        Log.d("TurboCrawler", "Encontrado escapedUrlRegex: $clean")
                        if (MediaUtils.isDirectMedia(clean)) return@withContext clean
                    }
                    
                    // Tenta Base64
                    base64Regex.findAll(script).forEach { match ->
                        try {
                            val decoded = String(android.util.Base64.decode(match.groupValues[1], android.util.Base64.DEFAULT))
                            Log.d("TurboCrawler", "Encontrado Base64 decoded: $decoded")
                            if (MediaUtils.isDirectMedia(decoded)) return@withContext decoded
                        } catch (e: Exception) {}
                    }
                }

                // 2. Busca em Atributos de Dados e JSON-LD
                doc.select("[data-config], [data-player-setup], [type=application/ld+json], .video-player-container").forEach { el ->
                    val content = if (el.tagName() == "script") el.html() else el.attr("data-config").ifEmpty { el.attr("data-player-setup") }
                    if (content.isNotEmpty()) {
                        sourceRegex.find(content)?.groupValues?.get(1)?.let { return@withContext it.replace("\\/", "/") }
                        jsonSourceRegex.find(content)?.groupValues?.get(1)?.let { return@withContext it.replace("\\/", "/") }
                    }
                }

                // 3. Tenta capturar o link de "Download" real com seguimento de redirecionamento
                val downloadLink = doc.select("a[href*=download], a.btn-download, a:contains(Download), a[href*=/dl/]").firstOrNull()?.absUrl("href")
                if (downloadLink != null && downloadLink != scrapeUrl) {
                    try {
                        val headRequest = Request.Builder()
                            .url(downloadLink)
                            .header("User-Agent", Constants.USER_AGENT)
                            .header("Referer", scrapeUrl)
                            .build() 
                        
                        client.newCall(headRequest).execute().use { headRes ->
                            val finalUrl = headRes.request.url.toString()
                            val contentType = headRes.header("Content-Type") ?: ""
                            
                            if (MediaUtils.isDirectMedia(finalUrl) && !contentType.contains("text/html")) {
                                return@withContext finalUrl
                            }
                            
                            // Se o corpo do download tiver o link real (algumas páginas /dl/ redirecionam para outra página HTML)
                            if (contentType.contains("text/html")) {
                                val downloadBody = headRes.body?.string() ?: ""
                                sourceRegex.find(downloadBody)?.groupValues?.get(1)?.let { return@withContext it.replace("\\/", "/") }
                                escapedUrlRegex.find(downloadBody)?.value?.let { return@withContext it.replace("\\/", "/") }
                            }
                        }
                    } catch (e: Exception) {}
                }

                // 4. Fallback: Metadados OG
                val ogVideo = doc.select("meta[property=og:video], meta[property=twitter:player:stream], meta[name=twitter:player:stream]").attr("content")
                if (ogVideo.isNotEmpty() && MediaUtils.isDirectMedia(ogVideo)) return@withContext ogVideo

                // 5. Fallback: Tags HTML5 e Iframe recursive
                val videoSrc = doc.select("video source, video, iframe").firstOrNull()?.let { 
                    val src = it.absUrl("src").ifEmpty { it.absUrl("data-src") }
                    if ((src.contains("turbo.cr/embed/") || src.contains("turbo.cr/v/")) && src != scrapeUrl) {
                        return@withContext scrapeTurboCr(src, depth + 1)
                    }
                    src
                }
                // 6. Tentativa JDownloader 2026: Varredura de strings XOR/Base64 em todo o corpo
                val rawLinks = "https?://[^\"]+".toRegex().findAll(body).map { it.value }.toList()
                for (link in rawLinks) {
                    if (link.contains("turbo") && MediaUtils.isDirectMedia(link)) {
                        Log.d("TurboCrawler", "Link bruto encontrado: $link")
                        return@withContext link
                    }
                }

                if (!videoSrc.isNullOrEmpty() && MediaUtils.isDirectMedia(videoSrc)) {
                    Log.d("TurboCrawler", "Encontrado videoSrc: $videoSrc")
                    return@withContext videoSrc
                }
                
                Log.d("TurboCrawler", "Nenhum link direto encontrado para: $scrapeUrl")
                null
            }
        } catch (e: Exception) { 
            Log.e("TurboCrawler", "Erro ao analisar Turbo.cr: ${e.message}")
            null 
        }
    }

    private suspend fun scrapePixeldrain(url: String): List<String> = withContext(Dispatchers.IO) {
        val lower = url.lowercase()
        try {
            if (lower.contains("/l/") || lower.contains("/u/")) {
                val listId = url.substringAfterLast("/").substringBefore("?")
                val apiUrl = "https://pixeldrain.com/api/list/$listId"
                val request = Request.Builder().url(apiUrl).header("User-Agent", Constants.USER_AGENT).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: ""
                        val gson = com.google.gson.Gson()
                        val obj = gson.fromJson(json, com.google.gson.JsonObject::class.java)
                        val files = obj?.getAsJsonArray("files")
                        val results = mutableListOf<String>()
                        if (files != null) {
                            for (f in files) {
                                val fileObj = f.asJsonObject
                                val fileId = fileObj.get("id")?.asString
                                val name = fileObj.get("name")?.asString ?: ""
                                val lowerName = name.lowercase()
                                val isImage = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp")
                                if (!fileId.isNullOrEmpty() && isImage) {
                                    results.add("https://pixeldrain.com/api/file/$fileId")
                                }
                            }
                        }
                        return@withContext results
                    }
                }
            } else {
                val id = url.substringAfterLast("/").substringBefore("?")
                if (id.isNotEmpty()) {
                    return@withContext listOf("https://pixeldrain.com/api/file/$id")
                }
            }
        } catch (e: Exception) {
            AppDiagnosticLogger.log("ERROR", "Erro no scrapePixeldrain ($url): ${e.message}")
        }
        emptyList()
    }

    private suspend fun scrapeBunkr(url: String, depth: Int = 0): List<String> = withContext(Dispatchers.IO) {
        if (depth > 2) return@withContext emptyList()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", Constants.USER_AGENT)
                .header("Referer", MediaUtils.getReferer(url))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val doc = Jsoup.parse(body, url)
                val media = mutableListOf<String>()

                // 1. Tags de imagem direta
                doc.select("img.lightboxed, .image-container img, a[href*=/d/], a.download-button").forEach {
                    val src = it.absUrl("src").ifEmpty { it.absUrl("data-src").ifEmpty { it.absUrl("href") } }
                    if (src.isNotEmpty()) {
                        media.add(src)
                    }
                }

                // 2. Regex para links CDN de imagem no corpo
                val cdnRegex = "https://[\\w\\d.-]+\\.b-cdn\\.net/[^\"'\\s]+\\.(jpg|jpeg|png|webp)".toRegex(RegexOption.IGNORE_CASE)
                cdnRegex.findAll(body).forEach { media.add(it.value) }

                // 3. Se for pasta/álbum (grid), extrai os itens recursivamente
                val items = doc.select("div.grid-images a[href], .image-container a[href], .grid-item a[href], .grid-images__item a[href]")
                if (items.isNotEmpty() && depth == 0) {
                    for (item in items.take(20)) {
                        val itemUrl = item.absUrl("href")
                        if (itemUrl.isNotEmpty() && (itemUrl.contains("/v/") || itemUrl.contains("/i/") || itemUrl.contains("/a/"))) {
                            media.addAll(scrapeBunkr(itemUrl, depth + 1))
                        }
                    }
                }

                // Filtragem rigorosa: CAPTURAR APENAS imagens (.jpg, .jpeg, .png, .webp). Descartar .rar, .zip, .7z, .tar, .mp4, etc.
                media.filter { link ->
                    val lower = link.lowercase().substringBefore("?")
                    val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                    val isInvalid = lower.endsWith(".rar") || lower.endsWith(".zip") || lower.endsWith(".7z") || lower.endsWith(".tar") || lower.endsWith(".mp4") || lower.endsWith(".ico") || lower.endsWith(".svg")
                    isImage && !isInvalid
                }.distinct()
            }
        } catch (e: Exception) { 
            emptyList() 
        }
    }

    private suspend fun scrapeGofile(url: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val contentId = when {
                url.contains("/d/") -> url.substringAfter("/d/").substringBefore("?")
                url.contains("?c=") -> url.substringAfter("?c=").substringBefore("&")
                else -> url.substringAfterLast("/").substringBefore("?")
            }
            if (contentId.isEmpty()) return@withContext emptyList()

            val apiUrl = "https://api.gofile.io/contents/$contentId?wt=4fd6sg89d7s6"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", Constants.USER_AGENT)
                .header("Accept", "application/json")
                .header("Origin", "https://gofile.io")
                .header("Referer", "https://gofile.io/")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val jsonString = response.body?.string() ?: ""
                val images = mutableListOf<String>()

                val gson = com.google.gson.Gson()
                val jsonObject = gson.fromJson(jsonString, com.google.gson.JsonObject::class.java)
                val dataObj = jsonObject?.getAsJsonObject("data")
                val contentsObj = dataObj?.getAsJsonObject("contents")

                if (contentsObj != null) {
                    for ((_, value) in contentsObj.entrySet()) {
                        val item = value.asJsonObject
                        val link = item.get("link")?.asString ?: continue
                        val mimetype = item.get("mimetype")?.asString ?: ""
                        val name = item.get("name")?.asString ?: ""
                        
                        val lower = link.lowercase()
                        val lowerName = name.lowercase()
                        val isImage = mimetype.startsWith("image/") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                        val isArchive = lowerName.endsWith(".rar") || lowerName.endsWith(".zip") || lowerName.endsWith(".7z") || lowerName.endsWith(".tar") || lower.endsWith(".mp4") || lower.endsWith(".iso") || lower.endsWith(".exe")

                        if (isImage && !isArchive) {
                            images.add(link)
                            AppDiagnosticLogger.log("MEDIA", "Gofile imagem direta extraída: $link")
                        } else {
                            AppDiagnosticLogger.log("MEDIA", "Gofile arquivo ignorado (compactado/não-imagem): $name ($link)")
                        }
                    }
                }
                images
            }
        } catch (e: Exception) {
            AppDiagnosticLogger.log("ERROR", "Erro no scrapeGofile ($url): ${e.message}")
            emptyList()
        }
    }
}
