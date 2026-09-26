package de.christophlangner.commentator.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
 * Textbausteine in DataStore, als JSON-Liste unter einem Schlüssel je Blog.
 *
 * Keine eigene Tabelle in Room: Es sind wenige kurze Texte ohne Beziehungen,
 * die immer vollständig gelesen werden. Eine Tabelle dafür wäre Aufwand ohne
 * Gegenwert.
 *
 * Hat ein Blog noch keine eigene Liste, gilt die frühere blogübergreifende -
 * die Bausteine aus der Zeit mit nur einem Blog bleiben dort, wo sie
 * geschrieben wurden. Gespeichert wird von da an unter der Kennung des Blogs.
 */
@Singleton
class DefaultReplyTemplateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReplyTemplateRepository {

    @Serializable
    private data class StoredTemplate(val id: String, val text: String)

    private val json = Json { ignoreUnknownKeys = true }

    override fun templates(instanceId: String): Flow<List<ReplyTemplate>> =
        dataStore.data.map { prefs ->
            read(prefs, instanceId).map { ReplyTemplate(it.id, it.text) }
        }

    override suspend fun add(instanceId: String, text: String): ReplyTemplate? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val template = ReplyTemplate(id = UUID.randomUUID().toString(), text = trimmed)
        dataStore.edit { prefs ->
            val current = read(prefs, instanceId)
            // Die Obergrenze schützt vor einer Liste, die niemand mehr
            // überblickt - und nebenbei vor einem unbegrenzt wachsenden
            // Eintrag in DataStore.
            if (current.size >= ReplyTemplate.MAX_TEMPLATES) return@edit
            write(prefs, instanceId, current + StoredTemplate(template.id, template.text))
        }
        return template
    }

    override suspend fun update(instanceId: String, id: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        dataStore.edit { prefs ->
            write(
                prefs,
                instanceId,
                read(prefs, instanceId).map { if (it.id == id) it.copy(text = trimmed) else it },
            )
        }
    }

    override suspend fun remove(instanceId: String, id: String) {
        dataStore.edit { prefs ->
            write(prefs, instanceId, read(prefs, instanceId).filterNot { it.id == id })
        }
    }

    private fun read(prefs: Preferences, instanceId: String): List<StoredTemplate> {
        val raw = prefs[keyFor(instanceId)] ?: prefs[LEGACY_KEY] ?: return emptyList()
        // Ein beschädigter Eintrag darf die App nicht am Start hindern; im
        // schlimmsten Fall sind die Bausteine weg, nicht die Anwendung.
        return runCatching { json.decodeFromString<List<StoredTemplate>>(raw) }
            .getOrDefault(emptyList())
    }

    private fun write(prefs: MutablePreferences, instanceId: String, value: List<StoredTemplate>) {
        prefs[keyFor(instanceId)] = json.encodeToString(value)
    }

    private companion object {
        /** Nur noch gelesen: die Liste aus der Zeit, als die App einen Blog kannte. */
        val LEGACY_KEY = stringPreferencesKey("reply_templates")

        fun keyFor(instanceId: String) = stringPreferencesKey("reply_templates_$instanceId")
    }
}
