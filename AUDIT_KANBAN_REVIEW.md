# Five-Axis Code Review: T4 + T5 + T6 Kanban Changes

**Reviewer:** Atlas (analyst)
**Date:** 2026-06-14
**Scope:** All changes from T4 (CRITICAL), T5 (MAJOR), T6 (MINOR) tasks
**Files reviewed:**
- KanbanScreen.kt (1301 lines)
- MainViewModel.kt (877 lines)
- Models.kt (181 lines)
- server/server.py (1257 lines)
- ApiClient.kt (205 lines)
- Test files (3 files, ~1040 lines total)

---

## AXIS 1: CORRECTNESS

### C-01 [CRITICAL] — PATCH /api/kanban/tasks/{id} status_cmd_map is incomplete and wrong

**File:** server.py:496-504 (handle_kanban_task_update)
```python
status_cmd_map = {
    "done": "complete",
    "blocked": "block",
    "ready": "unblock",     # WRONG: unblock only works on blocked->ready
    "todo": "promote",       # WRONG: promote moves todo->ready, not todo->todo
    "triage": "promote",     # WRONG: triage has no direct CLI mapping
    "archived": "archive",
    "archive": "archive",
}
```

**Problem:** Multiple entries are incorrect:
1. `"ready": "unblock"` — `unblock` CLI command moves a blocked task to ready. If the task is currently "todo" and you PATCH status to "ready", this will call `unblock` on a non-blocked task. The CLI has no "move to ready" command.
2. `"todo": "promote"` — `promote` moves todo->ready, not to todo. To move a task INTO todo status, there is no CLI subcommand available.
3. `"triage": "promote"` — Completely wrong. Triage has no direct CLI mapping.
4. `"running"` is missing from the map — no way to move a task to "running" status via PATCH.
5. `"scheduled"` and `"review"` are missing from the map entirely (though they're BOARD_COLNS, not STATUS_COLUMNS).

**Impact:** Any status transition that doesn't involve completing, blocking, archiving, or moving to "done" will either fail silently (falls through to `promote`) or produce incorrect behavior. Status transitions to "running", "ready" (from non-blocked), "todo", and "triage" are all broken.

**Fix required:** Either implement a direct `hermes kanban move --status <new_status>` wrapper, or document that PATCH only supports status transitions to done/blocked/other via specific endpoints. The Android app exposes "Move to..." for ALL status columns, so users will hit this.

**Severity: CRITICAL**

---

### C-02 [MAJOR] — Inline task creation dialog has no assignee or priority fields

**File:** KanbanScreen.kt:331-369, MainViewModel.kt:486-512

The inline create dialog only has a title input field and calls `viewModel.createTask(title = title, status = status)`. The spec from Finding 6 in the CRITICAL audit explicitly states:
> "Floating create dialog with title, assignee, priority"

And the MAJOR audit Finding 7 requires priority P1-P5 picker. The create dialog assigns default priority=1 and no assignee. This is a partially-implemented feature.

**Impact:** Users cannot set priority or assignee when creating tasks inline. They must open the detail sheet after creation to set these.

**Severity: MAJOR**

---

### C-03 [MAJOR] — handle_kanban_task_show unwraps {"task": {...}} but handle_kanban_task_detail re-wraps it differently

**File:** server.py:273 vs server.py:780-786

There are TWO task detail endpoints:
1. `GET /api/kanban/tasks/{task_id}` (handle_kanban_task_show, line 261) — unwraps `{"task": {...}}` and returns flat JSON.
2. `GET /api/kanban/tasks/{task_id}` (handle_kanban_task_detail, line 767) — registered at the SAME route pattern. Whichever is registered LAST in the route table wins.

**Actual route table (line 1219):**
```python
app.router.add_get("/api/kanban/tasks/{task_id}", handle_kanban_task_detail)
```

So `handle_kanban_task_show` is DEAD CODE — it's registered before `handle_kanban_task_detail` and gets overridden. The detail handler returns a different response shape (includes `task`, `comments`, `events`, `links`, `runs`, `attachments` as separate top-level keys) vs the flat `TaskShowResponse` model.

**Impact:** The Android `loadTask()` calls `GET /api/kanban/tasks/$taskId?board=...` which expects a flat `TaskShowResponse`. But `handle_kanban_task_detail` returns `{"task": {...}, "comments": [...], ...}`. This means the `task` object is nested under a `"task"` key, but `loadTask` does:
```kotlin
_selectedTask.value = json.decodeFromString<TaskShowResponse>(raw)
```
This will fail or misparse because the JSON has extra top-level keys. The `comments`, `events`, etc. are at the top level alongside `"task"`, not inside it.

**Wait — rechecking line 780-786:** The handler sets `task_data` from `task_data["task"]`, then separately builds `comments`, `events` etc. from the outer `task_data` (which is the raw parsed JSON). But `task_data.get("comments", [])` will look at the raw JSON — if Hermes returns flat task JSON with `comments` inside the `task` object, this fails. Actually: the `show --json` output from Hermes is flat (includes `comments`, `events` etc. as fields on the task). The handler does `task_data = json.loads(out)` then checks for `"task"` key. If `hermes kanban show` returns `{"task": {...}}`, it unwraps. If it returns flat, it uses directly.

**The real issue:** `task_data.get("comments", [])` at line 802 searches the OUTER dict. If the response is `{"task": {"comments": [...]}, "extra_field": ...}`, then after unwrapping: `task_obj = data["task"]` but `comments` is looked up from the OUTER `task_data` which has NO `comments` key — comments are inside `task_obj`. So comments = `[]` always.

Actually wait, re-reading more carefully at line 801-802:
```python
raw_comments = task_data.get("comments", []) if isinstance(task_data, dict) else []
```
Here `task_data` is the variable set at line 780-784, which is `task_data["task"]` if the task key exists, or the raw data. If the raw Hermes output is `{"task": {"id": "...", "comments": [...]}}`, then `task_data = {"task": {...}}`, so `isinstance(data, dict) and "task" in data` is True, and `task_obj = data["task"]` = the inner dict. But then at line 802, `task_data.get("comments", [])` refers to the variable `task_data` which was REASSIGNED at line 782 to `task_data["task"]` (the inner dict). Wait no — let me re-read:

Line 780: `task_data = json.loads(out)` — this is the flat JSON
Line 781-784: `if isinstance(task_data, dict) and "task" in data: task_obj = data["task"]` — NOTE: this uses `data` not `task_data`. `data` is undefined. This is a bug.

Actually the naming is confusing. Let me trace through exactly:
- Line 773: `task_data = json.loads(out)` — raw response
- Line 780-784: The variable is `task_data` not `data` on line 780. Line 781 checks `data` which doesn't exist... Wait, the code at 780 says:
```python
    task_data = json.loads(out)
    if isinstance(task_data, dict) and "task" in task_data:
        task_obj = task_data["task"]
    else:
        task_obj = task_data
```
So `task_data` is the raw parsed JSON. If it has a `"task"` key, `task_obj` is the inner dict. Then line 802: `raw_comments = task_data.get("comments", [])` — this still references the OUTER dict, not `task_obj`. So if comments are inside the task object, `task_data.get("comments")` returns `[]`.

**The outcome is likely correct most of the time** because `hermes kanban show --json` returns a flat object with comments/events baked in. So `task_data` IS the task, and `task_obj = task_data`, and `task_data.get("comments")` works fine. But the dead code path (unwrapping) is broken — if Hermes ever adds a wrapper, it will silently lose comments.

**Updated severity: MINOR** (fragile but likely works with current Hermes output)

---

### C-04 [MAJOR] — handle_kanban_task_update: "ready" -> "unblock" is semantically wrong for non-blocked tasks

**File:** server.py:499

When PATCH receives `status: "ready"`, it calls `hermes kanban unblock <task_id>`. The `unblock` CLI command:
- If the task is blocked: moves it to ready/scheduled
- If the task is NOT blocked: behavior depends on Hermes (likely error or no-op)

So moving a task from "todo" → "ready" via the move dropdown will silently fail or error on the server side. The client will show an error toast. The `status_cmd_map` entry `"ready": "unblock"` is fundamentally wrong.

**Impact:** A common workflow (moving a task from To Do to Ready) will fail silently.

**Severity: MAJOR** (related to C-01 but documenting separately)

---

### C-05 [MINOR] — KanbanScreen.kt unused imports: BorderStroke, border

**File:** KanbanScreen.kt:29-30

```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
```

These imports from the T6 task description were noted as removed ("Removed unused BorderStroke/border imports"). But they're still present at lines 29-30. They are dead imports — nothing in the file uses `BorderStroke` or `border`.

**Impact:** Dead code, no runtime impact. Increases compile time negligibly.

**Severity: MINOR**

---

### C-06 [MINOR] — handle_kanban_task_detail: dead code path (lines 803, 813)

**File:** server.py:803, 813

The comments/events extraction at lines 801-822 reads from `task_data` (the outer dict) instead of `task_obj` (the unwrapped inner dict). Because `hermes kanban show --json` returns flat task JSON, `task_data` and `task_obj` point to the same object, so this works. But if the Hermes output format changes, this silently breaks.

**Impact:** Latent bug. Works by coincidence.

**Severity: MINOR**

---

## AXIS 2: COMPLETENESS

### F-01 [CRITICAL] — No unit tests for new kanban methods

**Files:** MainViewModelTest.kt, test coverage gap

The following new ViewModel methods have ZERO test coverage:
- `updateTask()` — PATCH endpoint call
- `createTask()` — POST endpoint call
- `blockTask()` / `blockTaskWithReason()` — block endpoint
- `unblockTask()` — unblock endpoint
- `moveTask()` — move endpoint
- `archiveTask()` — archive endpoint
- `deleteTask()` — delete endpoint
- `setPriority()` — priority endpoint
- `selectTaskSelection()`, `enterSelectionMode()`, `exitSelectionMode()`, `selectAll()`, `deselectAll()` — selection mode
- `bulkComplete()`, `bulkAssign()`, `bulkArchive()` — bulk operations
- `linkTasks()`, `unlinkTasks()` — dependency links
- `loadProfiles()` — profile loading
- `setSearchQuery()`, `setAssigneeFilter()` — search/filter

That's ~20 new methods with no tests. The existing tests only cover default state, `setBoard`, `loadBoards`, `loadTasks`, `completeTask`, `commentOnTask`, `assignTask`, and JSON serialization. The new methods introduced in T4/T5/T6 are completely untested.

**Impact:** No regression coverage. Any breaking change to these methods will go undetected.

**Severity: CRITICAL** (test coverage gap for 20+ methods)

---

### F-02 [MAJOR] — assignTask with empty string assignee doesn't actually unassign

**File:** KanbanScreen.kt:743-746, server.py:423-444

When the user selects "Unassigned" from the assignee dropdown:
```kotlin
viewModel.assignTask(task.id, "")
```
This sends `{"assignee": ""}` to the server. The server handler:
```python
assignee = body.get("assignee", "")
if not assignee:
    return web.json_response({"error": ...}, status=422)
```
Empty string is falsy, so it returns 422. The "Unassign" option in the UI doesn't work.

**Impact:** Users cannot unassign a task from the detail sheet dropdown.

**Severity: MAJOR**

---

### F-03 [MAJOR] — No "Add Dependency" UI button in task detail

**File:** KanbanScreen.kt:774-828

The task detail sheet shows existing parents/children (if any), but there is no "Add link" button or "Add dependency" action. The spec from MAJOR Finding 3 states:
> "Add link" button -> task picker dialog

The `linkTasks()` ViewModel method exists and the server has the endpoint, but there's no UI to trigger it. The `addDependencyDialog` state variable exists (line 158) but is never set to `true` by any UI element.

**Impact:** Users can see existing dependencies but cannot create new ones from the app.

**Severity: MAJOR**

---

### F-04 [MINOR] — No "Created N ago" timestamp on task cards when created_at is null

**File:** KanbanScreen.kt:1269-1273

The relative timestamp display:
```kotlin
formatRelativeTime(task.created)?.let { relTime ->
```
If `task.created` is null (which it can be for tasks created before the field was added), no timestamp is shown. This is correct behavior (no data = no display), but there's no fallback or loading state for the timestamp.

**Impact:** Cosmetic. Tasks without `created_at` data simply don't show a timestamp.

**Severity: MINOR** (acceptable)

---

### F-05 [MINOR] — Inline create dialog missing keyboard submit action

**File:** KanbanScreen.kt:336-344

The inline create dialog's `OutlinedTextField` has `imeAction = ImeAction.Done` but no `keyboardActions` to handle the Done action. The user must tap the "Create" button. This is a minor UX friction.

**Impact:** UX polish. Not a functional issue.

**Severity: MINOR**

---

## AXIS 3: SECURITY

### S-01 [MAJOR] — handle_kanban_task_update: no input validation on body fields

**File:** server.py:485-557

The PATCH handler accepts arbitrary JSON body fields and passes them through:
```python
if body.get("status") is not None:
    new_status = body["status"]
    cmd = status_cmd_map.get(new_status)
```
There's no validation that `status` is a valid status string. An attacker could send `{"status": "../../etc/passwd"}` — while the `status_cmd_map.get()` would return None and fall through to `promote`, the value is passed to `_kanban([cmd, task_id])` which uses subprocess with a validated command name. So this is safe from command injection.

However, the `assignee` field passes directly to `_kanban(["assignee", task_id, body["assignee"]])`. The assignee string is passed as a subprocess argument (not shell-interpolated), so command injection is not possible. But there's no length validation — an attacker could send a 10MB assignee string.

**Impact:** Low actual risk (subprocess args are not shell-interpolated). But defense-in-depth would require length limits.

**Severity: MAJOR** (defense-in-depth gap)

---

### S-02 [MINOR] — Comment text length limit (10KB) is generous

**File:** server.py:305-309

```python
if len(text) > 10240:
    return web.json_response({"error": ...}, status=422)
```

10KB for a comment is very generous. This isn't a security issue per se, but could allow abuse if the API is exposed without rate limiting.

**Impact:** Low. Defense-in-depth consideration.

**Severity: MINOR**

---

### S-03 [OK] — Board slug validation is solid

**File:** server.py:336-340

The `_validate_slug()` function correctly validates slug format: `[a-z0-9-]`, max 64 chars, no leading/trailing hyphens. This prevents path traversal and injection via board slugs.

**Severity: OK**

---

### S-04 [OK] — Attachment ID validation prevents path traversal

**File:** server.py:1101-1105, 1164-1168

Both attachment handlers validate `att_id` with `re.match(r"^att_[0-9a-f]+$", att_id)` before filesystem operations. The serve handler also double-checks resolved path is within ATTACHMENTS_DIR.

**Severity: OK**

---

## AXIS 4: TEST COVERAGE

### T-01 [CRITICAL] — 20+ new ViewModel methods have zero test coverage

**File:** MainViewModelTest.kt

See F-01 above. The test file has ~50 tests, all for pre-existing methods. Every method added in T4/T5/T6 is untested. Key missing tests:
- `updateTask()` — does it call PATCH with correct body?
- `createTask()` — does it call POST with correct body?
- `blockTask()` / `unblockTask()` — do they call correct endpoints?
- `moveTask()` — does it POST to /move with status body?
- `bulkComplete()` / `bulkAssign()` / `bulkArchive()` — do they POST to /bulk with correct JSON?
- `setPriority()` — does it POST to /priority with correct body?
- `linkTasks()` / `unlinkTasks()` — do they call correct endpoints?
- `assignTask("")` — does it handle empty assignee (unassign)?
- `selectTaskSelection()`, `enterSelectionMode()`, `exitSelectionMode()` — selection state transitions
- `setSearchQuery()`, `setAssigneeFilter()` — filter state updates

**Impact:** No regression safety net for the entire kanban feature set.

**Severity: CRITICAL**

---

### T-02 [MAJOR] — No server-side tests at all

**File:** server/server.py

There are zero tests for any server handler. The server has 30+ route handlers and complex logic (status transitions, bulk operations, attachment handling). A single test file for the server would catch regressions in:
- Status command mapping
- Request validation
- Error handling
- JSON response formatting

**Impact:** Server changes are completely unguarded.

**Severity: MAJOR**

---

### T-03 [MINOR] — ModelsTest doesn't test new data classes

**File:** ModelsTest.kt

The test file covers `KanbanBoard`, `KanbanTask`, `TaskShowResponse`, `HermesSession`, `SessionMessages`, `CompanionHealth`. Missing coverage for:
- `TaskRef` — used by parents/children in TaskShowResponse
- `Run` — used in task detail
- `Attachment` — used in task detail
- `DependencyLinks` — used in task detail
- `LinkCounts` — used in KanbanTask
- `Age` — used in KanbanTask
- `Progress` — used in KanbanTask

**Impact:** Serialization round-trip not tested for new models.

**Severity: MINOR**

---

## AXIS 5: INTEGRATION

### I-01 [MAJOR] — Android PATCH vs server status_cmd_map mismatch

**File:** MainViewModel.kt:514-542, server.py:485-557

The Android `updateTask()` sends a PATCH with arbitrary `status` values (e.g., "running", "ready", "todo", "triage"). The server's `status_cmd_map` only handles `done`, `blocked`, `ready`, `todo`, `triage`, `archived`, `archive`. Missing: `running`, `scheduled`, `review`.

When the user uses "Move to..." in the context menu or detail sheet, the app calls `moveTask()` (POST /move endpoint) which works correctly. But if `updateTask()` is called with a status change (e.g., from the PATCH-based update), it will fail for "running".

**Impact:** Status transitions via PATCH are broken for some statuses. The "Move to..." feature works via POST /move, so the primary user flow is OK. But the PATCH endpoint is unreliable.

**Severity: MAJOR**

---

### I-02 [MAJOR] — Two handlers registered for same route: handle_kanban_task_show vs handle_kanban_task_detail

**File:** server.py:1219

```python
app.router.add_get("/api/kanban/tasks/{task_id}", handle_kanban_task_detail)
```

The `handle_kanban_task_show` (line 261) is registered at line 1219 as... wait, checking the route table again:

Line 1219: `app.router.add_get("/api/kanban/tasks/{task_id}", handle_kanban_task_detail)`

There's only ONE registration for this route. `handle_kanban_task_show` is defined but never registered in the route table. It's dead code.

**Impact:** The simpler `handle_kanban_task_show` is dead code. The richer `handle_kanban_task_detail` is the one that runs. This is not a bug per se, but dead code in the server.

**Severity: MINOR** (dead code, not a functional issue)

---

### I-03 [MAJOR] — Android expects flat TaskShowResponse but server returns wrapped format

**File:** MainViewModel.kt:438, server.py:869-876

Android `loadTask()`:
```kotlin
_selectedTask.value = json.decodeFromString<TaskShowResponse>(raw)
```

Server `handle_kanban_task_detail` returns:
```python
return web.json_response({
    "task": task_obj,
    "comments": comments,
    "events": events,
    "attachments": attachments,
    "links": links,
    "runs": runs,
})
```

The Android expects a flat JSON object matching `TaskShowResponse` (with `comments`, `events` etc. as fields on the same object). The server returns these as SEPARATE top-level keys. This means `decodeFromString<TaskShowResponse>` will either:
1. Parse the `"task"` key as an unknown field (ignored due to `ignoreUnknownKeys = true`)
2. Miss `comments`, `events` etc. because they're at the wrong level

**Wait — re-checking:** The `TaskShowResponse` model has `comments`, `events`, `parents`, `children` etc. as fields. The server puts them at the top level alongside `"task"`. So the raw JSON looks like:
```json
{"task": {"id": "...", ...}, "comments": [...], "events": [...], ...}
```

When Kotlin tries to decode this as `TaskShowResponse`, it will look for `id`, `title`, `comments`, `events` etc. at the top level. `id` and `title` are inside `"task"` — they won't be found. `comments` and `events` ARE at the top level — they will be parsed. But `id` will default to `""`, `title` to `""`, etc.

**This is a critical integration bug.** The task detail will show empty title, empty status, etc.

**Actually wait — let me re-examine.** The `handle_kanban_task_detail` handler at line 869 returns the full structure. But the Android `loadTask()` at line 438 does:
```kotlin
val raw = c.get("/api/kanban/tasks/$taskId?board=$b")
_selectedTask.value = json.decodeFromString<TaskShowResponse>(raw)
```

The raw response from the server is `{"task": {...}, "comments": [...], ...}`. Kotlin's `decodeFromString<TaskShowResponse>` with `ignoreUnknownKeys = true` will:
- Ignore `"task"` key (unknown to TaskShowResponse)
- Parse `"comments"` from top level — this works
- Parse `"events"` from top level — this works
- But `id`, `title`, `status`, `body`, `parents`, `children` are INSIDE the `"task"` object, not at top level

So the TaskShowResponse will have: `id = ""`, `title = ""`, `status = ""`, `body = null`, `parents = []`, `children = []`, but `comments = [...]` and `events = [...]`.

**This is a CRITICAL integration bug.** The task detail sheet will show blank title, blank status, no description — but will show comments and events.

**Severity: CRITICAL**

**Root cause:** The server's `handle_kanban_task_detail` wraps the task in `{"task": ...}` but the Android expects flat JSON. Either the server should return flat JSON (merge task fields with comments/events at top level), or the Android should unwrap the `"task"` key first.

---

### I-04 [MINOR] — Bulk action endpoints: Android uses POST /bulk but server expects specific body format

**File:** MainViewModel.kt:745-804, server.py:588-625

Android sends:
```json
{"action": "complete", "task_ids": ["id1", "id2"]}
```

Server expects:
```python
action = body.get("action", "")
task_ids = body.get("task_ids", [])
```

This matches. The integration is correct for the bulk endpoint.

**Severity: OK**

---

### I-05 [MINOR] — Priority endpoint: Android POSTs to /priority, server handles via edit --metadata

**File:** MainViewModel.kt:606-624, server.py:560-585

Android sends `{"priority": 3}` to `POST /api/kanban/tasks/{id}/priority`. Server calls `hermes kanban edit <id> --metadata '{"priority": 3}'`. This is a best-effort approach — the server notes "Priority can't be changed via CLI directly; store in metadata" and "If edit fails (task not done), silently skip - priority is best-effort."

**Impact:** Priority changes may silently fail for non-done tasks. The UI will show the new priority chip as selected, but the server may not persist it.

**Severity: MINOR** (known limitation, documented in code comments)

---

## SUMMARY

| # | Finding | Severity | Axis | File:Line |
|---|---------|----------|------|-----------|
| C-01 | status_cmd_map incomplete/wrong for PATCH | CRITICAL | Correctness | server.py:496-504 |
| C-02 | Inline create missing assignee/priority | MAJOR | Correctness | KanbanScreen.kt:331-369 |
| C-03 | handle_kanban_task_detail variable naming bug | MINOR | Correctness | server.py:780-802 |
| C-04 | "ready" -> "unblock" semantically wrong | MAJOR | Correctness | server.py:499 |
| C-05 | Unused BorderStroke/border imports | MINOR | Correctness | KanbanScreen.kt:29-30 |
| C-06 | Comments/events read from wrong dict level | MINOR | Correctness | server.py:801-822 |
| F-01 | 20+ new methods have zero test coverage | CRITICAL | Completeness | MainViewModelTest.kt |
| F-02 | assignTask("") returns 422, can't unassign | MAJOR | Completeness | server.py:433 |
| F-03 | No "Add Dependency" UI button | MAJOR | Completeness | KanbanScreen.kt:774 |
| F-04 | No timestamp fallback for null created_at | MINOR | Completeness | KanbanScreen.kt:1269 |
| F-05 | Inline create missing keyboard submit | MINOR | Completeness | KanbanScreen.kt:336 |
| S-01 | No input length validation on PATCH body | MAJOR | Security | server.py:485 |
| S-02 | Comment text 10KB limit generous | MINOR | Security | server.py:305 |
| S-03 | Slug validation is solid | OK | Security | server.py:336 |
| S-04 | Attachment ID validation prevents traversal | OK | Security | server.py:1101 |
| T-01 | 20+ new methods have zero test coverage | CRITICAL | Test Coverage | MainViewModelTest.kt |
| T-02 | No server-side tests at all | MAJOR | Test Coverage | server/ |
| T-03 | New data classes not tested | MINOR | Test Coverage | ModelsTest.kt |
| I-01 | PATCH status transitions broken for some | MAJOR | Integration | server.py:496 |
| I-02 | handle_kanban_task_show is dead code | MINOR | Integration | server.py:261 |
| I-03 | Android expects flat JSON, server returns wrapped | CRITICAL | Integration | server.py:869 |
| I-04 | Bulk endpoint integration correct | OK | Integration | server.py:588 |
| I-05 | Priority best-effort via metadata | MINOR | Integration | server.py:543 |

---

## VERDICT: MUST-FIX

### CRITICAL findings (3):
1. **C-01**: PATCH status_cmd_map is incomplete and wrong — breaks status transitions to running/ready/triage/todo
2. **F-01/T-01**: 20+ new ViewModel methods have zero test coverage — no regression safety net
3. **I-03**: Android expects flat TaskShowResponse JSON but server returns wrapped format — task detail shows blank title/status

### MAJOR findings (8):
1. **C-02**: Inline create dialog missing assignee and priority fields
2. **C-04**: "ready" -> "unblock" mapping is semantically wrong for non-blocked tasks
3. **F-02**: Cannot unassign tasks (empty assignee returns 422)
4. **F-03**: No "Add Dependency" UI button despite backend support
5. **S-01**: No input length validation on PATCH body fields
6. **T-02**: No server-side tests at all
7. **I-01**: PATCH status transitions broken for running/scheduled/review
8. **C-01** also affects integration axis

### Missing features still needing implementation:
1. **Fix I-03**: Server must return flat JSON for task detail (merge task fields with comments/events at top level), OR Android must unwrap `{"task": ...}` before parsing
2. **Fix C-01**: Implement proper status transition support in PATCH handler (use `hermes kanban move --status` as the universal status transition)
3. **Fix F-02**: Allow empty assignee for unassigning (change server validation to accept empty string)
4. **Fix F-03**: Add "Add Dependency" button in task detail sheet
5. **Add tests**: Minimum one test per new ViewModel method + server handler
6. **Fix C-02**: Add assignee dropdown and priority picker to inline create dialog
