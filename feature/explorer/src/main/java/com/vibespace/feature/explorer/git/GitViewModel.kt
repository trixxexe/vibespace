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
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val showBranchDialog: Boolean = false,
    val showCommitDialog: Boolean = false,
    val showRemoteDialog: Boolean = false,
    val remoteUrlInput: String = "",
    val remoteUsernameInput: String = "",
    val remoteTokenInput: String = "",
    val pendingDiscardPath: String? = null,
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
                _uiState.update { it.copy(status = status, errorMessage = status.errorMessage) }
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch(dispatchers.io) {
            when (val result = gitManager.refreshStatus()) {
                is VibeResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }

    fun onCommitMessageChange(message: String) {
        _uiState.update { it.copy(commitMessage = message, errorMessage = null) }
    }

    fun stageFile(path: String) {
        runAction { gitManager.stageFile(path) }
    }

    fun unstageFile(path: String) {
        runAction { gitManager.unstageFile(path) }
    }

    fun stageAll() {
        runAction { gitManager.stageAll() }
    }

    fun unstageAll() {
        runAction { gitManager.unstageAll() }
    }

    fun discardChanges(path: String) {
        _uiState.update { it.copy(pendingDiscardPath = path, errorMessage = null) }
    }

    fun cancelDiscardChanges() {
        _uiState.update { it.copy(pendingDiscardPath = null) }
    }

    fun confirmDiscardChanges() {
        val path = _uiState.value.pendingDiscardPath ?: return
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(pendingDiscardPath = null, errorMessage = null) }
            when (val result = gitManager.discardChanges(path)) {
                is VibeResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> Unit
            }
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
            when (val result = gitManager.getFileDiff(path)) {
                is VibeResult.Success -> _uiState.update {
                    it.copy(
                        activeDiffFile = path,
                        activeDiff = result.data,
                        errorMessage = null
                    )
                }
                is VibeResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> _uiState.update { it.copy(errorMessage = "Could not load the selected diff.") }
            }
        }
    }

    fun closeDiff() {
        _uiState.update { it.copy(activeDiffFile = null, activeDiff = null) }
    }

    fun createBranch(name: String) {
        runAction(closeBranchDialog = true) { gitManager.createBranch(name) }
    }

    fun checkoutBranch(name: String) {
        runAction(closeBranchDialog = true) { gitManager.checkoutBranch(name) }
    }

    fun toggleBranchDialog(show: Boolean) {
        _uiState.update { it.copy(showBranchDialog = show) }
    }

    fun toggleCommitDialog(show: Boolean) {
        _uiState.update { it.copy(showCommitDialog = show) }
    }

    fun toggleRemoteDialog(show: Boolean) {
        _uiState.update {
            it.copy(
                showRemoteDialog = show,
                remoteUrlInput = if (show) it.status.remoteUrl.orEmpty() else it.remoteUrlInput,
                errorMessage = null
            )
        }
    }

    fun updateRemoteUrl(value: String) { _uiState.update { it.copy(remoteUrlInput = value) } }
    fun updateRemoteUsername(value: String) { _uiState.update { it.copy(remoteUsernameInput = value) } }
    fun updateRemoteToken(value: String) { _uiState.update { it.copy(remoteTokenInput = value) } }

    fun configureRemote() {
        val state = _uiState.value
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null, syncMessage = null) }
            when (val result = gitManager.configureRemote(
                state.remoteUrlInput,
                state.remoteUsernameInput.ifBlank { null },
                state.remoteTokenInput.ifBlank { null }
            )) {
                is VibeResult.Success -> _uiState.update {
                    it.copy(isSyncing = false, showRemoteDialog = false, remoteTokenInput = "", syncMessage = "Git remote configured.")
                }
                is VibeResult.Error -> _uiState.update { it.copy(isSyncing = false, errorMessage = result.message) }
                else -> _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun push() {
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isSyncing = true, syncMessage = null, errorMessage = null) }
            when (val result = gitManager.push()) {
                is VibeResult.Success -> {
                    _uiState.update { it.copy(isSyncing = false, syncMessage = result.data) }
                }
                is VibeResult.Error -> {
                    _uiState.update { it.copy(isSyncing = false, errorMessage = result.message) }
                }
                else -> _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun pull() {
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isSyncing = true, syncMessage = null, errorMessage = null) }
            when (val result = gitManager.pull()) {
                is VibeResult.Success -> {
                    _uiState.update { it.copy(isSyncing = false, syncMessage = result.data) }
                }
                is VibeResult.Error -> {
                    _uiState.update { it.copy(isSyncing = false, errorMessage = result.message) }
                }
                else -> _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private fun runAction(
        closeBranchDialog: Boolean = false,
        action: suspend () -> VibeResult<Unit>
    ) {
        viewModelScope.launch(dispatchers.io) {
            when (val result = action()) {
                is VibeResult.Success -> _uiState.update {
                    it.copy(errorMessage = null, showBranchDialog = if (closeBranchDialog) false else it.showBranchDialog)
                }
                is VibeResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> _uiState.update { it.copy(errorMessage = "The source control action did not complete.") }
            }
        }
    }
}
