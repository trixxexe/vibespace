package com.vibespace.core.editor.sora

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.editor.model.EditorDocument

/**
 * Concrete implementation of SoraEditorBridge to bridge EditorEngine and Sora Editor state.
 */
class DefaultSoraEditorBridge : SoraEditorBridge {

    private var activeDocument: EditorDocument? = null
    private var isDarkTheme: Boolean = true
    private var fontSizeSp: Float = 13f

    override fun isAvailable(): Boolean = true

    override suspend fun attachDocument(document: EditorDocument): VibeResult<Unit> {
        activeDocument = document
        return VibeResult.Success(Unit)
    }

    override suspend fun applyTheme(isDarkTheme: Boolean): VibeResult<Unit> {
        this.isDarkTheme = isDarkTheme
        return VibeResult.Success(Unit)
    }

    override suspend fun setFontSize(fontSizeSp: Float): VibeResult<Unit> {
        this.fontSizeSp = fontSizeSp
        return VibeResult.Success(Unit)
    }
}
