package de.christophlangner.commentator.data.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class CredentialPayload(val username: String, val password: String)

/**
 * Speichert Zugangsdaten pro Instanz - verschlüsselt.
 *
 * In DataStore landet ausschließlich `Base64(IV || AES-GCM(JSON))`. Im
 * Klartext steht dort nichts. Der Schlüssel dafür liegt im Android Keystore.
 */
/**
 * Lesender Zugriff auf Zugangsdaten.
 *
 * Eigene Schnittstelle, damit der HTTP-Interceptor nicht von DataStore und
 * Keystore abhängt und ohne Android-Laufzeit geprüft werden kann.
 */
fun interface CredentialSource {
    suspend fun load(instanceId: String): InstanceCredentials?
}

@Singleton
class CredentialStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val crypto: KeystoreCrypto,
) : CredentialSource {

    private val json = Json
    private val cache = mutableMapOf<String, InstanceCredentials>()

    suspend fun save(instanceId: String, credentials: InstanceCredentials) {
        val payload = json.encodeToString(
            CredentialPayload(credentials.username, credentials.password.reveal()),
        )
        val encrypted = crypto.encrypt(payload)
        dataStore.edit { it[keyFor(instanceId)] = encrypted }
        synchronized(cache) { cache[instanceId] = credentials }
    }

    override suspend fun load(instanceId: String): InstanceCredentials? {
        synchronized(cache) { cache[instanceId] }?.let { return it }

        val stored = dataStore.data.first()[keyFor(instanceId)] ?: return null
        val decrypted = crypto.decrypt(stored) ?: return null
        val payload = runCatching {
            json.decodeFromString<CredentialPayload>(decrypted)
        }.getOrNull() ?: return null

        val credentials = InstanceCredentials(
            username = payload.username,
            password = ApplicationPassword(payload.password),
        )
        synchronized(cache) { cache[instanceId] = credentials }
        return credentials
    }

    suspend fun clear(instanceId: String) {
        dataStore.edit { it.remove(keyFor(instanceId)) }
        synchronized(cache) { cache.remove(instanceId) }
    }

    /**
     * Entfernt auch das Schlüsselmaterial. Nur aufrufen, wenn keine Instanz
     * mehr eingerichtet ist - der Keystore-Schlüssel wird von allen geteilt.
     */
    fun destroyKeyMaterial() {
        crypto.deleteKey()
        synchronized(cache) { cache.clear() }
    }

    private fun keyFor(instanceId: String) = stringPreferencesKey("credential_$instanceId")
}
