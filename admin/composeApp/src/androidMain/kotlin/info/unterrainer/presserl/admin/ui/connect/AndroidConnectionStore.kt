package info.unterrainer.presserl.admin.ui.connect

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import info.unterrainer.presserl.admin.ui.account.SlipCredentials
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The connection in the app's private preferences: the server address in plain text, the slip credentials encrypted
 * with AES-GCM under a key that never leaves the Android Keystore (design D7). Backups exclude the preferences
 * (manifest). Credentials that cannot be decrypted any more (key lost) are dropped.
 */
class AndroidConnectionStore(context: Context) : ConnectionStore {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Serializable
    private data class Secret(val username: String, val passPhrase: String)

    override fun load(): StoredConnection? {
        val base = preferences.getString(BASE, null) ?: return null
        val credentials = decrypt()?.let { SlipCredentials(base, it.username, it.passPhrase) }
        return StoredConnection(base, credentials)
    }

    override fun save(connection: StoredConnection) {
        val editor = preferences.edit().putString(BASE, connection.base)
        val credentials = connection.credentials
        if (credentials == null) {
            editor.remove(IV).remove(CIPHERTEXT)
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val plain = Json.encodeToString(Secret.serializer(), Secret(credentials.username, credentials.passPhrase))
            editor.putString(IV, encode(cipher.iv)).putString(CIPHERTEXT, encode(cipher.doFinal(plain.encodeToByteArray())))
        }
        editor.commit()
    }

    override fun clear() {
        preferences.edit().clear().commit()
        keyStore().deleteEntry(KEY_ALIAS)
    }

    private fun decrypt(): Secret? {
        val iv = preferences.getString(IV, null) ?: return null
        val ciphertext = preferences.getString(CIPHERTEXT, null) ?: return null
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, decode(iv))) }
            Json.decodeFromString(Secret.serializer(), cipher.doFinal(decode(ciphertext)).decodeToString())
        } catch (e: GeneralSecurityException) {
            preferences.edit().remove(IV).remove(CIPHERTEXT).commit()
            null
        } catch (e: IllegalArgumentException) {
            preferences.edit().remove(IV).remove(CIPHERTEXT).commit()
            null
        }
    }

    private fun key(): SecretKey = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
        ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val PREFERENCES = "presserl.connection"
        const val BASE = "base"
        const val IV = "credentials.iv"
        const val CIPHERTEXT = "credentials.ciphertext"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "presserl.slip-credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
