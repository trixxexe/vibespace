package com.vibespace.core.workspace.manager

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.workspace.model.Project
import com.vibespace.core.workspace.model.VirtualFile
import kotlinx.coroutines.flow.StateFlow

/**
 * Workspace management contract for opening, scanning, reading, writing, and altering project files.
 */
interface WorkspaceManager {
    val currentProject: StateFlow<Project?>

    suspend fun openProject(project: Project): VibeResult<Project>
    suspend fun closeProject(): VibeResult<Unit>

    suspend fun listFiles(parentDirectoryUri: String): VibeResult<List<VirtualFile>>

    /**
     * Recursively traverses all files and directories in the workspace (or a sub-path)
     * skipping ignored directories like .git, node_modules, build, etc.
     * Ideal for global search, indexing, and Quick Open command palettes.
     */
    suspend fun getAllProjectFiles(rootUri: String? = null): VibeResult<List<VirtualFile>>

    suspend fun readFile(fileUri: String): VibeResult<String>
    suspend fun writeFile(fileUri: String, content: String): VibeResult<Unit>
    suspend fun createFile(parentDirectoryUri: String, fileName: String, initialContent: String = ""): VibeResult<VirtualFile>
    suspend fun createDirectory(parentDirectoryUri: String, directoryName: String): VibeResult<VirtualFile>
    suspend fun delete(targetUri: String): VibeResult<Unit>
    suspend fun rename(targetUri: String, newName: String): VibeResult<VirtualFile>
}
