package com.hillbros.videoeditor.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** A named colour treatment applied to a clip. */
@Serializable
enum class ClipFilter(val label: String) {
    NONE("None"),
    GRAYSCALE("Mono"),
    SEPIA("Sepia"),
    VIVID("Vivid"),
    COOL("Cool"),
    WARM("Warm"),
    INVERT("Invert"),
}

/** Where a text overlay sits in the frame. */
@Serializable
enum class TextPosition(val label: String) {
    TOP("Top"),
    CENTER("Center"),
    BOTTOM("Bottom"),
}

@Serializable
data class TextOverlaySpec(
    val text: String = "",
    val sizeSp: Float = 42f,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val position: TextPosition = TextPosition.BOTTOM,
)

/**
 * One entry on the timeline. [trimStartMs]/[trimEndMs] are offsets into the
 * source file, so trimming never destroys the original media.
 */
@Serializable
data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val displayName: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val volume: Float = 1f,
    val speed: Float = 1f,
    val rotationDegrees: Float = 0f,
    val filter: ClipFilter = ClipFilter.NONE,
    /** -1f..1f, 0f = untouched. */
    val brightness: Float = 0f,
    /** -1f..1f, 0f = untouched. */
    val contrast: Float = 0f,
    /** -1f..1f, 0f = untouched. */
    val saturation: Float = 0f,
    val textOverlay: TextOverlaySpec? = null,
) {
    /** Length of the trimmed region before any speed change. */
    val trimmedDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)

    /** Length this clip actually contributes to the timeline. */
    val outputDurationMs: Long
        get() = if (speed <= 0f) trimmedDurationMs else (trimmedDurationMs / speed).toLong()

    val isMuted: Boolean get() = volume <= 0.001f
}

@Serializable
data class VideoProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val clips: List<Clip> = emptyList(),
    /** Optional background music mixed under the whole timeline. */
    val musicUri: String? = null,
    val musicDisplayName: String? = null,
    val musicVolume: Float = 0.35f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val totalDurationMs: Long get() = clips.sumOf { it.outputDurationMs }
}

/** Output size presets offered at export time. */
enum class ExportQuality(val label: String, val height: Int, val bitrate: Int) {
    SD("480p", 480, 2_500_000),
    HD("720p", 720, 5_000_000),
    FULL_HD("1080p", 1080, 10_000_000),
    ORIGINAL("Original", -1, 12_000_000),
}
