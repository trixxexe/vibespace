package com.vibespace.core.common.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.vibespace.core.common.result.VibeResult
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Contract for secure encryption and decryption of sensitive user credentials (such as AI API keys).
 * Fails closed: all fallible operations explicitly return [VibeResult].
 */
interface SecretStorage {
    fun encrypt(plaintext: String): VibeResult<String>
    fun decrypt(ciphertext: String): VibeResult<String>
    fun maskSecret(secret: String): String
}

/**
 * Android Keystore-backed AES-256-GCM secret storage.
 * Enforces authenticated encryption with no hardcoded fallback key in production.
 * If the Keystore is unavailable, operations fail closed by returning [VibeResult.Error].
 *
 * For unit test runners, a [customKey] can be passed into the constructor.
 */
class SecureSecretStorage(
    private val keyAlias: String = "vibespace_ai_credentials",
    private val customKey: SecretKey? = null
) : SecretStorage {

    private val isAndroidKeyStoreAvailable: Boolean by lazy {
        try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
            ks.load(null)
            true
        } catch (_: Throwable) {
            false
        }
    }

    init {
        if (customKey == null && isAndroidKeyStoreAvailable) {
            ensureKeystoreKey()
        }
    }

    private fun ensureKeystoreKey() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (_: Throwable) {
            // Failure to generate keystore key will fail closed during getSecretKey()
        }
    }

    private fun getSecretKey(): SecretKey? {
        if (customKey != null) {
            return customKey
        }
        if (!isAndroidKeyStoreAvailable) {
            return null
        }
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (_: Throwable) {
            null
        }
    }

    override fun encrypt(plaintext: String): VibeResult<String> {
        if (plaintext.isEmpty()) return VibeResult.Success("")
        val key = getSecretKey()
            ?: return VibeResult.Error("Android Keystore authenticated key unavailable; encryption refused.")

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            VibeResult.Success(PREFIX + Base64.getEncoder().encodeToString(combined))
        } catch (e: Throwable) {
            VibeResult.Error("Encryption failed: ${e.message}", e)
        }
    }

    override fun decrypt(ciphertext: String): VibeResult<String> {
        if (ciphertext.isEmpty()) return VibeResult.Success("")
        // Safe legacy plaintext migration: unencrypted legacy keys returned for one-time migration
        if (!ciphertext.startsWith(PREFIX)) {
            return VibeResult.Success(ciphertext)
        }

        val key = getSecretKey()
            ?: return VibeResult.Error("Android Keystore authenticated key unavailable; decryption failed.")

        return try {
            val rawBase64 = ciphertext.removePrefix(PREFIX)
            val combined = Base64.getDecoder().decode(rawBase64)
            if (combined.size < GCM_IV_LENGTH) {
                return VibeResult.Error("Ciphertext payload is truncated or corrupted.")
            }

            val iv = ByteArray(GCM_IV_LENGTH)
            val encryptedBytes = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            VibeResult.Success(String(decryptedBytes, StandardCharsets.UTF_8))
        } catch (e: Throwable) {
            VibeResult.Error("Decryption failed: corrupted ciphertext or invalid key: ${e.message}", e)
        }
    }

    override fun maskSecret(secret: String): String {
        val trimmed = secret.trim()
        if (trimmed.length <= 8) return "********"
        return "${trimmed.take(4)}...${trimmed.takeLast(4)}"
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        const val PREFIX = "enc:"
    }
}
