# Kanban Feature Parity Audit — MINOR + Server Features

**Date:** 2026-06-14
**Auditor:** Atlas (analyst)
**Scope:** MINOR-priority features from KANBAN_PARITY_SPEC.md + server-side gap analysis

---

## Files Audited

| File | Lines |
|------|-------|
| `KanbanScreen.kt` | 687 |
| `MainViewModel.kt` | 491 |
| `data/Models.kt` | 98 |
| `server/server.py` | 565 |

---

## Findings

### 1. Task Card: Priority Badge

**Spec:** Visual priority indicator on cards (e.g., "P0", "P1", colored chip)
**Status: MISSING**

- `KanbanScreen.kt:661-677` — The task card `Card` composable renders only `task.title` and optionally `task.assignee`. No priority badge is rendered.
- `Models.kt:32` — `KanbanTask.priority` field exists (Int, default 1) and is deserialized from the API. Data is available but never displayed.
- `Models.kt:44` — `TaskShowResponse.priority` also exists for the detail view, also never displayed.

**Severity:** MINOR (spec priority)
**Fix:** Add a priority chip to the task card in `KanbanScreen.kt` around line 668, e.g. a small `Surface` with `Text("P${task.priority}")` colored by priority level (P0=red, P1=orange, P2=yellow). Also display in the task detail sheet (line 345+).

---

### 2. Task Card: "Created N ago" Relative Timestamp

**Spec:** Relative timestamp like "created 3h ago" on cards
**Status: MISSING**

- `Models.kt:34` — `KanbanTask.created` field exists (String?, nullable) and is deserialized. Data may be available from the API.
- `KanbanScreen.kt:661-677` — Task card does not render any timestamp.
- The field is `String?` with no `@SerialName` annotation — verify the JSON key matches what `hermes kanban list --json` returns (likely `created_at` or `created`). If the key is `created_at`, the field won't deserialize.

**Severity:** MINOR (spec priority)
**Fix:** 
1. Verify the JSON key from `hermes kanban list --json` and add `@SerialName` if needed.
2. Parse the ISO timestamp and render a relative time string (e.g., using `java.time.Duration` or a simple helper) on the task card below the title.

---

### 3. Task Card: Comment/Link Count Badges

**Spec:** Metadata chips showing comment count and dependency/link count on cards
**Status: MISSING**

- `Models.kt:27-36` — `KanbanTask` has no `commentCount` or `linkCount` fields. The task list endpoint returns flat task objects; comments are only in `TaskShowResponse`.
- `KanbanScreen.kt:661-677` — No metadata chips rendered.
- The `hermes kanban list --json` output does not include comment/link counts — this data is only in `hermes kanban show --json`.

**Severity:** MINOR (spec priority)
**Fix:** Two approaches:
1. **Preferred:** Add `comment_count` and `link_count` fields to the `hermes kanban list` JSON output (server-side CLI change), then add fields to `KanbanTask` and render chips.
2. **Workaround:** Not feasible without API change — the list endpoint doesn't return this data.

**Server gap:** `hermes kanban list` does not include comment/link counts in its output. The server's `handle_kanban_tasks_list` (server.py:235-258) passes through whatever the CLI returns.

---

### 4. Task Detail Section Titles

**Spec:** Section headers in task detail: "Description", "Comments", "Activity"
**Status: PARTIAL**

- `KanbanScreen.kt:367` — "Comments" section title exists (`Text("Comments", style = titleSmall)`).
- `KanbanScreen.kt:361-364` — Body text is rendered but has no "Description" section title above it.
- `KanbanScreen.kt:345-358` — Title and status/assignee are shown but not under a "Description" or "Details" header.
- No "Activity" or "Events" section exists at all — `TaskShowResponse.events` (Models.kt:47) is deserialized but never rendered.

**Severity:** MINOR (spec priority)
**Fix:**
1. Add `Text("Description", style = MaterialTheme.typography.titleSmall)` before the body text at line 361.
2. Add an "Activity" section after comments that renders `task.events` (if non-empty) with event kind + timestamp.

---

