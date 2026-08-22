package com.hillbros.videoeditor.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.hillbros.videoeditor.data.Clip
import com.hillbros.videoeditor.data.ExportQuality
import com.hillbros.videoeditor.data.VideoProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class ExportFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ExportOutput(
    val file: File,
    /** Non-null once the result has been published to the system gallery. */
    val galleryUri: Uri?,
)

/**
 * Renders a [VideoProject] to a single MP4 using Media3 Transformer.
 *
 * Every clip becomes an [EditedMediaItem] carrying its own trim window and
 * effect chain; those form one sequence. Optional background music becomes a
 * second, audio-only sequence, which is how Transformer expresses mixing.
 */
class VideoExporter(private val context: Context) {

    suspend fun export(
        project: VideoProject,
        quality: ExportQuality,
        onProgress: (Float) -> Unit,
    ): ExportOutput {
        if (project.clips.isEmpty()) {
            throw ExportFailure("Add at least one clip before exporting.")
        }

        val outputFile = createOutputFile(project.name)

        // Transformer posts callbacks to the thread it was built on, so it
        // needs a Looper — the main thread is the reliable choice.
        withContext(Dispatchers.Main) {
            runTransformer(project, quality, outputFile, onProgress)
        }

        val galleryUri = withContext(Dispatchers.IO) {
            runCatching { saveToGallery(outputFile) }.getOrNull()
        }
        return ExportOutput(outputFile, galleryUri)
    }

    private suspend fun runTransformer(
        project: VideoProject,
        quality: ExportQuality,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ) = coroutineScope {
        val composition = buildComposition(project, quality)

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(quality.bitrate)
                            .build(),
                    )
                    .build(),
            )
            .build()

        val progressJob = launch(Dispatchers.Main) {
            val holder = ProgressHolder()
            while (isActive) {
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
                delay(PROGRESS_POLL_MS)
            }
        }

        try {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException,
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWith(
                                Result.failure(
                                    ExportFailure(
                                        exception.message ?: "Export failed while rendering.",
                                        exception,
                                    ),
                                ),
                            )
                        }
                    }
                }

                transformer.addListener(listener)
                continuation.invokeOnCancellation {
                    runCatching { transformer.cancel() }
                    outputFile.delete()
                }
                transformer.start(composition, outputFile.absolutePath)
            }
            onProgress(1f)
        } finally {
            progressJob.cancel()
        }
    }

    private fun buildComposition(project: VideoProject, quality: ExportQuality): Composition {
        val videoSequence = EditedMediaItemSequence(project.clips.map(::toEditedMediaItem))

        val sequences = mutableListOf(videoSequence)
        buildMusicSequence(project)?.let { sequences += it }

        val builder = Composition.Builder(sequences)

        if (quality.height > 0) {
            builder.setEffects(
                Effects(
                    /* audioProcessors = */ emptyList(),
                    /* videoEffects = */ listOf(Presentation.createForHeight(quality.height)),
                ),
            )
        }

        return builder.build()
    }

    private fun toEditedMediaItem(clip: Clip): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(clip.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartMs)
                    .setEndPositionMs(clip.trimEndMs)
                    .build(),
            )
            .build()

        return EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(clip.isMuted)
            .setEffects(
                Effects(
                    ClipEffects.audioProcessors(clip),
                    ClipEffects.videoEffects(clip),
                ),
            )
            .build()
    }

    /**
     * Background music rides in its own sequence. It is clipped to the length
     * of the timeline so a long track cannot extend the finished video.
     */
    private fun buildMusicSequence(project: VideoProject): EditedMediaItemSequence? {
        val musicUri = project.musicUri ?: return null
        val timelineMs = project.totalDurationMs
        if (timelineMs <= 0L) return null

        val mediaItem = MediaItem.Builder()
            .setUri(musicUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0L)
                    .setEndPositionMs(timelineMs)
                    .build(),
            )
            .build()

        val processors: List<AudioProcessor> =
            listOf(ClipEffects.gainProcessor(project.musicVolume))

        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(Effects(processors, emptyList()))
            .build()

        return EditedMediaItemSequence(listOf(editedItem))
    }

    private fun createOutputFile(projectName: String): File {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
        if (!directory.exists()) directory.mkdirs()

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeName = projectName
            .replace(Regex("[^A-Za-z0-9-_ ]"), "")
            .trim()
            .ifEmpty { "project" }
            .replace(' ', '-')

        return File(directory, "$safeName-$stamp.mp4")
    }

    /**
     * Copies the render into shared storage so it shows up in Photos/Gallery.
     * Only attempted on API 29+, where this needs no storage permission.
     */
    private fun saveToGallery(source: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, source.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/HillBros")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: return null

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private companion object {
        const val PROGRESS_POLL_MS = 250L
    }
}
