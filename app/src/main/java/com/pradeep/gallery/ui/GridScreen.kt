package com.pradeep.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pradeep.gallery.GalleryViewModel
import com.pradeep.gallery.data.MediaItem
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

/**
 * A grid with its own top bar, search/sort/filter and multi-select (pin, favorite, share, delete).
 * [scope] decides where pins live: the main grid, or one album.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridScreen(
    vm: GalleryViewModel,
    actions: MediaActions,
    title: String,
    items: List<MediaItem>,
    scope: String,
    onOpen: (ordered: List<MediaItem>, item: MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    searchable: Boolean = false,
    filterable: Boolean = false,
    menu: @Composable (dismiss: () -> Unit) -> Unit = {},
) {
    val pins by vm.pins.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var sort by remember { mutableStateOf(SortMode.NEWEST) }
    var filter by remember { mutableStateOf(TypeFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    val pinnedAt = remember(pins, scope) {
        pins.filter { it.scope == scope }.associate { it.mediaId to it.pinnedAt }
    }
    val visible = remember(items, query, filter) {
        items.filter {
            (filter == TypeFilter.ALL || (filter == TypeFilter.VIDEOS) == it.isVideo) &&
                (query.isBlank() || it.name.contains(query, true) || it.bucket.contains(query, true))
        }
    }
    val (pinned, rest) = remember(visible, pinnedAt, sort) { splitPinned(visible, pinnedAt, sort) }
    val ordered = remember(pinned, rest) { pinned + rest }
    val selecting = selected.isNotEmpty()
    val selectedItems = remember(selected, items) { items.filter { it.id in selected } }

    BackHandler(selecting) { selected = emptySet() }
    BackHandler(searching && !selecting) { searching = false; query = "" }

    Column(modifier.fillMaxSize()) {
        if (selecting) {
            val allPinned = selected.all { it in pinnedAt }
            TopAppBar(
                title = { Text("${selected.size} selected") },
                navigationIcon = {
                    IconButton({ selected = emptySet() }) { Icon(Icons.Filled.Close, "Clear selection") }
                },
                actions = {
                    IconButton({ selected = ordered.map { it.id }.toSet() }) { Icon(Icons.Filled.SelectAll, "Select all") }
                    IconButton({ vm.togglePin(selected, scope); selected = emptySet() }) {
                        Icon(if (allPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin, if (allPinned) "Unpin" else "Pin")
                    }
                    IconButton({ vm.toggleFavorite(selected); selected = emptySet() }) {
                        val allFav = selected.all { it in favorites }
                        Icon(if (allFav) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite, "Favorite")
                    }
                    IconButton({ actions.share(selectedItems) }) { Icon(Icons.Filled.Share, "Share") }
                    IconButton({ actions.trash(selectedItems) { selected = emptySet() } }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        } else {
            TopAppBar(
                title = {
                    if (searching) {
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                Box { if (query.isEmpty()) Text("Search name or album", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else Text(title)
                },
                navigationIcon = {
                    if (searching) IconButton({ searching = false; query = "" }) { Icon(Icons.Filled.Close, "Close search") }
                    else if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (searchable && !searching) IconButton({ searching = true }) { Icon(Icons.Filled.Search, "Search") }
                    Box {
                        IconButton({ sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            SortMode.entries.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.label) },
                                    leadingIcon = { RadioButton(sort == m, null) },
                                    onClick = { sort = m; sortMenu = false },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton({ moreMenu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(moreMenu, { moreMenu = false }) { menu { moreMenu = false } }
                    }
                },
            )
            if (filterable) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeFilter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (items.isEmpty()) "Nothing here yet" else "No matches",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            MediaGrid(
                pinned = pinned, rest = rest, favorites = favorites, sort = sort,
                selected = selected, selecting = selecting,
                onTap = { item ->
                    if (selecting) selected = if (item.id in selected) selected - item.id else selected + item.id
                    else onOpen(ordered, item)
                },
                onLongPress = { item -> selected = selected + item.id },
            )
        }
    }
}
