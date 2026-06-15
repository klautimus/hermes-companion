# Kanban Feature Parity: Hermes Dashboard → Companion App (COMPLETE)

## Data Model Fields (from dashboard plugin_api.py + kanban_db.py)

### Task object fields the dashboard uses:
- id, title, body, status, assignee, priority, tenant, workspace_kind, workspace_path
- created_at, updated_at, started_at, ended_at, archived
- claim_lock, claim_expires, worker_pid, max_runtime_seconds
- latest_summary (from task_runs.summary — the worker handoff text)

### Statuses (from VALID_STATUSES):
**triage, todo, scheduled, ready, running, blocked, review, done, archived**

### Full task detail API response (GET /tasks/:id):
```json
{
  "task": { ... Task fields ... },
  "comments": [ { id, task_id, author, body, created_at } ],
  "events": [ { id, task_id, kind, payload, created_at, run_id } ],
  "attachments": [ { id, task_id, filename, content_type, size, uploaded_by, stored_path, created_at } ],
  "links": { "parents": [...], "children": [...] },
  "runs": [ { id, task_id, profile, step_key, status, claim_lock, claim_expires, worker_pid, max_runtime_seconds, last_heartbeat_at, started_at, ended_at, outcome, summary, metadata, error } ]
}
```

### Board columns (from BOARD_COLUMNS):
**triage, todo, scheduled, ready, running, blocked, review, done** (+ archived via toggle)

### Card summary on board (from /board endpoint):
- id, title, status, assignee, priority, age (created_age_seconds, etc.)
- link_counts: { parents, children }
- comment_count
- progress: { done, total } — for parent tasks showing N/M children done
- latest_summary (200-char preview from task_runs.summary)
- diagnostics + warnings (from diagnostic rule engine)

---

## Feature Gap Analysis

### COLUMNS / STATUSES
| Feature | Dashboard | Companion | Gap |
|---------|-----------|-----------|-----|
| triage column | ✅ | ❌ | CRITICAL |
| scheduled column | ✅ | ❌ | CRITICAL |
| review column | ✅ | ❌ | CRITICAL |
| archived toggle | ✅ | ❌ | MAJOR |

### TASK CARD DISPLAY
| Feature | Dashboard | Companion | Gap |
|---------|-----------|-----------|-----|
| Title | ✅ | ✅ | ✅ |
| Status badge/color | ✅ | ✅ | ✅ |
| Assignee tag | ✅ | ✅ (subtle) | ✅ |
| Priority badge/dot | ✅ | ❌ | MAJOR |
| Comment count chip | ✅ | ❌ | MAJOR |
| Dependency count chip | ✅ | ❌ | MAJOR |
| Progress pill (N/M done) | ✅ | ❌ | MAJOR |
| Age ("created N ago") | ✅ | ❌ | MINOR |
| Latest summary preview | ✅ | ❌ | MINOR |
| Diagnostic warning badge | ✅ | ❌ | MINOR |
| Tenant tag | ✅ | ❌ | N/A (mobile) |
| Task ID | ✅ | ❌ | MINOR |

### TASK DETAIL DRAWER — Current companion has:
- Title (read-only) ✅
- Status badge ✅
- Assignee text ✅
- Body (read-only) ✅
- Comments list ✅
- Add comment ✅
- Complete button ✅
- Assign-to-self button ✅

