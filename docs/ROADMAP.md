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
- [ ] Developer 2: Sora Editor View integration via `SoraEditorBridge`.
- [ ] Developer 2: Real-time syntax highlighting for Kotlin, Python, JS, and Rust.
- [ ] Developer 3: Split editor panes (horizontal and vertical split) on tablets.
- [ ] Developer 1: Storage Access Framework (SAF) URI persistent permission management.
- [ ] Developer 4: File watcher integration for live workspace change detection.

---

## Phase 3: Runtime Execution & Terminal (v0.3.0)
- [ ] Developer 4: Full Termux `RUN_COMMAND` Intent bridge implementation.
- [ ] Developer 4: Python and Node.js code execution with interactive I/O.
- [ ] Developer 4: PRoot / Chroot rootfs container support.
- [ ] Developer 2: Code execution keyboard shortcuts (Ctrl+Enter / Run button).

---

## Phase 4: Language Intelligence & Git (v0.4.0)
- [ ] Developer 5: Tree-sitter C-binding / JNI parser integration.
- [ ] Developer 5: Language Server Protocol (LSP) client over stdin/stdout for Pyright, Rust Analyzer, and Kotlin LS.
- [ ] Developer 5: Semantic autocomplete popup overlay on editor canvas.
- [ ] Developer 1: Native Git integration using LibGit2 / JGit (status, diff, commit, push, pull).

---

## Phase 5: Production Release (v1.0.0)
- [ ] Full end-to-end multi-language Android IDE.
- [ ] Google Play Store release.
