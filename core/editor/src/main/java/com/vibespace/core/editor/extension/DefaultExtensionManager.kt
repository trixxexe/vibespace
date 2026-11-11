package com.vibespace.core.editor.extension

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.editor.theme.CustomEditorThemeRegistry
import com.vibespace.core.editor.theme.EditorThemeDefinition
import com.vibespace.core.editor.vsix.VsixPackageParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Mobile extension manager supporting Open-VSX marketplace search, curated extensions,
 * and direct installation from .vsix files.
 */
class DefaultExtensionManager(
    private val stateFile: java.io.File? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ExtensionManager {

    constructor(dispatchers: DispatcherProvider) : this(null, dispatchers)

    private val curatedExtensions = listOf(
        ExtensionMetadata(
            id = "zhuangtongfa.material-theme",
            name = "one-dark-pro",
            displayName = "One Dark Pro",
            publisher = "binaryify",
            version = "3.18.2",
            description = "Atom's iconic One Dark theme for VibeSpace editor.",
            category = ExtensionCategory.THEME,
            isInstalled = true,
            isEnabled = true,
            isBuiltIn = true,
            downloadCount = 8900000
        ),
        ExtensionMetadata(
            id = "dracula-theme.theme-dracula",
            name = "theme-dracula",
            displayName = "Dracula Official",
            publisher = "dracula-theme",
            version = "2.24.3",
            description = "Famous dark theme for VibeSpace editor and terminal.",
            category = ExtensionCategory.THEME,
            isInstalled = true,
            isEnabled = true,
            isBuiltIn = true,
            downloadCount = 6400000
        ),
        ExtensionMetadata(
            id = "pkief.material-icon-theme",
            name = "material-icon-theme",
            displayName = "Material Icon Theme",
            publisher = "Philipp Kief",
            version = "5.14.0",
            description = "Material Design Icons for VibeSpace file explorer tree.",
            category = ExtensionCategory.ICON_PACK,
            isInstalled = false,
            isEnabled = false,
            downloadCount = 21000000
        ),
        ExtensionMetadata(
            id = "github.github-vscode-theme",
            name = "github-vscode-theme",
            displayName = "GitHub Theme",
            publisher = "GitHub",
            version = "6.3.4",
            description = "Official GitHub Dark and Light themes with high contrast.",
            category = ExtensionCategory.THEME,
            isInstalled = false,
            isEnabled = false,
            downloadCount = 14500000
        ),
        ExtensionMetadata(
            id = "arcticicestudio.nord-visual-studio-code",
            name = "nord-visual-studio-code",
            displayName = "Nord Theme",
            publisher = "arcticicestudio",
            version = "0.19.0",
            description = "An arctic, north-bluish clean and elegant color palette.",
            category = ExtensionCategory.THEME,
            isInstalled = false,
            isEnabled = false,
            downloadCount = 2800000
        ),
        ExtensionMetadata(
            id = "ms-python.python",
            name = "python",
            displayName = "Python Language Support",
            publisher = "ms-python",
            version = "2024.18.0",
            description = "IntelliSense, syntax highlighting, and execution for Python files.",
            category = ExtensionCategory.LANGUAGE,
            downloadCount = 120000000
        ),
        ExtensionMetadata(
            id = "esbenp.prettier-vscode",
            name = "prettier-vscode",
            displayName = "Prettier Formatter",
            publisher = "esbenp",
            version = "10.4.1",
            description = "Code formatter using Prettier for JS, TS, HTML, CSS, JSON, and Markdown.",
            category = ExtensionCategory.FORMATTER,
            isInstalled = false,
            isEnabled = false,
            downloadCount = 45000000
        )
    )

    private val _installedExtensions = MutableStateFlow(
        curatedExtensions.filter { it.isInstalled }
    )
    override val installedExtensions: StateFlow<List<ExtensionMetadata>> = _installedExtensions.asStateFlow()
    private val stateLock = Any()
    private val themeStateFile = stateFile?.let { java.io.File(it.parentFile, "installed_editor_themes.json") }
    private val extensionThemes = linkedMapOf<String, EditorThemeDefinition>()

    init {
        loadPersistedState()
        loadPersistedThemes()
    }

    private fun loadPersistedState() {
        if (stateFile != null && stateFile.exists()) {
            try {
                val jsonStr = stateFile.readText()
                val array = org.json.JSONArray(jsonStr)
                val loaded = mutableListOf<ExtensionMetadata>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loaded.add(
                        ExtensionMetadata(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            displayName = obj.getString("displayName"),
                            publisher = obj.getString("publisher"),
                            version = obj.getString("version"),
                            description = obj.optString("description", ""),
                            category = ExtensionCategory.valueOf(obj.optString("category", ExtensionCategory.THEME.name)),
                            downloadUrl = obj.optString("downloadUrl").ifEmpty { null },
                            downloadCount = obj.optLong("downloadCount", 0),
                            isInstalled = true,
                            isEnabled = obj.optBoolean("isEnabled", true),
                            isBuiltIn = obj.optBoolean("isBuiltIn", false)
                        )
                    )
                }
                if (loaded.isNotEmpty()) {
                    _installedExtensions.value = loaded
                }
            } catch (_: Throwable) {}
        }
    }

    private fun savePersistedState(extensions: List<ExtensionMetadata>): VibeResult<Unit> {
        val file = stateFile ?: return VibeResult.Success(Unit)
        try {
            val array = org.json.JSONArray()
            for (ext in extensions) {
                val obj = JSONObject().apply {
                    put("id", ext.id)
                    put("name", ext.name)
                    put("displayName", ext.displayName)
                    put("publisher", ext.publisher)
                    put("version", ext.version)
                    put("description", ext.description)
                    put("category", ext.category.name)
                    put("downloadUrl", ext.downloadUrl ?: "")
                    put("downloadCount", ext.downloadCount)
                    put("isEnabled", ext.isEnabled)
                    put("isBuiltIn", ext.isBuiltIn)
                }
                array.put(obj)
            }
            writeAtomically(file, array.toString())
            return VibeResult.Success(Unit)
        } catch (e: Exception) {
            return VibeResult.Error("Unable to save installed extensions: ${e.message}", e)
        }
    }

    private fun loadPersistedThemes() {
        val file = themeStateFile ?: return
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("themes") ?: return
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val theme = EditorThemeDefinition(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    author = item.optString("author", "Community"),
                    backgroundColor = item.optString("backgroundColor", "#0F1115"),
                    gutterColor = item.optString("gutterColor", "#12141A"),
                    currentLineColor = item.optString("currentLineColor", "#181B22"),
                    selectionColor = item.optString("selectionColor", "#264F78"),
                    cursorColor = item.optString("cursorColor", "#00E5FF"),
                    textColor = item.optString("textColor", "#F3F4F6"),
                    lineNumberColor = item.optString("lineNumberColor", "#5A5A5A"),
                    keywordColor = item.optString("keywordColor", "#FF7B72"),
                    literalColor = item.optString("literalColor", "#A5D6FF"),
                    commentColor = item.optString("commentColor", "#8B949E"),
                    functionColor = item.optString("functionColor", "#D2A8FF")
                )
                val extensionId = item.getString("extensionId")
                extensionThemes[extensionId] = theme
                if (_installedExtensions.value.any { it.id == extensionId && it.isEnabled }) {
                    CustomEditorThemeRegistry.register(theme, extensionId)
                }
            }
        } catch (_: Exception) {
            // Keep built-in themes available if an imported theme file is corrupt.
        }
    }

    private fun persistTheme(extensionId: String, theme: EditorThemeDefinition): VibeResult<Unit> {
        synchronized(stateLock) {
            val previous = extensionThemes.put(extensionId, theme)
            val file = themeStateFile
            if (file == null) {
                CustomEditorThemeRegistry.register(theme, extensionId)
                return VibeResult.Success(Unit)
            }
            try {
                val array = org.json.JSONArray()
                extensionThemes.forEach { (id, definition) ->
                    array.put(JSONObject().apply {
                        put("extensionId", id)
                        put("id", definition.id)
                        put("name", definition.name)
                        put("author", definition.author)
                        put("backgroundColor", definition.backgroundColor)
                        put("gutterColor", definition.gutterColor)
                        put("currentLineColor", definition.currentLineColor)
                        put("selectionColor", definition.selectionColor)
                        put("cursorColor", definition.cursorColor)
                        put("textColor", definition.textColor)
                        put("lineNumberColor", definition.lineNumberColor)
                        put("keywordColor", definition.keywordColor)
                        put("literalColor", definition.literalColor)
                        put("commentColor", definition.commentColor)
                        put("functionColor", definition.functionColor)
                    })
                }
                writeAtomically(file, JSONObject().put("themes", array).toString())
                CustomEditorThemeRegistry.register(theme, extensionId)
                return VibeResult.Success(Unit)
            } catch (e: Exception) {
                if (previous == null) extensionThemes.remove(extensionId) else extensionThemes[extensionId] = previous
                return VibeResult.Error("Unable to save imported theme: ${e.message}", e)
            }
        }
    }

    private fun removePersistedTheme(extensionId: String): VibeResult<Unit> {
        synchronized(stateLock) {
            val removed = extensionThemes.remove(extensionId) ?: return VibeResult.Success(Unit)
            try {
                val file = themeStateFile
                if (file != null) {
                    val array = org.json.JSONArray()
                    extensionThemes.forEach { (id, definition) ->
                        array.put(JSONObject().apply {
                            put("extensionId", id)
                            put("id", definition.id)
                            put("name", definition.name)
                            put("author", definition.author)
                            put("backgroundColor", definition.backgroundColor)
                            put("gutterColor", definition.gutterColor)
                            put("currentLineColor", definition.currentLineColor)
                            put("selectionColor", definition.selectionColor)
                            put("cursorColor", definition.cursorColor)
                            put("textColor", definition.textColor)
                            put("lineNumberColor", definition.lineNumberColor)
                            put("keywordColor", definition.keywordColor)
                            put("literalColor", definition.literalColor)
                            put("commentColor", definition.commentColor)
                            put("functionColor", definition.functionColor)
                        })
                    }
                    writeAtomically(file, JSONObject().put("themes", array).toString())
                }
                CustomEditorThemeRegistry.removeExtension(extensionId)
                return VibeResult.Success(Unit)
            } catch (e: Exception) {
                extensionThemes[extensionId] = removed
                return VibeResult.Error("Unable to remove imported theme: ${e.message}", e)
            }
        }
    }

    private fun writeAtomically(file: java.io.File, content: String) {
        file.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw java.io.IOException("Unable to create ${parent.absolutePath}")
            }
        }
        val temporary = java.io.File(file.parentFile, "${file.name}.tmp")
        try {
            temporary.writeText(content)
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun persistAndPublish(extensions: List<ExtensionMetadata>): VibeResult<Unit> = synchronized(stateLock) {
        when (val result = savePersistedState(extensions)) {
            is VibeResult.Success -> {
                _installedExtensions.value = extensions
                result
            }
            is VibeResult.Error -> result
            else -> VibeResult.Error("Unable to save installed extensions.")
        }
    }

    override suspend fun getCuratedExtensions(): VibeResult<List<ExtensionMetadata>> = withContext(dispatchers.default) {
        val installedMap = _installedExtensions.value.associateBy { it.id }
        val updated = curatedExtensions.map { ext ->
            val installed = installedMap[ext.id]
            if (installed != null) {
                ext.copy(isInstalled = true, isEnabled = installed.isEnabled)
            } else {
                ext.copy(isInstalled = false, isEnabled = false)
            }
        }
        VibeResult.Success(updated)
    }

    override suspend fun searchOpenVsx(query: String): VibeResult<List<ExtensionMetadata>> = withContext(dispatchers.io) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return@withContext getCuratedExtensions()
        }

        try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val url = URL("https://open-vsx.org/api/-/search?query=$encoded&size=15")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                setRequestProperty("User-Agent", "VibeSpace-Android/1.0")
            }
            val jsonStr = try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    return@withContext VibeResult.Error("Open VSX returned HTTP $status.")
                }
                connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var totalBytes = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        totalBytes += count
                        if (totalBytes > MAX_SEARCH_RESPONSE_BYTES) {
                            return@withContext VibeResult.Error("Open VSX response exceeded the allowed size.")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
            } finally {
                connection.disconnect()
            }
            val root = JSONObject(jsonStr)
            val extensionsArray = root.optJSONArray("extensions") ?: org.json.JSONArray()

            val installedMap = _installedExtensions.value.associateBy { it.id }
            val results = mutableListOf<ExtensionMetadata>()

            for (i in 0 until extensionsArray.length()) {
                val item = extensionsArray.getJSONObject(i)
                val id = "${item.optString("namespace")}.${item.optString("name")}"
                val isInstalled = installedMap.containsKey(id)
                val isEnabled = installedMap[id]?.isEnabled ?: false

                results.add(
                    ExtensionMetadata(
                        id = id,
                        name = item.optString("name"),
                        displayName = item.optString("displayName", item.optString("name")),
                        publisher = item.optString("namespace"),
                        version = item.optString("version", "1.0.0"),
                        description = item.optString("description", "VS Code extension from Open-VSX"),
                        category = parseCategory(item.optJSONArray("categories")),
                        downloadUrl = item.optJSONObject("files")?.optString("download"),
                        downloadCount = item.optLong("downloadCount", 0),
                        isInstalled = isInstalled,
                        isEnabled = isEnabled
                    )
                )
            }

            VibeResult.Success(results)
        } catch (e: Exception) {
            VibeResult.Error("Open VSX search failed: ${e.message ?: "network unavailable"}", e)
        }
    }

    private fun parseCategory(categories: org.json.JSONArray?): ExtensionCategory {
        if (categories == null) return ExtensionCategory.THEME
        for (i in 0 until categories.length()) {
            val cat = categories.optString(i).lowercase()
            when {
                cat.contains("theme") -> return ExtensionCategory.THEME
                cat.contains("language") || cat.contains("programming") -> return ExtensionCategory.LANGUAGE
                cat.contains("formatter") -> return ExtensionCategory.FORMATTER
                cat.contains("linter") -> return ExtensionCategory.LINTER
                cat.contains("icon") -> return ExtensionCategory.ICON_PACK
                cat.contains("snippet") -> return ExtensionCategory.SNIPPET
            }
        }
        return ExtensionCategory.THEME
    }

    override suspend fun installExtension(extension: ExtensionMetadata): VibeResult<Unit> =
        VibeResult.Error("Extension metadata alone is not an installable package. Install a supported theme VSIX instead.")

    private fun installParsedTheme(
        extension: ExtensionMetadata,
        theme: EditorThemeDefinition
    ): VibeResult<ExtensionMetadata> = synchronized(stateLock) {
        val installed = extension.copy(
            category = ExtensionCategory.THEME,
            isInstalled = true,
            isEnabled = true
        )
        val previous = _installedExtensions.value
        val updated = listOf(installed) + previous.filter { it.id != installed.id }
        when (val saveResult = persistAndPublish(updated)) {
            is VibeResult.Error -> return@synchronized saveResult
            else -> Unit
        }
        when (val themeResult = persistTheme(installed.id, theme)) {
            is VibeResult.Error -> {
                persistAndPublish(previous)
                return@synchronized themeResult
            }
            else -> Unit
        }
        VibeResult.Success(installed)
    }

    override suspend fun downloadAndInstall(
        extension: ExtensionMetadata,
        onProgress: (Float) -> Unit
    ): VibeResult<Unit> = withContext(dispatchers.io) {
        if (extension.category != ExtensionCategory.THEME) {
            return@withContext VibeResult.Error("VibeSpace currently supports declarative color themes only.")
        }
        val downloadUrl = extension.downloadUrl?.takeIf { it.isNotBlank() }
            ?: return@withContext VibeResult.Error("This extension has no downloadable VSIX package.")
        if (!downloadUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext VibeResult.Error("Extension downloads must use HTTPS.")
        }

        val maxBytes = MAX_DOWNLOAD_BYTES
        try {
            val tempDirectory = stateFile?.parentFile
            val tempFile = java.io.File.createTempFile("vsix_", ".zip", tempDirectory)
            try {
                onProgress(0f)
                var currentUrl = URL(downloadUrl)
                var downloaded = false
                var redirectCount = 0
                while (!downloaded) {
                    if (!currentUrl.protocol.equals("https", ignoreCase = true) || currentUrl.userInfo != null) {
                        return@withContext VibeResult.Error("Extension download redirected to an unsafe URL.")
                    }
                    val connection = (currentUrl.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 15000
                        instanceFollowRedirects = false
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "VibeSpace-Android/1.0")
                    }
                    try {
                        val status = connection.responseCode
                        if (status in REDIRECT_CODES) {
                            if (redirectCount >= MAX_REDIRECTS) {
                                return@withContext VibeResult.Error("Extension download exceeded the redirect limit.")
                            }
                            val location = connection.getHeaderField("Location")
                                ?: return@withContext VibeResult.Error("Extension server returned a redirect without a destination.")
                            currentUrl = URL(currentUrl, location)
                            redirectCount++
                            continue
                        }
                        if (status !in 200..299) {
                            return@withContext VibeResult.Error("Extension server returned HTTP $status.")
                        }
                        val contentLength = connection.contentLengthLong
                        if (contentLength > maxBytes) {
                            return@withContext VibeResult.Error("Extension package exceeds the ${maxBytes / (1024 * 1024)} MB download limit.")
                        }
                        val buffer = ByteArray(8192)
                        var bytesRead = 0L
                        onProgress(0.05f)
                        connection.inputStream.use { input ->
                            tempFile.outputStream().use { output ->
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count == -1) break
                                    bytesRead += count
                                    if (bytesRead > maxBytes) {
                                        throw IllegalStateException("Extension package exceeds the ${maxBytes / (1024 * 1024)} MB download limit.")
                                    }
                                    output.write(buffer, 0, count)
                                    if (contentLength > 0) {
                                        onProgress((0.05f + 0.85f * bytesRead / contentLength).coerceIn(0.05f, 0.9f))
                                    }
                                }
                            }
                        }
                        downloaded = true
                    } finally {
                        connection.disconnect()
                    }
                }

                val parsed = tempFile.inputStream().use { VsixPackageParser.parseThemeFromVsix(it) }
                when (parsed) {
                    is VibeResult.Success -> when (val installed = installParsedTheme(extension, parsed.data)) {
                        is VibeResult.Success -> {
                            onProgress(1f)
                            VibeResult.Success(Unit)
                        }
                        is VibeResult.Error -> installed
                        else -> VibeResult.Error("Unknown error while saving the imported theme.")
                    }
                    is VibeResult.Error -> VibeResult.Error("Failed to install theme VSIX: ${parsed.message}", parsed.throwable)
                    else -> VibeResult.Error("Unknown error while parsing the theme VSIX.")
                }
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            VibeResult.Error("Failed to download extension '${extension.displayName}': ${e.message}", e)
        }
    }

    override suspend fun installFromVsix(inputStream: InputStream): VibeResult<ExtensionMetadata> = withContext(dispatchers.io) {
        when (val parseResult = inputStream.use { VsixPackageParser.parseThemeFromVsix(it) }) {
            is VibeResult.Success -> {
                val theme = parseResult.data
                val extension = ExtensionMetadata(
                    id = "local.${theme.id}",
                    name = theme.id,
                    displayName = theme.name,
                    publisher = "Local VSIX",
                    version = "1.0.0",
                    description = "Custom imported VS Code theme package: ${theme.name}",
                    category = ExtensionCategory.THEME,
                    isInstalled = true,
                    isEnabled = true
                )
                when (val result = installParsedTheme(extension, theme)) {
                    is VibeResult.Success -> result
                    is VibeResult.Error -> result
                    else -> VibeResult.Error("Unknown error while saving the imported theme.")
                }
            }
            is VibeResult.Error -> VibeResult.Error(parseResult.message, parseResult.throwable)
            else -> VibeResult.Error("Unknown VSIX parsing failure.")
        }
    }

    override suspend fun uninstallExtension(extensionId: String): VibeResult<Unit> = withContext(dispatchers.default) {
        synchronized(stateLock) {
        val installed = _installedExtensions.value.firstOrNull { it.id == extensionId }
        if (installed == null) {
            return@synchronized VibeResult.Error("Extension '$extensionId' is not installed.")
        }
        if (installed.isBuiltIn) {
            return@synchronized VibeResult.Error("Built-in extensions cannot be uninstalled.")
        }
        val previous = _installedExtensions.value
        when (val saveResult = persistAndPublish(previous.filterNot { it.id == extensionId })) {
            is VibeResult.Error -> return@synchronized saveResult
            else -> Unit
        }
        when (val themeResult = removePersistedTheme(extensionId)) {
            is VibeResult.Error -> {
                persistAndPublish(previous)
                return@synchronized themeResult
            }
            else -> themeResult
        }
        }
    }

    override suspend fun toggleExtension(extensionId: String, enabled: Boolean): VibeResult<Unit> = withContext(dispatchers.default) {
        synchronized(stateLock) {
        val installed = _installedExtensions.value.firstOrNull { it.id == extensionId }
        if (installed == null) {
            return@synchronized VibeResult.Error("Extension '$extensionId' is not installed.")
        }
        if (installed.isBuiltIn) {
            return@synchronized VibeResult.Error("Built-in extensions cannot be toggled.")
        }
        when (val saveResult = persistAndPublish(_installedExtensions.value.map {
            if (it.id == extensionId) it.copy(isEnabled = enabled) else it
        })) {
            is VibeResult.Error -> return@synchronized saveResult
            else -> Unit
        }
        if (enabled) {
            extensionThemes[extensionId]?.let { CustomEditorThemeRegistry.register(it, extensionId) }
        } else {
            CustomEditorThemeRegistry.removeExtension(extensionId)
        }
        VibeResult.Success(Unit)
        }
    }

    companion object {
        private const val MAX_DOWNLOAD_BYTES = 50L * 1024L * 1024L
        private const val MAX_SEARCH_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
