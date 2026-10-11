package com.example.xs.data.local

import androidx.room.*
import com.example.xs.data.model.LikedPost
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedPostDao {
    @Query("SELECT * FROM liked_posts ORDER BY likedAt DESC")
    fun getAllLikedPosts(): Flow<List<LikedPost>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedPost(post: LikedPost)

    @Delete
    suspend fun deleteLikedPost(post: LikedPost)

    @Query("SELECT EXISTS(SELECT 1 FROM liked_posts WHERE id = :postId)")
    suspend fun isLiked(postId: String): Boolean
}
