package com.hillbros.videoeditor.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Projects are persisted as a single JSON document in app storage. The whole
 * library is small (metadata only — never the media itself), so a read/modify/
 * write of one file is simpler and more robust than a database here.
 */
class ProjectRepository(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val writeLock = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val _projects = MutableStateFlow<List<VideoProject>>(emptyList())
    val projects: StateFlow<List<VideoProject>> = _projects.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val loaded = runCatching {
            if (!file.exists()) emptyList()
            else json.decodeFromString<List<VideoProject>>(file.readText())
        }.getOrElse {
            // A corrupt library should not brick the app — start clean and
            // keep the bad file around for debugging.
            if (file.exists()) file.renameTo(File(appContext.filesDir, "$FILE_NAME.corrupt"))
            emptyList()
        }
        _projects.value = loaded.sortedByDescending { it.updatedAt }
    }

    fun get(projectId: String): VideoProject? = _projects.value.firstOrNull { it.id == projectId }

    suspend fun upsert(project: VideoProject) {
        val stamped = project.copy(updatedAt = System.currentTimeMillis())
        val current = _projects.value.toMutableList()
        val index = current.indexOfFirst { it.id == stamped.id }
        if (index >= 0) current[index] = stamped else current.add(0, stamped)
        _projects.value = current.sortedByDescending { it.updatedAt }
        persist()
    }

    suspend fun delete(projectId: String) {
        _projects.value = _projects.value.filterNot { it.id == projectId }
        persist()
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        val snapshot = _projects.value
        writeLock.withLock {
            runCatching {
                // Write to a temp file first so an interrupted write cannot
                // leave a half-serialised library behind.
                val tmp = File(appContext.filesDir, "$FILE_NAME.tmp")
                tmp.writeText(json.encodeToString(snapshot))
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private companion object {
        const val FILE_NAME = "projects.json"
    }
}
