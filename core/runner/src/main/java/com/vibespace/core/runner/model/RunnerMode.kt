package com.vibespace.core.runner.model

/**
 * Execution modes supported by VibeSpace's Tri-Mode Runner Architecture.
 */
enum class RunnerMode(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val description: String
) {
    /**
     * Out-of-the-box instant offline script evaluator for JavaScript, Python, Web/HTML, and Shell.
     * Zero setup, zero download required for lightweight scripts and logic.
     */
    EMBEDDED(
        id = "embedded",
        displayName = "Instant Script Evaluator",
        shortBadge = "⚡ Instant",
        description = "Lightweight offline script evaluator for Python, JS, and Shell builtins. Zero download."
    ),

    /**
     * In-app Alpine Linux container running via PRoot.
     * Supports real apk, pip install, and npm install directly inside VibeSpace.
     */
    ALPINE(
        id = "alpine",
        displayName = "Alpine Linux Subsystem",
        shortBadge = "🐧 Alpine",
        description = "In-app Linux container. Supports pip install, npm install, and apk packages."
    ),

    /**
     * Native Android Termux environment accessed via RUN_COMMAND intent bridge.
     * For power users requiring hardware-accelerated compilers and tools.
     */
    TERMUX(
        id = "termux",
        displayName = "Termux Bridge",
        shortBadge = "🚀 Termux",
        description = "External Termux bridge. Hardware-accelerated native Android environment."
    )
}
