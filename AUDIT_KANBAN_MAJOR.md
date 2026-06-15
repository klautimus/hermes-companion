# Kanban Feature Parity Audit — MAJOR Features

**Audit Date:** 2026-06-14
**Spec:** KANBAN_PARITY_SPEC.md (items 7–14 = MAJOR priority)
**Verdict:** MUST-FIX — All 7 MAJOR areas have gaps. None are fully implemented.

---

## FINDING 1: Multi-Select + Bulk Actions

**Severity: MAJOR — completely missing**
**Spec Ref:** MAJOR #7 (shift/ctrl multi-select, bulk complete/archive/reassign)

### What's missing

**KanbanScreen.kt:**
- No multi-select state whatsoever. The `KanbanScreen` composable has no `selectedTasks` set, no shift/ctrl click handler, no selection mode toggle. (Lines 55–417: entire screen has zero selection tracking.)
- `KanbanColumn` (line 631) renders tasks as simple `clickable` cards — single click only, no long-press-to-select, no checkbox, no selection overlay.
- `KanbanTask` card at line 662: `Modifier.fillMaxWidth().clickable { onTaskClick(task.id) }` — always opens detail, never toggles selection.
- No bulk action bar / FAB / toolbar. No "Complete Selected", "Archive Selected", "Reassign Selected" anywhere in the file.

**MainViewModel.kt:**
- No bulk operation methods. Individual task methods exist (`completeTask` at line 399, `assignTask` at line 425) but no `bulkComplete`, `bulkArchive`, or `bulkAssign`.
- No endpoint calls for bulk operations.

**server.py:**
- No bulk endpoint registered. Route table (lines 540–549) has individual task endpoints only.
- Hermes CLI (`hermes kanban`) has no bulk subcommand wrapper.

### What needs to be added

**Android UI:**
1. `selectedTaskIds: MutableStateFlow<Set<String>>` in MainViewModel
2. `selectionMode: MutableStateFlow<Boolean>` toggle in KanbanScreen
3. Long-press on task card enters selection mode (first selection), subsequent taps toggle
4. Shift-click range selection (track lastSelectedId, select range)
5. Selection checkbox overlay on cards when in selection mode
6. Bottom action bar (or top bar) with: Complete All, Archive All, Reassign, Cancel Selection
7. Count indicator: "3 selected"

**Server API (server.py):**
1. `POST /api/kanban/tasks/bulk` endpoint accepting `{ "action": "complete|archive|assign", "task_ids": [...], "assignee?": "..." }`
2. Delegates to sequential `hermes kanban complete/assign` calls in `_kanban()`

---

## FINDING 2: Task Detail — Priority Display/Edit

**Severity: MAJOR — data model supports it, UI completely omits it**
**Spec Ref:** MAJOR #8 (priority display/edit)

### What's missing

**Models.kt (line 32):** `KanbanTask.priority: Int = 1` — field exists and is deserialized.
**Models.kt (line 44):** `TaskShowResponse.priority: Int = 1` — field exists.

**KanbanScreen.kt:**
- Task detail sheet (lines 341–416) displays: title, status badge, assignee, body, comments, action buttons. **No priority field anywhere.**
- Line 354: `if (task.assignee != null)` — assignee is shown, but no equivalent for priority.
- No priority badge/indicator on the task card in columns either (line 662–677: card shows only title + assignee).
- No priority picker/stepper/dropdown in the detail sheet.

**MainViewModel.kt:**
- `completeTask()` at line 399, `assignTask()` at line 425 — but no `setPriority()` method.
- No API call pattern for updating priority.

**server.py:**
- No handler for updating task priority. The server has no `PATCH /api/kanban/tasks/{task_id}` endpoint at all.
- The assign endpoint (line 423) only handles assignee.

### What needs to be added

**Android UI (KanbanScreen.kt):**
1. In task detail sheet (after assignee, ~line 358): add priority indicator — e.g., colored chip "P1", "P2", "P3" with `STATUS_COLORS`-like mapping.
2. Add priority picker: either a row of clickable chips (P0–P3) or a dropdown.
3. On priority change, call `viewModel.setPriority(task.id, newPriority)`.

**Android UI (KanbanColumn.kt card):**
4. Optional: small priority badge/dot on task card (similar to assignee display).

**MainViewModel.kt:**
5. `fun setPriority(taskId: String, priority: Int)` — calls `POST /api/kanban/tasks/{id}/priority` or `PATCH`.

