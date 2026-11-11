# VibeSpace

> **Native Android coding workspace**
> *Inspired by VS Code — Built natively with Jetpack Compose & Kotlin*

[![Android CI](https://github.com/trixxexe/vibespace/actions/workflows/android-ci.yml/badge.svg)](https://github.com/trixxexe/vibespace/actions/workflows/android-ci.yml)
[![Version](https://img.shields.io/badge/version-0.1.0--alpha-cyan.svg)](https://github.com/trixxexe/vibespace/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10.01-green.svg)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-1.3.1-blue.svg)](https://m3.material.io)

---

## Overview

**VibeSpace** is an open-source, native Android coding workspace for editing project files on phones, tablets, and foldables.

The app includes multi-tab editing, SAF-backed workspaces, workspace-wide regex search, local JGit repositories with optional HTTP(S) remotes, an Alpine Linux userspace runner, and an optional BYOK AI assistant. PRoot and Alpine are downloaded on first setup for supported Android ABIs. Python and TypeScript language servers can be installed into Alpine from its package managers; they are not bundled in the APK. The terminal accepts shell commands and persists working directory and exported variables per session, but does not provide a PTY, job control, or interactive terminal applications.

---

## Key Features in v0.1.0

- **Multi-Tab Code Editor**: Full tab switching, dirty state tracking (`*`), line number gutters, undo/redo history stacks, and search/replace.
- **Dark-First Developer Aesthetic**: Deep charcoal themes (`#0F1115`), subtle borders, monospace code typography, and full light theme support.
- **Adaptive Layouts**:
  - **Phone (< 600dp)**: Editor with an explorer drawer, expandable runner panel, and AI chat bottom sheet.
  - **Tablet (> 840dp)**: Navigation rail, explorer or local change panel, resizable editor and runner panel, and optional AI side panel.
- **Project Explorer**: Virtual file tree supporting folder navigation, file creation, deletion, and Storage Access Framework (SAF) URI abstraction.
- **Runner Panel**: Collapsible panel for embedded evaluation, optional Alpine Linux command execution, or the Termux bridge, with output and diagnostics.
- **Git Source Control**: Local Git repositories, staging, commits, branches, diffs, and HTTP(S) push/pull. Remote credentials remain in memory for the app process; configure a real commit author identity before committing.
- **Workspace Search**: Recursive search across project files with case sensitivity and linear-time regular expressions. Backreferences are not supported.
- **Language Servers**: Python and TypeScript servers can be installed inside Alpine. They provide LSP completions, diagnostics, hover, definitions, and formatting for filesystem-backed workspaces.
- **AI Coding Assistant**: Chat interface with attached code context from the active file, starter prompts, and diff review models.
- **Settings & Persistence**: Theme mode selection (Dark, Light, System), font size slider (10sp - 22sp), tab size preferences, and local persistence.

---

## Modular Architecture

```
VibeSpace/
├── app/                  # Application bootstrap, navigation graph, service locator
├── core/
│   ├── common/           # Result types, DispatcherProvider, VibeLogger, IdGenerator
│   ├── designsystem/     # Dark-first theme, colors, typography, shapes, UI components
│   ├── workspace/        # Project & VirtualFile models, SAF URI abstraction, WorkspaceManager
│   ├── language/         # LanguageDetector, LSP & Tree-sitter contracts
│   ├── editor/           # EditorEngine contract, EditorDocument, undo/redo stacks
│   ├── runner/           # RunnerManager, ExternalRunner, Termux intent contract
│   ├── terminal/         # TerminalService, TerminalSession, safe command interpreter
│   └── ai/               # AIProvider, AIRequest/Response, CodeContext, diff models
└── feature/
    ├── home/             # Home dashboard, recent projects, quick actions
    ├── explorer/         # File hierarchy tree view, file & folder actions
    ├── editor/           # Multi-tab editor workspace, canvas with line numbers, toolbar
    ├── terminal/         # Bottom collapsible panel (Terminal, Output, Problems, Console)
    ├── aichat/           # AI Assistant chat panel with active code context
    ├── search/           # In-document text search and replace, plus file and command picker
    └── settings/         # Theme selection, font size slider, editor preferences
```

---

## Getting Started

### Prerequisites
- JDK 17+
- Android SDK 35 (minSdk: 26)
- Android Studio Koala / Ladybug or newer

### Building with Gradle
```bash
# Clone the repository
git clone https://github.com/trixxexe/vibespace.git
cd vibespace

# Run unit tests
./gradlew testDebugUnitTest

# Run Android Lint
./gradlew lintDebug

# Build Debug APK
./gradlew assembleDebug
```
The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Team Ownership

VibeSpace is engineered for a five-developer team:
- **Developer 1 (Project Lead)**: Architecture, AI integration, Core contracts.
- **Developer 2 (Editor Engineer)**: Sora Editor integration, syntax highlighting, canvas performance.
- **Developer 3 (UI/UX Engineer)**: Compose design system, responsive layouts, theme tokens.
- **Developer 4 (Execution Engineer)**: Termux RUN_COMMAND bridge, code runner, terminal processes.
- **Developer 5 (Language Intelligence Engineer)**: Tree-sitter AST, Language Server Protocol (LSP).

See [docs/MODULE_OWNERS.md](docs/MODULE_OWNERS.md) for detailed boundaries.

---

## Documentation

- [AGENTS.md](AGENTS.md) — Comprehensive guide for AI coding agents & collaborators
- [ARCHITECTURE.md](docs/ARCHITECTURE.md) — Architectural overview, data flow, and module boundaries
- [API_CONTRACTS.md](docs/API_CONTRACTS.md) — Interface specifications and models
- [MODULE_OWNERS.md](docs/MODULE_OWNERS.md) — Team responsibilities and code review policies
- [DEVELOPMENT_SETUP.md](docs/DEVELOPMENT_SETUP.md) — Local environment setup and build guides
- [ROADMAP.md](docs/ROADMAP.md) — Technical roadmap through v1.0.0
- [CONTRIBUTING.md](CONTRIBUTING.md) — Contribution workflow and PR requirements

---

## License

VibeSpace is licensed under the Apache 2.0 License.
