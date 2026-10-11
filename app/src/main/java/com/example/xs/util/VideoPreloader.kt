package com.example.xs.util

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.offline.DownloadHelper
import com.example.xs.XsApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@OptIn(UnstableApi::class)
object VideoPreloader {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private const val PRELOAD_SIZE = 2 * 1024 * 1024L // 2MB por vídeo para início instantâneo

    fun preload(context: Context, url: String) {
        if (activeJobs.containsKey(url) || isVideoCached(context, url)) return

        val job = GlobalScope.launch(Dispatchers.IO) {
            try {
                val cache = VideoCache.getInstance(context)
                val upstreamDataSource = DefaultHttpDataSource.Factory()
                    .setUserAgent(Constants.USER_AGENT)
                    .setDefaultRequestProperties(mapOf("Referer" to getReferer(url)))
                    .createDataSource()

                val dataSource = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory { upstreamDataSource }
                    .createDataSource()

                val dataSpec = androidx.media3.datasource.DataSpec(Uri.parse(url), 0, PRELOAD_SIZE)
                
                CacheWriter(
                    dataSource,
                    dataSpec,
                    null
                ) { _, _, _ -> 
                    // Progresso opcional
                }.cache()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                activeJobs.remove(url)
            }
        }
        activeJobs[url] = job
    }

    private fun isVideoCached(context: Context, url: String): Boolean {
        val cache = VideoCache.getInstance(context)
        return cache.isCached(url, 0, PRELOAD_SIZE)
    }

    private fun getReferer(url: String): String {
        return when {
            url.contains("bunkr") || url.contains("b-cdn.net") -> "https://bunkr.is/"
            url.contains("gofile") -> "https://gofile.io/"
            url.contains("pixeldrain") -> "https://pixeldrain.com/"
            url.contains("turbo.cr") -> "https://simpcity.cr/"
            else -> "https://simpcity.cr/"
        }
    }
    
    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }
}
