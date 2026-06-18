# Plan 006: Fix Hermes Companion — Kanban task edits, action buttons, bulk ops, and chat timeout

## Status
- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: bug
- **Planned at**: commit `90da4c3`, 2026-06-18

## Why this matters

The Hermes Companion Android app has four interrelated bugs that make the Kanban board and chat unusable:

1. **Task edits don't save** — Tapping Save after editing title/body/priority silently fails. The app sends `PATCH /api/kanban/tasks/{id}` but the server has no PATCH handler (returns 405). The error is caught and displayed but the UI doesn't refresh, making it look like nothing happened.

2. **Action buttons don't work** — Triage, Ready, Running, Block, Unblock, Complete, Archive buttons all call `updateTaskStatus()` which uses PATCH (405) or `bulkUpdateStatus()` which POSTs to `/api/kanban/tasks/bulk` (404 — no handler). None of these operations actually modify the task on the server.

3. **Delete doesn't work** — `deleteTask()` sends `DELETE /api/kanban/tasks/{id}` but the server has no DELETE handler for tasks (returns 405).

4. **Chat messages fail** — The chat proxy works but the upstream Hermes API (port 8642) is extremely slow (10-30s for LLM responses). The Android OkHttp client's 300s read timeout should be sufficient, but the companion's `handle_chat_proxy` forwards the request correctly. The real issue is the Hermes API server is overloaded or the model is slow. Need to verify the proxy isn't stripping the streaming response.

## Current state

### Server routes (what exists)
The companion server at `~/.hermes/companion/server.py` registers these kanban routes:
- `GET /api/kanban/boards` — lists boards ✅
- `POST /api/kanban/boards` — creates board ✅
- `POST /api/kanban/boards/{slug}/rename` — renames board ✅
- `POST /api/kanban/boards/{slug}/archive` — archives board ✅
- `DELETE /api/kanban/boards/{slug}` — deletes board ✅
- `GET /api/kanban/profiles` — lists assignees ✅
- `GET /api/kanban/stats` — board stats ✅
- `GET /api/kanban/tasks` — lists tasks ✅
- `GET /api/kanban/tasks/{task_id}` — shows task ✅
- `POST /api/kanban/tasks/{task_id}/complete` — completes task ✅
- `POST /api/kanban/tasks/{task_id}/comment` — adds comment ✅
- `POST /api/kanban/tasks/{task_id}/assign` — assigns task ✅

### Server routes (what's MISSING)
- `PATCH /api/kanban/tasks/{task_id}` — **MISSING** (needed for title/body/status/priority edits)
- `DELETE /api/kanban/tasks/{task_id}` — **MISSING** (needed for delete)
- `POST /api/kanban/tasks/bulk` — **MISSING** (needed for bulk operations)

### Android client code
`app/src/main/java/org/hermes/community/companion/MainViewModel.kt`:
- `updateTaskTitle()` (line 446): `c.patch("/api/kanban/tasks/$taskId?board=...", {title})` → 405
- `updateTaskBody()` (line 458): `c.patch("/api/kanban/tasks/$taskId?board=...", {body})` → 405
- `updateTaskStatus()` (line 470): `c.patch("/api/kanban/tasks/$taskId?board=...", {status})` → 405
- `updateTaskPriority()` (line 483): `c.patch("/api/kanban/tasks/$taskId?board=...", {priority})` → 405
- `deleteTask()` (line 495): `c.delete("/api/kanban/tasks/$taskId?board=...")` → 405
- `bulkUpdateStatus()` (line 608): `c.post("/api/kanban/tasks/bulk?board=...", {action, value})` → 404
- `bulkReassign()` (line 626): `c.post("/api/kanban/tasks/bulk?board=...", {action, value})` → 404
- `completeTask()` (line 403): `c.post("/api/kanban/tasks/$taskId/complete?board=...")` → ✅ works
- `commentOnTask()` (line 415): `c.post("/api/kanban/tasks/$taskId/comment?board=...", {text})` → ✅ works

