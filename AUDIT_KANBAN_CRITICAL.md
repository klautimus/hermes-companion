# Kanban Feature Parity Audit — CRITICAL Features

**Generated:** 2025-06-14
**Scope:** Audit of 6 CRITICAL parity gaps from KANBAN_PARITY_SPEC.md
**Files audited:**
- KanbanScreen.kt (687 lines)
- MainViewModel.kt (491 lines)
- Models.kt (98 lines)
- server/server.py (565 lines)

---

## SUMMARY: MUST-FIX

All 6 CRITICAL features are missing or incomplete. The app cannot perform basic kanban workflow without these fixes.

---

## FINDING 1: CRITICAL — Triage Column Missing Entirely

**Severity:** CRITICAL
**Files:** KanbanScreen.kt:34, KanbanColumn @631
**Spec ref:** Spec line 9, 105

**Current code (KanbanScreen.kt:34):**
```kotlin
private val STATUS_COLUMNS = listOf("todo", "ready", "running", "blocked", "done")
```

**Dashboard has:** triage, todo, ready, running, blocked, done (+ archived toggle)

**Fix required:**
1. Add `"triage"` to STATUS_COLUMNS at correct position (index 0)
2. Add STATUS_LABELS entry: `"triage" to "Triage"`
3. Add STATUS_COLORS entry for triage column
4. Ensure server.py KanbanCounts model includes triage (already has it at line 18)

**Implementation approach:**
```kotlin
private val STATUS_COLUMNS = listOf("triage", "todo", "ready", "running", "blocked", "done")
private val STATUS_LABELS = mapOf(
    "triage" to "Triage", "todo" to "To Do", "ready" to "Ready",
    "running" to "Running", "blocked" to "Blocked", "done" to "Done"
)
private val STATUS_COLORS = mapOf(
    "triage" to Color(0xFF94E2D5),  // teal from BOARD_COLORS
    "todo" to Color(0xFFF9E2AF),
    "ready" to Color(0xFF89B4FA),
    "running" to Color(0xFFCBA6F7),
    "blocked" to Color(0xFFF38BA8),
    "done" to Color(0xFFA6E3A1),
)
```

---

## FINDING 2: CRITICAL — Task Count Badges Per Column

**Severity:** CRITICAL
**Files:** KanbanScreen.kt:647-650 (KanbanColumn), KanbanScreen.kt:563-570 (BoardDrawer)
**Spec ref:** Spec line 97-98, 106

**Current state:** Column headers show task count via `${tasks.size}` at line 648-649 — THIS PART WORKS.

**But:** The spec asks for "Task count badges per column" and "Columns show no counts" is marked MISSING.

**Analysis:** Actually the column headers DO show counts (line 648). The BoardDrawer shows per-status chips at lines 563-570. This finding may be outdated in the spec.

**Verdict:** PARTIALLY IMPLEMENTED — column counts work. Board drawer counts work. No action needed for this finding.

---

## FINDING 3: CRITICAL — Task Detail: Editable Title/Body/Description

**Severity:** CRITICAL
**Files:** KanbanScreen.kt:341-416 (Task Detail Bottom Sheet)
**Spec ref:** Spec line 37-40, 108

**Current state:** Task detail shows title (line 345) and body (line 362) as READ-ONLY Text composables. NO click-to-edit.

**Missing:**
- Click title → inline edit
- Click body → inline edit (markdown render + edit per spec)
- Save changes back to server

**Fix required:** Add editable state to TaskDetailBottomSheet:
1. Add `var editingTitle by remember { mutableStateOf(false) }`
2. Add `var editingBody by remember { mutableStateOf(false) }`
3. Add `var editTitleText by remember { mutableStateOf(task.title) }`
4. Add `var editBodyText by remember { mutableStateOf(task.body ?: "") }`
5. Replace Text with OutlinedTextField when editing
6. Add save button calling new ViewModel method `updateTaskTitle(taskId, title)` and `updateTaskBody(taskId, body)`

**Server support needed:** Need PATCH /api/kanban/tasks/{task_id} endpoint in server.py (currently MISSING — see Finding 6)

---

## FINDING 4: CRITICAL — Task Detail: All Status Transitions

**Severity:** CRITICAL
**Files:** KanbanScreen.kt:399-411 (Task Detail actions), MainViewModel.kt:399-438
**Spec ref:** Spec line 41, 109

**Current state:** Only "Complete" button exists (lines 400-405). "Assign" button exists (lines 406-410).

