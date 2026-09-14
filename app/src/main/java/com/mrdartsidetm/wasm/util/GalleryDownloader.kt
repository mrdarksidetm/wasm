package com.mrdartsidetm.wasm.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import java.io.File

object GalleryDownloader {

    /**
     * Saves a photo, video, or GIF file into the user's Android device Gallery / Photos app.
     * Uses Scoped Storage on Android 10+ (API 29+) and MediaStore with MediaScannerConnection on older releases.
     */
    fun saveToGallery(context: Context, file: File, mediaType: String? = null): Boolean {
        if (!file.exists() || file.length() == 0L) return false

        val fileName = file.name
        val isVideo = mediaType == "video" || fileName.endsWith(".mp4", ignoreCase = true)
        val isGif = mediaType == "gif" || fileName.endsWith(".gif", ignoreCase = true)

        val mimeType = when {
            isVideo -> "video/mp4"
            isGif -> "image/gif"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            else -> "image/jpeg"
        }

        val resolver = context.contentResolver

        val collectionUri = if (isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }

        val relativeSubDir = if (isVideo) "Movies/Wasm" else "Pictures/Wasm"
        val timestamp = System.currentTimeMillis()
        val displayName = "Wasm_${timestamp}_$fileName"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSubDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        return try {
            val itemUri = resolver.insert(collectionUri, values) ?: return false

            resolver.openOutputStream(itemUri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: run {
                resolver.delete(itemUri, null, null)
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            } else {
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Batch saves multiple media files to Gallery and returns the count of successfully saved files.
     */
    fun saveBatchToGallery(context: Context, files: List<Pair<String, File>>): Int {
        var count = 0
        for ((type, file) in files) {
            if (saveToGallery(context, file, type)) {
                count++
            }
        }
        return count
    }
}
