package com.example.xs.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.xs.XsApplication
import com.example.xs.data.local.AppDatabase
import com.example.xs.data.model.FavoriteTopic
import com.example.xs.data.model.MediaPost
import com.example.xs.data.model.SavedPost
import com.example.xs.data.repository.FavoriteRepository
import com.example.xs.data.repository.MediaScraperRepository
import com.example.xs.data.repository.SavedPostRepository
import com.example.xs.data.repository.ScrapeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val favoriteRepository = FavoriteRepository(AppDatabase.getDatabase(application).favoriteTopicDao())
    private val savedPostRepository = SavedPostRepository(AppDatabase.getDatabase(application).savedPostDao())
    private val likedPostDao = AppDatabase.getDatabase(application).likedPostDao()
    private val scraperRepository = MediaScraperRepository((application as XsApplication).okHttpClient)

    private val _viewingMedia = MutableStateFlow<List<String>>(emptyList())
    val viewingMedia: StateFlow<List<String>> = _viewingMedia.asStateFlow()

    private val _viewingTopicUrl = MutableStateFlow<String?>(null)
    val viewingTopicUrl: StateFlow<String?> = _viewingTopicUrl.asStateFlow()

    fun setViewingMedia(urls: List<String>, topicUrl: String? = null) {
        _viewingMedia.value = urls
        _viewingTopicUrl.value = topicUrl?.trimEnd('/')
    }
    
    val savedPosts: StateFlow<List<SavedPost>> = savedPostRepository.allSavedPosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedPosts: StateFlow<List<com.example.xs.data.model.LikedPost>> = likedPostDao.getAllLikedPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleSavePost(post: MediaPost) {
        viewModelScope.launch {
            if (savedPostRepository.isSaved(post.id)) {
                savedPosts.value.find { it.id == post.id }?.let {
                    savedPostRepository.deleteSavedPost(it)
                }
            } else {
                savedPostRepository.savePost(post)
            }
        }
    }

    fun toggleLikePost(post: MediaPost) {
        viewModelScope.launch {
            if (likedPostDao.isLiked(post.id)) {
                likedPosts.value.find { it.id == post.id }?.let {
                    likedPostDao.deleteLikedPost(it)
                }
            } else {
                val liked = com.example.xs.data.model.LikedPost(
                    id = post.id,
                    author = post.author,
                    contentHtml = post.contentHtml,
                    mediaUrls = post.mediaUrls,
                    externalLinks = post.externalLinks,
                    timestamp = post.timestamp,
                    topicTitle = post.topicTitle,
                    postUrl = post.postUrl
                )
                likedPostDao.insertLikedPost(liked)
            }
        }
    }

    fun updateSavedPostMedia(postId: String, mediaUrls: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentSaved = savedPosts.value.find { it.id == postId }
            if (currentSaved != null && currentSaved.mediaUrls.size < mediaUrls.size) {
                val updated = currentSaved.copy(mediaUrls = mediaUrls)
                savedPostRepository.insertSavedPost(updated)
            }
        }
    }

    fun refreshSavedPost(postId: String, postUrl: String, topicTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = scraperRepository.fetchMediaFromTopic(postUrl, topicTitle)
                if (result is ScrapeResult.Success) {
                    val freshPost = result.posts.find { it.id == postId }
                    if (freshPost != null) {
                        updateSavedPostMedia(postId, freshPost.mediaUrls)
                        updateFeedPostMedia(postId, freshPost.mediaUrls)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateFeedPostMedia(postId: String, mediaUrls: List<String>) {
        val currentFeed = _feedPosts.value.toMutableList()
        val index = currentFeed.indexOfFirst { it.id == postId }
        if (index != -1) {
            currentFeed[index] = currentFeed[index].copy(mediaUrls = mediaUrls)
            _feedPosts.value = currentFeed
        }
    }

    suspend fun isSaved(postId: String): Boolean {
        return savedPostRepository.isSaved(postId)
    }
    private val settingsManager = com.example.xs.util.SettingsManager(application)

    val favorites: StateFlow<List<FavoriteTopic>> = favoriteRepository.allFavorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wifiOnlyPreload = settingsManager.wifiOnlyPreload
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val profilePictureUri = settingsManager.profilePictureUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setWifiOnlyPreload(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setWifiOnlyPreload(enabled)
        }
    }

    fun setProfilePictureUri(uri: String?) {
        viewModelScope.launch {
            settingsManager.setProfilePictureUri(uri)
        }
    }

    fun shouldPreload(): Boolean {
        return settingsManager.shouldPreload(wifiOnlyPreload.value)
    }

    private val _feedPosts = MutableStateFlow<List<MediaPost>>(emptyList())
    val feedPosts: StateFlow<List<MediaPost>> = _feedPosts.asStateFlow()

    private val _explorePosts = MutableStateFlow<List<MediaPost>>(emptyList())
    val explorePosts: StateFlow<List<MediaPost>> = _explorePosts.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // Armazena a próxima página (anterior no tempo) a ser carregada para cada tópico no FEED
    private val feedPaginationMap = mutableMapOf<String, String?>()
    
    // Armazena a paginação para tópicos visualizados individualmente no PERFIL
    private val topicPaginationMap = mutableMapOf<String, String?>()

    // Helper para buscar informações do tópico rapidamente
    fun getFavoriteTopic(url: String): FavoriteTopic? {
        val clean = url.trimEnd('/')
        return favorites.value.find { it.url.trimEnd('/') == clean }
    }

    fun updateTopicAvatar(url: String, avatarUrl: String) {
        val clean = url.trimEnd('/')
        viewModelScope.launch {
            val topic = favorites.value.find { it.url.trimEnd('/') == clean }
            if (topic != null) {
                favoriteRepository.insertFavorite(topic.copy(avatarUrl = avatarUrl))
            }
        }
    }

    private fun updateTopicAvatarIfNeeded(url: String, avatarUrl: String) {
        val topic = getFavoriteTopic(url)
        if (topic != null && topic.avatarUrl == null) {
            updateTopicAvatar(url, avatarUrl)
        }
    }

    private val _authRequiredEvent = MutableSharedFlow<Unit>()
    val authRequiredEvent = _authRequiredEvent.asSharedFlow()

    private val _scrollToTopEvent = MutableSharedFlow<Unit>()
    val scrollToTopEvent = _scrollToTopEvent.asSharedFlow()

    private val _topicPosts = MutableStateFlow<Map<String, List<MediaPost>>>(emptyMap())
    val topicPosts = _topicPosts.asStateFlow()

    private val _isTopicLoading = MutableStateFlow(false)
    val isTopicLoading = _isTopicLoading.asStateFlow()

    fun fetchTopicPosts(url: String, title: String) {
        val cleanUrl = url.trimEnd('/')
        viewModelScope.launch(Dispatchers.IO) {
            _isTopicLoading.value = true
            try {
                Log.d("Pagination", "Buscando inicial independente: $cleanUrl")
                val result = scraperRepository.fetchMediaFromTopic(cleanUrl, title)
                when (result) {
                    is ScrapeResult.Success -> {
                        val currentMap = _topicPosts.value.toMutableMap()
                        // Organiza rigorosamente por data e ID (mais recente no topo)
                        val sortedPosts = result.posts.distinctBy { it.id }
                            .sortedWith(compareByDescending<MediaPost> { it.timestamp }
                                .thenByDescending { it.id.toLongOrNull() ?: 0L })
                        
                        currentMap[cleanUrl] = sortedPosts
                        _topicPosts.value = currentMap
                        topicPaginationMap[cleanUrl] = result.prevPageUrl
                        
                        // Atualiza o avatar do tópico automaticamente se for descoberto
                        result.topicAvatarUrl?.let { avatar ->
                            updateTopicAvatarIfNeeded(cleanUrl, avatar)
                        }
                        
                        Log.d("Pagination", "Sucesso! Total: ${sortedPosts.size}")
                    }
                    is ScrapeResult.NeedsAuthentication -> {
                        Log.w("Pagination", "Necessita Login detectado pelo Perfil")
                        _authRequiredEvent.emit(Unit) // Agora o perfil também pede login!
                    }
                    is ScrapeResult.Error -> Log.e("Pagination", "Erro: ${result.message}")
                }
            } catch (e: Exception) {
                Log.e("Pagination", "Falha catastrófica", e)
            } finally {
                _isTopicLoading.value = false
            }
        }
    }

    fun loadMoreForTopic(url: String, title: String) {
        val cleanUrl = url.trimEnd('/')
        viewModelScope.launch(Dispatchers.IO) {
            val nextPageUrl = topicPaginationMap[cleanUrl]
            if (nextPageUrl == null || _isTopicLoading.value) return@launch

            _isTopicLoading.value = true
            try {
                Log.d("Pagination", "Carregando próxima página: $nextPageUrl")
                val result = scraperRepository.fetchMediaFromTopic(nextPageUrl, title)
                if (result is ScrapeResult.Success) {
                    val currentMap = _topicPosts.value.toMutableMap()
                    val existingPosts = currentMap[cleanUrl] ?: emptyList()
                    
                    // Une e remove duplicados, mantendo a ordem rigorosa por data e ID
                    val combined = (existingPosts + result.posts)
                        .distinctBy { it.id }
                        .sortedWith(compareByDescending<MediaPost> { it.timestamp }
                            .thenByDescending { it.id.toLongOrNull() ?: 0L })
                    
                    currentMap[cleanUrl] = combined
                    _topicPosts.value = currentMap
                    
                    topicPaginationMap[cleanUrl] = result.prevPageUrl
                    Log.d("Pagination", "Página carregada. Total: ${combined.size}. Próxima: ${result.prevPageUrl}")
                } else {
                    topicPaginationMap[cleanUrl] = null
                }
            } catch (e: Exception) {
                Log.e("Pagination", "Erro loadMore", e)
                topicPaginationMap[cleanUrl] = null
            } finally {
                _isTopicLoading.value = false
            }
        }
    }

    fun triggerScrollToTop() {
        viewModelScope.launch {
            _scrollToTopEvent.emit(Unit)
        }
    }

    init {
        // Refresh feed whenever favorites change
        viewModelScope.launch {
            favorites.collect {
                refreshFeed()
            }
        }
    }

    fun refreshFeed() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            try {
                android.webkit.CookieManager.getInstance().flush()
                feedPaginationMap.clear()
                
                val allPosts = mutableListOf<MediaPost>()
                var authNeeded = false

                coroutineScope {
                    val deferreds = favorites.value.map { topic ->
                        async {
                            scraperRepository.fetchMediaFromTopic(topic.url, topic.title) to topic.url
                        }
                    }
                    
                    deferreds.awaitAll().forEach { (result, url) ->
                        when (result) {
                            is ScrapeResult.Success -> {
                                allPosts.addAll(result.posts)
                                feedPaginationMap[url] = result.prevPageUrl
                            }
                            is ScrapeResult.NeedsAuthentication -> authNeeded = true
                            is ScrapeResult.Error -> { /* Log error */ }
                        }
                    }
                }
                
                val feedPosts = allPosts.filter { post ->
                    val topic = favorites.value.find { it.url.trimEnd('/') == post.postUrl.trimEnd('/') || post.postUrl.contains(it.url.trimEnd('/')) }
                    topic?.isFollowed == true
                }.distinctBy { it.id }
                .sortedWith(compareByDescending<MediaPost> { it.timestamp }
                    .thenByDescending { it.id.toLongOrNull() ?: 0L })

                val explorePosts = allPosts.filter { post ->
                    val topic = favorites.value.find { it.url.trimEnd('/') == post.postUrl.trimEnd('/') || post.postUrl.contains(it.url.trimEnd('/')) }
                    topic?.isLiked == true || topic?.isFollowed == true
                }.distinctBy { it.id }
                .sortedWith(compareByDescending<MediaPost> { it.timestamp }
                    .thenByDescending { it.id.toLongOrNull() ?: 0L })

                _feedPosts.value = feedPosts
                _explorePosts.value = explorePosts
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isLoadingMore.value || _isRefreshing.value) return@launch
            
            val topicsToLoad = feedPaginationMap.filter { it.value != null }.toList()
            if (topicsToLoad.isEmpty()) return@launch

            _isLoadingMore.value = true
            try {
                val newPosts = mutableListOf<MediaPost>()
                
                coroutineScope {
                    val deferreds = topicsToLoad.map { (originalUrl, nextUrl) ->
                        async {
                            val topicTitle = favorites.value.find { it.url == originalUrl }?.title ?: "Unknown"
                            val result = nextUrl?.let { scraperRepository.fetchMediaFromTopic(it, topicTitle) }
                            originalUrl to result
                        }
                    }
                    
                    deferreds.awaitAll().forEach { (originalUrl, result) ->
                        if (result is ScrapeResult.Success) {
                            newPosts.addAll(result.posts)
                            feedPaginationMap[originalUrl] = result.prevPageUrl
                        }
                    }
                }
                
                if (newPosts.isNotEmpty()) {
                    val allCurrent = (_explorePosts.value + newPosts).distinctBy { it.id }
                        .sortedWith(compareByDescending<MediaPost> { it.timestamp }
                            .thenByDescending { it.id.toLongOrNull() ?: 0L })
                    
                    val feedPosts = allCurrent.filter { post ->
                        val topic = favorites.value.find { it.url.trimEnd('/') == post.postUrl.trimEnd('/') || post.postUrl.contains(it.url.trimEnd('/')) }
                        topic?.isFollowed == true
                    }

                    _feedPosts.value = feedPosts
                    _explorePosts.value = allCurrent
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun toggleFavorite(url: String, title: String) {
        val clean = url.trimEnd('/')
        viewModelScope.launch {
            val existing = favorites.value.find { it.url.trimEnd('/') == clean }
            if (existing != null) {
                // Se já existe, apenas inverte o estado de seguir
                favoriteRepository.insertFavorite(existing.copy(isFollowed = !existing.isFollowed))
            } else {
                // Se não existe, cria um novo seguindo
                favoriteRepository.insertFavorite(FavoriteTopic(url = clean, title = title, isFollowed = true))
            }
        }
    }

    fun toggleBlogLike(url: String, title: String) {
        val clean = url.trimEnd('/')
        viewModelScope.launch {
            val existing = favorites.value.find { it.url.trimEnd('/') == clean }
            if (existing != null) {
                // Inverte o estado de Like (Coração)
                favoriteRepository.insertFavorite(existing.copy(isLiked = !existing.isLiked))
            } else {
                // Adiciona como Liked, mas isFollowed = false (só aparece no Explore)
                favoriteRepository.insertFavorite(FavoriteTopic(url = clean, title = title, isFollowed = false, isLiked = true))
            }
        }
    }

    suspend fun isBlogFavorite(url: String): Boolean {
        val clean = url.trimEnd('/')
        return favorites.value.any { it.url.trimEnd('/') == clean && it.isFollowed }
    }

    suspend fun isBlogLiked(url: String): Boolean {
        val clean = url.trimEnd('/')
        return favorites.value.any { it.url.trimEnd('/') == clean && it.isLiked }
    }

    suspend fun isFavorite(url: String): Boolean {
        return favoriteRepository.isFavorite(url)
    }

    fun removeFavorite(topic: FavoriteTopic) {
        viewModelScope.launch {
            favoriteRepository.removeFavorite(topic)
        }
    }
}
