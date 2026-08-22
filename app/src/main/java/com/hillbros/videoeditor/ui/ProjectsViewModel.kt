package com.hillbros.videoeditor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hillbros.videoeditor.VideoEditorApp
import com.hillbros.videoeditor.data.ProjectRepository
import com.hillbros.videoeditor.data.VideoProject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProjectsViewModel(
    private val repository: ProjectRepository,
) : ViewModel() {

    val projects: StateFlow<List<VideoProject>> = repository.projects

    /** Creates an empty project and returns its id so the caller can navigate. */
    fun createProject(name: String, onCreated: (String) -> Unit) {
        val project = VideoProject(name = name.ifBlank { "Untitled project" })
        viewModelScope.launch {
            repository.upsert(project)
            onCreated(project.id)
        }
    }

    fun delete(projectId: String) {
        viewModelScope.launch { repository.delete(projectId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as VideoEditorApp
                ProjectsViewModel(app.repository)
            }
        }
    }
}
