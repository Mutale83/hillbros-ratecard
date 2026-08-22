package com.hillbros.videoeditor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hillbros.videoeditor.data.Clip
import com.hillbros.videoeditor.media.MediaUtils

/**
 * Horizontal strip of clips in playback order. The selected clip exposes its
 * reorder/duplicate/delete controls inline rather than behind a menu, because
 * reordering is the most common timeline action.
 */
@Composable
fun Timeline(
    clips: List<Clip>,
    selectedClipId: String?,
    onSelect: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(clips, key = { it.id }) { clip ->
            val isSelected = clip.id == selectedClipId
            val index = clips.indexOfFirst { it.id == clip.id }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier
                        .width(128.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(clip.id) },
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (isSelected) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                ) {
                    Box(Modifier.padding(8.dp)) {
                        Column(Modifier.align(Alignment.TopStart)) {
                            Text(
                                "${index + 1}. ${clip.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            modifier = Modifier.align(Alignment.BottomStart),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                MediaUtils.formatDuration(clip.outputDurationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (clip.isMuted) {
                                Icon(
                                    Icons.Default.VolumeOff,
                                    contentDescription = "Muted",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (isSelected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onMove(clip.id, -1) },
                            enabled = index > 0,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Move earlier",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = { onDuplicate(clip.id) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Duplicate",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(
                            onClick = { onDelete(clip.id) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove clip",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = { onMove(clip.id, 1) },
                            enabled = index < clips.lastIndex,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Move later",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
