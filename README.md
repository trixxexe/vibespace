# VibeSpace

> **Professional Native Android Coding IDE & Mobile Developer Environment**  
> *Inspired by VS Code — Built natively with Jetpack Compose & Kotlin*

[![Android CI](https://github.com/trixxexe/vibespace/actions/workflows/android-ci.yml/badge.svg)](https://github.com/trixxexe/vibespace/actions/workflows/android-ci.yml)
[![Version](https://img.shields.io/badge/version-0.1.0--alpha-cyan.svg)](https://github.com/trixxexe/vibespace/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10.01-green.svg)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-1.3.1-blue.svg)](https://m3.material.io)

---

## Overview

**VibeSpace** is an open-source, native Android Integrated Development Environment designed from the ground up for mobile coding. 

Unlike browser wrappers or desktop ports, VibeSpace delivers high-performance native text editing, multi-tab workspaces, responsive phone/tablet layouts, integrated terminal consoles, and AI-assisted workflows directly on Android devices.

---

## Key Features in v0.1.0

- **Multi-Tab Code Editor**: Full tab switching, dirty state tracking (`*`), line number gutters, undo/redo history stacks, and search/replace.
- **Dark-First Developer Aesthetic**: Deep charcoal themes (`#0F1115`), subtle borders, monospace code typography, and full light theme support.
- **Adaptive Responsive Layouts**:
  - **Phone (< 600dp)**: Prioritizes full-screen editing with sliding explorer drawers, expandable terminal panels, and bottom sheet AI chat.
  - **Tablet (> 840dp)**: Persistent 56dp Navigation Rail, permanent 260dp explorer sidebar, split-screen code editing, bottom dockable terminal, and right-hand AI companion.
- **Project Explorer**: Virtual file tree supporting folder navigation, file creation, deletion, and Storage Access Framework (SAF) URI abstraction.
- **Built-in Safe Terminal**: Collapsible bottom panel with Terminal, Output, Problems, and Console tabs.
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
    ├── search/           # Search and replace overlay with regex and match count
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
