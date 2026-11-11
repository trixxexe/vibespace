# Project: VibeSpace Codebase Hardening & Green CI

## Architecture
- Clean Architecture + MVVM + Jetpack Compose
- Downward dependency flow: `app` -> `feature` -> `core` -> `common`
- Core Modules:
  - `:core:common`: Utility models and dispatchers
  - `:core:ai`: AI providers (Gemini, OpenAI, Anthropic Claude, DeepSeek, Groq)
  - `:core:workspace`: SAF VirtualFile, JGit integration
  - `:core:runner`: Process execution, Alpine PRoot with resilient mirror fallbacks, Termux bridge, Instant evaluator
  - `:core:terminal`: Terminal service with built-in POSIX commands (ls, cat, mkdir, touch, rm, mv, cp, bash, sh, uname, env, which, head, tail, wc)
- Feature Modules:
  - `:feature:search`: RE2/J regex search engine
  - `:feature:editor`: Sora editor canvas with visible contrast text rendering, multi-tab workspace, ViewModel
  - `:feature:explorer`: File tree view with Rename, Duplicate, Copy Path, Git source control panel
  - `:feature:terminal`: Terminal PTY, runner switcher, interactive console
  - `:feature:settings`: Expanded editor preferences (tab size, font family, auto-save, word wrap, line numbers, cursor style)
  - `:feature:aichat`: Multi-provider BYOK AI assistant

## Feature Inventory
| # | Feature / Issue | Description | Milestone | Source |
|---|-----------------|-------------|-----------|--------|
| 1 | RE2 Regex Search Bounds | Fix `RegexSearchTest.kt` end-of-file zero-width range assertion `3..2` matching RE2 linear-time semantics | M1 (DONE) | Item 1 |
| 2 | JGit Host Config Isolation | Isolate `JGitWorkspaceManager` from host `~/.gitconfig` using `config.getNames("user", false)` without environment leakage | M1 (DONE) | Item 2 |
| 3 | AIProvider Null Safety | Replace `providers["gemini"]!!` in `AIProviderRegistry.register()` with safe null checks per AGENTS.md Rule 6 | M2 (DONE) | Item 3 |
| 4 | GitSourceControlPanel Compose Safety | Replace `uiState.activeDiff!!` with `uiState.activeDiff?.let { diff -> ... }` in `GitSourceControlPanel.kt` | M2 (DONE) | Item 4 |
| 5 | TermuxBridgeRunner Dead Condition | Eliminate `if (context != null)` dead condition warning and dead code in `TermuxBridgeRunner.kt:122` | M2 (DONE) | Item 5 |
| 6 | Editor Coroutines Test Warnings | Add `@OptIn(ExperimentalCoroutinesApi::class)` to `EditorViewModelTest.kt` to eliminate compiler warnings | M1 (DONE) | Item 6 |
| 7 | Instant Runner UX & Module Handling | Add `-m` and package manager (`pip`, `npm`, etc.) interception and clear error messages guiding user to Alpine/Termux; add tests | M2 (DONE) | Item 7 |
| 8 | Editor Canvas Language Compatibility | Verify and fix any compilation issues in `:feature:editor` (e.g. `SoraEditorCanvas.kt`) to ensure clean compilation | M2 (DONE) | Item 8 |
| 9 | Terminal Collector Cancellation | Cancel interactive terminal collector job in `TerminalViewModel.kt` on runner mode switch | M3 | M2 Review |
| 10 | SoraEditorCanvas Text Visibility | Fix text rendering, color contrast, and recomposition text preservation in `SoraEditorCanvas.kt` | M3 | User Feedback 1 |
| 11 | Alpine Rootfs Download & Fallback URLs | Fix Alpine URL path (`v3.24` vs `v3.24.0`), follow redirects, add mirror fallbacks and retry logic | M3 | User Feedback 2 |
| 12 | Built-in Terminal Shell Commands | Add `ls`, `cat`, `mkdir`, `touch`, `rm`, `mv`, `cp`, `bash`, `sh`, `uname`, `env`, `which`, `head`, `tail`, `wc` to `DefaultTerminalService` | M3 | User Feedback 3 |
| 13 | File & Folder Actions in Explorer | Add Rename dialog, Duplicate, and Copy Path to `ExplorerViewModel` and `ExplorerScreen` | M3 | User Feedback 4 |
| 14 | Expanded Mobile Settings | Add Tab size (2/4/8), Font family, Auto-save interval, Word wrap, Cursor style, Line numbers, Format on save | M3 | User Feedback 5 |
| 15 | Multi-Provider BYOK AI Expansion | Add Anthropic Claude, DeepSeek, and Groq provider presets to BYOK AI configuration | M3 | User Feedback 6 |
| 16 | Git Commit & Push to origin/main | Clean untracked root dirs (`com/`, `org/`), stage all fixes, commit and push to remote `origin/main` for GitHub Actions CI | M3 | User Feedback 7 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Search, Git & Coroutines Test Hardening | Fix Items 1, 2, 6 (`RegexSearchTest`, `JGitWorkspaceManager`, `EditorViewModelTest`) | none | DONE |
| M2 | Code Quality, Null Safety & Runner UX Hardening | Fix Items 3, 4, 5, 7, 8 (`AIProvider`, `GitSourceControlPanel`, `TermuxBridgeRunner`, `EmbeddedInstantRunner`, `SoraEditorCanvas`) | M1 | DONE |
| M3 | Mobile IDE Features & CI Push | Fix Items 9-16 (Editor visibility, Alpine mirrors, Built-in shell, File actions, Settings, BYOK AI, terminal collector, Git push) | M1, M2 | IN_PROGRESS |
