package com.example.xs.data.repository

import com.example.xs.data.local.SavedPostDao
import com.example.xs.data.model.MediaPost
import com.example.xs.data.model.SavedPost
import kotlinx.coroutines.flow.Flow

class SavedPostRepository(private val savedPostDao: SavedPostDao) {
    val allSavedPosts: Flow<List<SavedPost>> = savedPostDao.getAllSavedPosts()

    suspend fun savePost(post: MediaPost) {
        val savedPost = SavedPost(
            id = post.id,
            author = post.author,
            contentHtml = post.contentHtml,
            mediaUrls = post.mediaUrls,
            externalLinks = post.externalLinks,
            timestamp = post.timestamp,
            topicTitle = post.topicTitle,
            postUrl = post.postUrl
        )
        insertSavedPost(savedPost)
    }

    suspend fun insertSavedPost(post: SavedPost) {
        savedPostDao.insertSavedPost(post)
    }

    suspend fun unsavePost(postId: String) {
        // We need the object to delete it, or we can add a deleteById in DAO
        // For simplicity, let's assume we handle deletion by object in ViewModel
    }

    suspend fun deleteSavedPost(post: SavedPost) {
        savedPostDao.deleteSavedPost(post)
    }

    suspend fun isSaved(postId: String): Boolean {
        return savedPostDao.isSaved(postId)
    }
}
