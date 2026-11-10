package com.vibespace.core.common.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibeResultTest {

    @Test
    fun success_contains_data_and_maps_correctly() {
        val result: VibeResult<Int> = VibeResult.Success(42)

        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertEquals(42, result.getOrNull())

        val mapped = result.map { it * 2 }
        assertTrue(mapped.isSuccess)
        assertEquals(84, mapped.getOrNull())
    }

    @Test
    fun error_contains_message_and_preserves_on_map() {
        val error: VibeResult<Int> = VibeResult.Error("Failed to load file", code = 404)

        assertTrue(error.isError)
        assertFalse(error.isSuccess)
        assertNull(error.getOrNull())
        assertEquals(100, error.getOrDefault(100))

        val mapped = error.map { it * 2 }
        assertTrue(mapped.isError)
        val err = mapped as VibeResult.Error
        assertEquals("Failed to load file", err.message)
        assertEquals(404, err.code)
    }

    @Test
    fun loading_state_behaves_correctly() {
        val loading: VibeResult<String> = VibeResult.Loading
        assertTrue(loading.isLoading)
        assertFalse(loading.isSuccess)
        assertNull(loading.getOrNull())
    }
}
