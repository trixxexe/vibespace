package com.vibespace.core.common.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureSecretStorageTest {

    private val storage = SecureSecretStorage("test_key_alias")

    @Test
    fun encrypt_and_decrypt_returns_original_plaintext() {
        val apiKey = "AIzaSyD-1234567890abcdef_SecretKey"
        val encrypted = storage.encrypt(apiKey)

        assertTrue(encrypted.startsWith("enc:"))
        assertNotEquals(apiKey, encrypted)

        val decrypted = storage.decrypt(encrypted)
        assertEquals(apiKey, decrypted)
    }

    @Test
    fun decrypt_legacy_plaintext_key_returns_plaintext_directly() {
        val legacyKey = "AIzaSyD-LegacyPlaintextKey"
        val decrypted = storage.decrypt(legacyKey)
        assertEquals(legacyKey, decrypted)
    }

    @Test
    fun empty_string_handling() {
        assertEquals("", storage.encrypt(""))
        assertEquals("", storage.decrypt(""))
    }

    @Test
    fun maskSecret_masks_properly() {
        val key = "AIzaSyD1234567890abcdef"
        val masked = storage.maskSecret(key)
        assertEquals("AIza...cdef", masked)

        val shortKey = "secret"
        assertEquals("********", storage.maskSecret(shortKey))
    }
}
