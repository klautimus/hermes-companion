# Plan 027: Wire Task Detail Attachment Upload + Delete

> **Executor instructions**: Follow this plan step by step.

## Status
- **Priority**: P2 | **Effort**: M | **Risk**: LOW | **Depends on**: nothing
- **Category**: bug
- **Planned at**: commit `492da45`, 2026-06-19

## Why this matters

The TaskDetailSheet in KanbanScreen.kt shows an upload button and a delete button for
attachments, but both are disabled stubs (`enabled = false`, `/* TODO: file picker */`).
For public launch, either wire them or remove them — disabled buttons look broken.

## Current state

- `KanbanScreen.kt:1171`: `IconButton(onClick = { /* TODO: file picker */ }, enabled = false)` — Upload button
- `KanbanScreen.kt:1207`: `IconButton(onClick = { /* TODO: delete attachment */ }, enabled = false)` — Delete button
- Daemon already has `POST /api/attachments` (upload). ApiClient already has `uploadAttachment()`.
- Composer.kt has a working file picker pattern to reference.

## Scope

**In scope**:
- `KanbanScreen.kt` — wire the buttons (or remove the delete button)
- `MainViewModel.kt` — add uploadTaskAttachment method

## Steps

### Step 1: Add uploadTaskAttachment to MainViewModel

```kotlin
fun uploadTaskAttachment(taskId: String, bytes: ByteArray, fileName: String, mimeType: String) {
    val c = client() ?: return
    viewModelScope.launch {
        try {
            c.uploadAttachment(bytes, fileName, mimeType)
            loadTask(taskId)
        } catch (e: Exception) {
            _kanbanError.value = e.message
        }
    }
}
```

### Step 2: Wire upload button

Add a `rememberLauncherForActivityResult(GetContent())` contract for file picking
(follow the pattern in Composer.kt lines 60-65). On result, call
`viewModel.uploadTaskAttachment(task.id, bytes, name, mime)`. Remove `enabled = false`.

### Step 3: Remove delete button (recommended)

Attachments on kanban tasks are typically immutable once uploaded. Remove the delete
IconButton entirely rather than wiring it to a non-existent daemon endpoint.

### Step 4: Verify

```bash
./gradlew assembleDebug --no-daemon
# Expected: BUILD SUCCESSFUL
```

## Done criteria
- [ ] `./gradlew assembleDebug --no-daemon` exits 0
- [ ] No `TODO` comments remain in KanbanScreen.kt for attachment handling
- [ ] `git status` is CLEAN
