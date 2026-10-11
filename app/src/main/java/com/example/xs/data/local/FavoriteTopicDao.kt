package com.example.xs.data.local

import androidx.room.*
import com.example.xs.data.model.FavoriteTopic
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteTopicDao {
    @Query("SELECT * FROM favorite_topics ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteTopic>>

    @Query("SELECT * FROM favorite_topics WHERE isFollowed = 1 ORDER BY addedAt DESC")
    fun getFollowedTopics(): Flow<List<FavoriteTopic>>

    @Query("SELECT * FROM favorite_topics WHERE isLiked = 1 OR isFollowed = 1 ORDER BY addedAt DESC")
    fun getExploreTopics(): Flow<List<FavoriteTopic>>

    @Query("SELECT * FROM favorite_topics")
    suspend fun getAllFavoritesOnce(): List<FavoriteTopic>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(topic: FavoriteTopic)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(topics: List<FavoriteTopic>)

    @Delete
    suspend fun deleteFavorite(topic: FavoriteTopic)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_topics WHERE url = :url)")
    suspend fun isFavorite(url: String): Boolean

    @Query("UPDATE favorite_topics SET lastPostId = :lastPostId WHERE url = :url")
    suspend fun updateLastPostId(url: String, lastPostId: String)
}
