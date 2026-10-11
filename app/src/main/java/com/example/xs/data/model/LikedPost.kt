package com.example.xs.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.xs.data.local.Converters

@Entity(tableName = "liked_posts")
data class LikedPost(
    @PrimaryKey val id: String,
    val author: String,
    val contentHtml: String,
    @TypeConverters(Converters::class)
    val mediaUrls: List<String>,
    @TypeConverters(Converters::class)
    val externalLinks: List<String> = emptyList(),
    val timestamp: Long,
    val topicTitle: String,
    val postUrl: String,
    val likedAt: Long = System.currentTimeMillis()
)
