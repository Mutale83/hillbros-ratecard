package com.hillbros.videoeditor.media

import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList
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
            for (channelCount in 1..2) {
                putChannelMixingMatrix(
                    ChannelMixingMatrix
                        .createForConstantGain(channelCount, channelCount)
                        .scaleBy(gain),
                )
            }
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

    private fun textEffect(spec: TextOverlaySpec): Effect {
        val span = SpannableString(spec.text).apply {
            setSpan(
                ForegroundColorSpan(spec.colorArgb),
                0,
                length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
            setSpan(
                AbsoluteSizeSpan(spec.sizeSp.toInt().coerceAtLeast(8), false),
                0,
                length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
        }

        // Normalised device coordinates: y runs -1 (bottom) to +1 (top).
        val anchorY = when (spec.position) {
            TextPosition.TOP -> 0.75f
            TextPosition.CENTER -> 0f
            TextPosition.BOTTOM -> -0.75f
        }

        val settings = StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(0f, anchorY)
            .build()

        val overlay: TextureOverlay = PositionedTextOverlay(span, settings)
        return OverlayEffect(ImmutableList.of(overlay))
    }

    private class PositionedTextOverlay(
        private val span: SpannableString,
        private val settings: StaticOverlaySettings,
    ) : TextOverlay() {
        override fun getText(presentationTimeUs: Long): SpannableString = span
        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
    }

    /** A fixed 4x4 colour matrix, supplied to Media3 in column-major order. */
    private class ConstantRgbMatrix(private val matrix: FloatArray) : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
    }

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
