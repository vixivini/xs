package com.example.xs.util

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object AppDiagnosticLogger {
    private val logQueue = ConcurrentLinkedQueue<String>()
    private var isInitialized = false
    private var appContext: Context? = null

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        appContext = context.applicationContext
        val bootTime = System.currentTimeMillis()

        val androidVer = Build.VERSION.SDK_INT
        val model = Build.MODEL
        val netState = getNetworkState(appContext!!)

        log("BOOT", "=== INICIALIZAÇÃO DO AUDITOR DIAGNÓSTICO ===")
        log("BOOT", "Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(bootTime))}")
        log("BOOT", "Versão Android: $androidVer | Modelo: $model | Rede: $netState")

        // Uncaught Exception Handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = java.io.StringWriter()
            val pw = java.io.PrintWriter(sw)
            throwable.printStackTrace(pw)
            log("CRASH", "FATAL CRASH em ${thread.name}: ${throwable.message}\n${sw.toString()}")
            
            try {
                appContext?.let { saveLogsToDownloads(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun log(category: String, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val entry = "[$timestamp] [$category] $message"
        logQueue.offer(entry)
        Log.d("DiagnosticAudit", entry)
    }

    inline fun <T> benchmark(operationName: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - start
        log("BENCHMARK", "$operationName demorou ${duration}ms")
        if (duration > 100 && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            log("PERF_WARNING", "JANK_DETECTED: operação $operationName demorou $duration ms na Main Thread")
        }
        return result
    }

    private fun getNetworkState(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return "Sem Conexão"
            val caps = cm.getNetworkCapabilities(network) ?: return "Desconhecido"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Celular (Dados)"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Outro"
            }
        } catch (e: Exception) {
            "Erro ao checar rede: ${e.message}"
        }
    }

    fun saveLogsToDownloads(context: Context) {
        try {
            val sb = StringBuilder()
            sb.append("=== RELATÓRIO DE AUDITORIA E DIAGNÓSTICO XS ===\n")
            sb.append("Gerado em: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            sb.append("--------------------------------------------------\n\n")
            
            while (!logQueue.isEmpty()) {
                logQueue.poll()?.let { sb.append(it).append("\n") }
            }

            // Seção 3: Dump de Código-Fonte Fundamental do Sistema
            sb.append("\n\n=== DUMP DE CÓDIGO FUNDAMENTAL DO SISTEMA ===\n")
            
            sb.append("\n--- 1. MediaPager.kt (Carrossel / Card) ---\n")
            sb.append("""
package com.example.xs.ui.components
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.xs.util.MediaUtils

@Composable
fun MediaPager(mediaUrls: List<String>, modifier: Modifier = Modifier, shouldPlay: Boolean = true, onMediaClick: (Int) -> Unit = {}) {
    val totalItems = mediaUrls.size
    val pagerState = rememberPagerState(pageCount = { totalItems })
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }
    Box(modifier = modifier.background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
            val mediaUrl = mediaUrls[page]
            val isVideo = MediaUtils.isVideoUrl(mediaUrl)
            Box(modifier = Modifier.fillMaxSize().clickable { onMediaClick(page) }, contentAlignment = Alignment.Center) {
                if (isVideo) {
                    VideoPlayer(url = mediaUrl, modifier = Modifier.fillMaxSize(), isMuted = true, showControls = false, shouldPlay = shouldPlay && currentPage == page)
                } else {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(mediaUrl).size(coil.size.Dimension.Pixels(1080), coil.size.Dimension.Pixels(1080)).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}
            """.trimIndent())

            sb.append("\n\n--- 2. VideoPlayer.kt (ExoPlayer) ---\n")
            sb.append("""
// ExoPlayer configurado com DefaultHttpDataSource.Factory, Player.Listener completo e tratamento de fallback
            """.trimIndent())

            sb.append("\n\n--- 3. MediaScraperRepository.kt (Extrator / Resolvedor de Stream) ---\n")
            sb.append("""
// Repositório com cache de sessão, concorrência (activeJobs), e resolvedor turbo.cr de segundo nível
            """.trimIndent())

            val content = sb.toString()
            val fileName = "diagnostico_app_auditoria.txt"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(content)
                            writer.flush()
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    Log.d("DiagnosticAudit", "Relatório salvo com sucesso via MediaStore em Downloads: $uri")
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { fos ->
                    OutputStreamWriter(fos).use { writer ->
                        writer.write(content)
                        writer.flush()
                    }
                }
                Log.d("DiagnosticAudit", "Relatório salvo com sucesso em: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("DiagnosticAudit", "Erro ao salvar relatório de auditoria", e)
        }
    }
}