**server.py:**
6. `POST /api/kanban/tasks/{task_id}/priority` handler (or general-purpose `PATCH`).
7. Delegates to `hermes kanban` CLI or directly updates the kanban DB.

---

## FINDING 3: Task Detail — Dependency/Links Display

**Severity: MAJOR — completely missing on both client and server**
**Spec Ref:** MAJOR #9 (parent/child task info)

### What's missing

**Models.kt:**
- `KanbanTask` (line 27–36): no `parents`, `children`, or `links` fields.
- `TaskShowResponse` (line 39–48): no `parents`, `children`, or `links` fields.

**KanbanScreen.kt:**
- Task detail sheet (lines 341–416): no parent/child section, no dependency chips, no "blocked by" or "blocks" indicators.
- Task card in columns (lines 662–677): no dependency count badge.

**MainViewModel.kt:**
- No methods for loading or managing task dependencies/links.

**server.py:**
- No endpoints for task dependencies. No `GET /api/kanban/tasks/{id}/links`, no `POST` for linking, no `DELETE` for unlinking.
- `TaskShowResponse` from `hermes kanban show` may include dependency info (the model has `events` at Models.kt line 47), but the server doesn't reformat it for parents/children.

### What needs to be added

**Models.kt:**
1. Add to `TaskShowResponse`: `val parents: List<TaskRef> = emptyList()`, `val children: List<TaskRef> = emptyList()` where `TaskRef` is a minimal `{ id, title, status }` data class.

**KanbanScreen.kt (task detail):**
2. "Blocking" section: list of parent tasks with status-colored chips. Click navigates to that task.
3. "Blocked by" section: list of child tasks.
4. "Add link" button → task picker dialog.

**server.py:**
5. Parse `parents`/`children` from `hermes kanban show --json` response (the Hermes CLI may already return this in `task` object).
6. If Hermes doesn't return it natively, add `POST /api/kanban/tasks/{id}/link` and `DELETE /api/kanban/tasks/{id}/link/{child_id}` that call `hermes kanban link`.

---

## FINDING 4: Task Detail — Events/Runs History

**Severity: MAJOR — data model partially supports it, UI omits it**
**Spec Ref:** MAJOR #10 (audit trail)

### What's missing

**Models.kt:**
- `TaskShowResponse.events: List<KanbanEvent>` at line 47 — field exists.
- `KanbanEvent` (lines 57–62): has `kind`, `at`, `profile` — adequate model.

**KanbanScreen.kt:**
- Task detail sheet (lines 341–416): **completely ignores `task.events`**. No "History" section, no timeline, no event list.
- The comment section (lines 366–379) shows `task.comments` but events are never rendered.

**MainViewModel.kt:**
- `loadTask()` at line 386 correctly deserializes `TaskShowResponse` including events — the data arrives but is never displayed.

**server.py:**
- `handle_kanban_task_show` (line 261) returns the raw JSON from `hermes kanban show`. If Hermes returns events in the task object, they'll be in the response. This depends on whether `hermes kanban show --json` includes events.

### What needs to be added

**KanbanScreen.kt (task detail sheet):**
1. Add "History" section below comments (or in a tab). Render `task.events` as a chronological list:
   - `kind` label (e.g., "created", "claimed", "completed") with color
   - `profile` as author label
   - `at` as relative timestamp ("2h ago")
2. Use `LazyColumn` with `items(task.events)` similar to comment rendering pattern.

**server.py (if needed):**
3. Verify that `hermes kanban show --json` returns events. If not, the `_kanban("show", ...)` call may need a flag or the response may need augmentation.

---

## FINDING 5: Task Detail — Editable Assignee Dropdown

**Severity: MAJOR — only self-assign, no profile picker**
**Spec Ref:** MAJOR #11 (only self-assign, no profile picker)

### What's missing

**KanbanScreen.kt (lines 399–411):**
- The detail sheet has only two action buttons: "Complete" and "Assign".
- "Assign" button (line 406–410): calls `viewModel.assignTask(task.id, viewModel.username.value)` — always assigns to the *current user*. No dropdown, no picker, no way to assign to another profile.

**MainViewModel.kt:**
- No method to fetch available profiles.
- No `assignTaskToProfile(taskId, profile)` that accepts an arbitrary profile name — it only sends `viewModel.username.value`.

**server.py:**
- `handle_kanban_task_assign` (line 423) correctly accepts any assignee string in `body.get("assignee")`. The *server* supports arbitrary assignees. The *client* just never sends anything other than self.

### What needs to be added

