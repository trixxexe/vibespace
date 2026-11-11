# Handoff Report — Test Suite Investigation (Items 1, 2, and 6)

## 1. Observation

### Item 1: `RegexSearch` & `RegexSearchTest.kt`
- **File Locations**:
  - Implementation: `feature/search/src/main/java/com/vibespace/feature/search/RegexSearch.kt`
  - Unit Test: `feature/search/src/test/java/com/vibespace/feature/search/RegexSearchTest.kt`
  - Consumer: `feature/search/src/main/java/com/vibespace/feature/search/SearchViewModel.kt:77, 172`
- **Regex Engine**:
  - Engine used: `com.google.re2j.Pattern` (RE2/J version 1.8 from `gradle/libs.versions.toml: libs.re2j`).
- **Git History & Root Cause**:
  - In commit `98597695f57d9c202d9591c51e5c371ca7f0b7b6`, the test originally contained:
    ```kotlin
    val zeroWidth = RegexSearch.findMatches("aba", Pattern.compile("(?=a)"))
    val endOfFile = RegexSearch.findMatches("abc", Pattern.compile("$"))
    assertEquals(listOf(0..-1, 2..1), zeroWidth)
    assertEquals(listOf(3..2), endOfFile)
    ```
  - Compiling `(?=a)` with RE2/J failed with:
    `com.google.re2j.PatternSyntaxException: error parsing regexp: invalid or unsupported Perl syntax: '(?='`
  - RE2/J is a linear-time ($O(n)$) regular expression engine based on finite automata (NFA/DFA). It deliberately omits lookaround assertions (`(?=...)`, `(?!...)`, `(?<=...)`, `(?<!...)`) and backreferences to guarantee linear-time execution and prevent catastrophic backtracking (ReDoS), which is critical for mobile responsiveness.
  - In commit `cf1507b7ba2ba00151a7a21913499b8954fab342` ("test: use supported RE2 zero-width search patterns"), the test was changed to:
    ```kotlin
    val zeroWidth = RegexSearch.findMatches("abc", Pattern.compile("^|$"))
    val endOfFile = RegexSearch.findMatches("abc", Pattern.compile("$"))
    assertEquals(listOf(0..-1, 2..1), zeroWidth)
    assertEquals(listOf(3..2), endOfFile)
    ```
  - Observation on bounds: On string `"abc"`, `^|$` matches at index 0 (start=0, end=0 -> `0..-1`) and index 3 (start=3, end=3 -> `3..2`). While Kotlin's `IntRange.equals()` evaluates all empty ranges as equal (meaning `2..1 == 3..2` evaluates to `true`), the semantically accurate expected range at the end of `"abc"` (length 3) is `3..2`, identical to the `endOfFile` assertion.

---

### Item 2: `JGitWorkspaceManagerTest.kt` & Host Git Config Leakage
- **File Locations**:
  - Implementation: `core/workspace/src/main/java/com/vibespace/core/workspace/git/JGitWorkspaceManager.kt`
  - Unit Test: `core/workspace/src/test/java/com/vibespace/core/workspace/JGitWorkspaceManagerTest.kt`
- **Verbatim Error**:
  Running `.\gradlew.bat :core:workspace:testDebugUnitTest --tests "com.vibespace.core.workspace.JGitWorkspaceManagerTest"` on a machine with host global git credentials (`git config --global user.name` set) fails:
  ```
  JGitWorkspaceManagerTest > repositoryStagesAndCommitsWithExplicitIdentity FAILED
      java.lang.AssertionError
          at org.junit.Assert.fail(Assert.java:87)
          at org.junit.Assert.assertTrue(Assert.java:42)
          at org.junit.Assert.assertTrue(Assert.java:53)
          at com.vibespace.core.workspace.JGitWorkspaceManagerTest$repositoryStagesAndCommitsWithExplicitIdentity$1.invokeSuspend(JGitWorkspaceManagerTest.kt:40)
  ```
- **Observed Code**:
  In `JGitWorkspaceManagerTest.kt:38-43`:
  ```kotlin
  assertTrue(git.stageAll().isSuccess)
  val withoutIdentity = git.commit("Add source")
  assertTrue(withoutIdentity is VibeResult.Error) // Line 40: FAILS
  assertTrue(git.configureIdentity("Test Author", "author@example.test").isSuccess)
  assertTrue(git.commit("Add source") is VibeResult.Success)
  ```
  In `JGitWorkspaceManager.kt:289-294`:
  ```kotlin
  val config = ctx.repo.config
  val authorName = config.getString("user", null, "name")?.trim().orEmpty()
  val authorEmail = config.getString("user", null, "email")?.trim().orEmpty()
  if (authorName.isEmpty() || authorEmail.isEmpty()) {
      return@withRepository VibeResult.Error("Configure your Git author name and email before creating a commit.")
  }
  ```
  And in `JGitWorkspaceManager.kt:218-219`:
  ```kotlin
  authorName = ctx.repo.config.getString("user", null, "name"),
  authorEmail = ctx.repo.config.getString("user", null, "email"),
  ```
  And in `JGitWorkspaceManager.kt:68`:
  ```kotlin
  FileRepositoryBuilder().setGitDir(gitDir).setWorkTree(dir).readEnvironment().findGitDir().build()
  ```
