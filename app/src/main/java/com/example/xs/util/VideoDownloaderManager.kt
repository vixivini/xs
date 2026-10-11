package com.example.xs.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object VideoDownloaderManager {
    
    private const val TEMP_DIR = "temp_videos"
    private val downloadingJobs = ConcurrentHashMap<String, Deferred<Uri?>>()

    /**
     * Faz o download do vídeo para um arquivo temporário e retorna a URI local.
     * Estilo JDownloader: Baixa o conteúdo bruto para evitar problemas de streaming.
     */
    suspend fun downloadToTempFile(context: Context, url: String, okHttpClient: OkHttpClient): Uri? = withContext(Dispatchers.IO) {
        // Se já existe um trabalho de download para esta URL, apenas espera ele
        val existingJob = downloadingJobs[url]
        if (existingJob != null) {
            return@withContext existingJob.await()
        }

        val job = async {
            try {
                val tempDir = File(context.cacheDir, TEMP_DIR)
                if (!tempDir.exists()) tempDir.mkdirs()

                val fileName = UUID.nameUUIDFromBytes(url.toByteArray()).toString() + ".mp4"
                val file = File(tempDir, fileName)

                if (file.exists() && file.length() > 0) return@async Uri.fromFile(file)

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", Constants.USER_AGENT)
                    .header("Referer", MediaUtils.getReferer(url))
                    .header("Origin", if (url.contains("turbo.cr")) "https://turbo.cr" else "https://simpcity.cr")
                    .build()

                Log.d("VideoDownloader", "Iniciando download em massa: $url")
                
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@async null
                    
                    val contentType = response.header("Content-Type") ?: ""
                    if (contentType.contains("text/html")) return@async null
                    
                    val body = response.body ?: return@async null
                    FileOutputStream(file).use { outputStream ->
                        val inputStream: InputStream = body.byteStream()
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                    
                    Log.d("VideoDownloader", "Download concluído: $url")
                    Uri.fromFile(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        
        downloadingJobs[url] = job
        val result = job.await()
        // Removemos do mapa apenas após a conclusão para servir de cache de job para outros chamadores
        return@withContext result
    }

    fun deleteFile(uri: Uri) {
        try {
            val file = File(uri.path ?: return)
            if (file.exists() && file.absolutePath.contains(TEMP_DIR)) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
