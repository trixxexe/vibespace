package com.vibespace.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.workspace.manager.WorkspaceManager
import com.vibespace.core.workspace.model.Project
import com.vibespace.core.workspace.repository.RecentProjectsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val workspaceManager: WorkspaceManager,
    private val recentProjectsRepository: RecentProjectsRepository? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRecentProjects()
    }

    fun loadRecentProjects() {
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            if (recentProjectsRepository != null) {
                recentProjectsRepository.getRecentProjects().collect { projects ->
                    _uiState.update { it.copy(isLoading = false, recentProjects = projects) }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun createNewProject(name: String, onProjectReady: (Project) -> Unit) {
        viewModelScope.launch(dispatchers.io) {
            val projectId = IdGenerator.shortId("proj")
            val project = Project(
                id = projectId,
                name = name.trim().ifEmpty { "NewProject" },
                rootUri = "vibe://projects/$projectId"
            )
            workspaceManager.openProject(project)
            recentProjectsRepository?.addRecentProject(project)
            onProjectReady(project)
        }
    }

    fun openProject(project: Project, onProjectReady: (Project) -> Unit) {
        viewModelScope.launch(dispatchers.io) {
            workspaceManager.openProject(project)
            onProjectReady(project)
        }
    }
}
