package com.vibespace.core.editor.extension

import com.vibespace.core.common.result.VibeResult
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream

interface ExtensionManager {
    val installedExtensions: StateFlow<List<ExtensionMetadata>>

    suspend fun getCuratedExtensions(): VibeResult<List<ExtensionMetadata>>
    suspend fun searchOpenVsx(query: String): VibeResult<List<ExtensionMetadata>>
    suspend fun installExtension(extension: ExtensionMetadata): VibeResult<Unit>
    suspend fun downloadAndInstall(extension: ExtensionMetadata, onProgress: (Float) -> Unit): VibeResult<Unit>
    suspend fun installFromVsix(inputStream: InputStream): VibeResult<ExtensionMetadata>
    suspend fun uninstallExtension(extensionId: String): VibeResult<Unit>
    suspend fun toggleExtension(extensionId: String, enabled: Boolean): VibeResult<Unit>
}
