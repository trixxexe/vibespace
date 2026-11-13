package com.vibespace.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.editor.engine.EditorEngine
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
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val editorEngine: EditorEngine? = null,
    private val gitManager: com.vibespace.core.workspace.git.GitManager? = null
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
            if (!saveOpenDocumentsBeforeProjectChange()) return@launch
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

    fun cloneGitRepository(
        repoUrl: String,
        username: String? = null,
        token: String? = null,
        onCloneReady: (Project, String) -> Unit
    ) {
        val manager = gitManager
        if (manager == null) {
            _uiState.update { it.copy(errorMessage = "Git manager is not available.") }
            return
        }
        val cleanUrl = repoUrl.trim()
        if (cleanUrl.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter or paste a repository URL.") }
            return
        }
        val repoName = cleanUrl.trimEnd('/')
            .substringAfterLast('/')
            .removeSuffix(".git")
            .ifBlank { "ClonedRepo" }

        viewModelScope.launch(dispatchers.io) {
            if (!saveOpenDocumentsBeforeProjectChange()) return@launch
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val projectId = IdGenerator.shortId("git")
            val project = Project(
                id = projectId,
                name = repoName,
                rootUri = "vibe://projects/$projectId"
            )
            when (val openRes = workspaceManager.openProject(project)) {
                is VibeResult.Success -> {
                    when (val cloneRes = manager.cloneRepository(cleanUrl, username?.ifBlank { null }, token?.ifBlank { null })) {
                        is VibeResult.Success -> {
                            recentProjectsRepository?.addRecentProject(openRes.data)
                            withContext(dispatchers.main) {
                                _uiState.update { it.copy(isLoading = false) }
                                onCloneReady(openRes.data, cloneRes.data)
                            }
                        }
                        is VibeResult.Error -> {
                            withContext(dispatchers.main) {
                                _uiState.update { it.copy(isLoading = false, errorMessage = cloneRes.message) }
                            }
                        }
                        else -> {
                            withContext(dispatchers.main) {
                                _uiState.update { it.copy(isLoading = false) }
                            }
                        }
                    }
                }
                is VibeResult.Error -> {
                    withContext(dispatchers.main) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = openRes.message) }
                    }
                }
                else -> Unit
            }
        }
    }

    fun openDemoProject(onProjectReady: (Project) -> Unit) {
        viewModelScope.launch(dispatchers.io) {
            if (!saveOpenDocumentsBeforeProjectChange()) return@launch
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
            if (!saveOpenDocumentsBeforeProjectChange()) return@launch
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
            if (!saveOpenDocumentsBeforeProjectChange()) return@launch
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

    private suspend fun saveOpenDocumentsBeforeProjectChange(): Boolean {
        val engine = editorEngine ?: return true
        if (workspaceManager.currentProject.value == null) return true
        // All open documents belong to the active project. URI prefixes are not reliable for
        // SAF trees, whose document URIs differ from the selected tree URI.
        val openDocuments = engine.documents.value
        for (document in openDocuments) {
            if (!document.isDirty) continue
            when (val result = workspaceManager.writeFile(document.uri, document.content)) {
                is VibeResult.Success -> engine.markSaved(document.documentId, document.content)
                is VibeResult.Error -> {
                    _uiState.update { it.copy(errorMessage = "Project was not switched. Could not save ${document.fileName}: ${result.message}") }
                    return false
                }
                else -> {
                    _uiState.update { it.copy(errorMessage = "Project was not switched because ${document.fileName} could not be saved.") }
                    return false
                }
            }
        }
        return true
    }
}
