package com.pradeep.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val date: Long,          // millis, date taken or added
    val size: Long,
    val bucketId: Long,
    val bucket: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
)

data class Album(val bucketId: Long, val name: String, val count: Int, val cover: MediaItem)

/** Pinned items for [scope] first (most recently pinned first), then everything else in date order. */
fun orderWithPins(items: List<MediaItem>, pins: List<Pin>, scope: String): List<MediaItem> {
    val pinnedAt = pins.filter { it.scope == scope }.associate { it.mediaId to it.pinnedAt }
    if (pinnedAt.isEmpty()) return items
    val (pinned, rest) = items.partition { it.id in pinnedAt }
    return pinned.sortedByDescending { pinnedAt[it.id] } + rest
}

fun buildAlbums(items: List<MediaItem>): List<Album> =
    items.groupBy { it.bucketId }
        .map { (id, list) -> Album(id, list.first().bucket, list.size, list.first()) }
        .sortedByDescending { it.cover.date }

object MediaRepo {

    suspend fun load(ctx: Context, trashed: Boolean = false): List<MediaItem> = withContext(Dispatchers.IO) {
        val out = ArrayList<MediaItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
        )
        val selection =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val query = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC",
            )
            putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED,
                if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE,
            )
        }
        ctx.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection, query, null,
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val iBucketName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val video = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val taken = c.getLong(iTaken)
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    name = c.getString(iName) ?: "",
                    mime = c.getString(iMime) ?: "",
                    isVideo = video,
                    date = if (taken > 0) taken else c.getLong(iAdded) * 1000,
                    size = c.getLong(iSize),
                    bucketId = c.getLong(iBucket),
                    bucket = c.getString(iBucketName) ?: "Unknown",
                    durationMs = c.getLong(iDur),
                    width = c.getInt(iW),
                    height = c.getInt(iH),
                )
            }
        }
        out.sortedByDescending { it.date }
    }

    /** Groups of byte-identical files. Cheap size filter first, then full MD5 within same-size groups. */
    suspend fun findDuplicates(ctx: Context, items: List<MediaItem>): List<List<MediaItem>> =
        withContext(Dispatchers.IO) {
            items.filter { it.size > 0 }
                .groupBy { it.size }
                .values.filter { it.size > 1 }
                .flatMap { sameSize ->
                    sameSize.groupBy { md5(ctx, it.uri) }
                        .filterKeys { it != null }
                        .values.filter { it.size > 1 }
                }
                .sortedByDescending { it.first().size * it.size }
        }

    private fun md5(ctx: Context, uri: Uri): String? = try {
        val md = MessageDigest.getInstance("MD5")
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        } ?: return null
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    data class GeoItem(val item: MediaItem, val lat: Double, val lon: Double)

    suspend fun geoTagged(ctx: Context, items: List<MediaItem>): List<GeoItem> =
        withContext(Dispatchers.IO) {
            items.filter { !it.isVideo }.mapNotNull { item ->
                try {
                    val src = try { MediaStore.setRequireOriginal(item.uri) } catch (_: Exception) { item.uri }
                    ctx.contentResolver.openInputStream(src)?.use { s ->
                        val ll = ExifInterface(s).latLong
                        if (ll != null) GeoItem(item, ll[0], ll[1]) else null
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
}
