package com.example.xs.extractor

import com.example.xs.util.AppDiagnosticLogger
import com.example.xs.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PixeldrainExtractor(private val client: OkHttpClient) : MediaExtractorPlugin {
    override fun canHandle(url: String): Boolean {
        return url.lowercase().contains("pixeldrain.com")
    }

    override suspend fun extract(url: String): List<String> = withContext(Dispatchers.IO) {
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
                        AppDiagnosticLogger.log("EXTRACTOR", "Pixeldrain lista extraiu ${results.size} itens de: $url")
                        return@withContext results
                    }
                }
            } else {
                val id = url.substringAfterLast("/").substringBefore("?")
                if (id.isNotEmpty()) {
                    val finalUrl = "https://pixeldrain.com/api/file/$id"
                    AppDiagnosticLogger.log("EXTRACTOR", "Pixeldrain link direto construído: $finalUrl")
                    return@withContext listOf(finalUrl)
                }
            }
        } catch (e: Exception) {
            AppDiagnosticLogger.log("ERROR", "Erro PixeldrainExtractor ($url): ${e.message}")
        }
        emptyList()
    }
}
