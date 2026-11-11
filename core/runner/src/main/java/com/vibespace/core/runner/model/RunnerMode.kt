package com.vibespace.core.runner.model

/**
 * Execution options exposed by VibeSpace. Some options may be unavailable in a given build.
 */
enum class RunnerMode(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val description: String
) {
    /** Lightweight in-app evaluators and safe command built-ins; not complete language runtimes. */
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
     * Separate Termux app accessed via its RUN_COMMAND intent contract.
     * Availability and project-file access depend on the device's Termux configuration.
     */
    TERMUX(
        id = "termux",
        displayName = "Termux Bridge",
        shortBadge = "Termux Bridge",
        description = "Runs in Termux separately. Requires RUN_COMMAND permission; VibeSpace project files are not shared."
    )
}
