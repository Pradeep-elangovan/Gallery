package com.pradeep.gallery.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pradeep.gallery.GalleryViewModel
import com.pradeep.gallery.data.MediaItem
import com.pradeep.gallery.data.MediaRepo
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

// ---------------------------------------------------------------- Recycle bin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: GalleryViewModel, actions: MediaActions, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var trashed by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(reload) { trashed = MediaRepo.load(ctx, trashed = true) }
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(selected.isEmpty()) { onBack() }

    val targets = if (selected.isEmpty()) trashed else trashed.filter { it.id in selected }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (selected.isEmpty()) "Recycle bin" else "${selected.size} selected") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton({ actions.restore(targets) { selected = emptySet(); reload++ } }, enabled = trashed.isNotEmpty()) {
                    Icon(Icons.Filled.RestoreFromTrash, "Restore")
                }
                IconButton({ actions.deleteForever(targets) { selected = emptySet(); reload++ } }, enabled = trashed.isNotEmpty()) {
                    Icon(Icons.Filled.DeleteForever, "Delete forever")
                }
            },
        )
        Text(
            "Deleted items stay here for 30 days. Tap an item to select; with nothing selected, actions apply to everything.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (trashed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Recycle bin is empty") }
        } else {
            MediaGrid(
                pinned = emptyList(), rest = trashed, favorites = emptySet(), sort = SortMode.NEWEST,
                selected = selected, selecting = selected.isNotEmpty(),
                onTap = { selected = if (it.id in selected) selected - it.id else selected + it.id },
                onLongPress = { selected = selected + it.id },
            )
        }
    }
}

// ---------------------------------------------------------------- Duplicates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(vm: GalleryViewModel, actions: MediaActions, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    var groups by remember { mutableStateOf<List<List<MediaItem>>?>(null) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(Unit) {
        val found = MediaRepo.findDuplicates(ctx, items)
        groups = found
        // Default: keep the first of every group, select the rest for deletion.
        selected = found.flatMap { it.drop(1) }.map { it.id }.toSet()
    }
    BackHandler { onBack() }
    val doomed = remember(selected, items) { items.filter { it.id in selected } }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Duplicates") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        val g = groups
        when {
            g == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Scanning…") }
            g.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No duplicates found") }
            else -> {
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(g) { group ->
                        Column {
                            Text("${group.size} copies · ${Formatter.formatFileSize(ctx, group.first().size)} each",
                                style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.forEach { m ->
                                    Box(Modifier.size(88.dp).clickable {
                                        selected = if (m.id in selected) selected - m.id else selected + m.id
                                    }) {
                                        AsyncImage(m.uri, m.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        Checkbox(m.id in selected, null, Modifier.align(Alignment.TopStart))
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${doomed.size} selected · frees ${Formatter.formatFileSize(ctx, doomed.sumOf { it.size })}",
                        Modifier.weight(1f))
                    Button({ actions.trash(doomed) { selected = emptySet(); groups = null
                        // rescan after items refresh
                    } }, enabled = doomed.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: GalleryViewModel, onOpen: (MediaItem) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    var geo by remember { mutableStateOf<List<MediaRepo.GeoItem>?>(null) }
    LaunchedEffect(Unit) { geo = MediaRepo.geoTagged(ctx, items) }
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Map") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        val g = geo
        when {
            g == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Reading locations…") }
            g.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No geotagged photos") }
            else -> OsmMap(g, onOpen)
        }
    }
}

@Composable
private fun OsmMap(geo: List<MediaRepo.GeoItem>, onOpen: (MediaItem) -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osm")
            osmdroidTileCache = File(ctx.cacheDir, "osm/tiles")
        }
        MapView(ctx).apply { setMultiTouchControls(true) }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }
    AndroidView(
        factory = { map },
        modifier = Modifier.fillMaxSize(),
        update = { m ->
            m.overlays.clear()
            val pts = geo.map { GeoPoint(it.lat, it.lon) }
            geo.forEach { g ->
                m.overlays.add(Marker(m).apply {
                    position = GeoPoint(g.lat, g.lon)
                    title = g.item.name
                    setOnMarkerClickListener { _, _ -> onOpen(g.item); true }
                })
            }
            m.post {
                if (pts.size == 1) {
                    m.controller.setZoom(13.0); m.controller.setCenter(pts[0])
                } else {
                    m.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), false, 100)
                }
                m.invalidate()
            }
        },
    )
}
