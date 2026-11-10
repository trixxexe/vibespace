package com.vibespace.core.workspace.model

enum class FileNodeType {
    FILE,
    DIRECTORY
}

/**
 * Representation of a file or directory inside the VibeSpace project tree.
 * Abstracted to support local disk paths, app private storage, and Android Storage Access Framework (SAF).
 */
data class VirtualFile(
    val uri: String,
    val name: String,
    val nodeType: FileNodeType,
    val path: String? = null,
    val parentUri: String? = null,
    val sizeBytes: Long = 0L,
    val lastModifiedTimestamp: Long = System.currentTimeMillis(),
    val extension: String = name.substringAfterLast('.', "")
) {
    val isDirectory: Boolean
        get() = nodeType == FileNodeType.DIRECTORY

    val isFile: Boolean
        get() = nodeType == FileNodeType.FILE
}
