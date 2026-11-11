package com.vibespace.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.workspace.manager.WorkspaceManager
import com.vibespace.core.workspace.model.Project
import com.vibespace.core.workspace.repository.RecentProjectsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            val res = workspaceManager.openProject(project)
            if (res is VibeResult.Success) {
                recentProjectsRepository?.addRecentProject(res.data)
                withContext(dispatchers.main) {
                    onProjectReady(res.data)
                }
            } else if (res is VibeResult.Error) {
                withContext(dispatchers.main) {
                    _uiState.update { it.copy(errorMessage = res.message) }
                }
            }
        }
    }

    fun openDemoProject(onProjectReady: (Project) -> Unit) {
        viewModelScope.launch(dispatchers.io) {
            val project = Project(
                id = "demo",
                name = "DemoProject",
                rootUri = "vibe://demo"
            )
            when (val result = workspaceManager.openProject(project)) {
                is VibeResult.Success -> {
                    recentProjectsRepository?.addRecentProject(result.data)
                    withContext(dispatchers.main) {
                        _uiState.update { it.copy(isLoading = false) }
                        onProjectReady(result.data)
                    }
                }
                is VibeResult.Error -> withContext(dispatchers.main) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                else -> Unit
            }
        }
    }

    fun openSafTreeUri(
        treeUri: Uri,
        folderName: String,
        onProjectReady: (Project) -> Unit,
        onProjectError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val projectId = IdGenerator.shortId("saf")
            val project = Project(
                id = projectId,
                name = folderName.trim().ifEmpty { "SAF Project" },
                rootUri = treeUri.toString(),
                isStorageAccessFramework = true
            )
            when (val res = workspaceManager.openProject(project)) {
                is VibeResult.Success -> {
                    recentProjectsRepository?.addRecentProject(res.data)
                    withContext(dispatchers.main) {
                        _uiState.update { it.copy(isLoading = false) }
                        onProjectReady(res.data)
                    }
                }
                is VibeResult.Error -> {
                    withContext(dispatchers.main) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                        onProjectError?.invoke(res.message)
                    }
                }
                else -> Unit
            }
        }
    }

    fun openProject(
        project: Project,
        onProjectReady: (Project) -> Unit,
        onProjectError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = workspaceManager.openProject(project)) {
                is VibeResult.Success -> {
                    recentProjectsRepository?.addRecentProject(res.data)
                    withContext(dispatchers.main) {
                        _uiState.update { it.copy(isLoading = false) }
                        onProjectReady(res.data)
                    }
                }
                is VibeResult.Error -> {
                    withContext(dispatchers.main) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                        onProjectError?.invoke(res.message)
                    }
                }
                else -> Unit
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
