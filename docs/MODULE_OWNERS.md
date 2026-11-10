# VibeSpace Team Ownership & Module Boundaries

This matrix defines the strict ownership and module responsibilities for the five-engineer development team.

| Engineer | Title | Primary Modules | Secondary Modules | Code Review Area |
| :--- | :--- | :--- | :--- | :--- |
| **Developer 1** | Project Lead & Architect | `:app`, `:core:common`, `:core:ai`, `:feature:aichat` | All modules | Overall architecture, API contracts, releases |
| **Developer 2** | Editor Engineer | `:core:editor`, `:feature:editor` | `:core:designsystem` | Text canvas, Sora Editor integration, syntax highlighting |
| **Developer 3** | UI/UX Engineer | `:core:designsystem`, `:feature:home`, `:feature:settings` | `:app` navigation | Design system, tokens, adaptive layouts, theming |
| **Developer 4** | Execution Engineer | `:core:runner`, `:core:terminal`, `:feature:terminal` | `:core:workspace` | Termux Intent API, process management, safe terminals |
| **Developer 5** | Language Intelligence Engineer | `:core:language`, `:feature:search` | `:core:editor` | Tree-sitter parsers, LSP clients, code diagnostics |

---

## Conflict Avoidance & Git Workflow Rules

1. **Independent Module Development**: Each developer works inside their assigned feature and core directories.
2. **Branch Naming**:
   - `dev1/ai-...` or `lead/...`
   - `dev2/editor-...`
   - `dev3/ui-...`
   - `dev4/runner-...`
   - `dev5/lsp-...`
3. **Restricted Shared Files**:
   - `settings.gradle.kts`
   - `gradle/libs.versions.toml`
   - `docs/API_CONTRACTS.md`
   Any changes to shared Gradle files or shared interfaces must be reviewed and merged by Developer 1 (Project Lead).
4. **Never Overwrite User or Teammate Work**: Do not reformat entire shared modules or force push.
