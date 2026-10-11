package com.example.xs.extractor

import com.example.xs.util.AppDiagnosticLogger
import com.example.xs.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class BunkrExtractor(private val client: OkHttpClient) : MediaExtractorPlugin {
    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("bunkr.cr") || lower.contains("bunkr.ws") || lower.contains("bunkr.is") || lower.contains("bunkrr") || lower.contains("b-cdn.net")
    }

    override suspend fun extract(url: String): List<String> = withContext(Dispatchers.IO) {
        val results = scrapeBunkr(url, 0)
        if (results.isEmpty()) {
            AppDiagnosticLogger.log("EXTRACTOR", "Bunkr não encontrou mídias filhas em: $url")
            emptyList()
        } else {
            AppDiagnosticLogger.log("EXTRACTOR", "Bunkr extraiu ${results.size} itens de: $url")
            results
        }
    }

    private suspend fun scrapeBunkr(url: String, depth: Int): List<String> {
        if (depth > 2) return emptyList()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", Constants.USER_AGENT)
                .header("Referer", "https://simpcity.cr/")
                .build()
                
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val doc = Jsoup.parse(body, url)
                val media = mutableListOf<String>()

                doc.select("img.lightboxed, .image-container img, a[href*=/d/], a.download-button").forEach {
                    val src = it.absUrl("src").ifEmpty { it.absUrl("data-src").ifEmpty { it.absUrl("href") } }
                    if (src.isNotEmpty()) media.add(src)
                }

                val cdnRegex = "https://[\\w\\d.-]+\\.b-cdn\\.net/[^\"'\\s]+\\.(jpg|jpeg|png|webp)".toRegex(RegexOption.IGNORE_CASE)
                cdnRegex.findAll(body).forEach { media.add(it.value) }

                val items = doc.select("div.grid-images a[href], .image-container a[href], .grid-item a[href], .grid-images__item a[href]")
                if (items.isNotEmpty() && depth == 0) {
                    for (item in items.take(20)) {
                        val itemUrl = item.absUrl("href")
                        if (itemUrl.isNotEmpty() && (itemUrl.contains("/v/") || itemUrl.contains("/i/") || itemUrl.contains("/a/"))) {
                            media.addAll(scrapeBunkr(itemUrl, depth + 1))
                        }
                    }
                }

                return media.filter { link ->
                    val lower = link.lowercase().substringBefore("?")
                    val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                    val isInvalid = lower.endsWith(".rar") || lower.endsWith(".zip") || lower.endsWith(".7z") || lower.endsWith(".tar") || lower.endsWith(".mp4") || lower.endsWith(".ico") || lower.endsWith(".svg")
                    isImage && !isInvalid
                }.distinct()
            }
        } catch (e: Exception) {
            AppDiagnosticLogger.log("ERROR", "Erro BunkrExtractor: ${e.message}")
            return emptyList()
        }
    }
}