**Android UI:**
1. Replace "Assign" self-button with an assignee section in the detail sheet:
   - Show current assignee name (or "Unassigned")
   - Click opens a dropdown/bottom sheet picker of available profiles
   - Selecting a profile calls `viewModel.assignTask(task.id, selectedProfile)`
2. Add "Unassign" option in the picker.

**MainViewModel.kt:**
3. `fun loadProfiles(): List<String>` — fetches available worker profiles from the server.
   - Could call `GET /api/kanban/profiles` or read from `hermes kanban --json` output.
4. Keep `assignTask` signature that accepts any profile string (already correct).

**server.py (if needed):**
5. May need a `GET /api/kanban/profiles` endpoint if Hermes doesn't expose profile list easily.

---

## FINDING 6: Search/Filter Tasks

**Severity: MAJOR — no task-level search or assignee filter**
**Spec Ref:** MAJOR #12 (free-text search), MAJOR #13 (assignee filter)

### What's missing

**KanbanScreen.kt:**
- Top bar (lines 88–103): board picker + refresh button. No search field, no filter chip, no "Filter by assignee".
- KanbanColumn (lines 631–687): renders `tasks` list as-is with no client-side filtering.
- BoardDrawer (lines 423–612): has search for *boards* (line 464), but no task search anywhere.

**MainViewModel.kt:**
- `tasksByStatus` at line 99: groups all tasks by status, no filtering applied.
- No `searchQuery` or `assigneeFilter` state.
- No filtered task list derivation.

**server.py:**
- `handle_kanban_tasks_list` (line 235): already passes `?status=` and `?assignee=` query params to the Hermes CLI. So the *server API* supports these filters. The *client* just doesn't expose them.

### What needs to be added

**Android UI (KanbanScreen.kt):**
1. Add search bar to top bar (between board picker and refresh): `OutlinedTextField` with search icon.
2. On search text change, filter displayed tasks (either client-side on `_tasks` or re-fetch with query).
3. Add filter row below top bar: assignee chips (e.g., "All", "Mine", "analyst", "default").
4. On filter change, update `tasksByStatus` derivation or re-fetch.

**MainViewModel.kt:**
5. `searchQuery: MutableStateFlow("")` and `assigneeFilter: MutableStateFlow<String?>(null)`.
6. `filteredTasksByStatus` derived from `_tasks` + filters, OR pass filters to `loadTasks()` and re-fetch.
7. For client-side: `val filteredTasks = _tasks.map { list -> list.filter { ... } }`.

**server.py:**
8. Already supports `?status=` and `?assignee=` on the list endpoint. May want to add free-text search if Hermes CLI supports it, otherwise client-side filtering is fine.

---

## FINDING 7: Server-Side API Gaps

**Severity: MAJOR — POST (create), PATCH (update), and bulk endpoints missing**
**Spec Ref:** MAJOR #14 (no POST create, no PATCH update, no bulk endpoint)

### Current server.py route table (lines 540–549):

```
GET    /api/kanban/boards                    ✅ handle_kanban_boards
POST   /api/kanban/boards                    ✅ handle_kanban_boards_create (line 343)
POST   /api/kanban/boards/{slug}/rename      ✅ handle_kanban_board_rename (line 371)
POST   /api/kanban/boards/{slug}/archive     ✅ handle_kanban_board_archive (line 394)
DELETE /api/kanban/boards/{slug}             ✅ handle_kanban_board_delete (line 406)
GET    /api/kanban/tasks                     ✅ handle_kanban_tasks_list (line 235)
GET    /api/kanban/tasks/{task_id}            ✅ handle_kanban_task_show (line 261)
POST   /api/kanban/tasks/{task_id}/complete   ✅ handle_kanban_task_complete (line 282)
POST   /api/kanban/tasks/{task_id}/comment    ✅ handle_kanban_task_comment (line 295)
POST   /api/kanban/tasks/{task_id}/assign     ✅ handle_kanban_task_assign (line 423)
```

### What's missing

| Endpoint | Status | Purpose |
|----------|--------|---------|
| `POST /api/kanban/tasks` | **MISSING** | Create new task (with title, assignee, priority, parent) |
| `PATCH /api/kanban/tasks/{task_id}` | **MISSING** | General update (title, body, priority, status transitions) |
| `POST /api/kanban/tasks/bulk` | **MISSING** | Bulk complete/archive/reassign |
| `POST /api/kanban/tasks/{task_id}/block` | **MISSING** | Block a task |
| `POST /api/kanban/tasks/{task_id}/unblock` | **MISSING** | Unblock a task |
| `POST /api/kanban/tasks/{task_id}/priority` | **MISSING** | Set priority |
| `POST /api/kanban/tasks/{task_id}/link` | **MISSING** | Add parent/child link |
| `DELETE /api/kanban/tasks/{task_id}/link/{child}` | **MISSING** | Remove link |