- **Bytecode and Source Inspection**:
  In JGit (`org.eclipse.jgit.internal.storage.file.FileRepository`), `repoConfig` is constructed with `userConfig` (representing `~/.gitconfig`) as its `baseConfig`.
  When `config.getString("user", null, "name")` is invoked, JGit first checks `.git/config`. If not found, it falls back to `baseConfig` (`~/.gitconfig` or system git config).
  Consequently, on any machine with global git configured, `authorName` resolves to the host machine's git user, and `commit` succeeds instead of returning `VibeResult.Error`.
  In contrast, `config.getNames("user", false)` returns only section keys defined directly in `.git/config` without querying `baseConfig`.

---

### Item 6: Coroutines Test Warnings in `EditorViewModelTest.kt:218, 224`
- **File Locations**:
  - Unit Test: `feature/editor/src/test/java/com/vibespace/feature/editor/EditorViewModelTest.kt`
  - Implementation: `feature/editor/src/main/java/com/vibespace/feature/editor/EditorViewModel.kt:253-290`
- **Observed Lines**:
  In `EditorViewModelTest.kt:213-227`:
  ```kotlin
  213: val viewModel = EditorViewModel(
  214:     editorEngine = editorEngine,
  215:     workspaceManager = workspaceManager,
  216:     languageDetector = languageDetector,
  217:     lspClientProvider = { fakeLsp },
  218:     dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler))
  219: )
  220: val file = VirtualFile("$root/main.py", "main.py", FileNodeType.FILE, parentUri = root)
  221: 
  222: viewModel.openFile(file)
  223: viewModel.updateCompletions("pri")
  224: advanceUntilIdle()
  225: 
  226: assertTrue(viewModel.uiState.value.completions.any { it.label == "print_from_server" })
  ```
- **Warning Details**:
  In Kotlin Coroutines 1.9.0 (`gradle/libs.versions.toml: coroutines = "1.9.0"`), both `UnconfinedTestDispatcher` and `advanceUntilIdle()` are annotated with `@kotlinx.coroutines.ExperimentalCoroutinesApi`.
  Because neither the test function `completion_suggestions_include_real_lsp_results()` nor the test class `EditorViewModelTest` specifies `@OptIn(ExperimentalCoroutinesApi::class)`, the Kotlin compiler (Kotlin 2.0.21) emits experimental declaration warnings:
  - `(218, 48): This declaration is experimental and its usage should be marked with '@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)'...`
  - `(224, 9): This declaration is experimental and its usage should be marked with '@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)'...`

---

## 2. Logic Chain

### Item 1 (RegexSearch):
1. **Engine Selection**: VibeSpace uses RE2/J (`com.google.re2j`) for code and workspace search to prevent UI lockup and ReDoS attacks on mobile devices.
2. **Grammar Limitation**: RE2/J deliberately does not support lookarounds `(?=...)` because they cannot be matched in linear time with DFAs.
3. **Anchor Zero-Width Support**: RE2/J fully supports boundary anchors `^` and `$`.
4. **Offset Arithmetic**: On input string `"abc"` (indices 0, 1, 2, EOF 3):
   - Anchor `^` matches at start 0, end 0 (`0 until 0` -> `0..-1`).
   - Anchor `$` matches at start 3, end 3 (`3 until 3` -> `3..2`).
5. **Conclusion for Item 1**: The test pattern `^|$` and `$` are the correct RE2-supported patterns for testing zero-width and end-of-file searches. The assertion in `RegexSearchTest.kt` should assert `listOf(0..-1, 3..2)` to accurately reflect the end-of-string match index 3.

### Item 2 (JGit Config Isolation):
1. **Design Requirement**: VibeSpace is a mobile IDE where git user credentials must be configured per repository or managed within VibeSpace.
2. **Current Leak**: `JGitWorkspaceManager` calls `config.getString("user", null, "name")`, which traverses JGit's configuration parent hierarchy and reads host `~/.gitconfig` and system git configurations.
3. **Failure in Tests**: On developer machines with `user.name` set in host git, `git.commit()` succeeds before `git.configureIdentity()` is invoked. The test expects `withoutIdentity is VibeResult.Error`, causing line 40 to fail.
4. **Resolution via Local Keys**: JGit's `Config.getNames(section, subsection, recursive)` with `recursive = false` returns only keys defined directly in `.git/config`. Checking `config.getNames("user", false).contains("name")` guarantees repository-local isolation without host credential pollution. Additionally, stripping `.readEnvironment()` from `FileRepositoryBuilder` prevents environment variable leakage.

