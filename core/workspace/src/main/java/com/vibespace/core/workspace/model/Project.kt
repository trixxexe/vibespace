package com.vibespace.core.workspace.model

/**
 * Representation of an active or recent VibeSpace project workspace.
 */
data class Project(
    val id: String,
    val name: String,
    val rootUri: String,
    val rootPath: String? = null,
    val isStorageAccessFramework: Boolean = rootUri.startsWith("content://"),
    val lastOpenedTimestamp: Long = System.currentTimeMillis()
)
