# Plan 012: Complete Kanban Parity Features (Phase 1: Missing Columns + Task Detail)

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `cd ~/.hermes/projects/HermesCompanion && git diff --stat e44d810..HEAD -- app/src/main/java/org/hermes/community/companion/KanbanScreen.kt app/src/main/java/org/hermes/community/companion/data/Models.kt app/src/main/java/org/hermes/community/companion/MainViewModel.kt`

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED
- **Depends on**: none
- **Category**: feature (kanban parity per KANBAN_PARITY_SPEC.md)
- **Planned at**: commit `e44d810`, 2026-06-19

## Why this matters

The kanban screen is missing critical columns and task detail features documented in KANBAN_PARITY_SPEC.md. The Hermes Dashboard has 8 columns (triage, todo, scheduled, ready, running, blocked, review, done) but the companion app's `KanbanCounts` model only has 7 (missing `scheduled` and `review`). The task detail sheet is missing editable title/body, status actions, assignee dropdown, and priority editing.

## Current state

### Models.kt KanbanCounts (lines 16-25)
```kotlin
data class KanbanCounts(
    val triage: Int = 0,
    val todo: Int = 0,
    val ready: Int = 0,
    val running: Int = 0,
    val blocked: Int = 0,
    val done: Int = 0,
    val archived: Int = 0,
)
```
Missing: `scheduled` and `review`.

### MainViewModel.kt STATUSES (line 23)
```kotlin
private val STATUSES = listOf("triage", "todo", "scheduled", "ready", "running", "blocked", "review", "done")
```
This list HAS all 8 statuses — but `KanbanCounts` doesn't have `scheduled` or `review` fields to display their counts.

### KanbanScreen.kt
Renders columns dynamically. The `tasksByStatus` flow groups tasks by status string. The board list shows `KanbanCounts` as column badges. Missing counts for `scheduled` and `review` means those columns show 0 even when tasks exist.

### KANBAN_PARITY_SPEC.md gaps (9 critical features)
1. Triage column missing from companion
2. Scheduled column missing
3. Review column missing
4. Editable task title (inline)
5. Editable task body (inline)
6. Status action buttons (move to triage/todo/ready/running/blocked/review/done)
7. Assignee dropdown
8. Priority edit (0=normal, 1=high, 2=urgent)
9. Task metadata chips (age, progress, started_at, warnings)

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew assembleDebug` | BUILD SUCCESSFUL    |
| Unit test | `./gradlew test`          | all pass            |

## Suggested executor toolkit

### CodeGraph-first codebase exploration (mandatory)

1. `mcp_codegraph_codegraph_status(projectPath="/home/kevin/.hermes/projects/HermesCompanion")`
2. `mcp_codegraph_codegraph_explore(query="KanbanCounts KanbanColumn KanbanScreen TaskDetailSheet statusActions", projectPath="/home/kevin/.hermes/projects/HermesCompanion")`
3. `mcp_codegraph_codegraph_context(task="how does the kanban task detail sheet render? what actions are available? what fields are editable?", projectPath="/home/kevin/.hermes/projects/HermesCompanion")`

## Scope

**In scope**:
- `app/src/main/java/org/hermes/community/companion/data/Models.kt` — add `scheduled` and `review` to KanbanCounts
- `app/src/main/java/org/hermes/community/companion/KanbanScreen.kt` — add missing columns + task detail features
- `app/src/main/java/org/hermes/community/companion/MainViewModel.kt` — verify all 8 statuses are handled in task grouping

**Out of scope**:
- Daemon changes (PATCH endpoint already supports title/body/priority updates)
- Board management UI (already implemented)
- Search/filter (already implemented per T13 reference in KanbanScreen.kt)

## Steps

### Step 1: Add `scheduled` and `review` to KanbanCounts

In `Models.kt`, update `KanbanCounts`:

```kotlin
data class KanbanCounts(
    val triage: Int = 0,
    val todo: Int = 0,
    val scheduled: Int = 0,
    val ready: Int = 0,
    val running: Int = 0,
    val blocked: Int = 0,
    val review: Int = 0,
    val done: Int = 0,
    val archived: Int = 0,
)
```

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 2: Ensure all 8 status columns render in KanbanScreen

In `KanbanScreen.kt`, verify the column rendering loop iterates over ALL 8 statuses. Check if it uses the `STATUSES` list from MainViewModel or a hardcoded list.

If columns are rendered from a hardcoded list, replace with:
```kotlin
val COLUMN_ORDER = listOf("triage", "todo", "scheduled", "ready", "running", "blocked", "review", "done")
```

Ensure each column renders its tasks from `tasksByStatus[status]`.

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 3: Add editable title to task detail sheet

In `KanbanScreen.kt`, in the task detail composable (the bottom sheet or modal), replace the static `Text(task.title)` with an editable field:

```kotlin
var editTitle by remember { mutableStateOf(task.title) }
OutlinedTextField(
    value = editTitle,
    onValueChange = { editTitle = it },
    label = { Text("Title") },
    modifier = Modifier.fillMaxWidth(),
    trailingIcon = {
        if (editTitle != task.title) {
            IconButton(onClick = {
                viewModel.updateTask(task.id, title = editTitle)
            }) {
                Icon(Icons.Filled.Save, contentDescription = "Save title")
            }
        }
    }
)
```

This calls `MainViewModel.updateTask(id, title=...)` which sends `PATCH /api/kanban/tasks/{id}` with `{"title": "..."}`.

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 4: Add editable body to task detail

Same pattern as Step 3 but for the task body field:

```kotlin
var editBody by remember { mutableStateOf(task.body ?: "") }
OutlinedTextField(
    value = editBody,
    onValueChange = { editBody = it },
    label = { Text("Description") },
    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
    trailingIcon = {
        if (editBody != (task.body ?: "")) {
            IconButton(onClick = {
                viewModel.updateTask(task.id, body = editBody)
            }) {
                Icon(Icons.Filled.Save, contentDescription = "Save body")
            }
        }
    }
)
```

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 5: Add status action buttons row

In the task detail, add a horizontal row of status action buttons:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    MainViewModel.STATUSES.forEach { status ->
        FilterChip(
            selected = task.status == status,
            onClick = { viewModel.updateTaskStatus(task.id, status) },
            label = { Text(status.replaceFirstChar { it.uppercase() }) }
        )
    }
}
```

