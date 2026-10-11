package com.example.xs.extractor

import com.example.xs.util.AppDiagnosticLogger
import com.example.xs.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class GofileExtractor(private val client: OkHttpClient) : MediaExtractorPlugin {
    override fun canHandle(url: String): Boolean {
        return url.lowercase().contains("gofile.io")
    }

    override suspend fun extract(url: String): List<String> = withContext(Dispatchers.IO) {
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
                        }
                    }
                }
                AppDiagnosticLogger.log("EXTRACTOR", "Gofile extraiu ${images.size} itens de: $url")
                images
            }
        } catch (e: Exception) {
            AppDiagnosticLogger.log("ERROR", "Erro GofileExtractor ($url): ${e.message}")
            emptyList()
        }
    }
}
