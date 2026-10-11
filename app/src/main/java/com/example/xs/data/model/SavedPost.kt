package com.example.xs.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_posts")
data class SavedPost(
    @PrimaryKey val id: String,
    val author: String,
    val contentHtml: String,
    val mediaUrls: List<String>,
    val externalLinks: List<String>,
    val timestamp: Long,
    val topicTitle: String,
    val postUrl: String,
    val savedAt: Long = System.currentTimeMillis()
)
