package de.christophlangner.commentator.data.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import de.christophlangner.commentator.core.AppLog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.encoding.Base64

/**
 * Ver- und Entschlüsselung mit einem Schlüssel aus dem Android Keystore.
 *
 * `EncryptedSharedPreferences` aus `androidx.security:security-crypto` wäre
 * der bequemere Weg, ist seit 1.1.0-beta01 aber vollständig deprecated;
 * Google verweist ausdrücklich auf die direkte Nutzung des Keystore. Genau
 * das passiert hier - ohne zusätzliche Abhängigkeit.
 *
 * Der Schlüssel verlässt den Keystore nie. Auf Geräten mit sicherem Element
 * liegt er in Hardware.
 */
@Singleton
class KeystoreCrypto @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encode(iv + cipherText)
    }

    /** Gibt `null` zurück, wenn der Schlüssel fehlt oder die Daten manipuliert wurden. */
    fun decrypt(encoded: String): String? = try {
        val raw = Base64.decode(encoded)
        require(raw.size > IV_LENGTH) { "Datensatz zu kurz" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, raw, 0, IV_LENGTH),
        )
        String(cipher.doFinal(raw.copyOfRange(IV_LENGTH, raw.size)), Charsets.UTF_8)
    } catch (error: Exception) {
        // Bewusst ohne Weitergabe des Fehlers: Er würde nichts aussagen, das
        // die Oberfläche brauchbar darstellen könnte.
        AppLog.w("Zugangsdaten konnten nicht entschlüsselt werden")
        null
    }

    fun deleteKey() {
        runCatching { keyStore.deleteEntry(KEY_ALIAS) }
    }

    private fun secretKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "commentator_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
