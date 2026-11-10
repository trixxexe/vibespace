package com.vibespace.core.runner.termux

/**
 * Technical specification for Termux RUN_COMMAND integration for Developer 4 (Execution Engineer).
 *
 * Requirements:
 * 1. Intent Action: "com.termux.RUN_COMMAND"
 * 2. Target Component: "com.termux/com.termux.app.RunCommandService"
 * 3. Permission: "com.termux.permission.RUN_COMMAND" (requires user granting permission in Android settings)
 * 4. Extras:
 *    - "com.termux.RUN_COMMAND_PATH": Absolute path to executable script or binary (e.g., "/data/data/com.termux/files/usr/bin/bash")
 *    - "com.termux.RUN_COMMAND_ARGUMENTS": String array of arguments
 *    - "com.termux.RUN_COMMAND_WORKDIR": Working directory path
 *    - "com.termux.RUN_COMMAND_BACKGROUND": Boolean (true to run in background, false for interactive terminal)
 *    - "com.termux.RUN_COMMAND_SESSION_ACTION": Action (0 = open termux, 1 = don't open)
 *
 * Notice: On Android 10+, apps cannot execute arbitrary binaries in app data directories directly due to W^X restriction.
 * Termux integration allows native Linux compilation (Python, GCC, Node.js) via Termux's execution environment.
 */
object TermuxContract {
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
}
