# Contributing to VibeSpace

Welcome to the VibeSpace engineering team! We follow disciplined Android engineering practices to enable multiple developers to build collaboratively without conflicts.

---

## 1. Team Ownership & Responsibilities

Before picking up a task, verify ownership in [MODULE_OWNERS.md](docs/MODULE_OWNERS.md):
- **Developer 1 (Project Lead)**: Core architecture, DI, AI systems (`:core:ai`, `:feature:aichat`, `:app`).
- **Developer 2 (Editor Engineer)**: Code editor canvas, Sora Editor integration, syntax highlighting (`:core:editor`, `:feature:editor`).
- **Developer 3 (UI/UX Engineer)**: Compose design system, theming, tablet layouts (`:core:designsystem`, `:feature:home`, `:feature:settings`).
- **Developer 4 (Execution Engineer)**: Process execution, Termux integration, terminal panels (`:core:runner`, `:core:terminal`, `:feature:terminal`).
- **Developer 5 (Language Intelligence Engineer)**: LSP client, Tree-sitter, language detection (`:core:language`, `:feature:search`).

---

## 2. Git & Branching Rules

1. **No direct pushes to `main`**: All features must go through task branches and Pull Requests.
2. **Branch naming format**:
   - `lead/<feature-name>`
   - `editor/<feature-name>`
   - `ui/<feature-name>`
   - `runner/<feature-name>`
   - `language/<feature-name>`
3. **No force-pushing** to shared branches.
4. **No secrets in commits**: Never commit API keys, private tokens, or keystores.
5. **Keep commits atomic**: Use conventional commit titles (e.g., `feat(editor): implement line gutter scrolling`, `fix(workspace): handle null parent uri`).

---

## 3. Pull Request Guidelines

1. Open a PR using the provided [pull_request_template.md](.github/pull_request_template.md).
2. Tag the relevant module owner for review.
3. Verify all automated checks pass on GitHub Actions CI.
4. Squash-merge into `main` after review approval.
