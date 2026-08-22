package com.hillbros.videoeditor.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SourceInfo(
    val displayName: String,
    val durationMs: Long,
)

object MediaUtils {

    /**
     * Reads the pieces of metadata the editor needs before a clip can be placed
     * on the timeline. A source whose duration cannot be read is unusable, so
     * callers treat a null result as "reject this pick".
     */
    suspend fun readSourceInfo(context: Context, uri: Uri): SourceInfo? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val duration = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: return@withContext null
                if (duration <= 0L) return@withContext null
                SourceInfo(displayName = queryDisplayName(context, uri), durationMs = duration)
            } catch (e: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) {
                        cursor.getString(index)?.let { return it }
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Clip"
    }

    /** Takes a long-lived read grant so a project survives an app restart. */
    fun persistReadPermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /** Millisecond-precision label used on the trim handles. */
    fun formatPrecise(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val tenths = (ms.coerceAtLeast(0) % 1000) / 100
        return String.format("%d:%02d.%d", minutes, seconds, tenths)
    }
}
