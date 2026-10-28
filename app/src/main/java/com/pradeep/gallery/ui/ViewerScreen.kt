package com.pradeep.gallery.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pradeep.gallery.GalleryViewModel
import com.pradeep.gallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun ViewerScreen(
    vm: GalleryViewModel,
    actions: MediaActions,
    ids: List<Long>,
    startId: Long,
    scope: String,
    onClose: () -> Unit,
) {
    val all by vm.items.collectAsStateWithLifecycle()
    val pins by vm.pins.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val byId = remember(all) { all.associateBy { it.id } }
    val list = remember(ids, byId) { ids.mapNotNull { byId[it] } }

    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val pager = rememberPagerState(initialPage = ids.indexOf(startId).coerceAtLeast(0)) { list.size }
    var chrome by remember { mutableStateOf(true) }
    var slideshow by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val current = list[pager.currentPage.coerceIn(0, list.lastIndex)]
    val isPinned = pins.any { it.mediaId == current.id && it.scope == scope }
    val isFav = current.id in favs

    LaunchedEffect(slideshow, pager.currentPage) {
        if (slideshow) {
            chrome = false
            delay(3000)
            if (list.size < 2) { slideshow = false; return@LaunchedEffect }
            val next = (pager.currentPage + 1) % list.size
            if (list[next].isVideo) { slideshow = false; chrome = true } else pager.animateScrollToPage(next)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { list[it].id }) { page ->
            val item = list[page]
            if (item.isVideo) VideoPage(item, active = pager.currentPage == page)
            else ZoomableImage(item, onTap = { chrome = !chrome; if (slideshow) slideshow = false })
        }

        if (chrome) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.65f), Color.Transparent)))
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text(
                    "${pager.currentPage + 1} / ${list.size}", color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                if (list.size > 1) {
                    IconButton({ slideshow = !slideshow }) {
                        Icon(if (slideshow) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Slideshow", tint = Color.White)
                    }
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Color.White) }
                    DropdownMenu(menu, { menu = false }) {
                        if (!current.isVideo) DropdownMenuItem(
                            text = { Text("Set as wallpaper / contact") },
                            onClick = { menu = false; actions.setAs(current) },
                        )
                        DropdownMenuItem(text = { Text("Edit with…") }, onClick = { menu = false; actions.edit(current) })
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.65f))))
                    .navigationBarsPadding(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            ) {
                IconButton({ vm.togglePin(listOf(current.id), scope) }) {
                    Icon(if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        if (isPinned) "Unpin" else "Pin", tint = Color.White)
                }
                IconButton({ vm.toggleFavorite(listOf(current.id)) }) {
                    Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Favorite", tint = if (isFav) Color(0xFFFF5A7A) else Color.White)
                }
                IconButton({ actions.share(listOf(current)) }) { Icon(Icons.Filled.Share, "Share", tint = Color.White) }
                IconButton({ showInfo = true }) { Icon(Icons.Filled.Info, "Details", tint = Color.White) }
                IconButton({ actions.trash(listOf(current)) }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
            }
        }
    }

    if (showInfo) DetailsDialog(current) { showInfo = false }
}

@Composable
private fun ZoomableImage(item: MediaItem, onTap: () -> Unit) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        // Only take over the gesture when pinching or already zoomed, so paging still works at 1x.
                        if (ev.changes.size > 1 || scale > 1f) {
                            val ns = (scale * ev.calculateZoom()).coerceIn(1f, 6f)
                            val pan = ev.calculatePan()
                            val maxX = box.width * (ns - 1) / 2f
                            val maxY = box.height * (ns - 1) / 2f
                            scale = ns
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(item.uri).size(2048).build(),
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun VideoPage(item: MediaItem, active: Boolean) {
    if (!active) {
        AsyncImage(
            model = item.uri, contentDescription = item.name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().alpha(0.6f),
        )
        return
    }
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(item.uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) player.pause() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); player.release() }
    }
    AndroidView(
        factory = { c -> PlayerView(c).apply { this.player = player; useController = true } },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun DetailsDialog(item: MediaItem, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra by produceState<List<Pair<String, String>>>(emptyList(), item.uri) {
        value = withContext(Dispatchers.IO) {
            if (item.isVideo) emptyList() else try {
                ctx.contentResolver.openInputStream(item.uri)?.use { s ->
                    val e = ExifInterface(s)
                    buildList {
                        val cam = listOfNotNull(
                            e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL),
                        ).joinToString(" ")
                        if (cam.isNotBlank()) add("Camera" to cam)
                        e.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { add("Aperture" to "f/$it") }
                        e.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { add("Exposure" to "${it}s") }
                        e.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { add("ISO" to it) }
                        e.latLong?.let { add("Location" to "%.5f, %.5f".format(it[0], it[1])) }
                    }
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
    val rows = buildList {
        add("Name" to item.name)
        add("Album" to item.bucket)
        add("Date" to DateFormat.getDateTimeInstance().format(Date(item.date)))
        add("Size" to Formatter.formatFileSize(ctx, item.size))
        if (item.width > 0) add("Resolution" to "${item.width} × ${item.height}")
        if (item.isVideo) add("Duration" to fmtDuration(item.durationMs))
        add("Type" to item.mime)
        addAll(extra)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text("Details") },
        text = {
            androidx.compose.foundation.layout.Column {
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(k, Modifier.weight(0.35f), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, Modifier.weight(0.65f))
                    }
                }
            }
        },
    )
}

