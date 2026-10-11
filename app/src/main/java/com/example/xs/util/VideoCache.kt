package com.example.xs.util

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object VideoCache {
    private var cache: SimpleCache? = null
    private const val CACHE_SIZE = 250 * 1024 * 1024L // 250 MB

    fun getInstance(context: Context): SimpleCache {
        if (cache == null) {
            val cacheDir = File(context.cacheDir, "video_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(context)
            cache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return cache!!
    }
}
