package com.autoverse3d.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autoverse3d.core.database.ConfigurationDao
import com.autoverse3d.core.database.ConfigurationEntity
import com.autoverse3d.core.database.ModuleProgressEntity
import com.autoverse3d.core.database.ProgressDao
import com.autoverse3d.core.network.AutoVerseApi
import com.autoverse3d.domain.ContentSearch
import com.autoverse3d.domain.ModuleProgress
import com.autoverse3d.domain.SearchHit
import com.autoverse3d.domain.model.AssistantRequest
import com.autoverse3d.domain.model.AssistantResponse
import com.autoverse3d.domain.model.VehicleConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException

@Singleton
class ConfigurationRepository @Inject constructor(private val dao: ConfigurationDao) {
    fun observe(): Flow<List<VehicleConfiguration>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    suspend fun get(id: String) = dao.get(id)?.toDomain()
    suspend fun save(config: VehicleConfiguration) = dao.upsert(ConfigurationEntity.from(config))
    suspend fun delete(id: String) = dao.delete(id)
}

@Singleton
class ProgressRepository @Inject constructor(private val dao: ProgressDao) {
    fun observe(): Flow<Map<String, ModuleProgress>> = dao.observeAll().map { list -> list.associate { it.moduleId to it.toDomain() } }
    suspend fun get(moduleId: String) = dao.get(moduleId)?.toDomain() ?: ModuleProgress(moduleId)
    suspend fun save(progress: ModuleProgress) = dao.upsert(ModuleProgressEntity.from(progress))
}

private val Context.dataStore by preferencesDataStore(name = "autoverse_prefs")

/** Small user preferences: what to show under "Continue exploring". No personal data. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val lastVehicle = stringPreferencesKey("last_vehicle")
    private val lastMode = stringPreferencesKey("last_mode")

    val lastExplored: Flow<Pair<String, String>?> = context.dataStore.data.map { p ->
        val v = p[lastVehicle] ?: return@map null
        v to (p[lastMode] ?: "exterior")
    }

    suspend fun recordExplored(vehicleId: String, mode: String) {
        context.dataStore.edit { it[lastVehicle] = vehicleId; it[lastMode] = mode }
    }
}

sealed interface AssistantResult {
    data class Online(val response: AssistantResponse) : AssistantResult
    /** The AI is unreachable; answer from offline learning content instead. */
    data class Offline(val reason: String, val matches: List<SearchHit>) : AssistantResult
    data class RateLimited(val retryAfterSeconds: Int?, val matches: List<SearchHit>) : AssistantResult
    data class Invalid(val message: String) : AssistantResult
}

@Singleton
class AssistantRepository @Inject constructor(private val api: AutoVerseApi, private val content: ContentSource) {
    suspend fun ask(question: String, vehicleId: String?, componentId: String?): AssistantResult {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return AssistantResult.Invalid("Type a question first.")
        if (trimmed.length > 500) return AssistantResult.Invalid("Please keep questions under 500 characters.")
        val index = content.content().searchIndex
        val offline = { ContentSearch.search(index, trimmed, 4) }
        return try {
            AssistantResult.Online(api.ask(AssistantRequest(trimmed, vehicleId, componentId)))
        } catch (e: HttpException) {
            when (e.code()) {
                429 -> AssistantResult.RateLimited(e.response()?.headers()?.get("Retry-After")?.toIntOrNull(), offline())
                400 -> AssistantResult.Invalid("The assistant couldn't read that question. Try rephrasing it.")
                else -> AssistantResult.Offline("The AI assistant is temporarily unavailable.", offline())
            }
        } catch (e: IOException) {
            AssistantResult.Offline("You're offline, so the AI assistant isn't available.", offline())
        }
    }
}
