package com.pradeep.gallery

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache
import com.pradeep.gallery.data.AppDb

class PinGalleryApp : Application(), ImageLoaderFactory {
    val db: AppDb by lazy { Room.databaseBuilder(this, AppDb::class.java, "pingallery.db").build() }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .build()
}
