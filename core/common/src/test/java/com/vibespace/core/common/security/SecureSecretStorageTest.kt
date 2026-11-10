package com.vibespace.core.common.security

import com.vibespace.core.common.result.VibeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

class SecureSecretStorageTest {

    private val testKey = SecretKeySpec(
        "test_secret_aes_key_32_bytes!!".toByteArray(StandardCharsets.UTF_8).copyOf(32),
        "AES"
    )
    private val storageWithKey = SecureSecretStorage("test_key_alias", customKey = testKey)

    @Test
    fun encrypt_and_decrypt_returns_original_plaintext() {
        val apiKey = "AIzaSyD-1234567890abcdef_SecretKey"
        val encResult = storageWithKey.encrypt(apiKey)

        assertTrue(encResult is VibeResult.Success)
        val encrypted = (encResult as VibeResult.Success).data
        assertTrue(encrypted.startsWith("enc:"))
        assertNotEquals(apiKey, encrypted)

        val decResult = storageWithKey.decrypt(encrypted)
        assertTrue(decResult is VibeResult.Success)
        assertEquals(apiKey, (decResult as VibeResult.Success).data)
    }

    @Test
    fun encryption_fails_closed_when_keystore_key_unavailable() {
        // Without AndroidKeyStore on host JVM and without a custom key, it fails closed
        val noKeyStorage = SecureSecretStorage("unsupported_alias", customKey = null)
        val result = noKeyStorage.encrypt("secret_key_123")

        assertTrue(result is VibeResult.Error)
        val error = result as VibeResult.Error
        assertTrue(error.message.contains("unavailable"))
    }

    @Test
    fun decryption_fails_closed_when_keystore_key_unavailable() {
        val noKeyStorage = SecureSecretStorage("unsupported_alias", customKey = null)
        val result = noKeyStorage.decrypt("enc:dGVzdF9jaXBoZXJfdGV4dA==")

        assertTrue(result is VibeResult.Error)
        val error = result as VibeResult.Error
        assertTrue(error.message.contains("unavailable"))
    }

    @Test
    fun decrypt_corrupted_ciphertext_returns_error() {
        val corruptedResult = storageWithKey.decrypt("enc:not_valid_base_64!!!")
        assertTrue(corruptedResult is VibeResult.Error)
    }

    @Test
    fun decrypt_truncated_ciphertext_returns_error() {
        // Shorter than GCM 12-byte IV
        val truncatedBase64 = "enc:" + Base64.getEncoder().encodeToString(ByteArray(5))
        val result = storageWithKey.decrypt(truncatedBase64)
        assertTrue(result is VibeResult.Error)
        assertTrue((result as VibeResult.Error).message.contains("truncated") || (result as VibeResult.Error).message.contains("corrupted"))
    }

    @Test
    fun decrypt_legacy_plaintext_key_returns_plaintext_directly() {
        val legacyKey = "AIzaSyD-LegacyPlaintextKey"
        val decResult = storageWithKey.decrypt(legacyKey)
        assertTrue(decResult is VibeResult.Success)
        assertEquals(legacyKey, (decResult as VibeResult.Success).data)
    }

    @Test
    fun empty_string_handling() {
        val enc = storageWithKey.encrypt("")
        assertTrue(enc is VibeResult.Success)
        assertEquals("", (enc as VibeResult.Success).data)

        val dec = storageWithKey.decrypt("")
        assertTrue(dec is VibeResult.Success)
        assertEquals("", (dec as VibeResult.Success).data)
    }

    @Test
    fun maskSecret_masks_properly() {
        val key = "AIzaSyD1234567890abcdef"
        val masked = storageWithKey.maskSecret(key)
        assertEquals("AIza...cdef", masked)

        val shortKey = "secret"
        assertEquals("********", storageWithKey.maskSecret(shortKey))
    }
}