### 5. Task Detail: Body Text Styling

**Spec:** Better typography for body text in task detail
**Status: WEAK**

- `KanbanScreen.kt:362` — Body rendered as `Text(task.body, style = MaterialTheme.typography.bodyMedium)`.
- No markdown rendering (the spec notes the dashboard renders markdown). For mobile, at minimum the body should have proper line spacing and wrapping.
- Current styling is basic `bodyMedium` — acceptable but could use `lineHeight` adjustment or `buildAnnotatedString` for basic formatting.

**Severity:** MINOR (spec priority)
**Fix:** Consider using a markdown library (e.g., `compose-markdown`) or at minimum add `lineHeight = 20.sp` to improve readability of multi-line bodies.

---

### 6. Column Header Styling

**Spec:** Visual distinction between columns
**Status: ADEQUATE**

- `KanbanScreen.kt:641-652` — Column headers have colored `Surface` backgrounds using `color.copy(alpha = 0.2f)`, bold title, and a task count badge in a `CircleShape`.
- Each column has a distinct color from `STATUS_COLORS` (todo=cream, ready=blue, running=purple, blocked=pink, done=green).
- The header is visually distinct from the column body (which has `surfaceVariant` background).

**Severity:** OK — meets the spec intent. No fix needed.

---

### 7. Error Handling on Task Actions

**Spec:** Proper error messages when task actions fail
**Status: ADEQUATE**

- `MainViewModel.kt:399-409` — `completeTask()` catches exceptions and sets `_kanbanError`.
- `MainViewModel.kt:411-423` — `commentOnTask()` catches exceptions and sets `_kanbanError`.
- `MainViewModel.kt:425-437` — `assignTask()` catches exceptions and sets `_kanbanError`.
- `KanbanScreen.kt:107-110` — Error state is displayed: `error?.let { Text(it, color = ..., style = bodySmall) }`.
- Errors are cleared at the start of each action (`_kanbanError.value = null`).

**One issue:** Errors are displayed as a single text line at the top of the screen. If multiple actions fail simultaneously, only the last error is shown. This is acceptable for mobile.

**Severity:** OK — functional. Consider a snackbar for better UX (Nit).

---

### 8. Loading State Indicators

**Spec:** Progress indicators during API calls
**Status: MISSING**

- `KanbanScreen.kt` — No loading indicators anywhere. When `loadTasks()`, `loadBoards()`, `loadTask()`, `completeTask()`, `commentOnTask()`, or `assignTask()` are in flight, the UI shows no progress feedback.
- `MainViewModel.kt` — No `_isLoading` state flow exists. All actions are fire-and-forget coroutines with no loading tracking.
- The refresh button (line 100-102) doesn't show a spinning indicator during refresh.

**Severity:** MINOR (spec priority) — but impacts UX significantly on slow connections.
**Fix:**
1. Add a `_isLoading = MutableStateFlow(false)` to `MainViewModel`.
2. Set it to `true` at the start of each suspend call, `false` in a `finally` block.
3. In `KanbanScreen.kt`, show a `LinearProgressIndicator` or `CircularProgressIndicator` when loading.

---

## Server-Side Gap Analysis

### Routes Implemented (server.py)

| Route | Handler | Status |
|-------|---------|--------|
| `GET /api/kanban/boards` | `handle_kanban_boards` | OK |
| `POST /api/kanban/boards` | `handle_kanban_boards_create` | OK |
| `POST /api/kanban/boards/{slug}/rename` | `handle_kanban_board_rename` | OK |
| `POST /api/kanban/boards/{slug}/archive` | `handle_kanban_board_archive` | OK |
| `DELETE /api/kanban/boards/{slug}` | `handle_kanban_board_delete` | OK |
| `GET /api/kanban/tasks` | `handle_kanban_tasks_list` | OK |
| `GET /api/kanban/tasks/{id}` | `handle_kanban_task_show` | OK |
| `POST /api/kanban/tasks/{id}/complete` | `handle_kanban_task_complete` | OK |
| `POST /api/kanban/tasks/{id}/comment` | `handle_kanban_task_comment` | OK |
| `POST /api/kanban/tasks/{id}/assign` | `handle_kanban_task_assign` | OK |

