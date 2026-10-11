package com.example.xs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xs.data.model.MediaPost
import com.example.xs.ui.theme.InstagramBlue
import com.example.xs.ui.theme.InstagramSecondaryText

@Composable
fun MediaPostCard(
    post: MediaPost, 
    shouldPlay: Boolean = true,
    isSaved: Boolean = false,
    isFavorite: Boolean = false,
    isLiked: Boolean = false,
    showLikeIcon: Boolean = false,
    blogName: String? = null,
    blogAvatar: String? = null,
    onSaveClick: () -> Unit = {},
    onFollowClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onMediaClick: (List<String>, Int) -> Unit,
    onPostUpdated: (String, List<String>) -> Unit = { _, _ -> }
) {
    val uriHandler = LocalUriHandler.current
    var linksExpanded by remember { mutableStateOf(false) }
    
    val displayName = blogName ?: post.author

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar (Usa o do Blog se disponível, senão Placeholder)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(InstagramBlue.copy(alpha = 0.2f))
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                if (blogAvatar != null) {
                    coil.compose.AsyncImage(
                        model = blogAvatar,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = displayName.take(1).uppercase(),
                        color = InstagramBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onProfileClick() }
            ) {
                // Nome do Blog
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Indicadores de Estrela/Coração estilo Insta ao lado do nome
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFFFD700)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (isLiked && showLikeIcon) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFE91E63)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    if (displayName != post.author) {
                        Text(
                            text = "por ${post.author}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            if (isLiked && !isFavorite) {
                TextButton(
                    onClick = onFollowClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Seguir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Box {
                var showMenu by remember { mutableStateOf(false) }
                val context = androidx.compose.ui.platform.LocalContext.current

                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Mais opções",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Baixar todas as mídias") },
                        onClick = {
                            post.mediaUrls.forEach { url ->
                                com.example.xs.util.MediaDownloadManager.downloadMedia(context, url)
                            }
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Download, null) }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("Ver no Navegador") },
                        onClick = {
                            uriHandler.openUri(post.postUrl)
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Public, null) }
                    )
                }
            }
        }

        // Media Content
        if (post.mediaUrls.isNotEmpty()) {
            LaunchedEffect(post.mediaUrls) {
                onPostUpdated(post.id, post.mediaUrls)
            }
            MediaPager(
                mediaUrls = post.mediaUrls,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // Square like Instagram
                    .background(Color.Black),
                shouldPlay = shouldPlay,
                onMediaClick = { index -> onMediaClick(post.mediaUrls, index) }
            )
        }

        // Barra de Ações (Voltou ao original: apenas salvar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Comentar */ }) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comentar")
            }
            IconButton(onClick = { /* Compartilhar */ }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
            }
            Spacer(modifier = Modifier.weight(1f))
            
            IconButton(onClick = onSaveClick) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Salvar",
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Info and Links
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = "${post.mediaUrls.size} mídias anexadas",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            
            if (post.externalLinks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .clickable { linksExpanded = !linksExpanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (linksExpanded) "Esconder links" else "Ver todos os ${post.externalLinks.size} links",
                        style = MaterialTheme.typography.bodySmall,
                        color = InstagramSecondaryText
                    )
                }

                if (linksExpanded) {
                    post.externalLinks.forEach { link ->
                        Text(
                            text = link,
                            style = MaterialTheme.typography.bodySmall,
                            color = InstagramBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { uriHandler.openUri(link) }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), thickness = 0.5.dp)
    }
}
