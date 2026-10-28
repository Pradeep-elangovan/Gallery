package com.pradeep.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pradeep.gallery.data.MediaItem
import com.pradeep.gallery.ui.DuplicatesScreen
import com.pradeep.gallery.ui.GridScreen
import com.pradeep.gallery.ui.HomeScreen
import com.pradeep.gallery.ui.MapScreen
import com.pradeep.gallery.ui.PinGalleryTheme
import com.pradeep.gallery.ui.TrashScreen
import com.pradeep.gallery.ui.ViewerScreen
import com.pradeep.gallery.ui.rememberActions

class MainActivity : ComponentActivity() {
    private val vm: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PinGalleryTheme { PermissionGate(vm) } }
    }
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.ACCESS_MEDIA_LOCATION)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

@Composable
private fun PermissionGate(vm: GalleryViewModel) {
    val ctx = LocalContext.current
    fun hasAccess() = requiredPermissions().first().let {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(hasAccess()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAccess()
    }
    LaunchedEffect(granted) {
        if (granted) vm.refresh() else launcher.launch(requiredPermissions())
    }
    if (granted) {
        GalleryApp(vm)
    } else {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Pin Gallery needs access to your photos and videos.", style = MaterialTheme.typography.titleMedium)
                Button({ launcher.launch(requiredPermissions()) }, Modifier.padding(top = 16.dp)) { Text("Grant access") }
            }
        }
    }
}

private sealed interface Screen {
    data class Album(val bucketId: Long, val name: String) : Screen
    data class Viewer(val ids: List<Long>, val startId: Long, val scope: String) : Screen
    data object Duplicates : Screen
    data object Map : Screen
    data object Trash : Screen
}

@Composable
private fun GalleryApp(vm: GalleryViewModel) {
    val actions = rememberActions(vm)
    val items by vm.items.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Screen>() }
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    BackHandler(stack.isNotEmpty()) { pop() }

    // Home stays composed underneath so scroll position and selection survive opening the viewer.
    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize()) {
            HomeScreen(
                vm, actions, tab, { tab = it },
                onOpen = { list, item, scope -> push(Screen.Viewer(list.map { it.id }, item.id, scope)) },
                onAlbum = { id, name -> push(Screen.Album(id, name)) },
                onDuplicates = { push(Screen.Duplicates) },
                onMap = { push(Screen.Map) },
                onTrash = { push(Screen.Trash) },
            )
        }
        stack.forEachIndexed { index, screen ->
            androidx.compose.runtime.key(index, screen) {
                Surface(Modifier.fillMaxSize()) {
                    when (screen) {
                        is Screen.Album -> GridScreen(
                            vm, actions, screen.name, items.filter { it.bucketId == screen.bucketId },
                            scope = screen.bucketId.toString(),
                            onOpen = { list, item -> push(Screen.Viewer(list.map { it.id }, item.id, screen.bucketId.toString())) },
                            onBack = ::pop, searchable = true, filterable = true,
                        )
                        is Screen.Viewer -> ViewerScreen(vm, actions, screen.ids, screen.startId, screen.scope, ::pop)
                        Screen.Duplicates -> DuplicatesScreen(vm, actions, ::pop)
                        Screen.Map -> MapScreen(vm, onOpen = { m: MediaItem -> push(Screen.Viewer(listOf(m.id), m.id, "map")) }, onBack = ::pop)
                        Screen.Trash -> TrashScreen(vm, actions, ::pop)
                    }
                }
            }
        }
    }
}
