package com.vibespace.core.ai.model

/**
 * User-supplied configuration for the BYOK (Bring Your Own Key) AI assistant.
 */
data class AIConfiguration(
    val providerId: String = "gemini",
    val apiKey: String = "",
    val model: String = "gemini-3.8-flash",
    val customEndpoint: String = ""
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}
