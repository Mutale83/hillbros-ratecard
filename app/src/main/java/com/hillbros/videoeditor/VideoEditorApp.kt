package com.hillbros.videoeditor

import android.app.Application
import com.hillbros.videoeditor.data.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VideoEditorApp : Application() {

    lateinit var repository: ProjectRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        repository = ProjectRepository(this)
        appScope.launch { repository.load() }
    }
}
