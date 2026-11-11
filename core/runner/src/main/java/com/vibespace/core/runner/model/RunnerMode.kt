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
        shortBadge = "Instant",
        description = "Lightweight offline script evaluator for Python, JS, and Shell builtins. Zero download."
    ),

    /** In-app Alpine execution is available only in builds that package a compatible PRoot runtime. */
    ALPINE(
        id = "alpine",
        displayName = "Alpine (Unavailable)",
        shortBadge = "Unavailable",
        description = "This build does not include PRoot. Use Embedded or Termux execution."
    ),

    /**
     * Native Android Termux environment accessed via RUN_COMMAND intent bridge.
     * For power users requiring hardware-accelerated compilers and tools.
     */
    TERMUX(
        id = "termux",
        displayName = "Termux Bridge",
        shortBadge = "Termux Bridge",
        description = "Runs in Termux separately. Requires RUN_COMMAND permission; VibeSpace project files are not shared."
    )
}
