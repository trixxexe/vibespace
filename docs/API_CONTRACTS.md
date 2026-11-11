# VibeSpace API Contracts

This document formalizes the public interfaces and core contracts across VibeSpace modules. Any modification to these contracts requires approval from Developer 1 (Project Lead).

---

## 1. Editor Engine Contract (`core:editor`)

```kotlin
interface EditorEngine {
    val documents: StateFlow<List<EditorDocument>>
    val activeDocument: StateFlow<EditorDocument?>

    suspend fun openDocument(
        uri: String,
        fileName: String,
        content: String,
        language: LanguageDefinition
    ): VibeResult<EditorDocument>

    suspend fun closeDocument(documentId: String): VibeResult<Unit>
    suspend fun setActiveDocument(documentId: String): VibeResult<Unit>
    suspend fun updateDocument(documentId: String, newContent: String): VibeResult<EditorDocument>
    suspend fun updateCursorPosition(documentId: String, cursorPosition: CursorPosition): VibeResult<Unit>
    suspend fun markSaved(documentId: String): VibeResult<Unit>

    fun getDocument(documentId: String): EditorDocument?
    suspend fun undo(documentId: String): VibeResult<EditorDocument>
    suspend fun redo(documentId: String): VibeResult<EditorDocument>

    fun find(documentId: String, query: String, matchCase: Boolean = false): List<IntRange>
    suspend fun replace(documentId: String, range: IntRange, replacement: String): VibeResult<EditorDocument>
    suspend fun replaceAll(documentId: String, query: String, replacement: String, matchCase: Boolean = false): VibeResult<EditorDocument>
}
```

### Models:
- `EditorDocument(documentId, uri, fileName, content, language, isDirty, lineCount, cursorPosition, selection, version)`
- `CursorPosition(line, column, offset)`
- `TextSpan(startOffset, endOffset, styleKey)`

---

## 2. Workspace Contract (`core:workspace`)

```kotlin
interface WorkspaceManager {
    val currentProject: StateFlow<Project?>

    suspend fun openProject(project: Project): VibeResult<Project>
    suspend fun closeProject(): VibeResult<Unit>

    suspend fun listFiles(parentDirectoryUri: String): VibeResult<List<VirtualFile>>
    suspend fun readFile(fileUri: String): VibeResult<String>
    suspend fun writeFile(fileUri: String, content: String): VibeResult<Unit>
    suspend fun createFile(parentDirectoryUri: String, fileName: String, initialContent: String = ""): VibeResult<VirtualFile>
    suspend fun createDirectory(parentDirectoryUri: String, directoryName: String): VibeResult<VirtualFile>
    suspend fun delete(targetUri: String): VibeResult<Unit>
    suspend fun rename(targetUri: String, newName: String): VibeResult<VirtualFile>
}
```

### Models:
- `Project(id, name, rootUri, rootPath, isStorageAccessFramework, lastOpenedTimestamp)`
- `VirtualFile(uri, name, nodeType, path, parentUri, sizeBytes, lastModifiedTimestamp, extension)`

---

## 3. Language Intelligence Contracts (`core:language`)

```kotlin
interface LanguageDetector {
    fun detectByFileName(fileName: String): LanguageDefinition
    fun detectByExtension(extension: String): LanguageDefinition
    fun detectByContent(firstLines: String, fileName: String? = null): LanguageDefinition
}

interface LanguageServerClient {
    val languageId: String
    val isRunning: Boolean

    suspend fun start(workspaceRootUri: String): VibeResult<Unit>
    suspend fun stop(): VibeResult<Unit>
    suspend fun didOpen(documentUri: String, text: String): VibeResult<Unit>
    suspend fun didChange(documentUri: String, newText: String, version: Int): VibeResult<Unit>
    suspend fun didClose(documentUri: String): VibeResult<Unit>
    fun observeDiagnostics(documentUri: String): Flow<List<Diagnostic>>
    suspend fun requestCompletion(documentUri: String, line: Int, column: Int): VibeResult<List<CompletionItem>>
    suspend fun requestDefinition(documentUri: String, line: Int, column: Int): VibeResult<List<DefinitionLocation>>
    suspend fun formatDocument(documentUri: String): VibeResult<String>
}
```

---

## 4. Execution & Runner Contracts (`core:runner`)

```kotlin
interface RunnerManager {
    val activeExecutions: StateFlow<List<ProcessExecution>>
    val activeRunnerMode: StateFlow<RunnerMode>

    fun registerRunner(runner: ExternalRunner)
    fun getAvailableRunners(): List<ExternalRunner>
    fun setRunnerMode(mode: RunnerMode)
    fun getRunnerForMode(mode: RunnerMode): ExternalRunner?

    suspend fun runCommand(
        command: String,
        workingDirectory: String,
        preferredRunnerId: String? = null
    ): VibeResult<ProcessExecution>

    suspend fun stopExecution(executionId: String): VibeResult<Unit>
    fun observeOutput(executionId: String): Flow<ProcessOutput>
}
```

### Runner Options:
- `RunnerMode`: embedded evaluation, optional Alpine PRoot runtime, and Termux bridge. The Alpine option is unavailable in the current app build because PRoot is not packaged. Termux requires the Termux app, its permission, and accessible project files.
- `ProcessExecution(executionId, command, workingDirectoryUri, state, exitCode, startTime, endTime, outputs)`
- `ProcessOutput(line, isError, timestamp)`
- `ExecutionState`: `IDLE`, `STARTING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`

---

## 5. Terminal Contract (`core:terminal`)

```kotlin
interface TerminalService {
    val sessions: StateFlow<List<TerminalSession>>
    val activeSession: StateFlow<TerminalSession?>

    suspend fun createSession(title: String = "Terminal", workingDirectory: String = "~"): VibeResult<TerminalSession>
    suspend fun closeSession(sessionId: String): VibeResult<Unit>
    suspend fun setActiveSession(sessionId: String): VibeResult<Unit>
    suspend fun sendInput(sessionId: String, input: String): VibeResult<Unit>
    suspend fun clearSession(sessionId: String): VibeResult<Unit>
    fun observeLines(sessionId: String): Flow<TerminalLine>
}
```

---

## 6. AI Architecture Contracts (`core:ai`)

```kotlin
interface AIProvider {
    val providerId: String
    val displayName: String
    val supportedModels: List<String>
    val isConfigured: Boolean

    suspend fun generateResponse(request: AIRequest): VibeResult<AIResponse>
    fun streamResponse(request: AIRequest): Flow<AIResponseChunk>
}
```

### Models:
- `AIMessage(id, role, content, timestamp, attachedCodeContext, proposedChanges)`
- `CodeContext(fileUri, fileName, language, selectedText, fullContent, cursorLine)`
- `CodeChange(fileUri, originalSnippet, proposedSnippet, explanation)`
