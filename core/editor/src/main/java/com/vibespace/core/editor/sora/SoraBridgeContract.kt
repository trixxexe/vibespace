package com.vibespace.core.editor.sora

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.editor.model.EditorDocument

/**
 * Sora Editor integration contract for Developer 2 (Editor Engineer).
 *
 * Sora Editor is an efficient native Android code editor view by Rosemoe.
 * This contract establishes the adapter interface so Sora Editor can be plugged in
 * seamlessly without altering the rest of VibeSpace or its Compose UI.
 */
interface SoraEditorBridge {
    fun isAvailable(): Boolean
    suspend fun attachDocument(document: EditorDocument): VibeResult<Unit>
    suspend fun applyTheme(isDarkTheme: Boolean): VibeResult<Unit>
    suspend fun setFontSize(fontSizeSp: Float): VibeResult<Unit>
}
