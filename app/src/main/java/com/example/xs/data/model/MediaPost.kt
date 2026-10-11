package com.example.xs.data.model

data class MediaPost(
    val id: String,
    val author: String,
    val contentHtml: String,
    val mediaUrls: List<String>,
    val externalLinks: List<String> = emptyList(),
    val timestamp: Long,
    val topicTitle: String,
    val postUrl: String
)
