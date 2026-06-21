# Plan 031: Add Task Dependencies Endpoint to Server

## Status
- **Priority**: P1 | **Effort**: S | **Risk**: LOW | **Depends on**: none | **Category**: bug
- **Planned at**: commit `b09390e`, 2026-06-19

## Why this matters
MainViewModel.addDependency() (line 556) calls `POST /api/kanban/links` but server has no such route. CLI supports `hermes kanban link PARENT CHILD`.

## Current state
**MainViewModel.kt:556-568**: calls `c.post("/api/kanban/links?board=...", body)` → 404
**server/server.py**: no `/api/kanban/links` route

## Scope
**In scope**: `server/server.py` only

## Steps

### Step 1: Add handler
```python
async def handle_kanban_link(request: web.Request) -> web.Response:
    board = request.query.get("board", "")
    body = {}
    try: body = await request.json()
    except Exception: pass
    parent_id = body.get("parent_id", "")
    child_id = body.get("child_id", "")
    if not parent_id or not child_id:
        return web.json_response({"error": {"code": "VALIDATION_ERROR", "message": "parent_id and child_id required"}}, status=422)
    code, _, err = _kanban(["link", parent_id, child_id], board=board)
    if code != 0:
        return web.json_response({"error": {"code": "INTERNAL_ERROR", "message": err or "Failed"}}, status=500)
    return web.json_response({"ok": True}, status=201)
```

### Step 2: Register route
```python
app.router.add_post("/api/kanban/links", handle_kanban_link)
```

**Verify**: `python3 -c "from server.server import create_app; print('OK')"` → `OK`

## Done criteria
- [ ] `POST /api/kanban/links` returns 201
- [ ] Server tests pass
- [ ] `git status` CLEAN

## STOP conditions
- `hermes kanban link` syntax differs from `link PARENT CHILD`
