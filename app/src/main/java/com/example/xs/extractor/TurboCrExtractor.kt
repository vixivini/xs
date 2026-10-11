package com.example.xs.extractor

import com.example.xs.util.AppDiagnosticLogger
import com.example.xs.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class TurboCrExtractor(private val client: OkHttpClient) : MediaExtractorPlugin {
    override fun canHandle(url: String): Boolean {
        return url.lowercase().contains("turbo.cr")
    }

    override suspend fun extract(url: String): List<String> = withContext(Dispatchers.IO) {
        val targetUrl = when {
            url.contains("/v/") -> url.replace("/v/", "/embed/")
            url.contains("/f/") -> url.replace("/f/", "/embed/")
            else -> url
        }

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://simpcity.cr/")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext listOf(url)
                val body = response.body?.string() ?: ""

                val regex1 = """<source[^>]+src=["']([^"']+\.(?:mp4|m3u8)[^"']*)["']""".toRegex(RegexOption.IGNORE_CASE)
                val regex2 = """(?:file|source|src)\s*:\s*["']([^"']+\.(?:mp4|m3u8)[^"']*)["']""".toRegex(RegexOption.IGNORE_CASE)
                val regex3 = """https?://[^"'\s]+\.(?:mp4|m3u8)(?:\?[^"'\s]*)?""".toRegex(RegexOption.IGNORE_CASE)

                val m1 = regex1.find(body)?.groupValues?.get(1)
                if (!m1.isNullOrEmpty()) {
                    val finalUrl = m1.replace("\\/", "/")
                    AppDiagnosticLogger.log("EXTRACTOR", "Turbo.cr extraído via regex1: $finalUrl")
                    return@withContext listOf(finalUrl)
                }

                val m2 = regex2.find(body)?.groupValues?.get(1)
                if (!m2.isNullOrEmpty()) {
                    val finalUrl = m2.replace("\\/", "/")
                    AppDiagnosticLogger.log("EXTRACTOR", "Turbo.cr extraído via regex2: $finalUrl")
                    return@withContext listOf(finalUrl)
                }

                val m3 = regex3.find(body)?.value
                if (!m3.isNullOrEmpty()) {
                    val finalUrl = m3.replace("\\/", "/")
                    AppDiagnosticLogger.log("EXTRACTOR", "Turbo.cr extraído via regex3: $finalUrl")
                    return@withContext listOf(finalUrl)
                }

                AppDiagnosticLogger.log("EXTRACTOR", "Turbo.cr falhou ao extrair link direto, mantendo original como fallback: $url")
                return@withContext listOf(url)
            }
        } catch (e: Exception) {
            AppDiagnosticLogger.log("ERROR", "Erro TurboCrExtractor: ${e.message}")
        }
        listOf(url)
    }
}
