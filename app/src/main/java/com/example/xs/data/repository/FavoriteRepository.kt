package com.example.xs.data.repository

import com.example.xs.data.local.FavoriteTopicDao
import com.example.xs.data.model.FavoriteTopic
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val favoriteTopicDao: FavoriteTopicDao) {
    val allFavorites: Flow<List<FavoriteTopic>> = favoriteTopicDao.getAllFavorites()
    val followedFavorites: Flow<List<FavoriteTopic>> = favoriteTopicDao.getFollowedTopics()
    val exploreFavorites: Flow<List<FavoriteTopic>> = favoriteTopicDao.getExploreTopics()

    suspend fun addFavorite(url: String, title: String) {
        favoriteTopicDao.insertFavorite(FavoriteTopic(url, title, isFollowed = true, isLiked = false))
    }

    suspend fun insertFavorite(topic: FavoriteTopic) {
        favoriteTopicDao.insertFavorite(topic)
    }

    suspend fun removeFavorite(topic: FavoriteTopic) {
        favoriteTopicDao.deleteFavorite(topic)
    }

    suspend fun isFavorite(url: String): Boolean {
        return favoriteTopicDao.isFavorite(url)
    }

    suspend fun updateLastPostId(url: String, lastPostId: String) {
        favoriteTopicDao.updateLastPostId(url, lastPostId)
    }
}
