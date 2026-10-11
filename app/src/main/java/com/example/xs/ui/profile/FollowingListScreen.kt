package com.example.xs.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xs.data.model.FavoriteTopic
import com.example.xs.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingListScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onTopicClick: (FavoriteTopic) -> Unit
) {
    val favorites by viewModel.favorites.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seguindo", style = MaterialTheme.typography.titleMedium) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            items(favorites) { topic ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTopicClick(topic) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = topic.title.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = topic.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = topic.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1
                        )
                    }
                    
                    var showUnfollowDialog by remember { mutableStateOf(false) }

                    if (showUnfollowDialog) {
                        AlertDialog(
                            onDismissRequest = { showUnfollowDialog = false },
                            title = { Text("Parar de seguir?") },
                            text = { Text("Você deixará de ver as atualizações de ${topic.title} na sua lista de favoritos.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.removeFavorite(topic)
                                        showUnfollowDialog = false
                                    }
                                ) {
                                    Text("Parar de seguir", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showUnfollowDialog = false }) {
                                    Text("Cancelar")
                                }
                            }
                        )
                    }

                    Button(
                        onClick = { showUnfollowDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Seguindo", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
