package com.pradeep.gallery

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pradeep.gallery.data.Favorite
import com.pradeep.gallery.data.MediaItem
import com.pradeep.gallery.data.MediaRepo
import com.pradeep.gallery.data.Pin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as PinGalleryApp).db.dao()

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val pins: StateFlow<List<Pin>> =
        dao.pins().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favorites: StateFlow<Set<Long>> =
        dao.favorites().map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private var refreshJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    init {
        app.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri("external"), true, observer,
        )
    }

    /** Debounced so a burst of MediaStore changes triggers a single reload. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(300)
            _items.value = MediaRepo.load(getApplication())
            _loaded.value = true
        }
    }

    /** If every id is already pinned in [scope] this unpins them; otherwise pins the missing ones. */
    fun togglePin(ids: Collection<Long>, scope: String) = viewModelScope.launch {
        val current = pins.value.filter { it.scope == scope }.map { it.mediaId }.toSet()
        if (ids.all { it in current }) {
            dao.removePins(scope, ids.toList())
        } else {
            val now = System.currentTimeMillis()
            // Keep the selection's relative order: first id ends up first.
            dao.addPins(ids.filter { it !in current }.reversed().mapIndexed { i, id -> Pin(id, scope, now + i) })
        }
    }

    fun toggleFavorite(ids: Collection<Long>) = viewModelScope.launch {
        val current = favorites.value
        if (ids.all { it in current }) dao.removeFavorites(ids.toList())
        else dao.addFavorites(ids.map { Favorite(it) })
    }

    /** Called after the system confirmed trashing/deleting these items. */
    fun onRemoved(ids: Collection<Long>) = viewModelScope.launch {
        dao.removeAllPinsFor(ids.toList())
        dao.removeFavorites(ids.toList())
        refresh()
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
