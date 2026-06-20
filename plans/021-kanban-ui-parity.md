# Plan 021: Complete Kanban UI Parity

> **Executor**: Use CodeGraph MCP tools for Android at `/home/kevin/.hermes/projects/HermesCompanion`.

## Status
- **Priority**: P1 | **Effort**: L | **Risk**: MED | **Depends on**: daemon 015+016 | **Category**: feature
- **Planned at**: commit `4a85552`, 2026-06-19

## Why this matters
The Hermes dashboard kanban page has full task management: editable title/body, status changes, priority, assignee, block/unblock, archive, dependencies, decompose, comments, events timeline. The Android KanbanScreen has many of these features in the ViewModel but the UI is incomplete. This plan brings the UI to full parity.

## Current state
KanbanScreen.kt (~1800 lines) has:
- KanbanColumn with inline create (+ FAB)
- TaskCard with long-press selection mode
- Bulk selection with status/reassign actions
- Task count badges

MainViewModel.kt has methods for:
- createTask, deleteTask, completeTask, assignTask
- updateTaskTitle, updateTaskBody, updateTaskStatus, updateTaskPriority
- bulkUpdateStatus, bulkReassign
- addDependency, commentOnTask
- loadTasks, loadTask, loadStats, loadBoards

**MISSING UI features:**
- Task detail dialog/sheet (view full body, edit title/body)
- Block/unblock buttons
- Archive button
- Dependency visualization
- Decompose action
- Reclaim action
- Board switcher dropdown
- Search/filter input
- Comment thread view
- Events/runs timeline

## Scope
**In scope**: `KanbanScreen.kt`, `MainViewModel.kt`
**Out of scope**: New daemon endpoints (daemon Plans 015-016)

## Steps

### Step 1: Task Detail Bottom Sheet
Add a `TaskDetailSheet` composable that shows when a task card is tapped:
- Full title (editable inline)
- Full body (editable, multiline)
- Status dropdown
- Priority selector
- Assignee field
- Comments section (list + add)
- Action buttons: Complete, Block/Unblock, Archive, Delete

### Step 2: Board Switcher
Add a dropdown in the KanbanScreen top bar that lists all boards and allows switching. Uses existing `loadBoards()` + `setBoard()` methods.

### Step 3: Search/Filter
Add a search text field that filters tasks by title/assignee. Uses `taskSearchQuery` StateFlow (add if not exists).

### Step 4: Lifecycle Buttons
Add Block/Unblock/Archive/Reclaim action buttons to the TaskDetailSheet. Each calls the corresponding ViewModel method which hits the new daemon endpoints from Plans 015-016.

### Step 5: Dependency Visualization
In TaskDetailSheet, show parent/child task links with a simple list. Add a "Link Task" button that opens a task picker.

## Done criteria
- [ ] `./gradlew assembleDebug --no-daemon` succeeds
- [ ] `./gradlew test --no-daemon` passes
- [ ] Task detail sheet opens on tap, shows full task info
- [ ] Board switcher works
- [ ] Search filters tasks
- [ ] Block/unblock/archive buttons work (call correct endpoints)
- [ ] Install on device, navigate through kanban without crash
- [ ] `git status` clean; `git log -1` shows commit