**Missing status actions:**
- Block
- Unblock
- Move to Triage
- Move to Ready
- Move to Running
- Move to Done (Complete exists)
- Archive task

**MainViewModel only has:** `completeTask()` (line 399) and `assignTask()` (line 425)

**Fix required:**
1. Add ViewModel methods for each status transition:
   - `blockTask(taskId)`
   - `unblockTask(taskId)`
   - `moveTask(taskId, newStatus)`
   - `archiveTask(taskId)`
2. Add server endpoints in server.py:
   - POST /api/kanban/tasks/{task_id}/block
   - POST /api/kanban/tasks/{task_id}/unblock
   - POST /api/kanban/tasks/{task_id}/move (with status param)
   - POST /api/kanban/tasks/{task_id}/archive
3. Add status action row in TaskDetailBottomSheet with dropdown/menu

---

## FINDING 5: CRITICAL — Drag & Drop / Long-Press Context Menu

**Severity:** CRITICAL
**Files:** KanbanScreen.kt:631-687 (KanbanColumn), KanbanScreen.kt:661-677 (task cards)
**Spec ref:** Spec line 63-64, 107

**Current state:** Task cards are clickable (line 663) opening detail sheet. NO drag-drop. NO long-press menu.

**Missing:**
- Drag card between columns (HTML5 drag-drop equivalent for Compose)
- Long-press context menu for status change

**Fix required:**
1. Use `Modifier.draggable()` or `DragAndDrop` APIs (Compose 1.6+)
2. Add `Modifier.combinedClickable(onLongClick = { ... })` to task cards
3. Show DropdownMenu with status options on long-press
4. On drop/selection, call ViewModel `moveTask(taskId, newStatus)`

**Implementation approach:**
```kotlin
// In KanbanColumn task card:
Card(
    modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = { onTaskClick(task.id) },
            onLongClick = { onTaskLongPress(task) }
        )
        .draggable(...)
)
```

---

## FINDING 6: CRITICAL — Inline Task Creation

**Severity:** CRITICAL
**Files:** KanbanScreen.kt:113-122 (column rendering), KanbanColumn @631
**Spec ref:** Spec line 55-58, 110

**Current state:** NO + button on column headers. No inline create dialog.

**Missing:**
- + button on each column header
- Floating create dialog with title, assignee, priority
- Parent task dropdown (for subtasks)

**Fix required:**
1. Modify `KanbanColumn` composable to accept `onCreateClick: (String) -> Unit` parameter
2. Add IconButton with `Icons.Filled.Add` in column header (near count badge)
3. Add create task dialog state in KanbanScreen
4. Add ViewModel `createTask(board, status, title, assignee, priority, parentId)`
5. Add server endpoint: POST /api/kanban/tasks

---

## SERVER.PY GAPS (Backend Support Required)

**Current server.py endpoints (lines 539-549):**
- GET /api/kanban/boards ✅
- POST /api/kanban/boards ✅
- POST /api/kanban/boards/{slug}/rename ✅
- POST /api/kanban/boards/{slug}/archive ✅
- DELETE /api/kanban/boards/{slug} ✅
- GET /api/kanban/tasks ✅
- GET /api/kanban/tasks/{task_id} ✅
- POST /api/kanban/tasks/{task_id}/complete ✅
- POST /api/kanban/tasks/{task_id}/comment ✅
- POST /api/kanban/tasks/{task_id}/assign ✅

**MISSING endpoints needed for CRITICAL features:**
1. POST /api/kanban/tasks — Create task (for Finding 6)
2. PATCH /api/kanban/tasks/{task_id} — Update title/body/priority (for Finding 3)
3. POST /api/kanban/tasks/{task_id}/block — Block task (for Finding 4)
4. POST /api/kanban/tasks/{task_id}/unblock — Unblock task (for Finding 4)
5. POST /api/kanban/tasks/{task_id}/move — Move to any status (for Finding 4, 5)
6. POST /api/kanban/tasks/{task_id}/archive — Archive task (for Finding 4)

**Implementation:** Add _kanban wrapper calls for each new CLI command:
```python
# In server.py _kanban() calls:
["create", "--title", title, "--status", status, "--assignee", assignee, "--priority", priority]
["update", task_id, "--title", title, "--body", body, "--priority", priority]
["block", task_id]
["unblock", task_id]
["move", task_id, "--status", new_status]
["archive", task_id]
```