# Implementation Plans — Hermes Companion Android (Fresh E2E Audit 2026-06-19)

**Planned at commit**: `4a85552`

## Execution order & status

| Plan | Title | Priority | Effort | Depends on | Status |
|------|-------|----------|--------|------------|--------|
| 021 | Complete Kanban UI Parity | P1 | L | daemon 015+016 | TODO |
| 022 | Email 2FA UI Flow | P0 | M | daemon 017 | TODO |
| 023 | Attachment Display in Chat | P0 | M | daemon 018 | TODO |
| 024 | Streaming Chat Display | P1 | M | daemon 019 | TODO |
| 025 | Markdown Rendering Polish | P1 | S | — | TODO |

## Dependency notes

- **021 depends on daemon 015+016** — UI calls those new endpoints
- **022 depends on daemon 017** — 2FA UI needs backend endpoints
- **023 depends on daemon 018** — attachment display needs message-attachment association
- **025 independent** — pure UI polish
