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
    val showCloneDialog: Boolean = false,
    val showIdentityDialog: Boolean = false,
    val authorNameInput: String = "",
    val authorEmailInput: String = "",
    val remoteUrlInput: String = "",
    val remoteUsernameInput: String = "",
    val remoteTokenInput: String = "",
    val cloneUrlInput: String = "",
    val cloneUsernameInput: String = "",
    val cloneTokenInput: String = "",
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

    fun confirmDiscardChanges(onSuccess: (() -> Unit)? = null) {
        val path = _uiState.value.pendingDiscardPath ?: return
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(pendingDiscardPath = null, errorMessage = null) }
            when (val result = gitManager.discardChanges(path)) {
                is VibeResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> onSuccess?.invoke()
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
                    _uiState.update { it.copy(commitMessage = "", isCommitting = false, syncMessage = "Committed ${result.data.shortId}") }
                }
                is VibeResult.Error -> {
                    val needsIdentity = result.message.contains("Configure your Git author name and email", ignoreCase = true)
                    _uiState.update {
                        it.copy(
                            errorMessage = result.message,
                            isCommitting = false,
                            showIdentityDialog = if (needsIdentity) true else it.showIdentityDialog,
                            authorNameInput = if (needsIdentity && it.authorNameInput.isBlank()) "VibeSpace Developer" else it.authorNameInput,
                            authorEmailInput = if (needsIdentity && it.authorEmailInput.isBlank()) "developer@vibespace.dev" else it.authorEmailInput
                        )
                    }
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

    fun checkoutBranch(name: String, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch(dispatchers.io) {
            when (val result = gitManager.checkoutBranch(name)) {
                is VibeResult.Success -> {
                    _uiState.update { it.copy(errorMessage = null, showBranchDialog = false) }
                    onSuccess?.invoke()
                }
                is VibeResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> Unit
            }
        }
    }

    fun toggleBranchDialog(show: Boolean) {
        _uiState.update { it.copy(showBranchDialog = show) }
    }

    fun toggleCommitDialog(show: Boolean) {
        _uiState.update { it.copy(showCommitDialog = show) }
    }

    fun toggleIdentityDialog(show: Boolean) {
        _uiState.update {
            it.copy(
                showIdentityDialog = show,
                authorNameInput = if (show) it.status.authorName?.ifBlank { "VibeSpace Developer" } ?: "VibeSpace Developer" else it.authorNameInput,
                authorEmailInput = if (show) it.status.authorEmail?.ifBlank { "developer@vibespace.dev" } ?: "developer@vibespace.dev" else it.authorEmailInput,
                errorMessage = null
            )
        }
    }

    fun updateAuthorName(value: String) { _uiState.update { it.copy(authorNameInput = value) } }
    fun updateAuthorEmail(value: String) { _uiState.update { it.copy(authorEmailInput = value) } }

    fun configureIdentity() {
        val state = _uiState.value
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null) }
            when (val result = gitManager.configureIdentity(state.authorNameInput, state.authorEmailInput)) {
                is VibeResult.Success -> _uiState.update {
                    it.copy(isSyncing = false, showIdentityDialog = false, syncMessage = "Git author identity saved.")
                }
                is VibeResult.Error -> _uiState.update { it.copy(isSyncing = false, errorMessage = result.message) }
                else -> _uiState.update { it.copy(isSyncing = false) }
            }
        }
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

    fun toggleCloneDialog(show: Boolean) {
        _uiState.update {
            it.copy(
                showCloneDialog = show,
                errorMessage = null
            )
        }
    }

    fun updateCloneUrl(value: String) { _uiState.update { it.copy(cloneUrlInput = value) } }
    fun updateCloneUsername(value: String) { _uiState.update { it.copy(cloneUsernameInput = value) } }
    fun updateCloneToken(value: String) { _uiState.update { it.copy(cloneTokenInput = value) } }

    fun cloneRepository(onCloneComplete: ((String) -> Unit)? = null) {
        val state = _uiState.value
        if (state.cloneUrlInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter or paste a Git repository URL.") }
            return
        }
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null, syncMessage = null) }
            when (val result = gitManager.cloneRepository(
                url = state.cloneUrlInput,
                username = state.cloneUsernameInput.ifBlank { null },
                passwordOrToken = state.cloneTokenInput.ifBlank { null }
            )) {
                is VibeResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            showCloneDialog = false,
                            cloneTokenInput = "",
                            syncMessage = result.data
                        )
                    }
                    onCloneComplete?.invoke(result.data)
                }
                is VibeResult.Error -> {
                    _uiState.update { it.copy(isSyncing = false, errorMessage = result.message) }
                }
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

    fun pull(onPullComplete: (() -> Unit)? = null) {
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isSyncing = true, syncMessage = null, errorMessage = null) }
            when (val result = gitManager.pull()) {
                is VibeResult.Success -> {
                    _uiState.update { it.copy(isSyncing = false, syncMessage = result.data) }
                    onPullComplete?.invoke()
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
