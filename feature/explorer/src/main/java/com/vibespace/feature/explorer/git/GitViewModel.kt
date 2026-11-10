package com.vibespace.feature.explorer.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.workspace.git.FileDiff
import com.vibespace.core.workspace.git.GitManager
import com.vibespace.core.workspace.git.GitWorkspaceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GitUiState(
    val status: GitWorkspaceStatus = GitWorkspaceStatus(),
    val commitMessage: String = "",
    val activeDiffFile: String? = null,
    val activeDiff: FileDiff? = null,
    val isCommitting: Boolean = false,
    val errorMessage: String? = null
)

class GitViewModel(
    private val gitManager: GitManager,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitUiState())
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    init {
        observeGitStatus()
    }

    private fun observeGitStatus() {
        viewModelScope.launch(dispatchers.default) {
            gitManager.status.collect { status ->
                _uiState.update { it.copy(status = status) }
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch(dispatchers.io) {
            gitManager.refreshStatus()
        }
    }

    fun onCommitMessageChange(message: String) {
        _uiState.update { it.copy(commitMessage = message, errorMessage = null) }
    }

    fun stageFile(path: String) {
        viewModelScope.launch(dispatchers.io) {
            gitManager.stageFile(path)
        }
    }

    fun unstageFile(path: String) {
        viewModelScope.launch(dispatchers.io) {
            gitManager.unstageFile(path)
        }
    }

    fun stageAll() {
        viewModelScope.launch(dispatchers.io) {
            gitManager.stageAll()
        }
    }

    fun unstageAll() {
        viewModelScope.launch(dispatchers.io) {
            gitManager.unstageAll()
        }
    }

    fun discardChanges(path: String) {
        viewModelScope.launch(dispatchers.io) {
            gitManager.discardChanges(path)
        }
    }

    fun commit() {
        val message = _uiState.value.commitMessage.trim()
        if (message.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter a commit message.") }
            return
        }

        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isCommitting = true, errorMessage = null) }
            when (val result = gitManager.commit(message)) {
                is VibeResult.Success -> {
                    _uiState.update { it.copy(commitMessage = "", isCommitting = false) }
                }
                is VibeResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message, isCommitting = false) }
                }
                else -> {
                    _uiState.update { it.copy(isCommitting = false) }
                }
            }
        }
    }

    fun openDiff(path: String) {
        viewModelScope.launch(dispatchers.io) {
            val result = gitManager.getFileDiff(path)
            if (result is VibeResult.Success) {
                _uiState.update {
                    it.copy(
                        activeDiffFile = path,
                        activeDiff = result.data
                    )
                }
            }
        }
    }

    fun closeDiff() {
        _uiState.update { it.copy(activeDiffFile = null, activeDiff = null) }
    }

    fun createBranch(name: String) {
        viewModelScope.launch(dispatchers.io) {
            gitManager.createBranch(name)
        }
    }

    fun checkoutBranch(name: String) {
        viewModelScope.launch(dispatchers.io) {
            gitManager.checkoutBranch(name)
        }
    }
}
