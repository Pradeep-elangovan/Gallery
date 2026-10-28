package com.pradeep.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pradeep.gallery.data.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SortMode(val label: String) {
    NEWEST("Newest first"), OLDEST("Oldest first"), NAME("Name"), SIZE("Largest first")
}

enum class TypeFilter(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos") }

fun sortItems(items: List<MediaItem>, sort: SortMode): List<MediaItem> = when (sort) {
    SortMode.NEWEST -> items.sortedByDescending { it.date }
    SortMode.OLDEST -> items.sortedBy { it.date }
    SortMode.NAME -> items.sortedBy { it.name.lowercase() }
    SortMode.SIZE -> items.sortedByDescending { it.size }
}

/** Pinned items first (most recently pinned first); everything else follows in the chosen sort. */
fun splitPinned(
    items: List<MediaItem>,
    pinnedAt: Map<Long, Long>,
    sort: SortMode,
): Pair<List<MediaItem>, List<MediaItem>> {
    val sorted = sortItems(items, sort)
    val pinned = sorted.filter { it.id in pinnedAt }.sortedByDescending { pinnedAt[it.id] }
    val rest = sorted.filter { it.id !in pinnedAt }
    return pinned to rest
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

private fun dayLabel(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(dayFmt)
    }
}

fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
fun MediaGrid(
    pinned: List<MediaItem>,
    rest: List<MediaItem>,
    favorites: Set<Long>,
    sort: SortMode,
    selected: Set<Long>,
    selecting: Boolean,
    onTap: (MediaItem) -> Unit,
    onLongPress: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val sections = remember(rest, sort) {
        when (sort) {
            SortMode.NEWEST, SortMode.OLDEST -> rest.groupBy { dayLabel(it.date) }.toList()
            else -> if (rest.isEmpty()) emptyList() else listOf((if (pinned.isEmpty()) "" else "Everything else") to rest)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        if (pinned.isNotEmpty()) {
            item(key = "hdr_pinned", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Pinned", pinIcon = true)
            }
            items(pinned, key = { "p${it.id}" }) { item ->
                Thumb(item, pinned = true, fav = item.id in favorites, selected = item.id in selected,
                    selecting = selecting, onTap = { onTap(item) }, onLong = { onLongPress(item) })
            }
        }
        sections.forEach { (label, list) ->
            if (label.isNotEmpty()) {
                item(key = "hdr_$label", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(label) }
            }
            items(list, key = { "m${it.id}" }) { item ->
                Thumb(item, pinned = false, fav = item.id in favorites, selected = item.id in selected,
                    selecting = selecting, onTap = { onTap(item) }, onLong = { onLongPress(item) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, pinIcon: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pinIcon) {
            Icon(Icons.Filled.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (pinIcon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumb(
    item: MediaItem,
    pinned: Boolean,
    fav: Boolean,
    selected: Boolean,
    selecting: Boolean,
    onTap: () -> Unit,
    onLong: () -> Unit,
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .combinedClickable(onClick = onTap, onLongClick = onLong),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        if (selected) {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    .border(3.dp, MaterialTheme.colorScheme.primary),
            )
        }
        if (selecting) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp)
                    .background(if (selected) Color.White else Color.Black.copy(alpha = 0.25f), CircleShape),
            )
        }
        if (pinned) {
            Icon(
                Icons.Filled.PushPin, "Pinned", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp),
            )
        }
        if (fav) {
            Icon(
                Icons.Filled.Favorite, "Favorite", tint = Color(0xFFFF5A7A),
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(16.dp),
            )
        }
        if (item.isVideo) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(12.dp), tint = Color.White)
                Text(fmtDuration(item.durationMs), color = Color.White, fontSize = 10.sp)
            }
        }
    }
}
