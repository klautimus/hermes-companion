# Plan 032: Fix Task Attachment Association

## Status
- **Priority**: P1 | **Effort**: S | **Risk**: LOW | **Depends on**: none | **Category**: bug
- **Planned at**: commit `b09390e`, 2026-06-19

## Why this matters
uploadTaskAttachment() uploads a file but never links it to the task. The attachment exists on disk but the task's attachment list stays empty.

## Current state
**MainViewModel.kt:542-554**: `c.uploadAttachment(bytes, fileName, mimeType)` — taskId is passed but never sent to server.

## Scope
**In scope**: `server/server.py`, `MainViewModel.kt`, `ApiClient.kt`

## Steps

### Step 1: Add server endpoint
```python
async def handle_kanban_task_attachment(request):
    task_id = request.match_info["task_id"]
    board = request.query.get("board", "")
    reader = await request.multipart()
    file_field = await reader.next()
    # ... save file (same as handle_attachment_upload) ...
    # Associate with task in DB
    conn = sqlite3.connect(str(db_path))
    conn.execute("INSERT INTO task_attachments (task_id, attachment_id, filename) VALUES (?,?,?)", (task_id, att_id, filename))
    conn.commit(); conn.close()
    return web.json_response({"id": att_id, "filename": filename}, status=201)
```
Register: `app.router.add_post("/api/kanban/tasks/{task_id}/attachments", handle_kanban_task_attachment)`

### Step 2: Fix ViewModel
Change `uploadTaskAttachment` to call task-specific endpoint via new ApiClient method.

## Done criteria
- [ ] Upload from task detail → task shows attachment
- [ ] `git status` CLEAN
