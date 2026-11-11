## 2026-11-11T15:28:18Z
You are Worker 2 (Implementation Engineer for Milestone 2).
Working directory: d:\github\vibespace
Your state folder: d:\github\vibespace\.agents\teamwork\teamwork_worker_m2\
Original request path: d:\github\vibespace\.agents\teamwork\ORIGINAL_REQUEST.md
Scope / Project path: d:\github\vibespace\.agents\teamwork\orchestrator\PROJECT.md
Explorer 2 findings path: d:\github\vibespace\.agents\teamwork\teamwork_explorer_survey_2\handoff.md
Explorer 3 findings path: d:\github\vibespace\.agents\teamwork\teamwork_explorer_survey_3\handoff.md

You MUST read d:\github\vibespace\.agents\teamwork\ORIGINAL_REQUEST.md and the Explorer reports before writing any code.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Scope & Write Ownership:
You exclusively own these 6 files:
1. `core/ai/src/main/java/com/vibespace/core/ai/provider/AIProvider.kt`
2. `feature/explorer/src/main/java/com/vibespace/feature/explorer/git/GitSourceControlPanel.kt`
3. `core/runner/src/main/java/com/vibespace/core/runner/termux/TermuxBridgeRunner.kt`
4. `core/runner/src/main/java/com/vibespace/core/runner/embedded/EmbeddedInstantRunner.kt`
5. `core/runner/src/test/java/com/vibespace/core/runner/embedded/EmbeddedInstantRunnerTest.kt`
6. `feature/editor/src/main/java/com/vibespace/feature/editor/SoraEditorCanvas.kt`

Tasks:
1. `AIProvider.kt`:
   In `AIProviderRegistry.register(provider)`:
   Replace `providers["gemini"]!!` with safe null checks:
   ```kotlin
   providers[provider.providerId] = provider
   val currentActive = activeProviderId?.let { providers[it] }
   if (currentActive == null || (!currentActive.isConfigured && provider.isConfigured)) {
       activeProviderId = provider.providerId
   }
   ```
   Verify with `.\gradlew.bat :core:ai:testDebugUnitTest`.

2. `GitSourceControlPanel.kt`:
   Around line 450, replace `uiState.activeDiff!!` with safe `uiState.activeDiff?.let { diff -> ... }` matching the Dialog pattern on line 458.
   Verify with `.\gradlew.bat :feature:explorer:compileDebugKotlin`.

3. `TermuxBridgeRunner.kt`:
   Around line 122, remove `if (context != null)` dead check and dead `else` block; call `termuxContext.startService(intent)` directly.
   Verify with `.\gradlew.bat :core:runner:compileDebugKotlin --rerun-tasks` (confirm 0 compiler warnings on line 122).

4. `EmbeddedInstantRunner.kt` & `EmbeddedInstantRunnerTest.kt`:
   - In `runPython`: Detect `-m` (e.g. `python -m pip install requests` or `python -m unittest`). Output clear error explaining that running Python modules or package management requires a full runtime, and guide the user to switch runner mode to 'Alpine Linux' or 'Termux' using the runner menu in the terminal toolbar. Return exit code 1.
   - In `runPython` and `runJavaScript`: Support version flags (`-V`, `--version`, `-v`) returning version string and exit code 0.
   - In `runShell`: Intercept package managers (`pip`, `pip3`, `npm`, `npx`, `yarn`, `pnpm`, `cargo`, `gem`, `go`, `apk`, `apt`). Emit clear error indicating the package manager is unavailable in Instant Evaluator and guide user to 'Alpine Linux' or 'Termux'. Return exit code 127.
   - In `EmbeddedInstantRunnerTest.kt`: Add comprehensive unit tests covering `python -m pip install requests`, `pip install requests`, `npm install`, and version flags.
   - Verify with `.\gradlew.bat :core:runner:testDebugUnitTest`.

5. `SoraEditorCanvas.kt`:
   Inspect line 149. If there is a missing argument or compilation error (such as `editorLanguage`), inspect the signature and supply the appropriate argument/fallback so `:feature:editor` compiles cleanly!
   Verify with `.\gradlew.bat :feature:editor:compileDebugKotlin :feature:editor:testDebugUnitTest`.

Document all modified files, diffs, and verification commands/results in:
`d:\github\vibespace\.agents\teamwork\teamwork_worker_m2\handoff.md`
Send a completion message back when done.
