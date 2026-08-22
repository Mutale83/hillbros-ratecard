package com.hillbros.videoeditor.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hillbros.videoeditor.VideoEditorApp
import com.hillbros.videoeditor.data.Clip
import com.hillbros.videoeditor.data.ExportQuality
import com.hillbros.videoeditor.data.ProjectRepository
import com.hillbros.videoeditor.data.VideoProject
import com.hillbros.videoeditor.media.ClipEffects
import com.hillbros.videoeditor.media.ExportOutput
import com.hillbros.videoeditor.media.MediaUtils
import com.hillbros.videoeditor.media.VideoExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data class Done(val output: ExportOutput) : ExportState
    data class Failed(val message: String) : ExportState
}

data class EditorUiState(
    val project: VideoProject? = null,
    val selectedClipId: String? = null,
    val isImporting: Boolean = false,
    val exportState: ExportState = ExportState.Idle,
    val message: String? = null,
) {
    val clips: List<Clip> get() = project?.clips.orEmpty()
    val selectedClip: Clip? get() = clips.firstOrNull { it.id == selectedClipId }
    val selectedIndex: Int get() = clips.indexOfFirst { it.id == selectedClipId }
}

class EditorViewModel(
    application: Application,
    private val repository: ProjectRepository,
    private val projectId: String,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val exporter = VideoExporter(application)

    val player: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
        playWhenReady = false
    }

    /** Tracks which timeline entry the preview is on, to apply its effects. */
    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            applyPreviewSettingsForIndex(player.currentMediaItemIndex)
        }
    }

    init {
        player.addListener(playerListener)
        val project = repository.get(projectId)
        _uiState.update {
            it.copy(project = project, selectedClipId = project?.clips?.firstOrNull()?.id)
        }
        syncPlayerItems(resetPosition = true)
    }

    // ---------------------------------------------------------------- editing

    fun selectClip(clipId: String) {
        _uiState.update { it.copy(selectedClipId = clipId) }
        val index = _uiState.value.selectedIndex
        if (index >= 0 && index < player.mediaItemCount) {
            player.seekTo(index, 0L)
            applyPreviewSettingsForIndex(index)
        }
    }

    fun importClips(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            var rejected = 0
            val newClips = mutableListOf<Clip>()

            for (uri in uris) {
                MediaUtils.persistReadPermission(context, uri)
                val info = MediaUtils.readSourceInfo(context, uri)
                if (info == null) {
                    rejected++
                    continue
                }
                newClips += Clip(
                    uri = uri.toString(),
                    displayName = info.displayName,
                    sourceDurationMs = info.durationMs,
                    trimEndMs = info.durationMs,
                )
            }

            if (newClips.isNotEmpty()) {
                mutateProject(structural = true) { it.copy(clips = it.clips + newClips) }
                _uiState.update { state ->
                    state.copy(selectedClipId = state.selectedClipId ?: newClips.first().id)
                }
            }

            _uiState.update {
                it.copy(
                    isImporting = false,
                    message = when {
                        rejected > 0 && newClips.isEmpty() -> "Could not read those files."
                        rejected > 0 -> "Added ${newClips.size}, skipped $rejected unreadable."
                        else -> null
                    },
                )
            }
        }
    }

    fun removeClip(clipId: String) {
        val remaining = _uiState.value.clips.filterNot { it.id == clipId }
        mutateProject(structural = true) { it.copy(clips = remaining) }
        if (_uiState.value.selectedClipId == clipId) {
            _uiState.update { it.copy(selectedClipId = remaining.firstOrNull()?.id) }
        }
    }

    fun duplicateClip(clipId: String) {
        val clips = _uiState.value.clips
        val index = clips.indexOfFirst { it.id == clipId }
        if (index < 0) return
        val copy = clips[index].copy(id = java.util.UUID.randomUUID().toString())
        val updated = clips.toMutableList().apply { add(index + 1, copy) }
        mutateProject(structural = true) { it.copy(clips = updated) }
    }

    fun moveClip(clipId: String, offset: Int) {
        val clips = _uiState.value.clips.toMutableList()
        val from = clips.indexOfFirst { it.id == clipId }
        val to = from + offset
        if (from < 0 || to < 0 || to >= clips.size) return
        clips.add(to, clips.removeAt(from))
        mutateProject(structural = true) { it.copy(clips = clips) }
    }

    /** Applies an edit to the selected clip. Effect-only changes skip a
     *  playlist rebuild so playback is not interrupted mid-scrub. */
    fun updateSelectedClip(structural: Boolean = false, transform: (Clip) -> Clip) {
        val selectedId = _uiState.value.selectedClipId ?: return
        val updated = _uiState.value.clips.map { clip ->
            if (clip.id == selectedId) transform(clip) else clip
        }
        mutateProject(structural = structural) { it.copy(clips = updated) }
        if (!structural) applyPreviewSettingsForIndex(player.currentMediaItemIndex)
    }

    fun setMusic(uri: Uri?, displayName: String?) {
        val context = getApplication<Application>()
        uri?.let { MediaUtils.persistReadPermission(context, it) }
        mutateProject(structural = false) {
            it.copy(musicUri = uri?.toString(), musicDisplayName = displayName)
        }
    }

    fun setMusicVolume(volume: Float) {
        mutateProject(structural = false) { it.copy(musicVolume = volume) }
    }

    fun rename(name: String) {
        mutateProject(structural = false) { it.copy(name = name.ifBlank { it.name }) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    // ----------------------------------------------------------------- export

    fun export(quality: ExportQuality) {
        val project = _uiState.value.project ?: return
        if (project.clips.isEmpty()) {
            _uiState.update { it.copy(exportState = ExportState.Failed("Add a clip first.")) }
            return
        }
        player.pause()
        viewModelScope.launch {
            _uiState.update { it.copy(exportState = ExportState.Running(0f)) }
            runCatching {
                exporter.export(project, quality) { progress ->
                    _uiState.update { state ->
                        // A late progress tick must not overwrite a terminal state.
                        if (state.exportState is ExportState.Running) {
                            state.copy(exportState = ExportState.Running(progress))
                        } else {
                            state
                        }
                    }
                }
            }.onSuccess { output ->
                _uiState.update { it.copy(exportState = ExportState.Done(output)) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        exportState = ExportState.Failed(
                            error.message ?: "Export failed.",
                        ),
                    )
                }
            }
        }
    }

    fun dismissExport() = _uiState.update { it.copy(exportState = ExportState.Idle) }

    // ----------------------------------------------------------------- player

    private fun applyPreviewSettingsForIndex(index: Int) {
        val clip = _uiState.value.clips.getOrNull(index) ?: return
        player.volume = clip.volume
        player.setPlaybackSpeed(if (clip.speed > 0f) clip.speed else 1f)
        // Speed is handled by playback parameters during preview, so it is
        // filtered out of the GL chain here to avoid applying it twice.
        player.setVideoEffects(ClipEffects.videoEffects(clip.copy(speed = 1f)))
    }

    private fun syncPlayerItems(resetPosition: Boolean) {
        val clips = _uiState.value.clips
        val items = clips.map { clip ->
            MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs)
                        .setEndPositionMs(clip.trimEndMs)
                        .build(),
                )
                .build()
        }

        val previousIndex = player.currentMediaItemIndex
        val previousPosition = player.currentPosition
        val wasPlaying = player.isPlaying

        player.setMediaItems(items, /* resetPosition = */ resetPosition)
        player.prepare()

        if (!resetPosition && items.isNotEmpty()) {
            val safeIndex = previousIndex.coerceIn(0, items.lastIndex)
            player.seekTo(safeIndex, previousPosition)
            player.playWhenReady = wasPlaying
        }
        applyPreviewSettingsForIndex(player.currentMediaItemIndex)
    }

    // ------------------------------------------------------------- persistence

    private fun mutateProject(structural: Boolean, transform: (VideoProject) -> VideoProject) {
        val current = _uiState.value.project ?: return
        val updated = transform(current)
        _uiState.update { it.copy(project = updated) }
        if (structural) syncPlayerItems(resetPosition = false)
        viewModelScope.launch { repository.upsert(updated) }
    }

    override fun onCleared() {
        player.removeListener(playerListener)
        player.release()
        super.onCleared()
    }

    companion object {
        fun factory(projectId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as VideoEditorApp
                EditorViewModel(app, app.repository, projectId)
            }
        }
    }
}