### Item 6 (Coroutines Test Warnings):
1. **API Lifecycle**: Coroutine test APIs `UnconfinedTestDispatcher` and `advanceUntilIdle()` in `kotlinx.coroutines.test:1.9.0` remain experimental and require opt-in.
2. **Project Precedent**: `feature/terminal/src/test/java/com/vibespace/feature/terminal/MainDispatcherRule.kt` already annotates with `@OptIn(ExperimentalCoroutinesApi::class)`.
3. **Conclusion for Item 6**: Adding `@OptIn(ExperimentalCoroutinesApi::class)` to `completion_suggestions_include_real_lsp_results()` or `EditorViewModelTest` eliminates the compiler warnings in compliance with Kotlin Coroutines 1.9 best practices.

---

## 3. Caveats
- No caveats. The root cause for all three items was reproduced and verified directly against source code and Gradle test execution.

---

## 4. Conclusion & Concrete Implementation Plan

### Recommended Changes:

#### 1. `feature/search/src/test/java/com/vibespace/feature/search/RegexSearchTest.kt`
Ensure the assertion reflects the exact end-of-file index `3..2`:
```kotlin
<<<<
        val zeroWidth = RegexSearch.findMatches("abc", Pattern.compile("^|$"))
        val endOfFile = RegexSearch.findMatches("abc", Pattern.compile("$"))

        assertEquals(listOf(0..-1, 2..1), zeroWidth)
        assertEquals(listOf(3..2), endOfFile)
====
        val zeroWidth = RegexSearch.findMatches("abc", Pattern.compile("^|$"))
        val endOfFile = RegexSearch.findMatches("abc", Pattern.compile("$"))

        assertEquals(listOf(0..-1, 3..2), zeroWidth)
        assertEquals(listOf(3..2), endOfFile)
>>>>
```

#### 2. `core/workspace/src/main/java/com/vibespace/core/workspace/git/JGitWorkspaceManager.kt`
Isolate git configuration by reading author identity exclusively from repository-local config:
- Add helper method:
```kotlin
private fun localConfigString(config: org.eclipse.jgit.lib.Config, section: String, name: String): String? {
    val names = config.getNames(section, false)
    return if (names.any { it.equals(name, ignoreCase = true) }) {
        config.getString(section, null, name)
    } else null
}
```
- In `prepare()` (line 68), remove `.readEnvironment()`:
```kotlin
val repo = if (gitDir.exists()) {
    FileRepositoryBuilder().setGitDir(gitDir).setWorkTree(dir).findGitDir().build()
} else {
    Git.init().setDirectory(dir).setInitialBranch("main").call().repository
}
```
- In `refreshStatus()` (lines 218-219):
```kotlin
authorName = localConfigString(ctx.repo.config, "user", "name"),
authorEmail = localConfigString(ctx.repo.config, "user", "email"),
```
- In `commit()` (lines 290-297):
```kotlin
val config = ctx.repo.config
val authorName = localConfigString(config, "user", "name")?.trim().orEmpty()
val authorEmail = localConfigString(config, "user", "email")?.trim().orEmpty()
if (authorName.isEmpty() || authorEmail.isEmpty()) {
    return@withRepository VibeResult.Error("Configure your Git author name and email before creating a commit.")
}
config.save()
val commit = Git.wrap(ctx.repo).commit().setMessage(trimmed)
    .setAuthor(PersonIdent(authorName, authorEmail))
    .setCommitter(PersonIdent(authorName, authorEmail)).call()
```

#### 3. `feature/editor/src/test/java/com/vibespace/feature/editor/EditorViewModelTest.kt`
Add `@OptIn(ExperimentalCoroutinesApi::class)`:
- Add import:
```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi
```
- Annotate method (or class):
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@Test
fun completion_suggestions_include_real_lsp_results() = runTest {
    ...
}
```

---

## 5. Verification Method

To independently verify the investigation findings and resulting fixes:

1. **Verify Item 1 (RegexSearch)**:
   ```powershell
   .\gradlew.bat :feature:search:testDebugUnitTest --rerun
   ```
   Both `findsZeroWidthMatchesAndEndOfFileMatchesWithoutLooping` and `replacesZeroWidthAndEndOfFileMatchesSafely` must pass.

2. **Verify Item 2 (JGit Isolation)**:
   Ensure host git user identity is configured (`git config --global user.name` is non-empty), then run:
   ```powershell
   .\gradlew.bat :core:workspace:testDebugUnitTest --tests "com.vibespace.core.workspace.JGitWorkspaceManagerTest" --rerun
   ```
   The test will pass with local configuration isolation, confirming that `git.commit()` correctly returns `VibeResult.Error` prior to `configureIdentity()`.

3. **Verify Item 6 (Coroutines Warnings)**:
   ```powershell
   .\gradlew.bat :feature:editor:compileDebugUnitTestKotlin --warning-mode all
   ```
   Inspect compiler output to verify zero experimental or deprecation warnings on lines 218 and 224 of `EditorViewModelTest.kt`.
