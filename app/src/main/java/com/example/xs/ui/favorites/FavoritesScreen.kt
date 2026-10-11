package com.example.xs.ui.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xs.data.model.FavoriteTopic
import com.example.xs.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(viewModel: MainViewModel) {
    val favorites by viewModel.favorites.collectAsState()
    val wifiOnlyPreload by viewModel.wifiOnlyPreload.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Configurações e Favoritos") })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SettingsSection(
                wifiOnlyPreload = wifiOnlyPreload,
                onWifiOnlyPreloadChange = { viewModel.setWifiOnlyPreload(it) }
            )
            
            HorizontalDivider()
            
            if (favorites.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhum tópico favoritado ainda.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(favorites, key = { it.url }) { topic ->
                        FavoriteItem(
                            topic = topic,
                            onDelete = { viewModel.removeFavorite(topic) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    wifiOnlyPreload: Boolean,
    onWifiOnlyPreloadChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Configurações",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Pré-carregar vídeos apenas no Wi-Fi")
                Text(
                    text = "Economiza dados móveis ao não carregar vídeos antecipadamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = wifiOnlyPreload,
                onCheckedChange = onWifiOnlyPreloadChange
            )
        }
    }
}

@Composable
fun FavoriteItem(topic: FavoriteTopic, onDelete: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = topic.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = topic.url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remover")
            }
        }
    )
}
