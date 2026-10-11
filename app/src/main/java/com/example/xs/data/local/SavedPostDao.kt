package com.example.xs.data.local

import androidx.room.*
import com.example.xs.data.model.SavedPost
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPostDao {
    @Query("SELECT * FROM saved_posts ORDER BY savedAt DESC")
    fun getAllSavedPosts(): Flow<List<SavedPost>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPost(post: SavedPost)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<SavedPost>)

    @Delete
    suspend fun deleteSavedPost(post: SavedPost)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_posts WHERE id = :postId)")
    suspend fun isSaved(postId: String): Boolean
}
