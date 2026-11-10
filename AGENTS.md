# AGENTS.md — Instructions for AI Coding Agents & Collaborators

## 1. Product Vision & Mission
**VibeSpace** is a native Android coding IDE inspired by modern desktop environments (such as VS Code), tailored specifically for mobile touchscreens, tablets, foldables, and Samsung DeX.

**Fundamental Mandate**: VibeSpace is **NOT** a WebView wrapper around a web page. Every component is built natively using Jetpack Compose, Material 3, and idiomatic Kotlin.

---

## 2. Technology Stack & Toolchain
- **Language**: Kotlin 2.0.21
- **UI Framework**: Jetpack Compose with Material 3 (BOM 2024.10.01)
- **Build System**: Gradle 8.10.2 with Kotlin DSL (`*.gradle.kts`)
- **Dependency Management**: Gradle Version Catalog (`gradle/libs.versions.toml`)
- **Architecture**: Clean Architecture + MVVM + Unidirectional Data Flow (UDF)
- **Async & Concurrency**: Kotlin Coroutines & Flow with injected `DispatcherProvider`
- **Navigation**: AndroidX Navigation Compose
- **Target Platform**: Android API 26 (minSdk) to API 35 (compileSdk/targetSdk)
- **Continuous Integration**: GitHub Cloud Actions (`.github/workflows/android-ci.yml`)

---

## 3. Modular Architecture & Directory Map

```
VibeSpace/
├── app/                  # Application root, MainActivity, navigation host, service container
├── core/
│   ├── common/           # Result wrapper, DispatcherProvider, VibeLogger, IdGenerator
│   ├── designsystem/     # Dark-first theme, colors, typography, shapes, reusable UI components
│   ├── workspace/        # Project & VirtualFile models, SAF URI abstraction, WorkspaceManager
│   ├── language/         # LanguageDetector, LSP contracts, Tree-sitter contracts, diagnostics
│   ├── editor/           # EditorEngine contract, EditorDocument, undo/redo, text operations
│   ├── runner/           # RunnerManager, ExternalRunner, ProcessExecution, Termux contract
│   ├── terminal/         # TerminalService, TerminalSession, safe command interpreter
│   └── ai/               # AIProvider, AIRequest/Response, CodeContext, diff models
├── feature/
│   ├── home/             # Home dashboard, recent projects, quick actions, new project dialog
│   ├── explorer/         # File tree view, directory expand/collapse, file actions
│   ├── editor/           # Multi-tab editor workspace, canvas with line numbers, toolbar, status bar
│   ├── terminal/         # Bottom collapsible panel (Terminal, Output, Problems, Console)
│   ├── aichat/           # AI Assistant chat panel with active code context
│   ├── search/           # Search and replace overlay with regex and match count
│   └── settings/         # Theme selection, font size slider, editor preferences, persistence
├── docs/                 # ARCHITECTURE.md, API_CONTRACTS.md, MODULE_OWNERS.md, etc.
└── .github/              # Workflows, Issue templates, PR template
```

---

## 4. Module Responsibilities & Team Boundaries

| Engineer | Title | Primary Modules |
| :--- | :--- | :--- |
| **Developer 1** | Project Lead & Architect | `:app`, `:core:common`, `:core:ai`, `:feature:aichat` |
| **Developer 2** | Editor Engineer | `:core:editor`, `:feature:editor` |
| **Developer 3** | UI/UX Engineer | `:core:designsystem`, `:feature:home`, `:feature:settings` |
| **Developer 4** | Execution Engineer | `:core:runner`, `:core:terminal`, `:feature:terminal` |
| **Developer 5** | Language Intelligence Engineer | `:core:language`, `:feature:search` |

---

## 5. Architectural & Dependency Rules

1. **No Circular Dependencies**: Dependencies flow strictly downward from `app` -> `feature` -> `core` -> `common`.
2. **Feature Isolation**: Feature modules must never depend directly on each other. If `feature:editor` needs to communicate with `feature:terminal`, it must do so via shared Core contracts or top-level navigation in `app`.
3. **No Business Logic in MainActivity**: Keep `MainActivity` thin (less than 50 lines). It only initializes `VibeSpaceTheme` and `VibeNavigation`.
4. **Android File Path Reality**: Never assume files have POSIX paths like `/home/user`. Use `VirtualFile` which abstracts Android Storage Access Framework (`content://`) and app-private files.
5. **Safe Shell Execution**: Never execute arbitrary host shell commands without sandboxing or user consent. Use the Termux `RUN_COMMAND` Intent contract or safe built-in interpreters.
6. **No Fake Functionality**: Never claim autocomplete, execution, or AI responses work if they are hardcoded or simulated. Always return proper errors or status states (e.g., "No AI provider configured" or "Termux not installed").

---

## 6. Coding & Compose Conventions

### Kotlin:
- Use immutable data classes (`val` only).
- Return `VibeResult<T>` for fallible operations.
- Inject `DispatcherProvider` into all ViewModels and services. Never hardcode `Dispatchers.IO` directly.
- Use Kotlin Flow (`StateFlow` / `SharedFlow`) for reactive state.

### Compose:
- Follow standard state hoisting: composables accept state and emit events (`(T) -> Unit`).
- Avoid unnecessary recompositions by using `@Immutable` models and stable types.
- Ensure all interactive elements have a minimum touch target of 44dp-48dp.
- Support both compact smartphone viewports and wide tablet split views.

---

## 7. Testing Standards

- **Unit Tests**: Every core module and ViewModel must have unit test suites in `src/test/`.
- **Deterministic Coroutine Tests**: Use `runTest` and `TestDispatcherProvider`.
- **Zero Flakiness**: Use clean in-memory fakes rather than heavy runtime mocking libraries.

---

## 8. How to Add a New Module

1. Create directory under `core/` or `feature/`.
2. Add module include to `settings.gradle.kts`:
   ```kotlin
   include(":feature:mymodule")
   ```
3. Create `build.gradle.kts` using plugins from `libs.versions.toml`:
   ```kotlin
   plugins {
       alias(libs.plugins.android.library)
       alias(libs.plugins.kotlin.android)
       alias(libs.plugins.compose.compiler)
   }
   ```
4. Define `namespace = "com.vibespace.feature.mymodule"`.
5. Create minimal `AndroidManifest.xml` and `consumer-rules.pro`.
6. Add unit tests and verify build on CI.

---

## 9. How to Modify Shared Public Interfaces

1. Consult `docs/API_CONTRACTS.md`.
2. Propose modifications in an Issue using the `module_task.md` template.
3. Obtain signoff from Developer 1 (Project Lead).
4. Update tests across all affected modules before creating a Pull Request.
