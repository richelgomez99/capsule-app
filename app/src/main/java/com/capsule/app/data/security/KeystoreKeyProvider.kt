package com.capsule.app.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the 256-bit SQLCipher passphrase wrapped by Android Keystore.
 *
 * Key lifecycle (per data-model.md §Encryption Key Lifecycle):
 * 1. First launch: generate AES-256-GCM master key in Keystore, generate random
 *    32-byte passphrase, encrypt it with the master key, store ciphertext in
 *    SharedPreferences.
 * 2. Subsequent launches: unwrap the ciphertext with the Keystore master key.
 * 3. The passphrase is held in memory only while the DB is open.
 */
object KeystoreKeyProvider {

    private const val KEYSTORE_ALIAS = "orbit_db_master_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "orbit_key_prefs"
    private const val PREF_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
    private const val PREF_IV = "passphrase_iv"
    private const val GCM_TAG_LENGTH = 128

    /**
     * Returns the 32-byte SQLCipher passphrase, generating and persisting it on
     * first call.
     */
    fun getOrCreatePassphrase(context: android.content.Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val existingCiphertext = prefs.getString(PREF_ENCRYPTED_PASSPHRASE, null)
        val existingIv = prefs.getString(PREF_IV, null)

        return if (existingCiphertext != null && existingIv != null) {
            unwrap(
                android.util.Base64.decode(existingCiphertext, android.util.Base64.NO_WRAP),
                android.util.Base64.decode(existingIv, android.util.Base64.NO_WRAP)
            )
        } else {
            val passphrase = generateRandomPassphrase()
            val (ciphertext, iv) = wrap(passphrase)
            prefs.edit()
                .putString(PREF_ENCRYPTED_PASSPHRASE, android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP))
                .putString(PREF_IV, android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
                .apply()
            passphrase
        }
    }

    private fun generateRandomPassphrase(): ByteArray {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        ks.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun wrap(passphrase: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val ciphertext = cipher.doFinal(passphrase)
        return ciphertext to cipher.iv
    }

    private fun unwrap(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }
}