Note: `MainViewModel.STATUSES` is currently `private`. Change it to `internal` or `companion object` accessible.

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 6: Add priority dropdown

```kotlin
var priorityExpanded by remember { mutableStateOf(false) }
val priorityLabels = mapOf(0 to "Normal", 1 to "High", 2 to "Urgent")

Box {
    OutlinedButton(onClick = { priorityExpanded = true }) {
        Text("Priority: ${priorityLabels[task.priority] ?: "Normal"}")
    }
    DropdownMenu(expanded = priorityExpanded, onDismissRequest = { priorityExpanded = false }) {
        priorityLabels.forEach { (value, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    viewModel.updateTask(task.id, priority = value)
                    priorityExpanded = false
                }
            )
        }
    }
}
```

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 7: Add assignee display + edit

Check if profiles list is loaded (`viewModel.profiles`). If loaded, add:

```kotlin
var assigneeExpanded by remember { mutableStateOf(false) }
val profiles by viewModel.profiles.collectAsState()

Box {
    OutlinedButton(onClick = { assigneeExpanded = true }) {
        Text("Assignee: ${task.assignee ?: "Unassigned"}")
    }
    DropdownMenu(expanded = assigneeExpanded, onDismissRequest = { assigneeExpanded = false }) {
        DropdownMenuItem(
            text = { Text("Unassigned") },
            onClick = {
                viewModel.assignTask(task.id, "")
                assigneeExpanded = false
            }
        )
        profiles.forEach { profile ->
            DropdownMenuItem(
                text = { Text(profile) },
                onClick = {
                    viewModel.assignTask(task.id, profile)
                    assigneeExpanded = false
                }
            )
        }
    }
}
```

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 8: Add task metadata chips (age, progress, warnings)

```kotlin
// Metadata chips row
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    task.age?.createdAgeSeconds?.let { ageSec ->
        AssistChip(onClick = {}, label = { Text(formatAge(ageSec)) })
    }
    task.progress?.let { prog ->
        if (prog.total > 0) {
            AssistChip(onClick = {}, label = { Text("${prog.done}/${prog.total} done") })
        }
    }
    task.warnings?.forEach { warning ->
        AssistChip(
            onClick = {},
            label = { Text(warning.take(20)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        )
    }
}
```

Helper function:
```kotlin
fun formatAge(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        else -> "${seconds / 86400}d"
    }
}
```

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 9: Commit

```bash
cd ~/.hermes/projects/HermesCompanion
git add app/src/main/java/org/hermes/community/companion/data/Models.kt app/src/main/java/org/hermes/community/companion/KanbanScreen.kt app/src/main/java/org/hermes/community/companion/MainViewModel.kt
git commit -m "feat(kanban): add missing columns + task detail editing

- Add scheduled and review columns to KanbanCounts model
- Add inline editable title and body in task detail
- Add status action buttons row (all 8 statuses)
- Add priority dropdown (Normal/High/Urgent)
- Add assignee dropdown from profiles list
- Add metadata chips (age, progress, warnings)"
```

## Done criteria

- [ ] `./gradlew assembleDebug` exits 0
- [ ] `./gradlew test` exits 0
- [ ] KanbanCounts has `scheduled` and `review` fields
- [ ] All 8 status columns render in KanbanScreen
- [ ] Task detail has editable title, body, priority, assignee, status actions
- [ ] Metadata chips display when data is available
- [ ] `git status` is CLEAN

## STOP conditions

- If `MainViewModel.STATUSES` is not accessible from KanbanScreen — make it public/internal in MainViewModel.
- If `viewModel.profiles` flow doesn't exist — check the actual property name in MainViewModel (may be `_profiles` or `profileList`).
- If `viewModel.updateTask()` method doesn't exist — check how PATCH calls are made. May need to add a wrapper method.
- If the task detail sheet doesn't exist as a separate composable — find where task details are currently rendered (bottom sheet? full screen? dialog?).

## Maintenance notes

- The daemon's PATCH endpoint already supports title/body/priority updates via direct SQLite access. No daemon changes needed.
- Assignee assignment uses `POST /api/kanban/tasks/{id}/assign` — verify this endpoint exists in server.py (it does, line 777).
- The `profiles` endpoint (`GET /api/kanban/profiles`) is already registered (line 771). Verify `loadProfiles()` is called on KanbanScreen mount.
