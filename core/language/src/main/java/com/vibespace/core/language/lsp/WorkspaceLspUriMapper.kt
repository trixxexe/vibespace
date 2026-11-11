package com.vibespace.core.language.lsp

import java.io.File
import java.net.URI

/** Maps VibeSpace and SAF document identifiers to the mounted file URIs seen by a server. */
class WorkspaceLspUriMapper(
    private val workspaceRootUri: String,
    workspaceDirectory: File,
    private val documentUriToRelativePath: (String) -> String? = { null },
    private val relativePathToDocumentUri: (String) -> String? = { null }
) {
    private val workspaceRoot = workspaceDirectory.canonicalFile
    private val rootPrefix = workspaceRootUri.trimEnd('/')

    fun toServerUri(documentUri: String): String {
        val relativePath = runCatching {
            val parsed = URI(documentUri)
            if (parsed.scheme.equals("file", ignoreCase = true)) {
                val path = File(parsed).canonicalFile.toPath()
                val root = workspaceRoot.toPath()
                if (path.startsWith(root)) root.relativize(path).toString().replace('\\', '/') else null
            } else {
                when {
                    documentUri == rootPrefix -> ""
                    else -> documentUriToRelativePath(documentUri)
                        ?: documentUri.takeIf { it.startsWith("$rootPrefix/") }?.removePrefix("$rootPrefix/")
                }
            }
        }.getOrNull() ?: return documentUri
        if (!isSafeRelativePath(relativePath)) return documentUri
        val containerPath = if (relativePath.isEmpty()) "/workspace" else "/workspace/$relativePath"
        return URI("file", "", containerPath, null).toASCIIString()
    }

    fun toClientUri(serverUri: String): String {
        val path = runCatching { URI(serverUri).path }.getOrNull() ?: return serverUri
        if (path != "/workspace" && !path.startsWith("/workspace/")) return serverUri
        val relativePath = path.removePrefix("/workspace").trimStart('/')
        if (relativePath.isEmpty()) return workspaceRoot.toURI().toString()
        if (!isSafeRelativePath(relativePath)) return serverUri
        relativePathToDocumentUri(relativePath)?.let { return it }
        val candidate = File(workspaceRoot, relativePath).canonicalFile
        return if (candidate != workspaceRoot && candidate.toPath().startsWith(workspaceRoot.toPath())) {
            candidate.toURI().toString()
        } else serverUri
    }

    private fun isSafeRelativePath(path: String) = path.isEmpty() ||
        (!path.startsWith('/') && path.split('/').none { it.isBlank() || it == "." || it == ".." })
}