### Missing Server Routes

| Needed For | Required Route | Status |
|------------|---------------|--------|
| Block/unblock task | `POST /api/kanban/tasks/{id}/block` | **MISSING** |
| Unblock task | `POST /api/kanban/tasks/{id}/unblock` | **MISSING** |
| Edit task (title/body) | `PATCH /api/kanban/tasks/{id}` | **MISSING** |
| Create task | `POST /api/kanban/tasks` | **MISSING** |
| Archive task | `POST /api/kanban/tasks/{id}/archive` | **MISSING** |
| Delete task | `DELETE /api/kanban/tasks/{id}` | **MISSING** |
| Bulk update | `POST /api/kanban/tasks/bulk` | **MISSING** |

### Server-Side Issues

1. **No task creation endpoint** (server.py) — The Android app has no way to create tasks. `MainViewModel` has no `createTask()` method. The CLI supports `hermes kanban create` but the server doesn't wrap it.

2. **No task edit endpoint** — The dashboard supports editable title/body. The server has no `PATCH /api/kanban/tasks/{id}` route. CLI supports `hermes kanban edit`.

3. **No block/unblock endpoints** — The app only has "Complete" and "Assign" actions. Missing: Block, Unblock, Move to Triage/Ready/Running. CLI supports `hermes kanban block` and `hermes kanban unblock`.

4. **No task list includes comment/link counts** — `hermes kanban list --json` returns flat task objects without comment or dependency counts. The `handle_kanban_tasks_list` handler (line 235-258) passes through CLI output unchanged. This is a CLI limitation, not a server bug.

5. **Board task counts** — `hermes kanban boards list --json` does include per-status counts. The `KanbanBoard.counts` model (Models.kt:11) handles this. The board drawer (KanbanScreen.kt:563-570) renders them. This works correctly.

6. **Server passes `HERMES_KANBAN_BOARD` env var** (server.py:152) — This is correct and matches how the CLI `--board` flag works.

---

## Summary Table

| # | Feature | Status | Severity | File:Line |
|---|---------|--------|----------|-----------|
| 1 | Priority badge on cards | MISSING | MINOR | KanbanScreen.kt:668 |
| 2 | "Created N ago" timestamp | MISSING | MINOR | KanbanScreen.kt:668, Models.kt:34 |
| 3 | Comment/link count badges | MISSING | MINOR | KanbanScreen.kt:668, Models.kt:27-36 |
| 4 | Task detail section titles | PARTIAL | MINOR | KanbanScreen.kt:361,367 |
| 5 | Body text styling | WEAK | MINOR | KanbanScreen.kt:362 |
| 6 | Column header styling | OK | — | KanbanScreen.kt:641-652 |
| 7 | Error handling on actions | OK | — | KanbanScreen.kt:107-110 |
| 8 | Loading state indicators | MISSING | MINOR | KanbanScreen.kt (global), MainViewModel.kt |
| — | Server: create task route | MISSING | MAJOR | server.py |
| — | Server: edit task route | MISSING | MAJOR | server.py |
| — | Server: block/unblock routes | MISSING | MAJOR | server.py |
| — | Server: comment counts in list | MISSING | MINOR | server.py:235-258 (CLI limitation) |

---

## Verdict: MUST-FIX

**Rationale:** While the MINOR features themselves are polish items, the server is missing critical routes (create task, edit task, block/unblock) that block MAJOR features. The Android app cannot create tasks at all, and has no way to change task status except "Complete." These server gaps must be fixed before the MINOR UI features are worth implementing — there's no point showing a priority badge on a card if users can't create or fully manage tasks.

**Recommended fix order:**
1. Add server routes: create task, edit task (title/body/priority), block, unblock
2. Add `createTask()` and `updateTask()` to `MainViewModel.kt`
3. Add loading state tracking to `MainViewModel.kt`
4. Add priority badge, timestamps, and comment counts to task cards in `KanbanScreen.kt`
5. Add section titles and activity section to task detail sheet
