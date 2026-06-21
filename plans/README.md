# Implementation Plans — Hermes Companion (Fresh Audit 2026-06-19)

**Planned at commit**: `b09390e`
**Audit model**: GLM-5.2 via Z.AI
**Pipeline**: improve → (kanban-orchestrator for execution)

## Execution order & status

| Plan | Title | Priority | Effort | Depends on | Status |
|------|-------|----------|--------|------------|--------|
| 030 | Add Task Creation Endpoint to Server | P0 | S | — | TODO |
| 031 | Add Task Dependencies Endpoint to Server | P1 | S | — | TODO |
| 032 | Fix Task Attachment Association | P1 | S | — | TODO |
| 033 | Fix Syntax Highlighting (Prism4j) | P2 | S | — | TODO |
| 034 | Add Emoji Shortcode Support | P2 | S | — | TODO |
| 035 | Use Streaming Chat for Attachment Messages | P2 | S | — | TODO |
| 036 | Add Missing Kanban Features (Decompose, Specify, Runs, Events) | P1 | L | 030, 031 | TODO |
| 037 | GATE — Full E2E Verification | P0 | M | ALL | TODO |

## Dependency notes

- **Phase 1 (parallel-capable, no deps)**: 030, 031, 032, 033, 034, 035 — all independent
- **Phase 2 (after 030+031)**: 036 — needs task creation + dependencies endpoints first
- **Phase 3 (after all)**: 037 — GATE verifies everything end-to-end
- For strict serial execution (recommended for Kotlin/Android): 030 → 031 → 032 → 033 → 034 → 035 → 036 → 037

## Findings considered and rejected

- **Email 2FA**: SKIPPED per Kevin's directive — "our encryption will suffice" (scrypt hashing + EncryptedSharedPreferences + HTTPS tunnel is sufficient)
- **Prior plans 021-029**: Partially executed (commits exist for 022, 023, 025, 027, 029) but didn't fully resolve issues. Plan 025 (markdown polish) was committed but the Prism4j grammar locator is still null — the fix was incomplete. New plan 033 addresses this properly.

## What this audit covered

- ✅ Full source read of all 14 Kotlin source files + 7 Python server files
- ✅ CodeGraph reindexed (656 clean nodes, venv excluded via .codegraphignore)
- ✅ All server routes mapped (796 lines, 25+ endpoints)
- ✅ MainViewModel fully read (763 lines, all actions)
- ✅ KanbanScreen UI structure reviewed (2059 lines)
- ✅ MarkdownText.kt fully read (177 lines — found Prism4j stub)
- ✅ Composer.kt reviewed (266 lines — attachment picker)
- ✅ ApiClient.kt reviewed (417 lines — HTTP client)
- ✅ Server.py fully read (796 lines — all routes, no 2FA, no task creation)

## Audit methodology

- Skills loaded in order: using-agent-skills → improve-codebase-architecture → tdd → improve
- All linked reference files read: plan-template.md, audit-playbook.md, codegraph-first-recon.md, audit-to-execution-pipeline.md
- CodeGraph used for architecture overview and file structure mapping
- Direct file reads for detailed code review (small codebase, 21 source files)
