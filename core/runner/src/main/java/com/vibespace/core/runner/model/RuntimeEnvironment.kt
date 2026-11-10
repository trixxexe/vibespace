package com.vibespace.core.runner.model

enum class RuntimeEnvironment(
    val displayName: String,
    val defaultExecutable: String,
    val description: String
) {
    PYTHON("Python", "python3", "Python 3 interpreter via Termux or chroot"),
    NODEJS("Node.js", "node", "JavaScript runtime via Termux or standalone V8"),
    KOTLIN("Kotlin Script", "kotlinc", "Kotlin compiler / runner"),
    JAVA("Java", "java", "OpenJDK runtime"),
    BASH("Bash Shell", "bash", "Linux shell environment via Termux"),
    CUSTOM("Custom Command", "", "User-specified CLI executable")
}
