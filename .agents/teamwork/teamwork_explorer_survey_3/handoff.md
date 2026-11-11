# Investigation & Survey Report: Execution Runners (Item 7) & CI/Git Environment (Items 8 & 9)

**Author**: Explorer 3 (Runner & CI Environment Explorer)  
**Date**: 2026-11-11  
**State Folder**: `d:\github\vibespace\.agents\teamwork\teamwork_explorer_survey_3\`  

---

## 1. Observation

### 1.1 Execution Runner Architecture (`core:runner`, `core:terminal`, `feature:terminal`)
Direct inspection of the codebase reveals the following structure:
- **`core/runner/src/main/java/com/vibespace/core/runner/manager/ExternalRunner.kt`**:
  Defines the plug-in contract:
  ```kotlin
  interface ExternalRunner {
      val runnerId: String
      val name: String
      fun isAvailable(): Boolean
      suspend fun execute(command: String, workingDirectory: String, environmentVariables: Map<String, String> = emptyMap()): VibeResult<ProcessExecution>
      suspend fun stop(executionId: String): VibeResult<Unit>
      fun observeOutput(executionId: String): Flow<ProcessOutput>
  }
  ```
- **`core/runner/src/main/java/com/vibespace/core/runner/model/RunnerMode.kt`**:
  Enumerates the three runner modes:
  - `EMBEDDED` ("Instant Script Evaluator", shortBadge = "Instant", ID = "embedded")
  - `ALPINE` ("Alpine Linux", shortBadge = "Alpine", ID = "alpine")
  - `TERMUX` ("Termux Bridge", shortBadge = "Termux Bridge", ID = "termux")
- **`core/runner/src/main/java/com/vibespace/core/runner/manager/DefaultRunnerManager.kt`**:
  Initializes all three runners (lines 74-79):
  ```kotlin
  init {
      registerRunner(EmbeddedInstantRunner(workspaceManager = workspaceManager))
      registerRunner(alpineRunner ?: AlpineLinuxRunner())
      registerRunner(termuxRunner ?: TermuxBridgeRunner())
  }
  ```
  `DefaultRunnerManager` maintains `_activeRunnerMode` (defaults to `RunnerMode.EMBEDDED`), stages Storage Access Framework (SAF) workspaces into temporary POSIX directories for PRoot execution (`prepareSafWorkspace`), and coordinates interactive PTY terminals via `startInteractiveTerminal`, `sendTerminalInput`, `interruptTerminalCommand`, and `closeInteractiveTerminal`.
- **`feature/terminal/src/main/java/com/vibespace/feature/terminal/TerminalViewModel.kt`**:
  Coordinates terminal sessions and the active runner.
  - `switchRunnerMode(mode: RunnerMode)` (lines 108-114): Updates runner mode and initializes or destroys interactive PTY terminal.
  - `executeCommand(command: String, preferredRunnerId: String? = null)` (lines 202-246): Routes input to Alpine PTY if interactive terminal is active, otherwise invokes `runnerManager.runCommand(...)`.
  - `runActiveDocument(...)` (lines 255-290): Translates active editor file into `python3 '/workspace/...'` or `node '/workspace/...'` and executes via `preferredRunnerId = RunnerMode.ALPINE.id`.
- **`feature/terminal/src/main/java/com/vibespace/feature/terminal/TerminalPanel.kt`**:
  Lines 122-179 show a badge in the tab strip displaying `uiState.activeRunnerMode.shortBadge` ("Instant", "Alpine", "Termux Bridge"). Clicking it opens a `DropdownMenu` with all available runner modes and their descriptions.
  Lines 289-408 show an in-line setup banner when in `ALPINE` mode if Alpine is not yet installed:
  "Prepare Alpine Python and Node runtime - Extracts bundled Python, Node.js, and Alpine tools into app storage" with a one-tap action calling `viewModel.installAlpineSubsystem()`.

### 1.2 The "python -m pip install requests" Bug in Instant Runner
Examining `core/runner/src/main/java/com/vibespace/core/runner/embedded/EmbeddedInstantRunner.kt`:
1. In `execute` (lines 65-68):
   ```kotlin
   trimmed.startsWith("python") || trimmed.endsWith(".py") -> {
       exitCode = runPython(trimmed, workingDirectory, ::emitLine)
   }
   ```
2. In `runPython` (lines 107-114):
   ```kotlin
   emit("VibeSpace Instant Evaluator (Basic Print & Arithmetic)", false)
   emit("Lightweight evaluator only; loops, imports, and packages are not supported.", false)

   val codeOrFile = extractCodeFromCommand(command, "python3", "python")
   if (codeOrFile.isBlank()) {
       emit("Python interactive shell ready. Use 'Run' toolbar button to execute scripts.", false)
       return 0
   }
   ```
3. In `extractCodeFromCommand` (lines 247-260):
   ```kotlin
   val remainder = command.removePrefix(prefix).trim()
   if (remainder.startsWith("-c")) {
       return remainder.removePrefix("-c").trim().trim('"', '\'')
   }
   if (remainder.startsWith("-e")) {
       return remainder.removePrefix("-e").trim().trim('"', '\'')
   }
   return remainder
   ```
   When `command` is `"python -m pip install requests"`, `codeOrFile` becomes `"-m pip install requests"`.
4. In `executePythonSnippet` (lines 300-330):
   ```kotlin
   val firstWord = line.substringBefore('(').substringBefore(' ').trim()
   if (firstWord in listOf("import", "from", "def", "class", "while", "for", "with", "try")) {
       emit("SyntaxError: '$firstWord' statement is unsupported in Instant Evaluator. Switch runner to 'Alpine Linux' or 'Termux' for full language execution.", true)
       throw UnsupportedOperationException("Unsupported construct: $firstWord")
   }
   ...
   // Fallback evaluation
   emit(evaluateExpression(line, variables), false)
   ```
   `firstWord` is `"-m"`. It is NOT caught by the unsupported statement check.
   It does not start with `print(` or `=`.
   It falls through to `evaluateExpression("-m pip install requests", variables)`, which returns `"-m pip install requests"`.
   The line `"-m pip install requests"` is emitted as regular output with `isError = false`.
   `runPython` completes normally and returns exit code `0`.
5. In `runShell` (lines 203-245):
   If the user runs `pip install requests` directly (without prefixing `python`), it executes `runShell`:
   Binary `pip` is not recognized, returning `sh: pip: command not found` (exit code 127) with no explanation or suggestion to switch to Alpine Linux or Termux.
   Similarly, `npm install ...` returns `sh: npm: command not found`.

### 1.3 Dead Condition Compiler Warning in `TermuxBridgeRunner.kt:122`
In `core/runner/src/main/java/com/vibespace/core/runner/termux/TermuxBridgeRunner.kt`:
- Line 68-70:
  ```kotlin
  val termuxContext = context
  if (termuxContext == null || !isAvailable()) {
      ...
      return VibeResult.Success(failed)
  }
  ```
- Line 122:
  ```kotlin
  if (context != null) {
      context.startService(intent)
  ...
  ```
  Since `context == null` causes an early return at line 81, `context != null` at line 122 is always true, producing a compiler warning.

### 1.4 CI Workflow (`.github/workflows/android-ci.yml`)
Inspection of `.github/workflows/android-ci.yml`:
- Trigger branches: `main`, `dev`, `feature/**`, `codex/**` for push; `main`, `dev` for PRs.
- Environment: Ubuntu latest, JDK 17 (`temurin`), Gradle wrapper validation.
- Steps executed:
  1. `./gradlew testDebugUnitTest` (timeout: 5 minutes)
  2. Setup QEMU (`docker/setup-qemu-action@v3`)
  3. `bash .github/scripts/build-bundled-runtime.sh app/build/generated/bundled-runtime`
     Builds Alpine Linux 3.24.0 rootfs + PRoot for 4 architectures (`aarch64`, `armv7`, `x86_64`, `x86`) bundling `python3`, `py3-pip`, `nodejs`, `npm`, `util-linux-misc`, and `ca-certificates`.
  4. `./gradlew lintDebug`
  5. `reactivecircus/android-emulator-runner@v2` on API 35 x86_64: `./gradlew connectedDebugAndroidTest`
  6. `./gradlew assembleDebug`
  7. Enforce APK size budget: <= 700 MiB (`limit=$((700 * 1024 * 1024))`)
  8. Upload debug APK artifact and unit test reports.

### 1.5 Git Status, Remote, Branch
Command: `git status; git remote -v; git branch -a`
- Current branch: `main` (up to date with `origin/main`).
- Remote origin: `https://github.com/trixxexe/vibespace.git` (fetch and push).
- Working tree:
  - Modified: `gradle.properties` (added `org.gradle.tooling.parallel=true`).
  - Untracked: `.agents/`.

### 1.6 Current Test Execution Observations
Running `.\gradlew.bat testDebugUnitTest`:
- `:core:runner:testDebugUnitTest`: PASS
- `:core:terminal:testDebugUnitTest`: PASS
- `:feature:terminal:testDebugUnitTest`: PASS
- Failure 1: `:core:workspace:testDebugUnitTest` -> `JGitWorkspaceManagerTest.repositoryStagesAndCommitsWithExplicitIdentity FAILED` (assertion failure due to host git user config inheritance).
- Failure 2: `:feature:editor:compileDebugKotlin` -> `SoraEditorCanvas.kt:149:66 No value passed for parameter 'editorLanguage'`.

---

## 2. Logic Chain

1. **User Observation Correlation**:
   The user reported an image showing:
   ```
   [VibeSpace Instant Runner] Executing: python -m pip install requests
   VibeSpace Instant Evaluator (Basic Print & Arithmetic)
   Lightweight evaluator only; loops, imports, and packages are not supported.
   -m pip install requests
   [Process exited with code 0]
   ```
2. **Mechanism Identification**:
   - `EmbeddedInstantRunner.runPython` treats any command starting with `python` by stripping `python` and feeding the remainder into `executePythonSnippet`.
   - `executePythonSnippet` only checks for keywords like `import`, `def`, `class`, `for`, `while`. It has no knowledge of CLI flags like `-m`.
   - `-m pip install requests` falls through to expression evaluation, which simply prints the string `-m pip install requests` and returns exit code 0.
   - This misleads the user: it looks like pip succeeded or was echoed without error, even though no package was installed.
3. **Runner Mode Selection Analysis**:
   - The user defaults to `RunnerMode.EMBEDDED` ("Instant").
   - VibeSpace has a full bundled PRoot Alpine Linux subsystem (`RunnerMode.ALPINE`) capable of running `python3`, `pip`, `node`, `npm`, and `pylsp`.
   - VibeSpace also has `RunnerMode.TERMUX` for dispatching commands to an external Termux app via `RUN_COMMAND`.
   - When a user enters package manager commands (`pip`, `pip3`, `npm`, `npx`, `yarn`, `apk`, `apt`) or module executions (`python -m ...`) in `EmbeddedInstantRunner`, there is no guidance explaining that:
     a) Instant Evaluator cannot run package managers or modules;
     b) The user can switch to "Alpine Linux" or "Termux Bridge" via the runner switcher badge in the terminal header;
     c) The command must fail with a non-zero exit code (1 or 127).
4. **Compiler Warning in Termux Runner**:
   - `TermuxBridgeRunner.kt:122` checks `if (context != null)`, but line 68 has already verified `context != null` and returned if null. The redundant check triggers a dead code warning.
5. **CI Readiness**:
   - CI runs `./gradlew testDebugUnitTest` before everything else.
   - Currently, tests are blocked by `JGitWorkspaceManagerTest` and `SoraEditorCanvas.kt` compilation error. Once fixed, all tests pass.

---

## 3. Caveats

- `AlpineLinuxRunner` interactive PTY terminal requires `util-linux-misc` (`script`) to be installed in the Alpine rootfs. On initial setup without network, it relies on the bundled runtime archive (`assets/runtime/$architecture.tar.gz`).
- `TermuxBridgeRunner` relies on Android inter-process communication via `com.termux.RUN_COMMAND` intent. It cannot synchronously capture stdout/stderr or access private app files; it is fire-and-forget.
- CI pipeline requires Docker and QEMU to execute `.github/scripts/build-bundled-runtime.sh` when generating multi-arch runtime tarballs on Linux runners.

---

## 4. Conclusion & Actionable Recommendations

### Recommendation 1: Fix `EmbeddedInstantRunner.kt` to Handle Package & Module Commands Accurately
1. In `runPython`:
   Detect `-m` flags explicitly:
   ```kotlin
   if (codeOrFile.startsWith("-m")) {
       val module = codeOrFile.removePrefix("-m").trim().substringBefore(' ')
       emit("Error: Running Python modules ('-m $module') is unsupported in Instant Evaluator.", true)
       emit("Package management and external modules require a full runtime. Switch runner to 'Alpine Linux' or 'Termux' using the runner menu in the terminal toolbar.", true)
       return 1
   }
   ```
2. In `runPython` & `runJavaScript`:
   Handle version flags cleanly (`-V`, `--version`, `-v`):
   ```kotlin
   if (codeOrFile in listOf("-V", "--version", "-v")) {
       emit("Python 3.12 (VibeSpace Instant Evaluator)", false)
       return 0
   }
   ```
3. In `runShell`:
   Add dedicated handling for common package managers (`pip`, `pip3`, `npm`, `npx`, `yarn`, `pnpm`, `cargo`, `gem`, `go`, `apk`, `apt`):
   ```kotlin
   "pip", "pip3" -> {
       emit("Error: 'pip' package manager is not available in Instant Evaluator.", true)
       emit("To install and use packages, switch runner to 'Alpine Linux' or 'Termux' using the runner menu in the terminal toolbar.", true)
       127
   }
   "npm", "npx", "yarn", "pnpm" -> {
       emit("Error: '$binary' package manager is not available in Instant Evaluator.", true)
       emit("To manage Node packages, switch runner to 'Alpine Linux' or 'Termux' using the runner menu in the terminal toolbar.", true)
       127
   }
   ```

### Recommendation 2: Remove Dead Code Warning in `TermuxBridgeRunner.kt:122`
Replace `if (context != null) { context.startService(intent) ... }` with direct execution on `termuxContext.startService(intent)`.

### Recommendation 3: Add Unit Tests in `EmbeddedInstantRunnerTest.kt`
Add tests asserting that:
- `python -m pip install requests` fails with exit code 1 and outputs error explaining that module execution / pip requires Alpine or Termux.
- `pip install requests` fails with exit code 127 and outputs actionable guidance.
- `npm install ...` fails with exit code 127 and outputs actionable guidance.
- `python --version` outputs evaluator version info and exits 0.

### Recommendation 4: CI & Git Workflow Alignment
- Ensure all 9 identified codebase items (including `SoraEditorCanvas.kt` and `JGitWorkspaceManagerTest.kt`) are repaired.
- Run `.\gradlew.bat testDebugUnitTest` to confirm 100% test pass rate.
- Stage changes, commit with clean semantic message, and push to `origin/main`.

---

## 5. Verification Method

To independently verify all findings and validate fixes:
1. **Verify Runner Detection**:
   Inspect `core/runner/src/main/java/com/vibespace/core/runner/embedded/EmbeddedInstantRunner.kt` lines 107-144, 247-260, 300-330.
2. **Execute Unit Tests**:
   ```powershell
   .\gradlew.bat :core:runner:testDebugUnitTest
   .\gradlew.bat :feature:terminal:testDebugUnitTest
   ```
3. **Run Full Test Suite**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
4. **Git Remote & Status**:
   ```powershell
   git status
   git remote -v
   git branch
   ```
