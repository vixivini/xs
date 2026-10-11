package com.example.xs.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_topics")
data class FavoriteTopic(
    @PrimaryKey val url: String,
    val title: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPostId: String? = null,
    val avatarUrl: String? = null,
    val isFollowed: Boolean = true,  // Favorito/Seguindo (TELA HOME)
    val isLiked: Boolean = false     // Gostei (TELA PESQUISAR / EXPLORAR)
)
