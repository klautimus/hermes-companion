# Plan 036: Add Missing Kanban Dashboard Features (Decompose, Specify, Runs, Events)

## Status
- **Priority**: P1 | **Effort**: L | **Risk**: MED | **Depends on**: Plans 030, 031 | **Category**: direction
- **Planned at**: commit `b09390e`, 2026-06-19

## Why this matters
The Hermes dashboard kanban has features the app doesn't: decompose, specify, runs history, events timeline. User requirement: "every feature of the Hermes dashboard kanban page must be fully incorporated."

## Current state
App supports: board CRUD, task list/show/edit/comment/assign/complete/delete, bulk ops, search, filter, stats, inline create. After plans 030-031, also: task creation + dependencies.

**Missing**:
- **Decompose** — `hermes kanban decompose TASK_ID` splits a task into subtasks
- **Specify** — `hermes kanban specify TASK_ID` adds a specification
- **Runs history** — shows execution history (which workers ran the task, when, output)
- **Events timeline** — shows status transitions, comments, assignments over time

## Scope
**In scope**: `server/server.py`, `MainViewModel.kt`, `KanbanScreen.kt`

## Steps

### Step 1: Add server endpoints (4 new routes)
```python
# POST /api/kanban/tasks/{task_id}/decompose — decompose into subtasks
# POST /api/kanban/tasks/{task_id}/specify — add specification
# GET /api/kanban/tasks/{task_id}/runs — get run history
# GET /api/kanban/tasks/{task_id}/events — get event timeline
```
Each wraps the corresponding `hermes kanban` CLI command.

### Step 2: Add ViewModel actions
```kotlin
fun decomposeTask(taskId: String) { /* POST decompose */ }
fun specifyTask(taskId: String, spec: String) { /* POST specify */ }
fun loadTaskRuns(taskId: String) { /* GET runs → _taskRuns StateFlow */ }
fun loadTaskEvents(taskId: String) { /* GET events → _taskEvents StateFlow */ }
```

### Step 3: Add UI to task detail sheet in KanbanScreen
- "Decompose" button → shows subtask count picker
- "Specify" button → shows text editor
- "Runs" tab in task detail → list of run records
- "Events" tab → chronological timeline

**Verify**: `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL

## Done criteria
- [ ] Decompose creates subtasks visible in board
- [ ] Specify adds spec to task detail
- [ ] Runs history displays
- [ ] Events timeline displays
- [ ] Server tests pass for all 4 endpoints
- [ ] APK builds clean
- [ ] `git status` CLEAN

## STOP conditions
- `hermes kanban decompose/specify` CLI commands don't exist or have different syntax
- Task runs/events require DB schema changes not present in current kanban.db
