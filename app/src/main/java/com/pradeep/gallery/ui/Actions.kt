package com.pradeep.gallery.ui

import android.app.Activity
import android.app.WallpaperManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pradeep.gallery.GalleryViewModel
import com.pradeep.gallery.data.MediaItem

/** Share / trash / delete / restore. Destructive actions go through the system confirmation dialog. */
class MediaActions(
    private val ctx: Context,
    private val vm: GalleryViewModel,
    private val launch: (IntentSender, () -> Unit) -> Unit,
) {
    fun trash(items: List<MediaItem>, onDone: () -> Unit = {}) {
        if (items.isEmpty()) return
        val sender = MediaStore.createTrashRequest(ctx.contentResolver, items.map { it.uri }, true).intentSender
        launch(sender) { vm.onRemoved(items.map { it.id }); onDone() }
    }

    fun restore(items: List<MediaItem>, onDone: () -> Unit = {}) {
        if (items.isEmpty()) return
        val sender = MediaStore.createTrashRequest(ctx.contentResolver, items.map { it.uri }, false).intentSender
        launch(sender) { vm.refresh(); onDone() }
    }

    fun deleteForever(items: List<MediaItem>, onDone: () -> Unit = {}) {
        if (items.isEmpty()) return
        val sender = MediaStore.createDeleteRequest(ctx.contentResolver, items.map { it.uri }).intentSender
        launch(sender) { vm.onRemoved(items.map { it.id }); onDone() }
    }

    fun share(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val type = when {
            items.all { it.isVideo } -> "video/*"
            items.none { it.isVideo } -> "image/*"
            else -> "*/*"
        }
        val intent = if (items.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, items[0].uri)
                this.type = items[0].mime.ifBlank { type }
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
                this.type = type
            }
        }
        intent.clipData = ClipData.newRawUri("media", items[0].uri).also { cd ->
            items.drop(1).forEach { cd.addItem(ClipData.Item(it.uri)) }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun setAs(item: MediaItem) {
        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(item.uri, item.mime.ifBlank { "image/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("mimeType", item.mime.ifBlank { "image/*" })
        }
        start(Intent.createChooser(intent, "Set as"))
    }

    fun edit(item: MediaItem) {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(item.uri, item.mime.ifBlank { if (item.isVideo) "video/*" else "image/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        start(Intent.createChooser(intent, "Edit with"))
    }

    private fun start(intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(ctx, "No app found to handle this", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun rememberActions(vm: GalleryViewModel): MediaActions {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) pending?.invoke()
        pending = null
    }
    return remember(ctx, vm) {
        MediaActions(ctx, vm) { sender, onOk ->
            pending = onOk
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
}
