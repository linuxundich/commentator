package de.christophlangner.commentator.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.christophlangner.commentator.domain.repository.ReplyTemplate
import de.christophlangner.commentator.domain.repository.ReplyTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Textbausteine in DataStore, als JSON-Liste unter einem Schlüssel.
 *
 * Keine eigene Tabelle in Room: Es sind wenige kurze Texte ohne Beziehungen,
 * die immer vollständig gelesen werden. Eine Tabelle dafür wäre Aufwand ohne
 * Gegenwert.
 */
@Singleton
class DefaultReplyTemplateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReplyTemplateRepository {

    @Serializable
    private data class StoredTemplate(val id: String, val text: String)

    private val json = Json { ignoreUnknownKeys = true }

    override val templates: Flow<List<ReplyTemplate>> = dataStore.data.map { prefs ->
        read(prefs).map { ReplyTemplate(it.id, it.text) }
    }

    override suspend fun add(text: String): ReplyTemplate? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val template = ReplyTemplate(id = UUID.randomUUID().toString(), text = trimmed)
        dataStore.edit { prefs ->
            val current = read(prefs)
            // Die Obergrenze schützt vor einer Liste, die niemand mehr
            // überblickt - und nebenbei vor einem unbegrenzt wachsenden
            // Eintrag in DataStore.
            if (current.size >= ReplyTemplate.MAX_TEMPLATES) return@edit
            write(prefs, current + StoredTemplate(template.id, template.text))
        }
        return template
    }

    override suspend fun update(id: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        dataStore.edit { prefs ->
            write(
                prefs,
                read(prefs).map { if (it.id == id) it.copy(text = trimmed) else it },
            )
        }
    }

    override suspend fun remove(id: String) {
        dataStore.edit { prefs ->
            write(prefs, read(prefs).filterNot { it.id == id })
        }
    }

    private fun read(prefs: Preferences): List<StoredTemplate> {
        val raw = prefs[KEY] ?: return emptyList()
        // Ein beschädigter Eintrag darf die App nicht am Start hindern; im
        // schlimmsten Fall sind die Bausteine weg, nicht die Anwendung.
        return runCatching { json.decodeFromString<List<StoredTemplate>>(raw) }
            .getOrDefault(emptyList())
    }

    private fun write(prefs: androidx.datastore.preferences.core.MutablePreferences, value: List<StoredTemplate>) {
        prefs[KEY] = json.encodeToString(value)
    }

    private companion object {
        val KEY = stringPreferencesKey("reply_templates")
    }
}
