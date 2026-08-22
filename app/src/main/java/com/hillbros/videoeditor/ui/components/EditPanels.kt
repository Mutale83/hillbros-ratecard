package com.hillbros.videoeditor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hillbros.videoeditor.data.Clip
import com.hillbros.videoeditor.data.ClipFilter
import com.hillbros.videoeditor.data.TextOverlaySpec
import com.hillbros.videoeditor.data.TextPosition
import com.hillbros.videoeditor.data.VideoProject
import com.hillbros.videoeditor.media.MediaUtils
import kotlin.math.roundToInt

@Composable
private fun PanelLabel(text: String, value: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        value?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TrimPanel(
    clip: Clip,
    onTrimChanged: (Long, Long) -> Unit,
) {
    // Local state while dragging; the project is only updated on release so
    // the preview playlist is not rebuilt on every frame of the gesture.
    var range by remember(clip.id, clip.trimStartMs, clip.trimEndMs) {
        mutableStateOf(clip.trimStartMs.toFloat()..clip.trimEndMs.toFloat())
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelLabel(
            "Trim",
            "${MediaUtils.formatPrecise(range.start.toLong())} – " +
                MediaUtils.formatPrecise(range.endInclusive.toLong()),
        )
        RangeSlider(
            value = range,
            onValueChange = { newRange ->
                // Keep at least 100ms of footage so the clip stays valid.
                val start = newRange.start
                val end = newRange.endInclusive.coerceAtLeast(start + MIN_CLIP_MS)
                range = start..end.coerceAtMost(clip.sourceDurationMs.toFloat())
            },
            onValueChangeFinished = {
                onTrimChanged(range.start.toLong(), range.endInclusive.toLong())
            },
            valueRange = 0f..clip.sourceDurationMs.toFloat(),
        )
        Text(
            "Kept: ${MediaUtils.formatDuration((range.endInclusive - range.start).toLong())} " +
                "of ${MediaUtils.formatDuration(clip.sourceDurationMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { onTrimChanged(0L, clip.sourceDurationMs) },
        ) { Text("Reset trim") }
    }
}

@Composable
fun AdjustPanel(
    clip: Clip,
    onClipChanged: ((Clip) -> Clip) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PanelLabel("Brightness", percentLabel(clip.brightness))
        Slider(
            value = clip.brightness,
            onValueChange = { value -> onClipChanged { it.copy(brightness = value) } },
            valueRange = -1f..1f,
        )

        PanelLabel("Contrast", percentLabel(clip.contrast))
        Slider(
            value = clip.contrast,
            onValueChange = { value -> onClipChanged { it.copy(contrast = value) } },
            valueRange = -1f..1f,
        )

        PanelLabel("Saturation", percentLabel(clip.saturation))
        Slider(
            value = clip.saturation,
            onValueChange = { value -> onClipChanged { it.copy(saturation = value) } },
            valueRange = -1f..1f,
        )

        PanelLabel("Speed", "${clip.speed}x")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SPEEDS.forEach { speed ->
                FilterChip(
                    selected = clip.speed == speed,
                    onClick = { onClipChanged { it.copy(speed = speed) } },
                    label = { Text("${speed}x") },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = {
                onClipChanged { it.copy(rotationDegrees = (it.rotationDegrees + 90f) % 360f) }
            },
        ) {
            Icon(Icons.Default.Rotate90DegreesCcw, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Rotate (${clip.rotationDegrees.roundToInt()}°)")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPanel(
    clip: Clip,
    onClipChanged: ((Clip) -> Clip) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelLabel("Look")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClipFilter.entries.forEach { filter ->
                FilterChip(
                    selected = clip.filter == filter,
                    onClick = { onClipChanged { it.copy(filter = filter) } },
                    label = { Text(filter.label) },
                )
            }
        }
    }
}

@Composable
fun TextPanel(
    clip: Clip,
    onClipChanged: ((Clip) -> Clip) -> Unit,
) {
    val overlay = clip.textOverlay ?: TextOverlaySpec()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = overlay.text,
            onValueChange = { value ->
                onClipChanged { it.copy(textOverlay = overlay.copy(text = value)) }
            },
            label = { Text("Overlay text") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        PanelLabel("Size", overlay.sizeSp.roundToInt().toString())
        Slider(
            value = overlay.sizeSp,
            onValueChange = { value ->
                onClipChanged { it.copy(textOverlay = overlay.copy(sizeSp = value)) }
            },
            valueRange = 16f..140f,
        )

        PanelLabel("Position")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextPosition.entries.forEach { position ->
                FilterChip(
                    selected = overlay.position == position,
                    onClick = {
                        onClipChanged { it.copy(textOverlay = overlay.copy(position = position)) }
                    },
                    label = { Text(position.label) },
                )
            }
        }

        PanelLabel("Colour")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TEXT_COLORS.forEach { argb ->
                val selected = overlay.colorArgb == argb
                Spacer(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape,
                        )
                        .clickable {
                            onClipChanged { it.copy(textOverlay = overlay.copy(colorArgb = argb)) }
                        },
                )
            }
        }

        if (clip.textOverlay != null) {
            TextButton(onClick = { onClipChanged { it.copy(textOverlay = null) } }) {
                Text("Remove text")
            }
        }
    }
}

@Composable
fun AudioPanel(
    clip: Clip,
    project: VideoProject,
    onClipChanged: ((Clip) -> Clip) -> Unit,
    onPickMusic: () -> Unit,
    onClearMusic: () -> Unit,
    onMusicVolume: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelLabel("Clip volume", percentLabel(clip.volume, signed = false))
        Slider(
            value = clip.volume,
            onValueChange = { value -> onClipChanged { it.copy(volume = value) } },
            valueRange = 0f..1f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = clip.isMuted,
                onClick = {
                    onClipChanged { it.copy(volume = if (it.isMuted) 1f else 0f) }
                },
                label = { Text(if (clip.isMuted) "Muted" else "Mute clip") },
            )
        }

        Spacer(Modifier.height(8.dp))
        PanelLabel("Background music")
        if (project.musicUri == null) {
            Button(onClick = onPickMusic) {
                Icon(Icons.Default.MusicNote, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Add music")
            }
        } else {
            Text(
                project.musicDisplayName ?: "Selected track",
                style = MaterialTheme.typography.bodyMedium,
            )
            PanelLabel("Music volume", percentLabel(project.musicVolume, signed = false))
            Slider(
                value = project.musicVolume,
                onValueChange = onMusicVolume,
                valueRange = 0f..1f,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickMusic) { Text("Replace") }
                TextButton(onClick = onClearMusic) { Text("Remove") }
            }
            Text(
                "Music is mixed under the whole timeline and trimmed to its length.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun percentLabel(value: Float, signed: Boolean = true): String {
    val percent = (value * 100).roundToInt()
    return if (signed && percent > 0) "+$percent%" else "$percent%"
}

private val SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)

private val TEXT_COLORS = listOf(
    0xFFFFFFFF.toInt(),
    0xFF000000.toInt(),
    0xFF4F8DFD.toInt(),
    0xFFFFD166.toInt(),
    0xFFFF6B6B.toInt(),
    0xFF7BE3C3.toInt(),
)

private const val MIN_CLIP_MS = 100f
