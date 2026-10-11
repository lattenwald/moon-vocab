package org.lattenwald.moonvocab.data.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.lattenwald.moonvocab.domain.mark.StyleCombo
import org.lattenwald.moonvocab.domain.mark.parseStyleCombo

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Serializable
data class LlmService(val id: String, val name: String = "", val baseUrl: String = "", val apiKey: String = "", val model: String = "")

data class SettingsSnapshot(
    val backupTree: Uri?,
    val booksTree: Uri?,
    val services: List<LlmService>,
    val activeServiceId: String,
    val targetLanguage: String,
    val maxExcerptWords: Int,
    val deckPattern: String,
    val tagPattern: String,
    val reviewFirst: Boolean,
    val styles: Set<StyleCombo>,
    val noteTypeId: Long?,
    val noteTypeVersion: Int,
    val deckOverride: String?,
    val lastCheckAt: Long,
) {
    val activeService: LlmService get() = services.firstOrNull { it.id == activeServiceId } ?: services.first()
}

private val DEFAULT_SERVICE = LlmService(
    id = "openrouter", name = "OpenRouter", baseUrl = "https://openrouter.ai/api/v1", apiKey = "", model = "anthropic/claude-haiku-5.5",
)
private const val DEFAULT_DECK_OVERRIDE = "Moon+::Test"

internal object SettingsKeys {
    val backupTree = stringPreferencesKey("backupTree")
    val booksTree = stringPreferencesKey("booksTree")
    val services = stringPreferencesKey("services")
    val activeServiceId = stringPreferencesKey("activeServiceId")
    val targetLanguage = stringPreferencesKey("targetLanguage")
    val maxExcerptWords = intPreferencesKey("maxExcerptWords")
    val deckPattern = stringPreferencesKey("deckPattern")
    val tagPattern = stringPreferencesKey("tagPattern")
    val reviewFirst = booleanPreferencesKey("reviewFirst")
    val styles = stringSetPreferencesKey("styles")
    val noteTypeId = longPreferencesKey("noteTypeId")
    val noteTypeVersion = intPreferencesKey("noteTypeVersion")
    val deckOverride = stringPreferencesKey("deckOverride")
    val lastCheckAt = longPreferencesKey("lastCheckAt")
}

class Settings(private val store: DataStore<Preferences>) {
    val snapshot: Flow<SettingsSnapshot> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSnapshot() }

    suspend fun current(): SettingsSnapshot = snapshot.first()

    suspend fun update(transform: (SettingsSnapshot) -> SettingsSnapshot) {
        store.edit { prefs ->
            val old = prefs.toSnapshot()
            val new = transform(old)
            // The stored list may be one this version can't parse.
            prefs.write(new, writeServices = new.services != old.services)
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun Preferences.toSnapshot() = SettingsSnapshot(
    backupTree = this[SettingsKeys.backupTree]?.let(Uri::parse),
    booksTree = this[SettingsKeys.booksTree]?.let(Uri::parse),
    services = parseServices(this[SettingsKeys.services]),
    activeServiceId = this[SettingsKeys.activeServiceId] ?: DEFAULT_SERVICE.id,
    targetLanguage = this[SettingsKeys.targetLanguage] ?: "ru",
    maxExcerptWords = this[SettingsKeys.maxExcerptWords] ?: 15,
    deckPattern = this[SettingsKeys.deckPattern] ?: "Moon+::{language}",
    tagPattern = this[SettingsKeys.tagPattern] ?: "book::{title}",
    reviewFirst = this[SettingsKeys.reviewFirst] ?: true,
    styles = this[SettingsKeys.styles].orEmpty().mapNotNull(::parseStyleCombo).toSet(),
    noteTypeId = this[SettingsKeys.noteTypeId],
    noteTypeVersion = this[SettingsKeys.noteTypeVersion] ?: 0,
    deckOverride = (this[SettingsKeys.deckOverride] ?: DEFAULT_DECK_OVERRIDE).ifBlank { null },
    lastCheckAt = this[SettingsKeys.lastCheckAt] ?: 0L,
)

private fun MutablePreferences.write(s: SettingsSnapshot, writeServices: Boolean) {
    setOrRemove(SettingsKeys.backupTree, s.backupTree?.toString())
    setOrRemove(SettingsKeys.booksTree, s.booksTree?.toString())
    if (writeServices) {
        this[SettingsKeys.services] = json.encodeToString(
            s.services.map { it.copy(baseUrl = it.baseUrl.trim(), apiKey = it.apiKey.trim(), model = it.model.trim()) },
        )
    }
    this[SettingsKeys.activeServiceId] = s.activeServiceId
    this[SettingsKeys.targetLanguage] = s.targetLanguage
    this[SettingsKeys.maxExcerptWords] = s.maxExcerptWords
    this[SettingsKeys.deckPattern] = s.deckPattern
    this[SettingsKeys.tagPattern] = s.tagPattern
    this[SettingsKeys.reviewFirst] = s.reviewFirst
    this[SettingsKeys.styles] = s.styles.map { it.encode() }.toSet()
    setOrRemove(SettingsKeys.noteTypeId, s.noteTypeId)
    this[SettingsKeys.noteTypeVersion] = s.noteTypeVersion
    this[SettingsKeys.deckOverride] = s.deckOverride.orEmpty()
    this[SettingsKeys.lastCheckAt] = s.lastCheckAt
}

private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
    if (value == null) remove(key) else this[key] = value
}

private fun parseServices(stored: String?): List<LlmService> =
    stored?.let { runCatching { json.decodeFromString<List<LlmService>>(it) }.getOrNull() }?.takeIf { it.isNotEmpty() }
        ?: listOf(DEFAULT_SERVICE)
