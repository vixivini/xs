package com.example.xs.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.xs.XsApplication
import com.example.xs.data.local.AppDatabase
import com.example.xs.data.repository.FavoriteRepository
import com.example.xs.data.repository.MediaScraperRepository
import com.example.xs.data.repository.ScrapeResult
import com.example.xs.util.NotificationHelper

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val favoriteRepository = FavoriteRepository(database.favoriteTopicDao())
        val okHttpClient = (applicationContext as XsApplication).okHttpClient
        val scraperRepository = MediaScraperRepository(okHttpClient)

        val favorites = database.favoriteTopicDao().getAllFavoritesOnce()
        var hasNewContent = false

        for (topic in favorites) {
            val result = scraperRepository.fetchMediaFromTopic(topic.url, topic.title)
            if (result is ScrapeResult.Success) {
                val latestPost = result.posts.maxByOrNull { it.timestamp }
                if (latestPost != null && latestPost.id != topic.lastPostId) {
                    favoriteRepository.updateLastPostId(topic.url, latestPost.id)
                    hasNewContent = true
                }
            }
        }

        // Trigger notification if hasNewContent is true
        if (hasNewContent) {
            NotificationHelper(applicationContext).showNotification(
                "Novas atualizações",
                "Há novos conteúdos nos seus tópicos favoritos!"
            )
        }

        return Result.success()
    }
}
