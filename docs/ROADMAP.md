# VibeSpace Technical Roadmap

## Phase 1: Foundation (v0.1.0) — [COMPLETED]
- [x] Multi-module Gradle Kotlin DSL architecture with version catalog (`libs.versions.toml`).
- [x] Core contracts: `EditorEngine`, `WorkspaceManager`, `RunnerManager`, `TerminalService`, `AIProvider`, `LanguageDetector`.
- [x] Jetpack Compose Material 3 dark-first developer theme and design system tokens.
- [x] Responsive layout scaffold for compact smartphones and tablets / foldables.
- [x] Functional multi-tab editor shell with line numbers, gutter, dirty status, and undo/redo stacks.
- [x] Project Explorer tree with file creation, deletion, and directory expansion.
- [x] Safe built-in terminal service and bottom panel with multi-stream tabs.
- [x] AI Assistant chat interface with code context attachment and diff approval models.
- [x] Settings screen with appearance, font size slider, and local persistence.
- [x] Comprehensive documentation, GitHub Actions CI workflow, PR and Issue templates.

---

## Phase 2: Editor & Workspace Enhancement (v0.2.0)
- [x] Sora Editor canvas integration with themed syntax highlighting and editor actions.
- [x] SAF tree selection, persisted URI permissions, and workspace-backed file operations.
- [ ] Split editor panes (horizontal and vertical split) on tablets.
- [ ] File watcher integration for live external workspace change detection.

---

## Phase 3: Runtime Execution & Terminal (v0.3.0)
- [x] Developer 4: Termux `RUN_COMMAND` Intent bridge implementation (requires Termux and its permission).
- [x] Developer 4: Bundle Python 3, Node.js/npm, Alpine, util-linux PTY support, and checksum-pinned PRoot for supported ABIs; install extra packages through Alpine.
- [x] Developer 4: Persistent interactive Alpine terminal with live output, Ctrl+C, and SAF workspace synchronization.
- [ ] Hardware keyboard execution shortcuts (Ctrl+Enter) and shortcut customization.

---

## Phase 4: Language Intelligence & Git (v0.4.0)
- [ ] Developer 5: Tree-sitter C-binding / JNI parser integration.
- [x] Developer 5: LSP client over stdin/stdout with installable Python and TypeScript servers in Alpine.
- [ ] Tree-sitter parsing and language-aware semantic autocomplete beyond configured LSP servers.
- [x] Developer 1: JGit integration for status, diff, stage, commit, branches, and HTTP(S) push/pull.

---

## Phase 5: Production Release (v1.0.0)
- [ ] Full end-to-end multi-language Android IDE.
- [ ] Google Play Store release.
