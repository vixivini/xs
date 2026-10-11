package com.example.xs

import android.app.Application
import android.webkit.WebSettings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.*
import com.example.xs.util.AppDiagnosticLogger
import com.example.xs.util.Constants
import com.example.xs.util.NotificationHelper
import com.example.xs.util.WebViewCookieJar
import com.example.xs.worker.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.Coil

class XsApplication : Application(), ImageLoaderFactory {
    lateinit var okHttpClient: OkHttpClient
        private set

    override fun newImageLoader(): ImageLoader {
        val customOkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .cookieJar(WebViewCookieJar())
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Referer", "https://simpcity.cr/")
                    .header("Origin", "https://simpcity.cr")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .header("Sec-Fetch-Dest", "image")
                    .header("Sec-Fetch-Mode", "no-cors")
                    .header("Sec-Fetch-Site", "cross-site")
                    .build()
                chain.proceed(request)
            }
            .build()

        okHttpClient = customOkHttpClient

        return ImageLoader.Builder(this)
            .okHttpClient(customOkHttpClient)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .crossfade(true)
            .dispatcher(Dispatchers.IO)
            .eventListener(object : coil.EventListener {
                override fun onSuccess(request: coil.request.ImageRequest, result: coil.request.SuccessResult) {
                    AppDiagnosticLogger.log("IMAGE_OK", request.data.toString())
                }
                override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) {
                    val errMsg = result.throwable.message ?: "Desconhecido"
                    AppDiagnosticLogger.log("IMAGE_FAIL", "${request.data} -> $errMsg")
                }
            })
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // Configura explicitamente o ImageLoader global do Coil
        val imageLoader = newImageLoader()
        Coil.setImageLoader(imageLoader)

        // 1. Inicialização Imediata do Logger de Diagnóstico
        AppDiagnosticLogger.init(this)
        AppDiagnosticLogger.log("APP", "XsApplication.onCreate() iniciado.")

        // Lifecycle observer para detectar ON_STOP (app em segundo plano / fechando)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                AppDiagnosticLogger.log("LIFECYCLE", "App entrou em segundo plano (ON_STOP). Salvando logs...")
                kotlinx.coroutines.runBlocking {
                    withContext(Dispatchers.IO + NonCancellable) {
                        AppDiagnosticLogger.saveLogsToDownloads(this@XsApplication)
                    }
                }
            }
        })

        // Inicializa o User-Agent padrão do WebView e sincroniza com Constants
        try {
            Constants.USER_AGENT = WebSettings.getDefaultUserAgent(this)
        } catch (e: Exception) {
            // Mantém o fallback definido em Constants se falhar
        }

        NotificationHelper(this).createNotificationChannel()
        scheduleSync()
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SyncWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
