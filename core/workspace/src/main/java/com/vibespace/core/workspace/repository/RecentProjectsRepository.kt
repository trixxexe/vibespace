package com.vibespace.core.workspace.repository

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.workspace.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface RecentProjectsRepository {
    fun getRecentProjects(): Flow<List<Project>>
    suspend fun addRecentProject(project: Project): VibeResult<Unit>
    suspend fun removeRecentProject(projectId: String): VibeResult<Unit>
    suspend fun clearRecentProjects(): VibeResult<Unit>
}

class InMemoryRecentProjectsRepository(
    initialProjects: List<Project> = emptyList()
) : RecentProjectsRepository {

    private val _projects = MutableStateFlow(initialProjects)

    override fun getRecentProjects(): Flow<List<Project>> = _projects.asStateFlow()

    override suspend fun addRecentProject(project: Project): VibeResult<Unit> {
        val updated = listOf(project) + _projects.value.filter { it.id != project.id }
        _projects.value = updated.take(15)
        return VibeResult.Success(Unit)
    }

    override suspend fun removeRecentProject(projectId: String): VibeResult<Unit> {
        _projects.value = _projects.value.filter { it.id != projectId }
        return VibeResult.Success(Unit)
    }

    override suspend fun clearRecentProjects(): VibeResult<Unit> {
        _projects.value = emptyList()
        return VibeResult.Success(Unit)
    }
}