### Specific server.py gaps

1. **No task creation endpoint.** Android has no UI for inline task creation on columns, and even if it did, there's no server endpoint to handle it. The Hermes CLI `hermes kanban create` exists but has no HTTP wrapper.

2. **No PATCH/update endpoint.** The server has `complete` and `assign` as separate POST endpoints, but no general update for: editing title, editing body, changing status to arbitrary values (triage/ready/running/blocked/done), or setting priority.

3. **No bulk endpoint.** Hermes CLI supports `hermes kanban complete <id>` individually but there's no batch version.

4. **Missing status transitions.** The Android app only has "Complete" (line 400–404 in KanbanScreen.kt). No "Block", "Unblock", "Move to Ready", "Move to Running", "Move to Triage", "Move to Done" — these are under CRITICAL but worth noting since the server doesn't expose them either.

### What needs to be added to server.py

```python
# 1. Create task
async def handle_kanban_task_create(request):
    """POST /api/kanban/tasks — create a new task."""
    # Body: { "title": "...", "status?": "todo", "assignee?": "...", "priority?": 1, "parent_id?": "..." }

# 2. General update
async def handle_kanban_task_update(request):
    """PATCH /api/kanban/tasks/{task_id} — update title/body/priority/status."""

# 3. Bulk operations
async def handle_kanban_tasks_bulk(request):
    """POST /api/kanban/tasks/bulk — { "action": "complete|archive|assign", "task_ids": [...] }."""

# 4. Status transitions
async def handle_kanban_task_block(request):
    """POST /api/kanban/tasks/{task_id}/block."""

async def handle_kanban_task_unblock(request):
    """POST /api/kanban/tasks/{task_id}/unblock."""

# 5. Priority
async def handle_kanban_task_set_priority(request):
    """POST /api/kanban/tasks/{task_id}/priority — body: { "priority": 2 }."""

# 6. Dependency links
async def handle_kanban_task_link(request):
    """POST /api/kanban/tasks/{task_id}/link — body: { "child_id": "..." }."""

async def handle_kanban_task_unlink(request):
    """DELETE /api/kanban/tasks/{task_id}/link/{child_id}."""
```

---

## SUMMARY TABLE

| # | Feature | Client UI | Server API | Data Model | Severity |
|---|---------|-----------|------------|------------|----------|
| 1 | Multi-select + bulk actions | ❌ Missing entirely | ❌ No bulk endpoint | N/A | MAJOR |
| 2 | Priority display/edit | ❌ Not shown in detail/card | ❌ No priority endpoint | ✅ `priority` field exists | MAJOR |
| 3 | Dependency/links display | ❌ Missing entirely | ❌ No link endpoints | ❌ No parent/child fields | MAJOR |
| 4 | Events/runs history | ❌ Not rendered in UI | ⚠️ Depends on CLI output | ✅ `events` field exists | MAJOR |
| 5 | Editable assignee dropdown | ⚠️ Self-assign only | ✅ Server accepts any assignee | N/A | MAJOR |
| 6 | Search/filter tasks | ❌ No task search/filter UI | ✅ Server supports `?status=` `?assignee=` | N/A | MAJOR |
| 7 | POST/PATCH/bulk API | ❌ No create UI | ❌ Missing 8+ endpoints | ❌ Missing parent/child | MAJOR |

## VERDICT: MUST-FIX

All 7 MAJOR areas have significant gaps. The most critical blockers are:

1. **Server API:** Need `POST /api/kanban/tasks` (create), `PATCH /api/kanban/tasks/:id` (update), `POST /api/kanban/tasks/bulk` (bulk ops), and link/priority/status transition endpoints. Without these, the Android app cannot add the UI.

2. **Android UI:** Every MAJOR feature needs UI work: multi-select mode, priority display/picker, dependency section, events timeline, assignee dropdown, search/filter bar.

3. **Data Model (Models.kt):** Needs `parents`/`children` fields on `TaskShowResponse`, and a `TaskRef` data class for linked tasks.

### Priority order for implementation:
1. Server-side: Add POST create + PATCH update + bulk endpoint (unblocks all client work)
2. Data model: Add parent/child/link fields
3. Android: Add assignee dropdown + priority display + events history (medium effort, high value)
4. Android: Add search/filter bar
5. Android: Add multi-select + bulk actions (highest UI complexity)
