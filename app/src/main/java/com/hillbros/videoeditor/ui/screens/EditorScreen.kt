package com.hillbros.videoeditor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hillbros.videoeditor.data.ExportQuality
import com.hillbros.videoeditor.media.MediaUtils
import com.hillbros.videoeditor.ui.EditorViewModel
import com.hillbros.videoeditor.ui.ExportState
import com.hillbros.videoeditor.ui.components.AdjustPanel
import com.hillbros.videoeditor.ui.components.AudioPanel
import com.hillbros.videoeditor.ui.components.FilterPanel
import com.hillbros.videoeditor.ui.components.PlayerSurface
import com.hillbros.videoeditor.ui.components.TextPanel
import com.hillbros.videoeditor.ui.components.Timeline
import com.hillbros.videoeditor.ui.components.TrimPanel

private val EDIT_TABS = listOf("Trim", "Adjust", "Look", "Text", "Audio")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.factory(projectId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK),
    ) { uris: List<Uri> -> viewModel.importClips(uris) }

    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.setMusic(it, it.lastPathSegment?.substringAfterLast('/')) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val project = state.project
    if (project == null) {
        MissingProject(onBack)
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        project.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showExportDialog = true },
                        enabled = state.clips.isNotEmpty(),
                    ) { Text("Export") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PlayerSurface(
                player = viewModel.player,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${state.clips.size} clips · ${MediaUtils.formatDuration(project.totalDurationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        videoPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.VideoOnly,
                            ),
                        )
                    },
                ) {
                    if (state.isImporting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                    Spacer(Modifier.size(6.dp))
                    Text("Add clips")
                }
            }

            Timeline(
                clips = state.clips,
                selectedClipId = state.selectedClipId,
                onSelect = viewModel::selectClip,
                onMove = viewModel::moveClip,
                onDuplicate = viewModel::duplicateClip,
                onDelete = viewModel::removeClip,
            )

            HorizontalDivider()

            val selectedClip = state.selectedClip
            if (selectedClip == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Add a clip to start editing.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                TabRow(selectedTabIndex = selectedTab) {
                    EDIT_TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    when (selectedTab) {
                        0 -> TrimPanel(
                            clip = selectedClip,
                            onTrimChanged = { start, end ->
                                viewModel.updateSelectedClip(structural = true) {
                                    it.copy(trimStartMs = start, trimEndMs = end)
                                }
                            },
                        )

                        1 -> AdjustPanel(
                            clip = selectedClip,
                            onClipChanged = { transform ->
                                viewModel.updateSelectedClip(transform = transform)
                            },
                        )

                        2 -> FilterPanel(
                            clip = selectedClip,
                            onClipChanged = { transform ->
                                viewModel.updateSelectedClip(transform = transform)
                            },
                        )

                        3 -> TextPanel(
                            clip = selectedClip,
                            onClipChanged = { transform ->
                                viewModel.updateSelectedClip(transform = transform)
                            },
                        )

                        4 -> AudioPanel(
                            clip = selectedClip,
                            project = project,
                            onClipChanged = { transform ->
                                viewModel.updateSelectedClip(transform = transform)
                            },
                            onPickMusic = { musicPicker.launch(arrayOf("audio/*")) },
                            onClearMusic = { viewModel.setMusic(null, null) },
                            onMusicVolume = viewModel::setMusicVolume,
                        )
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        ExportDialog(
            exportState = state.exportState,
            onExport = { quality ->
                viewModel.export(quality)
            },
            onDismiss = {
                showExportDialog = false
                viewModel.dismissExport()
            },
        )
    }
}

@Composable
private fun ExportDialog(
    exportState: ExportState,
    onExport: (ExportQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    var quality by remember { mutableStateOf(ExportQuality.HD) }
    val isRunning = exportState is ExportState.Running

    AlertDialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        title = { Text(exportTitle(exportState)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (exportState) {
                    is ExportState.Running -> {
                        LinearProgressIndicator(
                            progress = { exportState.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${(exportState.progress * 100).toInt()}%")
                        Text(
                            "Keep the app open while rendering.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is ExportState.Done -> {
                        Text("Saved as ${exportState.output.file.name}")
                        Text(
                            if (exportState.output.galleryUri != null) {
                                "It is in your gallery under Movies/HillBros."
                            } else {
                                "Saved to the app's Movies folder."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is ExportState.Failed -> {
                        Text(
                            exportState.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    ExportState.Idle -> {
                        Text("Choose an output size:")
                        ExportQuality.entries.forEach { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = quality == option,
                                    onClick = { quality = option },
                                )
                                Text(option.label)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (exportState) {
                ExportState.Idle -> TextButton(onClick = { onExport(quality) }) { Text("Export") }
                is ExportState.Failed -> TextButton(onClick = { onExport(quality) }) { Text("Retry") }
                is ExportState.Done -> TextButton(onClick = onDismiss) { Text("Done") }
                is ExportState.Running -> {}
            }
        },
        dismissButton = {
            if (!isRunning) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

private fun exportTitle(state: ExportState): String = when (state) {
    ExportState.Idle -> "Export video"
    is ExportState.Running -> "Rendering…"
    is ExportState.Done -> "Export complete"
    is ExportState.Failed -> "Export failed"
}

@Composable
private fun MissingProject(onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("That project could not be opened.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onBack) { Text("Back to projects") }
        }
    }
}

private const val MAX_PICK = 20
