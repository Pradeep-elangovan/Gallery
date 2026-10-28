package com.pradeep.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pradeep.gallery.GalleryViewModel
import com.pradeep.gallery.data.MediaItem
import com.pradeep.gallery.data.SCOPE_ALL
import com.pradeep.gallery.data.buildAlbums

@Composable
fun HomeScreen(
    vm: GalleryViewModel,
    actions: MediaActions,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpen: (List<MediaItem>, MediaItem, String) -> Unit,
    onAlbum: (Long, String) -> Unit,
    onDuplicates: () -> Unit,
    onMap: () -> Unit,
    onTrash: () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { onTab(0) }, label = { Text("Photos") },
                    icon = { Icon(if (tab == 0) Icons.Filled.Photo else Icons.Outlined.Photo, null) })
                NavigationBarItem(tab == 1, { onTab(1) }, label = { Text("Albums") },
                    icon = { Icon(if (tab == 1) Icons.Filled.PhotoAlbum else Icons.Outlined.PhotoAlbum, null) })
                NavigationBarItem(tab == 2, { onTab(2) }, label = { Text("Favorites") },
                    icon = { Icon(if (tab == 2) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null) })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> GridScreen(
                    vm, actions, "Pin Gallery", items, SCOPE_ALL,
                    onOpen = { list, item -> onOpen(list, item, SCOPE_ALL) },
                    searchable = true, filterable = true,
                    menu = { dismiss ->
                        DropdownMenuItem({ Text("Duplicates") }, { dismiss(); onDuplicates() })
                        DropdownMenuItem({ Text("Map") }, { dismiss(); onMap() })
                        DropdownMenuItem({ Text("Recycle bin") }, { dismiss(); onTrash() })
                    },
                )
                1 -> AlbumsTab(items, onAlbum)
                else -> GridScreen(
                    vm, actions, "Favorites", items.filter { it.id in favorites }, "favorites",
                    onOpen = { list, item -> onOpen(list, item, "favorites") },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumsTab(items: List<MediaItem>, onAlbum: (Long, String) -> Unit) {
    val albums = remember(items) { buildAlbums(items) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Albums") })
        if (albums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No albums") }
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(150.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                items(albums, key = { it.bucketId }) { a ->
                    Column(Modifier.clickable { onAlbum(a.bucketId, a.name) }) {
                        AsyncImage(
                            model = a.cover.uri, contentDescription = a.name, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)),
                        )
                        Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                            modifier = Modifier.padding(top = 6.dp))
                        Text("${a.count}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
