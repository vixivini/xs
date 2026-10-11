package com.example.xs.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.xs.ui.MainViewModel
import com.example.xs.util.MediaUtils
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel, 
    onPostClick: (List<String>, Int) -> Unit,
    onFollowingClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val savedPosts by viewModel.savedPosts.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val profilePictureUri by viewModel.profilePictureUri.collectAsState()
    val context = LocalContext.current
    
    // Controle de visualização (Grade vs Feed de Salvos)
    var isListView by remember { mutableStateOf(false) }
    var initialScrollIndex by remember { mutableStateOf(0) }
    
    val gridState = rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Efeito para rolar até o post clicado na grade
    LaunchedEffect(isListView) {
        if (isListView && initialScrollIndex >= 0) {
            listState.scrollToItem(initialScrollIndex)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.setProfilePictureUri(it.toString())
            }
        }
    )

    // Modo Grade (Padrão e Único agora)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        item(span = { GridItemSpan(3) }) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                            .clickable { launcher.launch(arrayOf("image/*")) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (profilePictureUri != null) {
                            AsyncImage(
                                model = profilePictureUri,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(text = "XS", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ProfileStat(number = savedPosts.size.toString(), label = "Salvos")
                        ProfileStat(
                            number = favorites.size.toString(), 
                            label = "Seguindo",
                            modifier = Modifier.clickable { onFollowingClick() }
                        )
                    }
                    
                    // Botão de Configurações (Acesso ao Web Add secreto)
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(text = "Seu Perfil", fontWeight = FontWeight.Bold)
                    Text(text = "Gerencie seus conteúdos salvos e tópicos favoritos.", fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                TabRow(
                    selectedTabIndex = 0,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[0]),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    divider = {}
                ) {
                    Tab(
                        selected = true,
                        onClick = { },
                        icon = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GridOn, contentDescription = "Salvos", modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SALVOS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }
        }

        items(
            items = savedPosts,
            key = { post -> post.id },
            contentType = { post -> if (post.mediaUrls.any { MediaUtils.isVideoUrl(it) }) "video" else "image" }
        ) { post ->
            val firstMedia = post.mediaUrls.firstOrNull()
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(Color.LightGray.copy(alpha = 0.1f))
                    .clickable { 
                        // Agora clica na grade e vai DIRETO para o Full Screen 
                        // usando a lista completa de fotos
                        viewModel.setViewingMedia(post.mediaUrls, post.postUrl)
                        onPostClick(post.mediaUrls, 0)
                    }
            ) {
                if (firstMedia != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(firstMedia)
                            .decoderFactory(VideoFrameDecoder.Factory())
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    if (post.mediaUrls.size > 1) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Carrossel",
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp)
                        )
                    } else if (MediaUtils.isVideoUrl(firstMedia)) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileStat(number: String, label: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(text = number, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = label, fontSize = 12.sp)
    }
}
