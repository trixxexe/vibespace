package com.vibespace.core.workspace.git

import com.vibespace.core.common.result.VibeResult
import kotlinx.coroutines.flow.StateFlow

interface GitManager {
    val status: StateFlow<GitWorkspaceStatus>

    suspend fun refreshStatus(): VibeResult<GitWorkspaceStatus>
    suspend fun stageFile(path: String): VibeResult<Unit>
    suspend fun unstageFile(path: String): VibeResult<Unit>
    suspend fun stageAll(): VibeResult<Unit>
    suspend fun unstageAll(): VibeResult<Unit>
    suspend fun discardChanges(path: String): VibeResult<Unit>
    suspend fun commit(message: String): VibeResult<GitCommit>
    suspend fun configureIdentity(name: String, email: String): VibeResult<Unit> =
        VibeResult.Error("Git author configuration is not supported by this Git manager.")
    suspend fun checkoutBranch(branchName: String): VibeResult<Unit>
    suspend fun createBranch(branchName: String): VibeResult<Unit>
    suspend fun getFileDiff(path: String): VibeResult<FileDiff>
    suspend fun configureRemote(url: String, username: String? = null, passwordOrToken: String? = null): VibeResult<Unit> =
        VibeResult.Error("Remote configuration is not supported by this Git manager.")
    suspend fun push(): VibeResult<String>
    suspend fun pull(): VibeResult<String>
}
