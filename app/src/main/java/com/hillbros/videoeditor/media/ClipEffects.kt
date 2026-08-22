package com.hillbros.videoeditor.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextureOverlay
import com.hillbros.videoeditor.data.Clip
import com.hillbros.videoeditor.data.ClipFilter
import com.hillbros.videoeditor.data.TextPosition
import com.hillbros.videoeditor.data.TextOverlaySpec

/**
 * Translates a [Clip]'s edit settings into the Media3 effect chain used for
 * both the on-device preview and the final export, so what you see while
 * editing is what lands in the exported file.
 */
object ClipEffects {

    fun videoEffects(clip: Clip): List<Effect> {
        val effects = mutableListOf<Effect>()

        if (clip.rotationDegrees != 0f) {
            effects += ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(clip.rotationDegrees)
                .build()
        }

        if (clip.speed != 1f && clip.speed > 0f) {
            effects += SpeedChangeEffect(clip.speed)
        }

        // Brightness and contrast take -1f..1f directly, matching our model.
        if (clip.brightness != 0f) effects += Brightness(clip.brightness)
        if (clip.contrast != 0f) effects += Contrast(clip.contrast)

        // HslAdjustment's saturation is expressed in -100..100.
        if (clip.saturation != 0f) {
            effects += HslAdjustment.Builder()
                .adjustSaturation(clip.saturation * 100f)
                .build()
        }

        filterEffect(clip.filter)?.let { effects += it }

        clip.textOverlay
            ?.takeIf { it.text.isNotBlank() }
            ?.let { effects += textEffect(it) }

        return effects
    }

    fun audioProcessors(clip: Clip): List<AudioProcessor> {
        val processors = mutableListOf<AudioProcessor>()

        if (clip.speed != 1f && clip.speed > 0f) {
            processors += SonicAudioProcessor().apply { setSpeed(clip.speed) }
        }

        // A fully muted clip drops its audio track outright (see
        // EditedMediaItem.setRemoveAudio), so only partial gain lands here.
        if (!clip.isMuted && clip.volume != 1f) {
            processors += gainProcessor(clip.volume)
        }

        return processors
    }

    /** Scales every channel by [gain]; used for per-clip and music volume. */
    fun gainProcessor(gain: Float): AudioProcessor =
        ChannelMixingAudioProcessor().apply {
            // Scaled identity matrices: each input channel maps to the matching
            // output channel at [gain], leaving the channel layout untouched.
            putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(gain))
            putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(gain))
        }

    private fun filterEffect(filter: ClipFilter): Effect? = when (filter) {
        ClipFilter.NONE -> null
        ClipFilter.GRAYSCALE -> RgbFilter.createGrayscaleFilter()
        ClipFilter.INVERT -> RgbFilter.createInvertedFilter()
        ClipFilter.SEPIA -> ConstantRgbMatrix(SEPIA_MATRIX)
        ClipFilter.WARM -> ConstantRgbMatrix(WARM_MATRIX)
        ClipFilter.COOL -> ConstantRgbMatrix(COOL_MATRIX)
        ClipFilter.VIVID -> HslAdjustment.Builder().adjustSaturation(40f).build()
    }

    /**
     * Renders the caption into a transparent full-frame bitmap and overlays
     * that. Drawing the text ourselves keeps placement under our control and
     * avoids Media3's text-overlay settings types, whose shape differs
     * between releases.
     */
    private fun textEffect(spec: TextOverlaySpec): Effect {
        val bitmap = Bitmap.createBitmap(OVERLAY_WIDTH, OVERLAY_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.colorArgb
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            // sizeSp is authored against a 1080-tall frame; scale to the
            // overlay canvas so the caption keeps its relative size.
            textSize = spec.sizeSp * (OVERLAY_HEIGHT / 1080f)
            // A soft shadow keeps light text legible over bright footage.
            setShadowLayer(textSize / 12f, 0f, textSize / 24f, Color.argb(160, 0, 0, 0))
        }

        val baseline = when (spec.position) {
            TextPosition.TOP -> OVERLAY_HEIGHT * 0.16f
            TextPosition.CENTER -> OVERLAY_HEIGHT * 0.53f
            TextPosition.BOTTOM -> OVERLAY_HEIGHT * 0.90f
        }

        canvas.drawText(spec.text, OVERLAY_WIDTH / 2f, baseline, paint)

        val overlay: TextureOverlay = BitmapOverlay.createStaticBitmapOverlay(bitmap)
        return OverlayEffect(listOf(overlay))
    }

    /** A fixed 4x4 colour matrix, supplied to Media3 in column-major order. */
    private class ConstantRgbMatrix(private val matrix: FloatArray) : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
    }

    private const val OVERLAY_WIDTH = 1920
    private const val OVERLAY_HEIGHT = 1080

    private val SEPIA_MATRIX = floatArrayOf(
        0.393f, 0.349f, 0.272f, 0f,
        0.769f, 0.686f, 0.534f, 0f,
        0.189f, 0.168f, 0.131f, 0f,
        0f, 0f, 0f, 1f,
    )

    private val WARM_MATRIX = floatArrayOf(
        1.12f, 0f, 0f, 0f,
        0f, 1.02f, 0f, 0f,
        0f, 0f, 0.86f, 0f,
        0f, 0f, 0f, 1f,
    )

    private val COOL_MATRIX = floatArrayOf(
        0.86f, 0f, 0f, 0f,
        0f, 1.0f, 0f, 0f,
        0f, 0f, 1.14f, 0f,
        0f, 0f, 0f, 1f,
    )
}
