# Plan 030: Add Task Creation Endpoint to Server

> **Executor instructions**: Follow this plan step by step. Run every verification command and confirm the expected result before moving to the next step. If anything in the "STOP conditions" occurs, stop and report.

## Status
- **Priority**: P0
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: bug
- **Planned at**: commit `b09390e`, 2026-06-19

## Why this matters
MainViewModel.createTask() calls `POST /api/kanban/tasks?board=<slug>` (line 649) but the server has NO route for `POST /api/kanban/tasks`. Users tap "Create Task" and nothing happens — 404 silently.

## Current state
**server/server.py** route table (lines 773-780) — has GET tasks, individual task routes, but NO POST for creation:
```python
app.router.add_get("/api/kanban/tasks", handle_kanban_tasks_list)
app.router.add_get("/api/kanban/tasks/{task_id}", handle_kanban_task_show)
# Missing: app.router.add_post("/api/kanban/tasks", handle_kanban_task_create)
```

**MainViewModel.kt:638-653** — the client call that 404s:
```kotlin
fun createTask(title: String, status: String, assignee: String, priority: Int) {
    c.post("/api/kanban/tasks?board=${boardSlug.value}", body)
}
```

The Hermes CLI supports: `hermes kanban add --title "..." --assignee "..." --priority N`

## Commands
| Purpose | Command | Expected |
|---------|---------|----------|
| Import check | `cd ~/.hermes/projects/HermesCompanion && python3 -c "from server.server import create_app; print('OK')"` | `OK` |
| Server tests | `python3 -m pytest server/test_server.py -x -q` | all pass |

## Scope
**In scope**: `server/server.py`, `server/test_server.py`
**Out of scope**: Android ViewModel/UI (already call correct endpoint)

## Steps

### Step 1: Add handler after handle_kanban_tasks_list (~line 268)
```python
async def handle_kanban_task_create(request: web.Request) -> web.Response:
    board = request.query.get("board", "")
    body = {}
    try: body = await request.json()
    except Exception: pass
    title = body.get("title", "").strip()
    if not title:
        return web.json_response({"error": {"code": "VALIDATION_ERROR", "message": "title required"}}, status=422)
    assignee = body.get("assignee", "ops")
    priority = body.get("priority", 3)
    args = ["add", "--title", title, "--assignee", assignee, "--priority", str(priority)]
    code, out, err = _kanban(args, board=board)
    if code != 0:
        return web.json_response({"error": {"code": "INTERNAL_ERROR", "message": err or "Failed"}}, status=500)
    try: return web.json_response(json.loads(out), status=201)
    except json.JSONDecodeError: return web.json_response({"ok": True, "title": title}, status=201)
```

### Step 2: Register route in create_app() BEFORE {task_id} routes (~line 773)
```python
app.router.add_post("/api/kanban/tasks", handle_kanban_task_create)
```

**Verify**: `python3 -c "from server.server import create_app; print('OK')"` → `OK`

## Done criteria
- [ ] `POST /api/kanban/tasks?board=test` with `{"title":"Test"}` returns 201
- [ ] Server tests pass
- [ ] `git status` CLEAN after commit

## STOP conditions
- `hermes kanban add` CLI syntax differs (check with `hermes kanban add --help`)
