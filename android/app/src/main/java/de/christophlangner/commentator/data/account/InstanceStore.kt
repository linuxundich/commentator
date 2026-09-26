package de.christophlangner.commentator.data.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.christophlangner.commentator.domain.model.WordPressInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class StoredInstance(
    val id: String,
    val displayName: String,
    val siteUrl: String,
    val username: String,
    val userId: Long,
    val canModerate: Boolean,
    val hasBridgePlugin: Boolean,
    val canManageOptions: Boolean = false,
    val iconUrl: String? = null,
    val themeIconUrl: String? = null,
)

@Serializable
private data class StoredInstances(
    val instances: List<StoredInstance> = emptyList(),
    val activeId: String? = null,
)

/**
 * Verwaltet die eingerichteten WordPress-Instanzen.
 *
 * Gespeichert wird eine Liste samt Kennung des angezeigten Blogs. Weil das
 * von Anfang an so war, kam der Mehrfachbetrieb ohne Datenmigration aus.
 *
 * Hier stehen ausschließlich unkritische Metadaten - das Application Password
 * liegt verschlüsselt im [CredentialStore].
 */
@Singleton
class InstanceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("wordpress_instances")

    val instances: Flow<List<WordPressInstance>> =
        dataStore.data.map { prefs -> read(prefs).instances.map(::toDomain) }

    val activeInstance: Flow<WordPressInstance?> = dataStore.data.map { prefs ->
        val stored = read(prefs)
        val active = stored.activeId?.let { id -> stored.instances.firstOrNull { it.id == id } }
            ?: stored.instances.firstOrNull()
        active?.let(::toDomain)
    }

    suspend fun currentActive(): WordPressInstance? = activeInstance.first()

    suspend fun all(): List<WordPressInstance> = instances.first()

    suspend fun byId(instanceId: String): WordPressInstance? =
        all().firstOrNull { it.id == instanceId }

    /**
     * Der Blog unter dieser Adresse, falls er schon eingerichtet ist.
     *
     * Braucht die Anmeldung, um einen zweiten Eintrag für denselben Blog zu
     * vermeiden.
     */
    suspend fun byUrl(siteUrl: String): WordPressInstance? =
        all().firstOrNull { it.siteUrl.equals(siteUrl, ignoreCase = true) }

    suspend fun upsert(instance: WordPressInstance, makeActive: Boolean = true) {
        dataStore.edit { prefs ->
            val current = read(prefs)
            val updated = current.instances.filterNot { it.id == instance.id } + toStored(instance)
            prefs[key] = json.encodeToString(
                current.copy(
                    instances = updated,
                    activeId = if (makeActive) instance.id else current.activeId,
                ),
            )
        }
    }

    suspend fun remove(instanceId: String) {
        dataStore.edit { prefs ->
            val current = read(prefs)
            val remaining = current.instances.filterNot { it.id == instanceId }
            prefs[key] = json.encodeToString(
                StoredInstances(
                    instances = remaining,
                    activeId = current.activeId.takeIf { it != instanceId }
                        ?: remaining.firstOrNull()?.id,
                ),
            )
        }
    }

    suspend fun setActive(instanceId: String) {
        dataStore.edit { prefs ->
            val current = read(prefs)
            if (current.instances.none { it.id == instanceId }) return@edit
            prefs[key] = json.encodeToString(current.copy(activeId = instanceId))
        }
    }

    private fun read(prefs: Preferences): StoredInstances {
        val raw = prefs[key] ?: return StoredInstances()
        return runCatching { json.decodeFromString<StoredInstances>(raw) }
            .getOrDefault(StoredInstances())
    }

    private fun toDomain(stored: StoredInstance) = WordPressInstance(
        id = stored.id,
        displayName = stored.displayName,
        siteUrl = stored.siteUrl,
        username = stored.username,
        userId = stored.userId,
        canModerate = stored.canModerate,
        hasBridgePlugin = stored.hasBridgePlugin,
        canManageOptions = stored.canManageOptions,
        iconUrl = stored.iconUrl,
        themeIconUrl = stored.themeIconUrl,
    )

    private fun toStored(instance: WordPressInstance) = StoredInstance(
        id = instance.id,
        displayName = instance.displayName,
        siteUrl = instance.siteUrl,
        username = instance.username,
        userId = instance.userId,
        canModerate = instance.canModerate,
        hasBridgePlugin = instance.hasBridgePlugin,
        canManageOptions = instance.canManageOptions,
        iconUrl = instance.iconUrl,
        themeIconUrl = instance.themeIconUrl,
    )
}
