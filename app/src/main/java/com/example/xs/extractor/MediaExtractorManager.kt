package com.example.xs.extractor

import okhttp3.OkHttpClient

class MediaExtractorManager(client: OkHttpClient) {
    private val plugins = listOf(
        TurboCrExtractor(client),
        BunkrExtractor(client),
        GofileExtractor(client),
        PixeldrainExtractor(client)
    )

    suspend fun extract(url: String): List<String> {
        val plugin = plugins.find { it.canHandle(url) }
        return plugin?.extract(url) ?: listOf(url) // Se não houver plugin, mantém a URL original
    }
    
    fun isSupported(url: String): Boolean {
        return plugins.any { it.canHandle(url) }
    }
}