### Chat issue
The companion's `handle_chat_proxy` (line 228) forwards to Hermes API at `http://127.0.0.1:8642/v1/chat/completions`. The upstream is slow (LLM inference takes 10-30s). The companion has `ClientTimeout(total=300)` which is fine. The Android `ApiClient` has `readTimeout(300, TimeUnit.SECONDS)` which is also fine. The issue is likely that the Hermes API server is overloaded or the model response is being streamed but the companion's `await upstream.read()` blocks until the full response arrives. Need to verify the proxy handles streaming correctly.

## Scope

**In scope (server changes only)**:
- `~/.hermes/companion/server.py` — Add PATCH, DELETE, and bulk handlers
- `~/.hermes/projects/HermesCompanion/server/server.py` — Mirror the same changes

**Out of scope**:
- Android app code (the client is correct — it sends the right requests to the right URLs)
- Hermes API server (port 8642) — that's a separate service
- Chat model performance — the LLM is inherently slow; we just need to make sure the proxy doesn't break streaming

## Git workflow
- Branch: `fix/kanban-patch-delete-bulk`
- Commit message style: `fix(server): <imperative description>`
- Push to origin/master when done

## Steps

### Step 1: Add PATCH handler for task updates

Add to `server.py` (after the existing `handle_kanban_task_assign` function, around line 475):

```python
async def handle_kanban_task_update(request: web.Request) -> web.Response:
    """PATCH /api/kanban/tasks/{task_id} — update task fields (title, body, status, priority)."""
    task_id = request.match_info["task_id"]
    board = request.query.get("board", "")
    body = await request.json()
    
    # Build hermes kanban edit command args
    args = ["edit", task_id]
    
    if "title" in body:
        args.extend(["--title", body["title"]])
    if "body" in body:
        args.extend(["--body", body["body"]])
    if "status" in body:
        args.extend(["--status", body["status"]])
    if "priority" in body:
        args.extend(["--priority", str(body["priority"])])
    
    code, out, err = _kanban(args, board=board)
    if code != 0:
        return web.json_response(
            {"error": {"code": "INTERNAL_ERROR", "message": err or "Failed to update task"}},
            status=500,
        )
    return web.json_response({"ok": True})
```

Register it in `create_app()`:
```python
app.router.add_patch("/api/kanban/tasks/{task_id}", handle_kanban_task_update)
```

### Step 2: Add DELETE handler for task deletion

```python
async def handle_kanban_task_delete(request: web.Request) -> web.Response:
    """DELETE /api/kanban/tasks/{task_id} — delete a task."""
    task_id = request.match_info["task_id"]
    board = request.query.get("board", "")
    code, _, err = _kanban(["delete", task_id], board=board)
    if code != 0:
        return web.json_response(
            {"error": {"code": "INTERNAL_ERROR", "message": err or "Failed to delete task"}},
            status=500,
        )
    return web.json_response({"ok": True})
```

Register:
```python
app.router.add_delete("/api/kanban/tasks/{task_id}", handle_kanban_task_delete)
```

### Step 3: Add bulk operations handler

```python
async def handle_kanban_bulk(request: web.Request) -> web.Response:
    """POST /api/kanban/tasks/bulk — bulk update tasks (set_status, set_assignee)."""
    board = request.query.get("board", "")
    body = await request.json()
    
    task_ids = body.get("task_ids", [])
    action = body.get("action", "")
    value = body.get("value", "")
    
    if not task_ids or not action:
        return web.json_response(
            {"error": {"code": "VALIDATION_ERROR", "message": "task_ids and action required"}},
            status=422,
        )
    
    # Map action to hermes kanban CLI args
    results = []
    for task_id in task_ids:
        if action == "set_status":
            args = ["edit", task_id, "--status", value]
        elif action == "set_assignee":
            args = ["assign", task_id, value]
        elif action == "archive":
            args = ["archive", task_id]
        else:
            return web.json_response(
                {"error": {"code": "VALIDATION_ERROR", "message": f"unknown action: {action}"}},
                status=422,
            )
        code, _, err = _kanban(args, board=board)
        if code != 0:
            results.append({"task_id": task_id, "error": err})
        else:
            results.append({"task_id": task_id, "ok": True})
    
    return web.json_response({"results": results})
```

Register:
```python
app.router.add_post("/api/kanban/tasks/bulk", handle_kanban_bulk)
```

