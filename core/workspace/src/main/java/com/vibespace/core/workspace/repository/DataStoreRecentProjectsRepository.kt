package com.vibespace.core.workspace.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.workspace.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

val Context.recentProjectsDataStore: DataStore<Preferences> by preferencesDataStore(name = "vibespace_recent_projects")

/**
 * Production persistent recent projects repository backed by AndroidX DataStore Preferences.
 * Stores project metadata as serialized JSON to survive app process destruction.
 */
class DataStoreRecentProjectsRepository(
    private val context: Context,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : RecentProjectsRepository {

    private object PreferencesKeys {
        val RECENT_PROJECTS_JSON = stringPreferencesKey("recent_projects_json")
    }

    override fun getRecentProjects(): Flow<List<Project>> {
        return context.recentProjectsDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val jsonStr = preferences[PreferencesKeys.RECENT_PROJECTS_JSON] ?: "[]"
                parseProjectsJson(jsonStr)
            }
            .flowOn(dispatchers.io)
    }

    override suspend fun addRecentProject(project: Project): VibeResult<Unit> = withContext(dispatchers.io) {
        try {
            context.recentProjectsDataStore.edit { preferences ->
                val existing = parseProjectsJson(preferences[PreferencesKeys.RECENT_PROJECTS_JSON] ?: "[]")
                val updated = listOf(project) + existing.filter { it.id != project.id }
                preferences[PreferencesKeys.RECENT_PROJECTS_JSON] = serializeProjects(updated.take(15))
            }
            VibeResult.Success(Unit)
        } catch (e: Exception) {
            VibeResult.Error("Failed to add recent project: ${e.message}", e)
        }
    }

    override suspend fun removeRecentProject(projectId: String): VibeResult<Unit> = withContext(dispatchers.io) {
        try {
            context.recentProjectsDataStore.edit { preferences ->
                val existing = parseProjectsJson(preferences[PreferencesKeys.RECENT_PROJECTS_JSON] ?: "[]")
                val updated = existing.filter { it.id != projectId }
                preferences[PreferencesKeys.RECENT_PROJECTS_JSON] = serializeProjects(updated)
            }
            VibeResult.Success(Unit)
        } catch (e: Exception) {
            VibeResult.Error("Failed to remove recent project: ${e.message}", e)
        }
    }

    override suspend fun clearRecentProjects(): VibeResult<Unit> = withContext(dispatchers.io) {
        try {
            context.recentProjectsDataStore.edit { preferences ->
                preferences[PreferencesKeys.RECENT_PROJECTS_JSON] = "[]"
            }
            VibeResult.Success(Unit)
        } catch (e: Exception) {
            VibeResult.Error("Failed to clear recent projects: ${e.message}", e)
        }
    }

    private fun serializeProjects(projects: List<Project>): String {
        val array = JSONArray()
        for (project in projects) {
            val obj = JSONObject().apply {
                put("id", project.id)
                put("name", project.name)
                put("rootUri", project.rootUri)
                put("rootPath", project.rootPath ?: JSONObject.NULL)
                put("isStorageAccessFramework", project.isStorageAccessFramework)
                put("lastOpenedTimestamp", project.lastOpenedTimestamp)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseProjectsJson(jsonStr: String): List<Project> {
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<Project>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rootPath = if (obj.isNull("rootPath")) null else obj.getString("rootPath")
                list.add(
                    Project(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        rootUri = obj.getString("rootUri"),
                        rootPath = rootPath,
                        isStorageAccessFramework = obj.optBoolean("isStorageAccessFramework", false),
                        lastOpenedTimestamp = obj.optLong("lastOpenedTimestamp", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