### TASK DETAIL DRAWER — MISSING from companion:
| Feature | Dashboard | Companion | Gap |
|---------|-----------|-----------|-----|
| **Editable title** (click to rename) | ✅ | ❌ | **CRITICAL** |
| **Editable body** (markdown render + edit) | ✅ | ❌ | **CRITICAL** |
| **Status actions row** (→triage/→ready/→running/block/unblock/complete/archive) | ✅ | Only "Complete" | **CRITICAL** |
| **Assignee dropdown** (pick from profiles + unassign) | ✅ | Only self-assign | **CRITICAL** |
| **Priority display + edit** (1-5 or high/med/low) | ✅ | ❌ | **CRITICAL** |
| **Comments with author + timestamp** | ✅ Basic | ❌ No author/timestamps | **MAJOR** |
| **Attachments section** (upload/download/list) | ✅ | ❌ | **MAJOR** |
| **Dependency/links section** (parent/child chips, add/remove) | ✅ | ❌ | **MAJOR** |
| **Runs/attempts history** (profile, outcome, elapsed, summary) | ✅ | ❌ | **MAJOR** |
| **Events/activity history** (timeline of all changes) | ✅ | ❌ | **MAJOR** |
| **Result section** (worker's handoff summary) | ✅ | ❌ | **MAJOR** |
| **⚗ Decompose button** (LLM fan-out for triage tasks) | ✅ | ❌ | N/A (mobile has no LLM) |
| **✨ Specify button** (LLM spec rewrite for triage) | ✅ | ❌ | N/A |

### INLINE TASK CREATION
| Feature | Dashboard | Companion | Gap |
|---------|-----------|-----------|-----|
| + button on column headers | ✅ | ❌ | **CRITICAL** |
| Title + assignee + priority on create | ✅ | ❌ | **CRITICAL** |
| Auto-park in triage | ✅ | ❌ | **CRITICAL** |
| Parent task dropdown on create | ✅ | ❌ | MAJOR |

### DRAG & DROP / CONTEXT MENU
| Feature | Dashboard | Companion | Gap |
|---------|-----------|-----------|-----|
| Drag card between columns | ✅ HTML5 | ❌ | **CRITICAL** |
| Long-press → status change menu | ✅ DropdownMenu | ❌ | **CRITICAL** |
| Confirm on destructive transitions | ✅ | ❌ | MAJOR |

### MULTI-SELECT / BULK ACTIONS
| Feature | Dashboard | Companion | Gap |
|---------|-----------|-----------|-----|
| Multi-select (shift/ctrl click) | ✅ | ❌ | **CRITICAL** |
| Bulk status transition | ✅ | ❌ | **CRITICAL** |
| Bulk archive | ✅ | ❌ | **CRITICAL** |
| Bulk reassign | ✅ | ❌ | **CRITICAL** |

### FILTERING / SEARCH / TOOLBAR
| Feature | Dashboard | Companion | Gap |
|---------|-----------|-----------|-----|
| Free-text search | ✅ | ❌ | MAJOR |
| Assignee filter dropdown | ✅ | ❌ | MAJOR |
| Tenant filter | ✅ | ❌ | N/A |
| Show archived toggle | ✅ | ❌ | MAJOR |
| Lanes by profile (in Running column) | ✅ | ❌ | MINOR |
| Nudge dispatcher button | ✅ | ❌ | MINOR |

### BOARD MANAGEMENT
| Feature | Dashboard | Companion | Gap |
|---------|-----------|-----------|-----|
| Create board | ✅ | ✅ | ✅ |
| Rename board | ✅ | ✅ | ✅ |
| Archive board | ✅ | ✅ | ✅ |
| Delete board | ✅ | ✅ | ✅ |
| Switch board | ✅ | ✅ | ✅ |
| Search/filter boards | ✅ | ✅ | ✅ |
| Board stats (counts per status) | ✅ | ❌ | MAJOR |
| Board description | ✅ | ❌ | MINOR |
| Per-board icon | ✅ | ❌ | MINOR |

### SERVER API GAPS (server.py)
The companion server currently wraps `hermes kanban` CLI. The dashboard uses REST. Missing server routes:

| Endpoint | Dashboard | Companion Server | Gap |
|----------|-----------|------------------|-----|
| GET /board (grouped by column) | ✅ | Need to add | **CRITICAL** |
| GET /tasks/:id (full detail with comments+events+links+runs) | ✅ Need to unwrap | Partial (CLI returns wrapped) | **CRITICAL** |
| POST /tasks (create with full fields) | ✅ | ❌ | **CRITICAL** |
| PATCH /tasks/:id (update status/assignee/priority/title/body) | ✅ | ❌ | **CRITICAL** |
| DELETE /tasks/:id | ✅ | ❌ | MAJOR |
| POST /tasks/bulk (bulk update) | ✅ | ❌ | **CRITICAL** |
| POST /tasks/:id/comments | ✅ | ✅ | ✅ |
| GET /tasks/:id/attachments | ✅ | ❌ | MAJOR |
| POST /tasks/:id/attachments | ✅ | ❌ | MAJOR |
| GET /attachments/:id (download) | ✅ | ❌ | MAJOR |
| DELETE /attachments/:id | ✅ | ❌ | MAJOR |
| POST /tasks/:id/decompose | ✅ | ❌ | N/A |
| POST /tasks/:id/specify | ✅ | ❌ | N/A |
| POST /links (add dependency) | ✅ | ❌ | MAJOR |
| DELETE /links (remove dependency) | ✅ | ❌ | MAJOR |
| GET /profiles (list assignable profiles) | ✅ | Need to add | **CRITICAL** |
| GET /stats (board stats) | ✅ | Need to add | MAJOR |
| WS /events (live updates) | ✅ | ❌ | MINOR (polling OK for mobile) |

### Data Model Gaps (Models.kt)
Current TaskShowResponse: id, title, status, assignee, priority, body, comments, events

TaskShowResponse needs to ADD:
- `created_at` / `updated_at` / `started_at` (Long/Double timestamps)
- `result` (String — worker handoff result text, separate from body)
- `latest_summary` (String — from task_runs.summary, the worker's completion summary)
- `attachments` (List of Attachment objects)
- `links` (DependencyLinks with parents + children lists)
- `runs` (List of Run objects — attempt history)
- `tenant` (String?)
- `workspace_kind` (String?)
- `age` (Age object with created_age_seconds etc.)
- `link_counts` (LinkCounts with parents + children counts)
- `comment_count` (Int)
- `progress` (Progress with done + total)
- `diagnostics` / `warnings` (for card badge)

Current KanbanTask (list view): id, title, status, assignee, priority, body, created, updated

KanbanTask needs to ADD:
- `created_at` (Long, not String)
- `updated_at` (Long)
- `comment_count` (Int)
- `link_counts` ({ parents: Int, children: Int })
- `progress` ({ done: Int, total: Int })
- `latest_summary` (String?)
- `age` (Age object)
- `warnings` (for diagnostic badge)

Current KanbanComment: author, body, at
Current KanbanEvent: kind, at, profile

KanbanComment needs: `author` (has it), `body` (has it), `created_at` (rename from `at`)

### Summary: Key Missing Features by Priority

#### CRITICAL (must-have for basic parity)
1. **Three missing columns**: triage, scheduled, review
2. **Inline task creation** (+ button per column header) — title + assignee form
3. **Server REST API**: POST /tasks, PATCH /tasks/:id, GET /board (proper grouped), GET /profiles
4. **Task detail: editable title/body** — click to edit, PATCH to save
5. **Task detail: full status action row** — →triage, →ready, →running, block, unblock, complete, archive, delete
6. **Task detail: assignee dropdown** — pick from profiles list, not just self-assign
7. **Task detail: priority display + edit** — show badge, allow changing
8. **Context menu on long-press** — change status, assign, delete
9. **Multi-select + bulk actions** — select multiple, bulk complete/archive/reassign

#### MAJOR (important management features)
10. **Comments with author + enriched data** — rename `at` to `created_at`, add proper timestamp parsing
11. **Attachments section** — upload (multipart), download, list, delete
12. **Dependency/links section** — show parents/children, add/remove links
13. **Runs/attempts history** — show profile, outcome, elapsed, summary per attempt
14. **Events/activity timeline** — chronological list of all status changes, edits, claims
15. **Result/latest_summary section** — the worker's handoff summary
16. **Task card: comment/link/progress counts** — metadata chips
17. **Task card: priority badge** — colored dot/chip
18. **Search + assignee filter** — filter tasks
19. **Board stats** — counts per status, oldest-ready age
20. **Data model expansion** — TaskShowResponse, KanbanTask need many new fields

#### MINOR (polish)
21. Task card: age ("created N ago")
22. Task card: diagnostic warning badge
23. Column: per-column task count badges
24. Column header styling
25. Archived column toggle
26. Shows — board stats page