### Step 4: Verify the hermes kanban CLI supports the needed subcommands

Before finalizing, verify these commands work:
```bash
hermes kanban edit --help
hermes kanban delete --help
hermes kanban assign --help
hermes kanban archive --help
```

If `edit` doesn't support `--title`/`--body`/`--status`/`--priority` flags, use `hermes kanban show` to check the actual CLI interface and adjust the args accordingly.

### Step 5: Sync to daemon repo and restart

```bash
cp ~/.hermes/companion/server.py ~/.hermes/projects/HermesCompanion/server/server.py
systemctl --user restart hermes-companion
```

### Step 6: Verify endpoints work

```bash
# Test PATCH (update title)
curl -s -u kevin:'Kevi667n!1991!' -X PATCH "http://127.0.0.1:8777/api/kanban/tasks/t_ab413aab?board=agent-engineering-skills-integration" \
  -H "Content-Type: application/json" -d '{"title": "Test update"}'
# Expected: {"ok": true}

# Test DELETE
curl -s -u kevin:'Kevi667n!1991!' -X DELETE "http://127.0.0.1:8777/api/kanban/tasks/t_ab413aab?board=agent-engineering-skills-integration"
# Expected: {"ok": true}

# Test bulk status update
curl -s -u kevin:'Kevi667n!1991!' -X POST "http://127.0.0.1:8777/api/kanban/tasks/bulk?board=agent-engineering-skills-integration" \
  -H "Content-Type: application/json" -d '{"task_ids": ["t_ab413aab"], "action": "set_status", "value": "done"}'
# Expected: {"results": [{"task_id": "t_ab413aab", "ok": true}]}
```

### Step 7: Investigate chat timeout

The chat proxy forwards to Hermes API which calls the LLM. The proxy itself is correct (uses `async with` and `await upstream.read()`). The issue is the upstream is slow. Check:

1. Is the Hermes API actually processing or stuck? Check `journalctl --user -u hermes-gateway` for errors.
2. Is the model configured correctly? `hermes config show | grep model`
3. Test with a very short message and measure response time.

If the upstream is genuinely slow (LLM taking >30s), the fix is to either:
- Increase Android OkHttp timeout (already 300s, should be enough)
- Or make the companion return a streaming response instead of buffering the full response

For now, verify the proxy isn't the bottleneck by testing directly:
```bash
time curl -s -X POST http://127.0.0.1:8642/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer VfTDD7Kdw8RIwp4jVnEN7iQEfrynjDorZU3Jr73jZOk" \
  -d '{"model":"hermes-agent","messages":[{"role":"user","content":"hi"}],"stream":false}' \
  --max-time 60
```

If this takes >30s, the issue is the Hermes API/LLM, not the companion proxy.

## Test plan

After implementing:
1. `curl` PATCH a task title → `{"ok": true}`
2. `curl` DELETE a task → `{"ok": true}`
3. `curl` POST bulk set_status → `{"results": [{"ok": true}]}`
4. Open Android app, edit a task title, tap Save → title should update
5. Open Android app, tap Complete button → task should move to Done
6. Open Android app, tap Archive button → task should move to Archived
7. Open Android app, tap Delete → task should be removed

## Done criteria

- [ ] `PATCH /api/kanban/tasks/{id}` returns 200 with `{"ok": true}`
- [ ] `DELETE /api/kanban/tasks/{id}` returns 200 with `{"ok": true}`
- [ ] `POST /api/kanban/tasks/bulk` returns 200 with results array
- [ ] Server changes synced to both `~/.hermes/companion/server.py` and `~/.hermes/projects/HermesCompanion/server/server.py`
- [ ] Daemon restarted and health check passes
- [ ] Git committed and pushed
- [ ] Chat proxy verified working (or upstream slowness documented)

## STOP conditions

- `hermes kanban edit` doesn't support the flags we need → check actual CLI and adjust
- `hermes kanban delete` doesn't exist → check actual CLI and adjust
- PATCH/DELETE routes conflict with existing routes → rename/adjust

## Maintenance notes

When adding new kanban operations to the Android app, ensure the server has a matching handler BEFORE shipping the client change. The pattern is: server handler first, then client call.
